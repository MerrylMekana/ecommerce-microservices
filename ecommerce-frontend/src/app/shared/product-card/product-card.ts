import { Component, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Product } from '../../model/product';
import { CartService } from '../../services/cart.service';
import { QuantityControlComponent } from '../quantity-control/quantity-control';
import { formatPrice } from '../format-price';

@Component({
  selector: 'app-product-card',
  standalone: true,
  imports: [RouterLink, QuantityControlComponent],
  templateUrl: './product-card.html'
})
export class ProductCardComponent {
  readonly product = input.required<Product>();
  readonly added = output<string>();
  readonly quantity = signal(1);

  constructor(private readonly cartService: CartService) {}

  addToCart(): void {
    this.cartService.add(this.product().skuCode, this.quantity());
    this.added.emit(this.product().name);
  }

  formatPrice(price: number): string {
    return formatPrice(price);
  }
}
