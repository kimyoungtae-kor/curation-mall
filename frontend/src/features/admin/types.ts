import type { OrderDetail, OrderStatus, PageMetadata, PaymentStatus } from "@/features/commerce/types";

export type AdminEnvelope<T> = { data: T };
export type AdminPageResponse<T> = { data: T[]; page: PageMetadata };

export type ProductStatus = "DRAFT" | "PUBLISHED" | "HIDDEN" | "DISCONTINUED";
export type VariantStatus = "ACTIVE" | "INACTIVE";

export type AdminProductSummary = {
  id: string;
  slug: string;
  name: string;
  brandName: string;
  status: ProductStatus;
  minimumPrice: number;
  totalStock: number;
  version: number;
  updatedAt: string;
};

export type AdminVariant = {
  id: string;
  sku: string;
  optionLabel: string;
  price: number;
  stockQuantity: number;
  status: VariantStatus;
  sortOrder: number;
  version: number;
};

export type AdminImage = {
  id: string;
  storageKey: string;
  alt: string;
  sortOrder: number;
};

export type AdminMediaUpload = {
  storageKey: string;
  url: string;
  contentType: string;
  sizeBytes: number;
  width: number;
  height: number;
};

export type AdminProductDetail = {
  id: string;
  brandId: string;
  slug: string;
  name: string;
  summary: string | null;
  description: string | null;
  status: ProductStatus;
  featured: boolean;
  categoryIds: string[];
  speciesIds: string[];
  variants: AdminVariant[];
  images: AdminImage[];
  version: number;
};

export type ProductVariantInput = {
  id: string | null;
  version: number | null;
  sku: string;
  optionLabel: string;
  price: number;
  stockQuantity: number;
  status: VariantStatus;
  sortOrder: number;
};

export type ProductImageInput = {
  id?: string | null;
  storageKey: string;
  alt: string;
  sortOrder: number;
};

export type ProductUpsertInput = {
  brandId: string;
  slug: string;
  name: string;
  summary: string;
  description: string;
  status: ProductStatus;
  featured: boolean;
  categoryIds: string[];
  speciesIds: string[];
  variants: ProductVariantInput[];
  images: ProductImageInput[];
  version?: number | null;
};

export type ReferenceItem = { id: string; code: string; name: string };

export type HomeSection = {
  id: string;
  sectionKey: string;
  title: string | null;
  content: string;
  sortOrder: number;
  version: number;
  updatedAt: string;
};

export type HeroLinkType = "COLLECTION" | "PRODUCT" | "CONTENT" | "HELP";
export type HeroStatus = "DRAFT" | "PUBLISHED" | "HIDDEN";

export type HeroSlide = {
  id: string;
  title: string;
  description: string;
  imageStorageKey: string;
  imageAlt: string;
  linkType: HeroLinkType;
  linkValue: string;
  status: HeroStatus;
  sortOrder: number;
  version: number;
};

export type AdminOrderSummary = {
  orderNumber: string;
  orderType: "MEMBER" | "GUEST";
  buyerName: string;
  orderStatus: OrderStatus;
  paymentStatus: PaymentStatus;
  totalAmount: number;
  orderedAt: string;
};

export type AdminOrderDetail = { order: OrderDetail; version: number };

export type AdminUserSummary = {
  id: string;
  email: string;
  name: string;
  phone: string;
  status: string;
  orderCount: number;
  totalPurchased: number;
  createdAt: string;
};
