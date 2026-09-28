import { Component, inject, OnInit } from '@angular/core';
import { ProductService } from '../../services/productService';
import { Product } from '../../model/product';

@Component({
  selector: 'app-home-page',
  standalone: true,
  imports: [],
  templateUrl: './home-page.html',
  styleUrl: './home-page.css'
})
export class HomePage implements OnInit {

  private readonly productService = inject(ProductService);

  products: Product[] = [];

  ngOnInit(): void {

    this.productService.getProducts().subscribe({
      next: (products) => {
        this.products = products;
      },

      error: (error) => {
        console.error('Failed to load products:', error);
      }
    });

  }
}