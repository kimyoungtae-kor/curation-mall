import type { CollectionCardWire, CollectionSummary } from "@/features/collections/types";
import type {
  ProductImageDto,
  ProductSummary,
  PublicProductCardDto,
} from "@/features/products/types";

export type ContentLinkWire = {
  type: string;
  value: string;
};

export type HeroSlideWire = {
  id: string;
  title: string;
  description: string;
  image: ProductImageDto | null;
  link: ContentLinkWire;
  sortOrder: number;
};

export type ExploreItemWire = {
  id?: string;
  slug?: string;
  code?: string;
  name: string;
  image?: ProductImageDto | null;
};

export type LifestyleContentWire = {
  id: string;
  title: string;
  description: string;
  image: ProductImageDto | null;
  link: ContentLinkWire;
  sortOrder: number;
};

export type PublicHomeResponse = {
  data: {
    announcement: {
      text: string;
      link: ContentLinkWire;
    } | null;
    heroSlides: HeroSlideWire[];
    featuredCollections: CollectionCardWire[];
    popularProducts: PublicProductCardDto[];
    newProducts: PublicProductCardDto[];
    explore: {
      species: ExploreItemWire[];
      categories: ExploreItemWire[];
      brands: ExploreItemWire[];
    };
    lifestyleContents: LifestyleContentWire[];
    serviceGuide: {
      shippingFee: number;
      freeShippingThreshold: number;
      links: string[];
    };
  };
};

export type ContentLink = {
  type: string;
  value: string;
  href: string;
  external: boolean;
};

export type HeroSlide = {
  id: string;
  title: string;
  description: string;
  image: ProductImageDto | null;
  link: ContentLink;
  sortOrder: number;
};

export type ExploreItem = {
  key: string;
  slug: string;
  name: string;
  kind: "species" | "category" | "brand";
  image: ProductImageDto | null;
};

export type LifestyleContent = {
  id: string;
  title: string;
  description: string;
  image: ProductImageDto | null;
  link: ContentLink;
  sortOrder: number;
};

export type HomeData = {
  announcement: { text: string; link: ContentLink } | null;
  heroSlides: HeroSlide[];
  featuredCollections: CollectionSummary[];
  popularProducts: ProductSummary[];
  newProducts: ProductSummary[];
  explore: ExploreItem[];
  lifestyleContents: LifestyleContent[];
  serviceGuide: {
    shippingFee: number;
    freeShippingThreshold: number;
    links: string[];
  };
};
