import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { LegacyOrderRequest, OrderRequest } from '../model/order';

@Injectable({
  providedIn: 'root'
})
export class OrderService {

  constructor(private httpClient: HttpClient) {}

  placeOrder(order: OrderRequest | LegacyOrderRequest, idempotencyKey: string): Observable<string> {
    return this.httpClient.post(
      'http://localhost:9000/api/order',
      order,
      {
        headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey }),
        responseType: 'text'
      }
    );
  }
}