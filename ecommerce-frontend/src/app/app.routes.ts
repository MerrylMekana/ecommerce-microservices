import { Routes } from '@angular/router';
import { adminGuard } from './security/admin.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/home-page/home-page').then(module => module.HomePageComponent)
  },
  {
    path: 'products',
    loadComponent: () => import('./pages/products-page/products-page').then(module => module.ProductsPageComponent)
  },
  {
    path: 'products/:skuCode',
    loadComponent: () => import('./pages/product-details-page/product-details-page')
      .then(module => module.ProductDetailsPageComponent)
  },
  {
    path: 'cart',
    loadComponent: () => import('./pages/cart-page/cart-page').then(module => module.CartPageComponent)
  },
  {
    path: 'checkout',
    loadComponent: () => import('./pages/checkout-page/checkout-page').then(module => module.CheckoutPageComponent)
  },
  {
    path: 'order-confirmation',
    loadComponent: () => import('./pages/order-confirmation-page/order-confirmation-page')
      .then(module => module.OrderConfirmationPageComponent)
  },
  {
    path: 'add-product',
    canActivate: [adminGuard],
    loadComponent: () => import('./pages/add-product/add-product').then(module => module.AddProductComponent)
  },
  {
    path: 'inventory',
    canActivate: [adminGuard],
    loadComponent: () => import('./pages/inventory-page/inventory-page')
      .then(module => module.InventoryPageComponent)
  },
  {
    path: '**',
    loadComponent: () => import('./pages/not-found-page/not-found-page').then(module => module.NotFoundPageComponent)
  }
];