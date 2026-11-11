import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { InventoryPageComponent } from './inventory-page';
import { InventoryService } from '../../services/inventory.service';
import { ProductService } from '../../services/productService';

describe('InventoryPageComponent', () => {
  let fixture: ComponentFixture<InventoryPageComponent>;
  let component: InventoryPageComponent;
  let addStock: ReturnType<typeof vi.fn>;
  let removeStock: ReturnType<typeof vi.fn>;
  let initializeStock: ReturnType<typeof vi.fn>;

  const product = {
    id: 'product-1',
    skuCode: 'SKU-1',
    name: 'Example',
    description: 'Description',
    price: 10
  };

  beforeEach(async () => {
    addStock = vi.fn((_sku: string, quantity: number, _key: string) =>
      of({ skuCode: 'SKU-1', quantity: 5 + quantity }));
    removeStock = vi.fn((_sku: string, quantity: number, _key: string) =>
      of({ skuCode: 'SKU-1', quantity: 5 - quantity }));
    initializeStock = vi.fn((_sku: string, quantity: number) => of({ skuCode: 'SKU-1', quantity }));
    await TestBed.configureTestingModule({
      imports: [InventoryPageComponent],
      providers: [
        provideRouter([]),
        { provide: ProductService, useValue: { getProducts: () => of([product]) } },
        {
          provide: InventoryService,
          useValue: {
            getStock: () => of([{ skuCode: 'SKU-1', quantity: 5 }]),
            addStock,
            removeStock,
            initializeStock
          }
        }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(InventoryPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads Product and Inventory data and sends separate add/remove adjustments', () => {
    const row = component.rows()[0];
    component.setQuantity('SKU-1', inputEvent('2'));

    component.add(row);
    expect(addStock).toHaveBeenCalledWith('SKU-1', 2, expect.any(String));
    expect(component.rows()[0].stock?.quantity).toBe(7);

    component.setQuantity('SKU-1', inputEvent('3'));
    component.remove(component.rows()[0]);
    expect(removeStock).toHaveBeenCalledWith('SKU-1', 3, expect.any(String));
    expect(component.rows()[0].stock?.quantity).toBe(2);
  });

  it('validates stock quantities and presents conflicts from the service', () => {
    component.setQuantity('SKU-1', inputEvent('0'));
    component.add(component.rows()[0]);
    expect(addStock).not.toHaveBeenCalled();
    expect(component.rowErrors()['SKU-1']).toContain('positive whole number');

    removeStock.mockImplementationOnce(() => throwError(() => ({
      status: 409,
      error: { detail: 'Cannot remove more stock than is available.' }
    })));
    component.setQuantity('SKU-1', inputEvent('9'));
    component.remove(component.rows()[0]);
    expect(component.rowErrors()['SKU-1']).toContain('Cannot remove more stock');
  });

  it('initializes a missing inventory row with zero quantity', () => {
    component.stocks.set([]);
    component.setQuantity('SKU-1', inputEvent('0'));
    component.initialize(component.rows()[0]);

    expect(initializeStock).toHaveBeenCalledWith('SKU-1', 0);
    expect(component.rows()[0].stock?.quantity).toBe(0);
  });

  it('reuses the same operation key after an ambiguous network failure', () => {
    addStock.mockImplementationOnce(() => throwError(() => ({ status: 0 })));
    component.setQuantity('SKU-1', inputEvent('2'));
    component.add(component.rows()[0]);
    const firstKey = addStock.mock.calls[0][2];

    component.add(component.rows()[0]);

    expect(addStock).toHaveBeenCalledTimes(2);
    expect(addStock.mock.calls[1][2]).toBe(firstKey);
  });

  function inputEvent(value: string): Event {
    const input = document.createElement('input');
    input.value = value;
    const event = new Event('input');
    Object.defineProperty(event, 'target', { value: input });
    return event;
  }
});
