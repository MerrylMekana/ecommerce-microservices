import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { CartService } from '../../services/cart.service';
import { ProductService } from '../../services/productService';
import { OrderService } from '../../services/orderService';
import { CheckoutAttemptService } from '../../services/checkout-attempt.service';
import { OrderConfirmationSummary, OrderRequest } from '../../model/order';
import { Product } from '../../model/product';
import { formatPrice } from '../../shared/format-price';

interface CheckoutLine {
  skuCode: string;
  quantity: number;
  product: Product | null;
  lineTotal: number;
}

@Component({
  selector: 'app-checkout-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './checkout-page.html'
})
export class CheckoutPageComponent implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly oidcSecurityService = inject(OidcSecurityService);
  private readonly router = inject(Router);
  readonly cartService = inject(CartService);
  private readonly productService = inject(ProductService);
  private readonly orderService = inject(OrderService);
  private readonly checkoutAttemptService = inject(CheckoutAttemptService);

  readonly form = this.formBuilder.nonNullable.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]]
  });
  readonly products = signal<Product[]>([]);
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly error = signal('');
  readonly idempotencyConflict = signal(false);
  readonly lines = computed<CheckoutLine[]>(() => this.cartService.items().map(entry => {
    const product = this.products().find(item =>
      item.skuCode.toLocaleLowerCase() === entry.skuCode.toLocaleLowerCase()
    ) ?? null;
    return {
      skuCode: entry.skuCode,
      quantity: entry.quantity,
      product,
      lineTotal: product ? product.price * entry.quantity : 0
    };
  }));
  readonly total = computed(() => this.lines().reduce((sum, line) => sum + line.lineTotal, 0));
  readonly unavailableItems = computed(() => this.lines().filter(line => !line.product));

  ngOnInit(): void {
    this.productService.getProducts().subscribe({
      next: products => {
        this.products.set(products);
        this.loading.set(false);
      },
      error: error => {
        this.error.set(error?.status === 401 || error?.status === 403
          ? 'Please sign in to load current catalog and price information.'
          : 'We could not load current product details. Your cart has not been changed.');
        this.loading.set(false);
      }
    });

    this.oidcSecurityService.userData$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(({ userData }) => {
        if (this.form.pristine && userData) {
          this.form.patchValue({
            firstName: userData.given_name ?? '',
            lastName: userData.family_name ?? '',
            email: userData.email ?? ''
          });
        }
      });
  }

  async submit(): Promise<void> {
    this.error.set('');
    this.idempotencyConflict.set(false);
    if (this.loading() || this.unavailableItems().length > 0 || this.cartService.items().length === 0) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const userDetails = this.form.getRawValue();
    const request: OrderRequest = {
      items: this.lines().map(line => ({
        skuCode: line.skuCode,
        price: line.product!.price,
        quantity: line.quantity
      })).sort((left, right) => left.skuCode.localeCompare(right.skuCode)),
      userDetails
    };

    this.submitting.set(true);
    let idempotencyKey: string;
    try {
      idempotencyKey = await this.checkoutAttemptService.idempotencyKeyFor(request);
    } catch (error) {
      console.error('Unable to prepare a persistent checkout attempt', error);
      this.error.set('We could not save this checkout attempt safely. Please check browser storage and try again.');
      this.submitting.set(false);
      return;
    }
    this.orderService.placeOrder(request, idempotencyKey).subscribe({
      next: () => this.completeOrder(request),
      error: error => {
        this.error.set(this.checkoutErrorMessage(error));
        this.idempotencyConflict.set(
          error instanceof HttpErrorResponse && error.error?.code === 'IDEMPOTENCY_KEY_REUSED'
        );
        this.submitting.set(false);
      }
    });
  }

  formatPrice(price: number): string {
    return formatPrice(price);
  }

  private completeOrder(request: OrderRequest): void {
    const summary: OrderConfirmationSummary = {
      items: request.items.map(item => ({
        ...item,
        name: this.products().find(product => product.skuCode === item.skuCode)?.name ?? item.skuCode
      })),
      total: this.total(),
      completedAt: new Date().toISOString()
    };
    this.checkoutAttemptService.saveConfirmation(summary);
    this.cartService.clear();
    this.checkoutAttemptService.clearAttempt();
    this.submitting.set(false);
    void this.router.navigateByUrl('/order-confirmation');
  }

  private checkoutErrorMessage(error: unknown): string {
    if (!(error instanceof HttpErrorResponse)) {
      return 'We could not confirm your order. Your cart is saved; retry checkout without changing it.';
    }
    const code = error.error?.code;
    if (code === 'INSUFFICIENT_INVENTORY') {
      return 'One or more items do not have enough stock. Your cart is unchanged; adjust quantities and retry.';
    }
    if (code === 'SKU_NOT_FOUND' || error.status === 404) {
      return 'A product in your cart is no longer available. Your cart is unchanged.';
    }
    if (code === 'IDEMPOTENCY_KEY_REUSED') {
      return 'The saved checkout key was used for a different request. Start a new attempt to submit this exact order.';
    }
    if (code === 'INVENTORY_RELEASE_PENDING' || code === 'INVENTORY_UNAVAILABLE') {
      return 'Checkout is temporarily unavailable while inventory is being confirmed. Your cart is saved; retry the same request.';
    }
    return error.error?.detail
      ?? 'We could not confirm your order. Your cart is saved; retry checkout without changing it.';
  }

  startNewAttempt(): void {
    this.checkoutAttemptService.clearAttempt();
    this.idempotencyConflict.set(false);
    this.error.set('');
  }
}
