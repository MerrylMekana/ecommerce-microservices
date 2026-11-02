import { beforeEach, describe, expect, it } from 'vitest';
import { CartService } from './cart.service';

describe('CartService', () => {
  beforeEach(() => localStorage.clear());

  it('persists SKU and quantity and restores them after service recreation', () => {
    const cart = new CartService();
    cart.add('SKU-A', 2);
    cart.add('SKU-B');

    expect(cart.items()).toEqual([
      { skuCode: 'SKU-A', quantity: 2 },
      { skuCode: 'SKU-B', quantity: 1 }
    ]);
    expect(cart.itemCount()).toBe(3);
    expect(new CartService().items()).toEqual(cart.items());
  });

  it('merges matching SKU entries regardless of case', () => {
    const cart = new CartService();
    cart.add('SKU-A', 1);
    cart.add('sku-a', 2);

    expect(cart.items()).toEqual([{ skuCode: 'SKU-A', quantity: 3 }]);
  });

  it('updates quantity and supports remove and clear', () => {
    const cart = new CartService();
    cart.add('SKU-A', 2);
    cart.setQuantity('SKU-A', 4);
    expect(cart.itemCount()).toBe(4);

    cart.remove('SKU-A');
    expect(cart.items()).toEqual([]);
    cart.add('SKU-B');
    cart.clear();
    expect(cart.items()).toEqual([]);
    expect(localStorage.getItem('hearth-harbor-cart')).toBe('[]');
  });

  it('rejects invalid quantities and blank SKUs', () => {
    const cart = new CartService();
    expect(() => cart.add('SKU-A', 0)).toThrow();
    expect(() => cart.add('SKU-A', 2_147_483_648)).toThrow();
    expect(() => cart.add('   ')).toThrow();
  });
});
