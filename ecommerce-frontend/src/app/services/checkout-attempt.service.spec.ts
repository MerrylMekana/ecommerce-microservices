import { beforeEach, describe, expect, it } from 'vitest';
import { OrderRequest } from '../model/order';
import { CheckoutAttemptService } from './checkout-attempt.service';

describe('CheckoutAttemptService', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  it('reuses the key for unchanged requests including reordered items', async () => {
    const service = new CheckoutAttemptService();
    const firstKey = await service.idempotencyKeyFor(request());
    const retryKey = await service.idempotencyKeyFor(request(true));

    expect(retryKey).toBe(firstKey);
    expect(JSON.parse(localStorage.getItem('hearth-harbor-checkout-attempt') ?? '{}').fingerprint).toMatch(/^[0-9a-f]{64}$/);
  });

  it('creates a new key when item or customer data changes', async () => {
    const service = new CheckoutAttemptService();
    const firstKey = await service.idempotencyKeyFor(request());
    const changed: OrderRequest = {
      ...request(),
      items: [{ skuCode: 'IPHONE-15', price: 799, quantity: 3 }]
    };

    expect(await service.idempotencyKeyFor(changed)).not.toBe(firstKey);
  });

  it('creates a new key when price or customer details change', async () => {
    const service = new CheckoutAttemptService();
    const firstKey = await service.idempotencyKeyFor(request());
    const priceChanged = request();
    priceChanged.items[0].price += 1;
    expect(await service.idempotencyKeyFor(priceChanged)).not.toBe(firstKey);

    const customerChanged = request();
    customerChanged.userDetails.email = 'another@example.com';
    expect(await service.idempotencyKeyFor(customerChanged)).not.toBe(firstKey);
  });

  it('reuses the stored key after service recreation and clears it for a new attempt', async () => {
    const service = new CheckoutAttemptService();
    const firstKey = await service.idempotencyKeyFor(request());
    expect(await new CheckoutAttemptService().idempotencyKeyFor(request())).toBe(firstKey);

    service.clearAttempt();
    expect(await service.idempotencyKeyFor(request())).not.toBe(firstKey);
  });

  it('stores and reads a transient confirmation summary', () => {
    const service = new CheckoutAttemptService();
    const summary = {
      items: [{ skuCode: 'IPHONE-15', price: 799, quantity: 1, name: 'Phone' }],
      total: 799,
      completedAt: '2026-10-20T00:00:00.000Z'
    };
    service.saveConfirmation(summary);

    expect(service.readConfirmation()).toEqual(summary);
    service.clearConfirmation();
    expect(service.readConfirmation()).toBeNull();
  });

  function request(reverse = false): OrderRequest {
    const items = [
      { skuCode: 'IPHONE-15', price: 799, quantity: 2 },
      { skuCode: 'AIRPODS-PRO', price: 249, quantity: 1 }
    ];
    return {
      items: reverse ? items.reverse() : items,
      userDetails: {
        firstName: 'Taylor',
        lastName: 'Customer',
        email: 'buyer@example.com'
      }
    };
  }
});
