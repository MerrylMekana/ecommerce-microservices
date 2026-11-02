import { Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-cart-toast',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './cart-toast.html'
})
export class CartToastComponent {
  readonly message = input('');
  readonly dismiss = output<void>();
}
