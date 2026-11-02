import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { OrderConfirmationSummary } from '../../model/order';
import { CheckoutAttemptService } from '../../services/checkout-attempt.service';
import { formatPrice } from '../../shared/format-price';

@Component({
  selector: 'app-order-confirmation-page',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './order-confirmation-page.html'
})
export class OrderConfirmationPageComponent implements OnInit {
  private readonly checkoutAttemptService = inject(CheckoutAttemptService);
  readonly summary = signal<OrderConfirmationSummary | null>(null);

  ngOnInit(): void {
    this.summary.set(this.checkoutAttemptService.readConfirmation());
  }

  formatPrice(price: number): string {
    return formatPrice(price);
  }
}
