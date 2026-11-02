import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CheckoutPageComponent } from './checkout-page';
import { CartService } from '../../services/cart.service';
import { ProductService } from '../../services/productService';
import { OrderService } from '../../services/orderService';
import { CheckoutAttemptService } from '../../services/checkout-attempt.service';

describe('CheckoutPageComponent', () => {
  let fixture: ComponentFixture<CheckoutPageComponent>;
  let component: CheckoutPageComponent;
  let cartService: CartService;
  let placeOrder: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    localStorage.clear();
    sessionStorage.clear();
    placeOrder = vi.fn(() => of('Order Placed Successfully'));

    await TestBed.configureTestingModule({
      imports: [CheckoutPageComponent],
      providers: [
        provideRouter([]),
        {
          provide: OidcSecurityService,
          useValue: {
            userData$: of({
              userData: {
                given_name: 'Taylor',
                family_name: 'Customer',
                email: 'buyer@example.com'
              }
            })
          }
        },
        {
          provide: ProductService,
          useValue: {
            getProducts: () => of([
              {
                id: 'product-1',
                skuCode: 'IPHONE-15',
                name: 'Phone',
                description: 'A product',
                price: 799
              },
              {
                id: 'product-2',
                skuCode: 'AIRPODS-PRO',
                name: 'Earbuds',
                description: 'Another product',
                price: 249
              }
            ])
          }
        },
        { provide: OrderService, useValue: { placeOrder } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(CheckoutPageComponent);
    component = fixture.componentInstance;
    cartService = TestBed.inject(CartService);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('sends the multi-item request and clears cart only after success', async () => {
    const navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    cartService.add('IPHONE-15', 2);
    cartService.add('AIRPODS-PRO', 1);
    await component.submit();

    expect(placeOrder).toHaveBeenCalledTimes(1);
    const [request, key] = placeOrder.mock.calls[0] as [
      { items: Array<{ skuCode: string; price: number; quantity: number }> },
      string
    ];
    expect(request.items).toEqual([
      { skuCode: 'AIRPODS-PRO', price: 249, quantity: 1 },
      { skuCode: 'IPHONE-15', price: 799, quantity: 2 }
    ]);
    expect(key).toBeTruthy();
    expect(cartService.items()).toEqual([]);
    expect(localStorage.getItem('hearth-harbor-checkout-attempt')).toBeNull();
    expect(TestBed.inject(CheckoutAttemptService).readConfirmation()?.total).toBe(1847);
    expect(navigateByUrl).toHaveBeenCalledWith('/order-confirmation');
  });

  it('retains cart and idempotency key after a failed request and reuses the key on retry', async () => {
    placeOrder.mockImplementation(() => throwError(() =>
      new HttpErrorResponse({ status: 503, error: { code: 'INVENTORY_UNAVAILABLE' } })
    ));
    cartService.add('IPHONE-15', 1);

    await component.submit();
    await component.submit();

    expect(cartService.items()).toEqual([{ skuCode: 'IPHONE-15', quantity: 1 }]);
    expect(placeOrder).toHaveBeenCalledTimes(2);
    expect(placeOrder.mock.calls[0][1]).toBe(placeOrder.mock.calls[1][1]);
    expect(component.error()).toContain('temporarily unavailable');
  });

  it('allows an explicit new key after an idempotency conflict', async () => {
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    placeOrder.mockImplementationOnce(() => throwError(() =>
      new HttpErrorResponse({ status: 409, error: { code: 'IDEMPOTENCY_KEY_REUSED' } })
    ));
    cartService.add('IPHONE-15', 1);
    await component.submit();
    const oldKey = placeOrder.mock.calls[0][1];

    expect(component.idempotencyConflict()).toBe(true);
    expect(cartService.items()).toHaveLength(1);
    component.startNewAttempt();
    await component.submit();

    expect(placeOrder.mock.calls[1][1]).not.toBe(oldKey);
    expect(cartService.items()).toEqual([]);
  });
});
