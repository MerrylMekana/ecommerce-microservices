import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Product } from '../../model/product';
import { CartService } from '../../services/cart.service';
import { ProductService } from '../../services/productService';
import { QuantityControlComponent } from '../../shared/quantity-control/quantity-control';
import { formatPrice } from '../../shared/format-price';
import { CartToastComponent } from '../../shared/cart-toast/cart-toast';

@Component({
  selector: 'app-product-details-page',
  standalone: true,
  imports: [RouterLink, QuantityControlComponent, CartToastComponent],
  templateUrl: './product-details-page.html'
})
export class ProductDetailsPageComponent implements OnInit {
  private readonly productService = inject(ProductService);
  private readonly route = inject(ActivatedRoute);
  private readonly cartService = inject(CartService);
  readonly product = signal<Product | null>(null);
  readonly quantity = signal(1);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly added = signal('');

  ngOnInit(): void {
    const skuCode = this.route.snapshot.paramMap.get('skuCode') ?? '';
    this.productService.getProducts().subscribe({
      next: products => {
        this.product.set(products.find(
          item => item.skuCode.toLocaleLowerCase() === skuCode.toLocaleLowerCase()
        ) ?? null);
        this.loading.set(false);
      },
      error: error => {
        this.error.set(error?.status === 401 || error?.status === 403
          ? 'Please sign in to view product details.'
          : 'We could not load this product. Please try again.');
        this.loading.set(false);
      }
    });
  }

  addToCart(): void {
    const product = this.product();
    if (!product) {
      return;
    }
    this.cartService.add(product.skuCode, this.quantity());
    this.added.set(`${product.name} added to your cart.`);
  }

  formatPrice(price: number): string {
    return formatPrice(price);
  }
}
