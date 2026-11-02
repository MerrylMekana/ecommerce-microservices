import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AddProductComponent } from './add-product';
import { ProductService } from '../../services/productService';
import { InventoryService } from '../../services/inventory.service';
import { Product } from '../../model/product';

describe('AddProductComponent', () => {
  let component: AddProductComponent;
  let fixture: ComponentFixture<AddProductComponent>;
  let createProduct: ReturnType<typeof vi.fn>;
  let initializeStock: ReturnType<typeof vi.fn>;

  const product: Product = {
    id: 'product-1',
    skuCode: 'SKU-1',
    name: 'Example',
    description: 'Description',
    price: 10
  };

  beforeEach(async () => {
    createProduct = vi.fn(() => of(product));
    initializeStock = vi.fn(() => of({ skuCode: product.skuCode, quantity: 0 }));
    await TestBed.configureTestingModule({
      imports: [AddProductComponent],
      providers: [
        provideRouter([]),
        { provide: ProductService, useValue: { createProduct } },
        { provide: InventoryService, useValue: { initializeStock } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AddProductComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('requires a SKU and rejects invalid price and initial quantity', () => {
    component.onSubmit();
    expect(createProduct).not.toHaveBeenCalled();

    component.productForm.patchValue({
      skuCode: 'SKU-1',
      name: 'Example',
      description: 'Description',
      price: 10,
      initialStock: -1
    });
    expect(component.productForm.invalid).toBe(true);
    component.productForm.patchValue({ initialStock: 1.5, price: 0 });
    expect(component.productForm.invalid).toBe(true);
    expect(createProduct).not.toHaveBeenCalled();
  });

  it('creates product then initializes inventory including zero quantity', () => {
    component.productForm.patchValue({
      skuCode: ' SKU-1 ',
      name: 'Example',
      description: 'Description',
      price: 10,
      initialStock: 0
    });

    component.onSubmit();

    expect(createProduct).toHaveBeenCalledWith({
      skuCode: 'SKU-1',
      name: 'Example',
      description: 'Description',
      price: 10
    });
    expect(initializeStock).toHaveBeenCalledWith('SKU-1', 0);
    expect(component.productCreated()).toBe(true);
    expect(component.createdProduct()).toEqual(product);
  });

  it('reports partial success and retries only inventory initialization', () => {
    initializeStock.mockImplementationOnce(() => throwError(() => new Error('inventory unavailable')));
    component.productForm.patchValue({
      skuCode: 'SKU-1',
      name: 'Example',
      description: 'Description',
      price: 10,
      initialStock: 7
    });
    component.onSubmit();

    expect(createProduct).toHaveBeenCalledTimes(1);
    expect(component.productCreated()).toBe(false);
    expect(component.inventoryError()).toContain('product was created');

    component.initializeCreatedInventory();
    expect(createProduct).toHaveBeenCalledTimes(1);
    expect(initializeStock).toHaveBeenLastCalledWith('SKU-1', 7);
    expect(component.productCreated()).toBe(true);
  });

  it('explains duplicate SKU conflicts without calling inventory', () => {
    createProduct.mockImplementationOnce(() => throwError(() =>
      new HttpErrorResponse({ status: 409 })
    ));
    component.productForm.patchValue({
      skuCode: 'SKU-1',
      name: 'Example',
      description: 'Description',
      price: 10,
      initialStock: 1
    });

    component.onSubmit();

    expect(component.error()).toContain('already exists');
    expect(initializeStock).not.toHaveBeenCalled();
  });
});
