import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { ProductService } from '../../services/productService';
import { Product } from '../../model/product';
import { FormsModule } from '@angular/forms';
import { OrderService } from '../../services/orderService';
import { OidcSecurityService } from 'angular-auth-oidc-client';

@Component({
  selector: 'app-home-page',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './home-page.html',
  styleUrl: './home-page.css'
})
export class HomePageComponent implements OnInit {

  products: Product[] = [];
  private readonly orderIdempotencyKeys = new Map<string, string>();
  loading = true;
  error = '';
  orderSuccess = false;
  orderError = '';

  constructor(
    private productService: ProductService,
    private orderService: OrderService,
    private changeDetectorRef: ChangeDetectorRef,
    private oidcSecurityService: OidcSecurityService
  ) {}

  ngOnInit(): void {

    this.productService.getProducts().subscribe({
      next: (products) => {
        console.log('Products received:', products);

        this.products = products;
        this.loading = false;

        this.changeDetectorRef.detectChanges();
      },

      error: (error) => {
        console.error('Failed to load products:', error);

        this.error = 'Unable to load products';
        this.loading = false;

        this.changeDetectorRef.detectChanges();
      }
    });
  }
  orderProduct(product: Product, quantity: number): void {
    this.orderSuccess = false;
    this.orderError = '';
    this.oidcSecurityService.userData$.subscribe(result => {
      console.log('FULL USER DATA:', result.userData);

    const userDetails = {
      email: result.userData.email,
      firstName: result.userData.given_name,
      lastName: result.userData.family_name,
      // address: ''
    };
    console.log('USER DETAILS:', userDetails);


    const order = {
      skuCode: product.skuCode,
      price: product.price,
      quantity: Number(quantity),
      userDetails: userDetails
    };
    const orderKey = JSON.stringify(order);
    const idempotencyKey = this.orderIdempotencyKeys.get(orderKey) ?? crypto.randomUUID();
    this.orderIdempotencyKeys.set(orderKey, idempotencyKey);

    console.log('Placing order:', order);

    this.orderService.placeOrder(order, idempotencyKey).subscribe({
      next: (response) => {
        console.log('Order response:', response);
        this.orderIdempotencyKeys.delete(orderKey);
        this.orderSuccess = true;
        this.changeDetectorRef.detectChanges();
      },
      error: (error) => {
        console.error('Order failed:', error);

        this.orderError =
          (typeof error?.error === 'string' ? error.error : error?.error?.detail) ||
          'Unable to place order. Product may be out of stock.';
        if (error?.status === 409 || error?.error?.code === 'ORDER_CREATION_FAILED') {
          this.orderIdempotencyKeys.delete(orderKey);
        }

        this.changeDetectorRef.detectChanges();
      }
    });
  });
}
}