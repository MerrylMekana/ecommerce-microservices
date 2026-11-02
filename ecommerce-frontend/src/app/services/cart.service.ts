import { computed, Injectable, signal } from '@angular/core';
import { CartEntry } from '../model/cart';

const CART_STORAGE_KEY = 'hearth-harbor-cart';

@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly entries = signal<CartEntry[]>(this.readStoredEntries());
  readonly items = this.entries.asReadonly();
  readonly itemCount = computed(() =>
    this.entries().reduce((count, entry) => count + entry.quantity, 0)
  );

  add(skuCode: string, quantity = 1): void {
    this.validate(skuCode, quantity);
    const normalizedSku = skuCode.toLocaleLowerCase();
    const existing = this.entries().find(
      entry => entry.skuCode.toLocaleLowerCase() === normalizedSku
    );
    if (existing) {
      this.validate(skuCode, existing.quantity + quantity);
    }
    const next = existing
      ? this.entries().map(entry =>
          entry.skuCode.toLocaleLowerCase() === normalizedSku
            ? { ...entry, quantity: entry.quantity + quantity }
            : entry
        )
      : [...this.entries(), { skuCode, quantity }];
    this.save(next);
  }

  setQuantity(skuCode: string, quantity: number): void {
    this.validate(skuCode, quantity);
    this.save(this.entries().map(entry =>
      entry.skuCode === skuCode ? { ...entry, quantity } : entry
    ));
  }

  remove(skuCode: string): void {
    this.save(this.entries().filter(entry => entry.skuCode !== skuCode));
  }

  clear(): void {
    this.save([]);
  }

  private save(entries: CartEntry[]): void {
    localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(entries));
    this.entries.set(entries);
  }

  private readStoredEntries(): CartEntry[] {
    const stored = localStorage.getItem(CART_STORAGE_KEY);
    if (!stored) {
      return [];
    }

    try {
      const parsed: unknown = JSON.parse(stored);
      if (!Array.isArray(parsed)) {
        throw new TypeError('Stored cart must be an array');
      }
      return parsed.filter((entry): entry is CartEntry =>
        typeof entry === 'object'
        && entry !== null
        && 'skuCode' in entry
        && typeof entry.skuCode === 'string'
        && entry.skuCode.trim().length > 0
        && 'quantity' in entry
        && Number.isInteger(entry.quantity)
        && entry.quantity > 0
      );
    } catch (error) {
      console.error('Stored cart data is invalid', error);
      localStorage.removeItem(CART_STORAGE_KEY);
      return [];
    }
  }

  private validate(skuCode: string, quantity: number): void {
    if (!skuCode.trim()) {
      throw new Error('A product SKU is required');
    }
    if (!Number.isInteger(quantity) || quantity <= 0 || quantity > 2_147_483_647) {
      throw new Error('Quantity must be a positive whole number');
    }
  }
}
