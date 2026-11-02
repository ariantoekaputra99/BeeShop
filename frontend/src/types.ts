export type Product = {
  id: number; name: string; sku: string; price: number; stock: number; weightGram: number;
  category?: string; imageUrl?: string; description?: string; active: boolean;
};
export type Page<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number };
export type CartLine = { productId: number; name: string; price: number; stock: number; imageUrl?: string; quantity: number };
export type ShippingOption = { code: string; courier: string; service: string; etd: string; fee: number };
export type ShippingQuote = { totalWeightGram: number; options: ShippingOption[] };
export type CheckoutResponse = {
  orderNumber: string; status: string; total: number; paymentProvider: string;
  redirectUrl: string; snapToken?: string; expiresAt: string;
};
export type OrderStatus = 'PENDING_PAYMENT' | 'PAID' | 'PROCESSING' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED' | 'REFUNDED';
export type Order = {
  orderNumber: string; status: OrderStatus;
  customerName: string; customerEmail: string; customerPhone: string;
  address: string; city: string; postalCode: string; notes?: string;
  subtotal: number; shippingFee: number; total: number; totalWeightGram?: number;
  courier?: string; courierService?: string; shippingEtd?: string; trackingNumber?: string;
  paymentStatus?: string; paymentType?: string; paymentRedirectUrl?: string;
  createdAt: string; expiresAt?: string; paidAt?: string; shippedAt?: string; deliveredAt?: string; cancelledAt?: string;
  items: { sku: string; productName: string; quantity: number; unitPrice: number; lineTotal: number }[];
  history: { status: OrderStatus; note?: string; at: string }[];
};
export type DashboardStats = { revenue: number; ordersByStatus: Record<OrderStatus, number>; activeProducts: number; lowStockProducts: number };
export type ApiErrorBody = { status: number; message: string; fields?: Record<string, string> };
