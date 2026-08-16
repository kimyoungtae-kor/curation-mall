export type OrderType = "MEMBER" | "GUEST";

export type OrderStatus =
  | "PENDING_PAYMENT"
  | "PAID"
  | "PREPARING"
  | "SHIPPED"
  | "DELIVERED"
  | "CANCEL_REQUESTED"
  | "CANCELLED";

export type PaymentStatus =
  | "READY"
  | "PROCESSING"
  | "APPROVED"
  | "FAILED"
  | "UNKNOWN"
  | "CANCELLED"
  | "PARTIAL_CANCELLED";

export type Currency = "KRW";

export type PageMetadata = {
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};

export type AuthUser = {
  id: string;
  email: string;
  name: string;
  phone: string;
  roles: string[];
};

export type AuthState = {
  authenticated: boolean;
  user: AuthUser | null;
  cartCount: number;
  wishlistCount: number;
};

export type AuthStateResponse = { data: AuthState };

export type OrderQuoteRequest = {
  orderType: OrderType;
  cartItemIds: string[];
};

export type OrderQuoteLine = {
  cartItemId: string;
  variantId: string;
  productName: string;
  optionLabel: string;
  quantity: number;
  unitPrice: number;
  lineAmount: number;
  availability: "AVAILABLE" | "PRICE_CHANGED";
};

export type OrderQuote = {
  orderType: OrderType;
  lines: OrderQuoteLine[];
  itemsAmount: number;
  discountAmount: number;
  shippingAmount: number;
  totalAmount: number;
  currency: Currency;
  warnings: string[];
  quotedAt: string;
};

export type OrderQuoteResponse = { data: OrderQuote };

export type BuyerSnapshot = {
  name: string;
  email: string;
  phone: string;
};

export type ShippingSnapshot = {
  recipientName: string;
  recipientPhone: string;
  postalCode: string;
  address1: string;
  address2: string;
  deliveryMessage: string | null;
};

export type CreateOrderRequest = {
  orderType: OrderType;
  cartItemIds: string[];
  buyer: BuyerSnapshot;
  shipping: ShippingSnapshot;
  agreements: {
    purchaseTermsAccepted: boolean;
    privacyCollectionAccepted: boolean;
  };
};

export type CreatedOrder = {
  orderNumber: string;
  orderType: OrderType;
  orderStatus: OrderStatus;
  paymentStatus: PaymentStatus;
  itemsAmount: number;
  discountAmount: number;
  shippingAmount: number;
  totalAmount: number;
  currency: Currency;
  reservationExpiresAt: string;
  createdAt: string;
};

export type PreparedPayment = {
  paymentAttemptId: string;
  provider: string;
  method?: string | null;
  amount: number;
  status: PaymentStatus;
  approvedAt?: string | null;
  testPayment?: boolean;
};

export type CreateOrderResult = {
  replayed: boolean;
  order: CreatedOrder;
  payment: PreparedPayment;
  guestLookupToken: string | null;
};

export type CreateOrderResponse = { data: CreateOrderResult };

export type SimulatedPaymentRequest = {
  provider: "SIMULATED";
  orderNumber: string;
  simulationResult: "APPROVE" | "FAIL";
  amount: number;
  guestLookupToken?: string;
};

export type ConfirmedPayment = {
  paymentAttemptId?: string;
  provider?: string;
  method?: string | null;
  status: PaymentStatus;
  amount: number;
  approvedAt?: string | null;
  testPayment: boolean;
  failureCode?: string | null;
  failureMessage?: string | null;
};

export type PaymentConfirmation = {
  orderNumber: string;
  orderStatus: OrderStatus;
  payment: ConfirmedPayment;
  nextAction?: "WAIT_FOR_RECONCILIATION";
};

export type PaymentConfirmationResponse = { data: PaymentConfirmation };

export type OrderItemSnapshot = {
  productName: string;
  brandName: string;
  sku: string;
  optionLabel: string;
  unitPrice: number;
  quantity: number;
  lineAmount: number;
  imageUrl: string | null;
};

export type PaymentSnapshot = {
  paymentAttemptId?: string;
  provider?: string;
  method?: string | null;
  status: PaymentStatus;
  amount: number;
  approvedAt?: string | null;
  testPayment?: boolean;
};

export type OrderStatusHistory = {
  fromStatus?: OrderStatus | null;
  toStatus: OrderStatus;
  reason?: string | null;
  createdAt: string;
};

export type OrderDetail = {
  orderNumber: string;
  orderType: OrderType;
  orderStatus: OrderStatus;
  paymentStatus: PaymentStatus;
  buyer: BuyerSnapshot;
  shipping: ShippingSnapshot;
  items: OrderItemSnapshot[];
  itemsAmount: number;
  discountAmount: number;
  shippingAmount: number;
  totalAmount: number;
  currency?: Currency;
  payments: PaymentSnapshot[];
  statusHistory: OrderStatusHistory[];
  orderedAt: string;
  paidAt: string | null;
};

export type OrderDetailResponse = { data: OrderDetail };

export type OrderListItem = {
  orderNumber: string;
  orderType: OrderType;
  orderStatus: OrderStatus;
  paymentStatus: PaymentStatus;
  totalAmount: number;
  currency: Currency;
  orderedAt?: string;
  createdAt?: string;
  itemCount?: number;
  representativeItemName?: string | null;
};

export type OrderListResponse = {
  data: OrderListItem[];
  page: PageMetadata;
};

export type GuestOrderLookupRequest = {
  orderNumber: string;
  guestLookupToken: string;
};

export type CheckoutHandoff = {
  version: 1;
  order: CreatedOrder;
  payment: PreparedPayment;
  guestLookupToken: string | null;
  confirmation: PaymentConfirmation | null;
  savedAt: number;
};

export type GuestLookupHandoff = {
  version: 1;
  orderNumber: string;
  guestLookupToken: string;
  detail: OrderDetail;
  savedAt: number;
};
