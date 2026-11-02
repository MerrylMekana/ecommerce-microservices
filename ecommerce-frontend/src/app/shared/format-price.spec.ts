import { describe, expect, it } from 'vitest';
import { formatPrice } from './format-price';

describe('formatPrice', () => {
  it('formats catalog prices as INR', () => {
    expect(formatPrice(1234.5)).toBe('₹1,234.50');
  });
});
