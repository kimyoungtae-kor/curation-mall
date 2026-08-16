import type {
  PageMetadata,
  ProductImageDto,
  ProductSummary,
  PublicProductCardDto,
} from "@/features/products/types";

export type CollectionCardWire = {
  id: string;
  slug: string;
  title: string;
  description: string;
  heroImage?: ProductImageDto | null;
  image?: ProductImageDto | null;
  thumbnail?: ProductImageDto | null;
  sortOrder?: number;
  publishedAt?: string;
};

export type PublicCollectionListResponse = {
  data: CollectionCardWire[];
  page: PageMetadata;
};

export type PublicCollectionDetailResponse = {
  data: {
    id: string;
    slug: string;
    title: string;
    description: string;
    heroImage: ProductImageDto | null;
    products: PublicProductCardDto[];
    publishedAt: string | null;
  };
};

export type CollectionSummary = {
  id: string;
  slug: string;
  title: string;
  description: string;
  image: ProductImageDto | null;
  sortOrder: number;
};

export type CollectionListPage = {
  collections: CollectionSummary[];
  page: PageMetadata;
};

export type CollectionDetail = CollectionSummary & {
  products: ProductSummary[];
  publishedAt: string | null;
};
