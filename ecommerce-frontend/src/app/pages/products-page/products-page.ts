import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Product } from '../../model/product';
import { ProductService } from '../../services/productService';
import { ProductCardComponent } from '../../shared/product-card/product-card';
import { CartToastComponent } from '../../shared/cart-toast/cart-toast';

type ProductSort = 'catalog' | 'name-asc' | 'price-asc' | 'price-desc';

@Component({
  selector: 'app-products-page',
  standalone: true,
  imports: [FormsModule, ProductCardComponent, CartToastComponent],
  templateUrl: './products-page.html'
})
export class ProductsPageComponent implements OnInit {
  private readonly productService = inject(ProductService);
  readonly products = signal<Product[]>([]);
  readonly query = signal('');
  readonly sort = signal<ProductSort>('catalog');
  readonly loading = signal(true);
  readonly error = signal('');
  readonly feedback = signal('');
  readonly visibleProducts = computed(() => {
    const query = this.query().trim().toLocaleLowerCase();
    const products = this.products().filter(product =>
      `${product.name} ${product.skuCode} ${product.description}`.toLocaleLowerCase().includes(query)
    );
    switch (this.sort()) {
      case 'name-asc':
        return [...products].sort((a, b) => a.name.localeCompare(b.name));
      case 'price-asc':
        return [...products].sort((a, b) => a.price - b.price);
      case 'price-desc':
        return [...products].sort((a, b) => b.price - a.price);
      default:
        return products;
    }
  });

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
          ? 'Please sign in to browse the product catalog.'
          : 'We could not load products. Check your connection and try again.');
        this.loading.set(false);
      }
    });
  }

  announceAdded(productName: string): void {
    this.feedback.set(`${productName} added to your cart.`);
  }
}
