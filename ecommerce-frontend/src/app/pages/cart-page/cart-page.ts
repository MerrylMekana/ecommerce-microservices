import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CartEntry } from '../../model/cart';
import { Product } from '../../model/product';
import { CartService } from '../../services/cart.service';
import { ProductService } from '../../services/productService';
import { QuantityControlComponent } from '../../shared/quantity-control/quantity-control';
import { formatPrice } from '../../shared/format-price';

interface CartLine {
  entry: CartEntry;
  product: Product | null;
}

@Component({
  selector: 'app-cart-page',
  standalone: true,
  imports: [RouterLink, QuantityControlComponent],
  templateUrl: './cart-page.html'
})
export class CartPageComponent implements OnInit {
  readonly cartService = inject(CartService);
  private readonly productService = inject(ProductService);
  readonly products = signal<Product[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly feedback = signal('');
  readonly lines = computed<CartLine[]>(() => this.cartService.items().map(entry => ({
    entry,
    product: this.products().find(product =>
      product.skuCode.toLocaleLowerCase() === entry.skuCode.toLocaleLowerCase()
    ) ?? null
  })));
  readonly subtotal = computed(() => this.lines().reduce((total, line) =>
    total + (line.product ? line.product.price * line.entry.quantity : 0), 0
  ));
  readonly hasUnavailableItems = computed(() => this.lines().some(line => !line.product));

  ngOnInit(): void {
    this.loadProducts();
  }

  loadProducts(): void {
    this.loading.set(true);
    this.error.set('');
    this.productService.getProducts().subscribe({
      next: products => {
        this.products.set(products);
        this.loading.set(false);
      },
      error: error => {
        this.error.set(error?.status === 401 || error?.status === 403
          ? 'Please sign in to load current product information.'
          : 'We could not refresh product details. Your cart is safely saved.');
        this.loading.set(false);
      }
    });
  }

  updateQuantity(skuCode: string, quantity: number): void {
    this.cartService.setQuantity(skuCode, quantity);
    this.feedback.set(`Quantity updated for ${skuCode}.`);
  }

  remove(skuCode: string): void {
    this.cartService.remove(skuCode);
    this.feedback.set(`${skuCode} removed from your cart.`);
  }

  clearCart(): void {
    this.cartService.clear();
    this.feedback.set('Cart cleared.');
  }

  formatPrice(price: number): string {
    return formatPrice(price);
  }
}
