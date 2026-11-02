import { Injectable } from '@angular/core';
import { OrderConfirmationSummary, OrderRequest } from '../model/order';

const ATTEMPT_STORAGE_KEY = 'hearth-harbor-checkout-attempt';
const CONFIRMATION_STORAGE_KEY = 'hearth-harbor-order-confirmation';

interface StoredCheckoutAttempt {
  fingerprint: string;
  idempotencyKey: string;
}

@Injectable({ providedIn: 'root' })
export class CheckoutAttemptService {
  async idempotencyKeyFor(request: OrderRequest): Promise<string> {
    const fingerprint = await this.fingerprint(request);
    const stored = this.readAttempt();
    if (stored?.fingerprint === fingerprint) {
      return stored.idempotencyKey;
    }

    const attempt: StoredCheckoutAttempt = {
      fingerprint,
      idempotencyKey: crypto.randomUUID()
    };
    localStorage.setItem(ATTEMPT_STORAGE_KEY, JSON.stringify(attempt));
    return attempt.idempotencyKey;
  }

  clearAttempt(): void {
    localStorage.removeItem(ATTEMPT_STORAGE_KEY);
  }

  saveConfirmation(summary: OrderConfirmationSummary): void {
    sessionStorage.setItem(CONFIRMATION_STORAGE_KEY, JSON.stringify(summary));
  }

  readConfirmation(): OrderConfirmationSummary | null {
    const stored = sessionStorage.getItem(CONFIRMATION_STORAGE_KEY);
    if (!stored) {
      return null;
    }
    try {
      const parsed: unknown = JSON.parse(stored);
      if (!this.isConfirmationSummary(parsed)) {
        throw new TypeError('Stored order summary is invalid');
      }
      return parsed;
    } catch (error) {
      console.error('Stored confirmation data is invalid', error);
      sessionStorage.removeItem(CONFIRMATION_STORAGE_KEY);
      return null;
    }
  }

  clearConfirmation(): void {
    sessionStorage.removeItem(CONFIRMATION_STORAGE_KEY);
  }

  private async fingerprint(request: OrderRequest): Promise<string> {
    const canonicalRequest: OrderRequest = {
      items: [...request.items]
        .sort((left, right) => left.skuCode.localeCompare(right.skuCode))
        .map(item => ({
          skuCode: item.skuCode,
          price: item.price,
          quantity: item.quantity
        })),
      userDetails: {
        firstName: request.userDetails.firstName,
        lastName: request.userDetails.lastName,
        email: request.userDetails.email
      }
    };
    const bytes = new TextEncoder().encode(JSON.stringify(canonicalRequest));
    const digest = await crypto.subtle.digest('SHA-256', bytes);
    return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('');
  }

  private readAttempt(): StoredCheckoutAttempt | null {
    const stored = localStorage.getItem(ATTEMPT_STORAGE_KEY);
    if (!stored) {
      return null;
    }
    try {
      const parsed: unknown = JSON.parse(stored);
      if (
        typeof parsed === 'object'
        && parsed !== null
        && 'fingerprint' in parsed
        && typeof parsed.fingerprint === 'string'
        && 'idempotencyKey' in parsed
        && typeof parsed.idempotencyKey === 'string'
      ) {
        return {
          fingerprint: parsed.fingerprint,
          idempotencyKey: parsed.idempotencyKey
        };
      }
      throw new TypeError('Stored checkout attempt is invalid');
    } catch (error) {
      console.error('Stored checkout attempt is invalid', error);
      localStorage.removeItem(ATTEMPT_STORAGE_KEY);
      return null;
    }
  }

  private isConfirmationSummary(value: unknown): value is OrderConfirmationSummary {
    return typeof value === 'object'
      && value !== null
      && 'items' in value
      && Array.isArray(value.items)
      && value.items.every(item =>
        typeof item === 'object'
        && item !== null
        && 'skuCode' in item && typeof item.skuCode === 'string'
        && 'name' in item && typeof item.name === 'string'
        && 'price' in item && typeof item.price === 'number' && Number.isFinite(item.price)
        && 'quantity' in item && Number.isInteger(item.quantity) && item.quantity > 0
      )
      && 'total' in value
      && typeof value.total === 'number' && Number.isFinite(value.total)
      && 'completedAt' in value
      && typeof value.completedAt === 'string';
  }
}
