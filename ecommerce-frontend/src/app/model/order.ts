export interface UserDetails {
  firstName: string;
  lastName: string;
  email: string;
}

export interface Order {
  id?: number;
  orderNumber?: string;
  skuCode: string;
  price: number;
  quantity: number;
  userDetails: UserDetails;
}
