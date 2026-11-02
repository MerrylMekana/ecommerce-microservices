import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ProductCardComponent } from './product-card';
import { CartService } from '../../services/cart.service';

describe('ProductCardComponent', () => {
  let fixture: ComponentFixture<ProductCardComponent>;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [ProductCardComponent],
      providers: [provideRouter([])]
    }).compileComponents();
    fixture = TestBed.createComponent(ProductCardComponent);
    fixture.componentRef.setInput('product', {
      id: 'product-1',
      skuCode: 'SKU-1',
      name: 'Example product',
      description: 'Description',
      price: 799
    });
    fixture.detectChanges();
  });

  it('adds the selected quantity without navigating and emits product-name feedback', () => {
    const router = TestBed.inject(Router);
    const originalUrl = router.url;
    const added = vi.fn();
    fixture.componentInstance.added.subscribe(added);
    fixture.componentInstance.quantity.set(2);

    fixture.nativeElement.querySelectorAll('button')[2].click();

    expect(TestBed.inject(CartService).items()).toEqual([{ skuCode: 'SKU-1', quantity: 2 }]);
    expect(added).toHaveBeenCalledWith('Example product');
    expect(router.url).toBe(originalUrl);
  });
});
