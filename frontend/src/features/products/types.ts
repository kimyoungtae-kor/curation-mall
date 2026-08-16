export type TaxonomyDto = {
  slug: string;
  name: string;
};

export type ProductImageDto = {
  url: string;
  alt: string;
  sortOrder?: number;
};

export type PublicProductCardDto = {
  id: string;
  slug: string;
  brand: { id: string; slug: string; name: string };
  name: string;
  summary: string | null;
  thumbnail: ProductImageDto | null;
  listPrice: number;
  salePrice: number;
  currency: "KRW";
  inStock: boolean;
  wishlisted: boolean;
  featured?: boolean;
  species?: TaxonomyDto[];
  categories?: TaxonomyDto[];
};

export type PageMetadata = {
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};

export type PublicProductListResponse = {
  data: PublicProductCardDto[];
  page: PageMetadata;
};

export type ProductVariantDto = {
  id: string;
  sku: string;
  optionLabel: string;
  listPrice: number;
  salePrice: number;
  stockQuantity: number;
  purchasable: boolean;
  maxPurchaseQuantity: number;
};

export type ProductDetailDto = {
  id: string;
  slug: string;
  brand: { id: string; slug: string; name: string };
  name: string;
  summary: string | null;
  description: string;
  images: ProductImageDto[];
  species: TaxonomyDto[];
  categories: TaxonomyDto[];
  attributes: Record<string, unknown>;
  variants: ProductVariantDto[];
  currency: "KRW";
  wishlisted: boolean;
};

export type PublicProductDetailResponse = {
  data: ProductDetailDto;
};

export type ProductSummary = {
  id: string;
  slug: string;
  name: string;
  summary: string | null;
  brandName: string;
  thumbnailUrl: string | null;
  thumbnailAlt: string;
  price: number;
  originalPrice: number | null;
  soldOut: boolean;
};

export type ProductListPage = {
  products: ProductSummary[];
  page: PageMetadata;
};

export const PRODUCT_SORT_VALUES = [
  "newest,desc",
  "price,asc",
  "price,desc",
  "name,asc",
] as const;

export type ProductSort = (typeof PRODUCT_SORT_VALUES)[number];

export type ProductListQuery = {
  q?: string;
  brand?: string;
  category?: string;
  species?: string;
  inStock?: boolean;
  sort: ProductSort;
  page: number;
  size: number;
};
