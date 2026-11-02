import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin, Observable } from 'rxjs';
import { InventoryStock } from '../../model/inventory';
import { Product } from '../../model/product';
import { InventoryService } from '../../services/inventory.service';
import { ProductService } from '../../services/productService';

interface ProductStockRow {
  product: Product;
  stock: InventoryStock | null;
}

@Component({
  selector: 'app-inventory-page',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './inventory-page.html'
})
export class InventoryPageComponent implements OnInit {
  private readonly productService = inject(ProductService);
  private readonly inventoryService = inject(InventoryService);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly products = signal<Product[]>([]);
  readonly stocks = signal<InventoryStock[]>([]);
  readonly busySku = signal('');
  readonly feedback = signal('');
  readonly quantities = signal<Record<string, string>>({});
  readonly rowErrors = signal<Record<string, string>>({});
  readonly rows = computed<ProductStockRow[]>(() => this.products().map(product => ({
    product,
    stock: this.stocks().find(stock =>
      stock.skuCode.toLocaleLowerCase() === product.skuCode.toLocaleLowerCase()
    ) ?? null
  })));

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    forkJoin({
      products: this.productService.getProducts(),
      stocks: this.inventoryService.getStock()
    }).subscribe({
      next: ({ products, stocks }) => {
        this.products.set(products);
        this.stocks.set(stocks);
        this.loading.set(false);
      },
      error: error => {
        this.error.set(error?.status === 401 || error?.status === 403
          ? 'Sign in to manage catalog inventory.'
          : 'Inventory could not be loaded. Check the Product and Inventory services, then try again.');
        this.loading.set(false);
      }
    });
  }

  quantityFor(skuCode: string, initialized: boolean): string {
    return this.quantities()[skuCode] ?? (initialized ? '1' : '0');
  }

  setQuantity(skuCode: string, event: Event): void {
    const input = event.target;
    if (input instanceof HTMLInputElement) {
      this.quantities.update(values => ({ ...values, [skuCode]: input.value }));
    }
  }

  initialize(row: ProductStockRow): void {
    const quantity = this.validQuantity(row.product.skuCode, true);
    if (quantity === null) {
      return;
    }
    this.runAdjustment(row.product.skuCode, () =>
      this.inventoryService.initializeStock(row.product.skuCode, quantity), 'Inventory initialized');
  }

  add(row: ProductStockRow): void {
    const quantity = this.validQuantity(row.product.skuCode, false);
    if (quantity === null) {
      return;
    }
    this.runAdjustment(row.product.skuCode, () =>
      this.inventoryService.addStock(row.product.skuCode, quantity), 'Stock added');
  }

  remove(row: ProductStockRow): void {
    const quantity = this.validQuantity(row.product.skuCode, false);
    if (quantity === null) {
      return;
    }
    this.runAdjustment(row.product.skuCode, () =>
      this.inventoryService.removeStock(row.product.skuCode, quantity), 'Stock removed');
  }

  private validQuantity(skuCode: string, allowZero: boolean): number | null {
    const raw = this.quantities()[skuCode] ?? (allowZero ? '0' : '1');
    if (raw.trim() === '') {
      this.setRowError(skuCode, 'Enter a stock quantity.');
      return null;
    }
    const value = Number(raw);
    if (!Number.isInteger(value) || value < (allowZero ? 0 : 1) || value > 2_147_483_647) {
      this.setRowError(skuCode, allowZero
        ? 'Enter a whole number from 0 to 2,147,483,647.'
        : 'Enter a positive whole number no greater than 2,147,483,647.');
      return null;
    }
    this.clearRowError(skuCode);
    return value;
  }

  private runAdjustment(
    skuCode: string,
    request: () => Observable<InventoryStock>,
    successMessage: string
  ): void {
    this.busySku.set(skuCode);
    request().subscribe({
      next: updated => {
        this.stocks.update(stocks => {
          const existingIndex = stocks.findIndex(stock => stock.skuCode === skuCode);
          if (existingIndex < 0) {
            return [...stocks, updated];
          }
          return stocks.map(stock => stock.skuCode === skuCode ? updated : stock);
        });
        this.feedback.set(`${successMessage} for ${skuCode}. Current stock: ${updated.quantity}.`);
        this.quantities.update(values => ({ ...values, [skuCode]: '1' }));
        this.clearRowError(skuCode);
        this.busySku.set('');
      },
      error: error => {
        this.setRowError(skuCode, this.inventoryErrorMessage(error));
        this.feedback.set('');
        this.busySku.set('');
      }
    });
  }

  private inventoryErrorMessage(error: { status?: number; error?: { detail?: string } }): string {
    if (error.status === 401 || error.status === 403) {
      return 'You are not authorized to change inventory. Sign in with an account permitted by the Gateway.';
    }
    if (error.status === 404) {
      return 'No inventory record exists for this product. Initialize it first.';
    }
    if (error.status === 409) {
      return error.error?.detail ?? 'The stock change conflicts with current inventory. Refresh and retry.';
    }
    if (error.status === 400) {
      return error.error?.detail ?? 'Enter a valid stock quantity.';
    }
    return error.error?.detail ?? 'We could not confirm whether the stock change completed. Refresh inventory before trying again.';
  }

  private setRowError(skuCode: string, message: string): void {
    this.rowErrors.update(errors => ({ ...errors, [skuCode]: message }));
  }

  private clearRowError(skuCode: string): void {
    this.rowErrors.update(errors => {
      const updated = { ...errors };
      delete updated[skuCode];
      return updated;
    });
  }
}
