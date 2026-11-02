export interface UserDetails {
  firstName: string;
  lastName: string;
  email: string;
}

export interface OrderItemRequest {
  skuCode: string;
  price: number;
  quantity: number;
}

export interface OrderRequest {
  items: OrderItemRequest[];
  userDetails: UserDetails;
}

export interface LegacyOrderRequest {
  skuCode: string;
  price: number;
  quantity: number;
  userDetails: UserDetails;
}

export interface OrderConfirmationSummary {
  items: Array<OrderItemRequest & { name: string }>;
  total: number;
  completedAt: string;
}
