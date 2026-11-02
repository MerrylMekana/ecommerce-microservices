import { Component, input, output } from '@angular/core';

@Component({
  selector: 'app-quantity-control',
  standalone: true,
  templateUrl: './quantity-control.html'
})
export class QuantityControlComponent {
  readonly value = input(1);
  readonly label = input('Quantity');
  readonly valueChange = output<number>();

  decrement(): void {
    if (this.value() > 1) {
      this.valueChange.emit(this.value() - 1);
    }
  }

  increment(): void {
    if (this.value() < 2_147_483_647) {
      this.valueChange.emit(this.value() + 1);
    }
  }

  updateFromInput(event: Event): void {
    const inputElement = event.target;
    if (!(inputElement instanceof HTMLInputElement)) {
      return;
    }
    const quantity = Number(inputElement.value);
    if (Number.isInteger(quantity) && quantity > 0 && quantity <= 2_147_483_647) {
      this.valueChange.emit(quantity);
    } else {
      inputElement.value = String(this.value());
    }
  }
}
