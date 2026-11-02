import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { InventoryService } from './inventory.service';

describe('InventoryService', () => {
  let service: InventoryService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(InventoryService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uses Gateway stock routes for stock reads, initialization and adjustments', () => {
    service.getStock().subscribe();
    const get = http.expectOne('http://localhost:9000/api/inventory/stock');
    expect(get.request.method).toBe('GET');
    get.flush([]);

    service.initializeStock('SKU-1', 0).subscribe();
    const initialize = http.expectOne('http://localhost:9000/api/inventory/stock');
    expect(initialize.request.body).toEqual({ skuCode: 'SKU-1', quantity: 0 });
    initialize.flush({ skuCode: 'SKU-1', quantity: 0 });

    service.addStock('SKU 2', 3).subscribe();
    const add = http.expectOne('http://localhost:9000/api/inventory/stock/SKU%202/add');
    expect(add.request.body).toEqual({ quantity: 3 });
    add.flush({ skuCode: 'SKU 2', quantity: 3 });

    service.removeStock('SKU-1', 1).subscribe();
    const remove = http.expectOne('http://localhost:9000/api/inventory/stock/SKU-1/remove');
    expect(remove.request.body).toEqual({ quantity: 1 });
    remove.flush({ skuCode: 'SKU-1', quantity: 2 });

  });
});
