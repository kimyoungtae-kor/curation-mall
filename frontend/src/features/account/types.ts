import type { PageMetadata } from "@/features/products/types";

export type Customer = {
  id: string;
  email: string;
  name: string;
  phone: string;
  roles: string[];
};

export type AuthSnapshot = {
  authenticated: boolean;
  user: Customer | null;
  cartCount: number;
  wishlistCount: number;
};

export type MergeAdjustment = {
  variantId: string;
  mergedQuantity: number;
  reason: "STOCK_LIMIT" | "PURCHASE_LIMIT" | "VARIANT_UNAVAILABLE";
};

export type AuthResult = {
  authenticated: true;
  user: Customer;
  mergeResult: {
    merged: boolean;
    cartItemCount: number;
    wishlistCount: number;
    adjustments: MergeAdjustment[];
  };
};

export type CartAvailability =
  | "AVAILABLE"
  | "PRICE_CHANGED"
  | "OUT_OF_STOCK"
  | "UNAVAILABLE";

export type CartItem = {
  id: string;
  product: {
    slug: string;
    brandName: string;
    name: string;
    thumbnailUrl: string | null;
  };
  variantId: string;
  sku: string;
  optionLabel: string;
  quantity: number;
  unitPriceAtAdd: number;
  currentUnitPrice: number;
  lineAmount: number;
  availability: CartAvailability;
  priceChanged: boolean;
  maxPurchaseQuantity: number;
};

export type Cart = {
  id: string;
  status: "ACTIVE" | "MERGED" | "ORDERED" | "EXPIRED";
  items: CartItem[];
  itemsAmount: number;
  shippingAmountEstimate: number;
  totalAmountEstimate: number;
  itemCount: number;
  updatedAt: string;
};

export type WishlistItem = {
  productId: string;
  slug: string;
  brandName: string;
  name: string;
  thumbnailUrl: string | null;
  minimumPrice: number | null;
  wishlisted: boolean;
};

export type WishlistPage = {
  data: WishlistItem[];
  page: PageMetadata;
};
