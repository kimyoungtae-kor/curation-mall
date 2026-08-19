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

export function productDeleteConflictMessage(error: unknown) {
  if (typeof error !== "object" || error === null || !("code" in error)) return null;

  switch (error.code) {
    case "PRODUCT_MUST_BE_UNPUBLISHED":
      return "판매 중인 상품은 삭제할 수 없습니다. 먼저 숨김 또는 판매 종료 상태로 저장한 뒤 다시 시도해 주세요.";
    case "PRODUCT_IN_USE":
      return "홈 콘텐츠에서 사용 중인 상품입니다. 홈의 상품 링크를 다른 대상으로 변경한 뒤 다시 시도해 주세요.";
    case "PRODUCT_HAS_ORDER_HISTORY":
      return "주문 이력이 확인되었습니다. 기존 주문 스냅샷과 결제 이력 보존 내용을 확인한 뒤 한 번 더 삭제를 승인해 주세요.";
    case "PRODUCT_HAS_ACTIVE_RESERVATION":
      return "결제 진행 중이거나 활성 재고 예약이 있는 상품은 삭제할 수 없습니다. 결제와 재고 예약이 종료된 후 다시 시도해 주세요.";
    case "PRODUCT_DELETE_CONFLICT":
      return "상품의 참조 데이터가 변경되어 삭제하지 못했습니다. 화면을 새로고침한 뒤 다시 시도해 주세요.";
    default:
      return null;
  }
}

export function isProductOrderHistoryConflict(error: unknown) {
  return typeof error === "object"
    && error !== null
    && "code" in error
    && error.code === "PRODUCT_HAS_ORDER_HISTORY";
}
