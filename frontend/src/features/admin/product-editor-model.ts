import type {
  AdminProductDetail,
  ProductUpsertInput,
  ProductVariantInput,
} from "./types";

export type ProductDraft = ProductUpsertInput;

export function blankVariant(sortOrder: number): ProductVariantInput {
  return {
    id: null,
    version: null,
    sku: "",
    optionLabel: "",
    price: 0,
    stockQuantity: 0,
    status: "ACTIVE",
    sortOrder,
  };
}

export function productToDraft(product: AdminProductDetail): ProductDraft {
  return {
    brandId: product.brandId,
    slug: product.slug,
    name: product.name,
    summary: product.summary ?? "",
    description: product.description ?? "",
    status: product.status,
    featured: product.featured,
    categoryIds: product.categoryIds,
    speciesIds: product.speciesIds,
    variants: product.variants.map((variant) => ({
      id: variant.id,
      version: variant.version,
      sku: variant.sku,
      optionLabel: variant.optionLabel,
      price: variant.price,
      stockQuantity: variant.stockQuantity,
      status: variant.status,
      sortOrder: variant.sortOrder,
    })),
    images: product.images.map((image) => ({
      id: image.id,
      storageKey: image.storageKey,
      alt: image.alt,
      sortOrder: image.sortOrder,
    })),
    version: product.version,
  };
}
