import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { OrderService } from './orderService';
import { OrderRequest } from '../model/order';

describe('OrderService', () => {
  let service: OrderService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(OrderService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTestingController.verify());

  it('posts multi-item requests through the gateway with the idempotency header', () => {
    const request: OrderRequest = {
      items: [
        { skuCode: 'IPHONE-15', price: 799, quantity: 2 },
        { skuCode: 'AIRPODS-PRO', price: 249, quantity: 1 }
      ],
      userDetails: {
        firstName: 'Taylor',
        lastName: 'Customer',
        email: 'buyer@example.com'
      }
    };
    let response: string | undefined;
    service.placeOrder(request, 'attempt-key-001').subscribe(value => response = value);

    const httpRequest = httpTestingController.expectOne('http://localhost:9000/api/order');
    expect(httpRequest.request.method).toBe('POST');
    expect(httpRequest.request.headers.get('Idempotency-Key')).toBe('attempt-key-001');
    expect(httpRequest.request.body.items).toEqual(request.items);
    httpRequest.flush('Order Placed Successfully', { status: 201, statusText: 'Created' });
    expect(response).toBe('Order Placed Successfully');
  });
});
