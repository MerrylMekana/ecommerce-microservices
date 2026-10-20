import { Component, inject } from '@angular/core';
import {
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { ProductService } from '../../services/productService';

@Component({
  selector: 'app-add-product',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './add-product.html',
  styleUrl: './add-product.css'
})
export class AddProductComponent {

  private readonly formBuilder = inject(FormBuilder);
  private readonly productService = inject(ProductService);

  productForm: FormGroup = this.formBuilder.group({
    skuCode: ['', Validators.required],
    name: ['', Validators.required],
    description: ['', Validators.required],
    price: [null, [Validators.required, Validators.min(0.01)]]
  });

  productCreated = false;
  error = '';

  onSubmit(): void {

    if (this.productForm.invalid) {
      this.productForm.markAllAsTouched();
      return;
    }

    const product = this.productForm.value;

    this.productService.createProduct(product).subscribe({
      next: () => {
        this.productCreated = true;
        this.error = '';
        this.productForm.reset();
      },
      error: (error) => {
        console.error('Failed to create product:', error);
        this.productCreated = false;
        this.error = 'Failed to create product. Please try again.';
      }
    });
  }
}
