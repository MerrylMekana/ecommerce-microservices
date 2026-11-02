import { Component, computed, inject, signal } from '@angular/core';
import {
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ProductService } from '../../services/productService';
import { InventoryService } from '../../services/inventory.service';
import { Product } from '../../model/product';

@Component({
  selector: 'app-add-product',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './add-product.html',
  styleUrl: './add-product.css'
})
export class AddProductComponent {

  private readonly formBuilder = inject(FormBuilder);
  private readonly productService = inject(ProductService);
  private readonly inventoryService = inject(InventoryService);

  productForm: FormGroup = this.formBuilder.group({
    skuCode: ['', [Validators.required, Validators.pattern(/.*\S.*/)]],
    name: ['', [Validators.required, Validators.pattern(/.*\S.*/)]],
    description: ['', [Validators.required, Validators.pattern(/.*\S.*/)]],
    price: [null, [Validators.required, Validators.min(0.01)]],
    initialStock: [0, [
      Validators.required,
      Validators.min(0),
      Validators.max(2_147_483_647),
      control => Number.isInteger(control.value) ? null : { integer: true }
    ]]
  });

  readonly createdProduct = signal<Product | null>(null);
  readonly initialStock = signal<number | null>(null);
  readonly inventoryReady = signal(false);
  readonly productCreated = computed(() => this.createdProduct() !== null && this.inventoryReady());
  readonly error = signal('');
  readonly inventoryError = signal('');
  readonly submitting = signal(false);
  readonly initializingInventory = signal(false);

  onSubmit(): void {
    if (this.productForm.invalid || this.createdProduct()) {
      this.productForm.markAllAsTouched();
      return;
    }

    const { initialStock, ...product } = this.productForm.getRawValue();
    product.skuCode = String(product.skuCode).trim();
    this.submitting.set(true);

    this.productService.createProduct(product).subscribe({
      next: created => {
        this.createdProduct.set(created);
        this.initialStock.set(Number(initialStock));
        this.error.set('');
        this.submitting.set(false);
        this.initializeCreatedInventory();
      },
      error: (error) => {
        console.error('Failed to create product:', error);
        this.error.set(error?.status === 409
          ? 'A product with this SKU already exists. Choose a different SKU.'
          : error?.status === 401 || error?.status === 403
            ? 'You must be signed in with an account permitted to manage the catalog.'
          : 'The product could not be created. Check the details and try again.');
        this.submitting.set(false);
      }
    });
  }

  initializeCreatedInventory(): void {
    const product = this.createdProduct();
    const quantity = this.initialStock();
    if (!product || quantity === null || this.initializingInventory()) {
      return;
    }
    this.initializingInventory.set(true);
    this.inventoryError.set('');
    this.inventoryService.initializeStock(product.skuCode, quantity).subscribe({
      next: () => {
        this.inventoryReady.set(true);
        this.initializingInventory.set(false);
        this.productForm.reset({ skuCode: '', name: '', description: '', price: null, initialStock: 0 });
      },
      error: error => {
        console.error('Product created but inventory initialization failed:', error);
        this.inventoryError.set(error?.status === 409
          ? 'The product exists, but inventory could not be initialized because a stock record already has a different quantity.'
          : error?.status === 401 || error?.status === 403
            ? 'The product exists, but you are not authorized to initialize inventory. Sign in with an account permitted by the Gateway.'
          : 'The product was created, but its inventory record could not be initialized. It will not be available for checkout until inventory is set.');
        this.initializingInventory.set(false);
      }
    });
  }

  addAnotherProduct(): void {
    this.createdProduct.set(null);
    this.initialStock.set(null);
    this.inventoryReady.set(false);
    this.error.set('');
    this.inventoryError.set('');
    this.productForm.reset({ skuCode: '', name: '', description: '', price: null, initialStock: 0 });
  }
}
