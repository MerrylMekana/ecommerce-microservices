import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { InventoryStock } from '../model/inventory';

@Injectable({ providedIn: 'root' })
export class InventoryService {
  private readonly baseUrl = 'http://localhost:9000/api/inventory/stock';

  constructor(private readonly httpClient: HttpClient) {}

  getStock(): Observable<InventoryStock[]> {
    return this.httpClient.get<InventoryStock[]>(this.baseUrl);
  }

  initializeStock(skuCode: string, quantity: number): Observable<InventoryStock> {
    return this.httpClient.post<InventoryStock>(this.baseUrl, { skuCode, quantity });
  }

  addStock(skuCode: string, quantity: number, operationKey: string): Observable<InventoryStock> {
    return this.httpClient.post<InventoryStock>(
      `${this.baseUrl}/${encodeURIComponent(skuCode)}/add`,
      { quantity },
      { headers: { 'Idempotency-Key': operationKey } }
    );
  }

  removeStock(skuCode: string, quantity: number, operationKey: string): Observable<InventoryStock> {
    return this.httpClient.post<InventoryStock>(
      `${this.baseUrl}/${encodeURIComponent(skuCode)}/remove`,
      { quantity },
      { headers: { 'Idempotency-Key': operationKey } }
    );
  }
}
