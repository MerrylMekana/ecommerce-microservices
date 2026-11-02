import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Product } from '../../model/product';
import { ProductService } from '../../services/productService';
import { ProductCardComponent } from '../../shared/product-card/product-card';
import { CartToastComponent } from '../../shared/cart-toast/cart-toast';

@Component({
  selector: 'app-home-page',
  standalone: true,
  imports: [RouterLink, ProductCardComponent, CartToastComponent],
  templateUrl: './home-page.html',
  styleUrl: './home-page.css'
})
export class HomePageComponent implements OnInit {
  private readonly productService = inject(ProductService);
  readonly products = signal<Product[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly feedback = signal('');

  ngOnInit(): void {
    this.productService.getProducts().subscribe({
      next: products => {
        this.products.set(products);
        this.loading.set(false);
      },
      error: error => {
        this.error.set(this.productErrorMessage(error));
        this.loading.set(false);
      }
    });
  }

  announceAdded(productName: string): void {
    this.feedback.set(`${productName} added to your cart.`);
  }

  private productErrorMessage(error: { status?: number }): string {
    if (error.status === 401 || error.status === 403) {
      return 'Please sign in to browse the product catalog.';
    }
    return 'We could not load the catalog. Please try again.';
  }
}
