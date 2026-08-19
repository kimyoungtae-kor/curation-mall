import { describe, expect, it } from "vitest";
import {
  blankVariant,
  isProductOrderHistoryConflict,
  productDeleteConflictMessage,
  productToDraft,
} from "./product-editor-model";
import type { AdminProductDetail } from "./types";

describe("admin product editor model", () => {
  it("기존 옵션의 id와 version을 전체 저장 요청에 유지한다", () => {
    const product: AdminProductDetail = {
      id: "product-1",
      brandId: "brand-1",
      slug: "versioned-product",
      name: "버전 상품",
      summary: null,
      description: null,
      status: "PUBLISHED",
      featured: false,
      categoryIds: [],
      speciesIds: [],
      variants: [{
        id: "variant-1",
        sku: "SKU-1",
        optionLabel: "기본",
        price: 10000,
        stockQuantity: 7,
        status: "ACTIVE",
        sortOrder: 1,
        version: 4,
      }],
      images: [],
      version: 2,
    };

    expect(productToDraft(product).variants[0]).toMatchObject({
      id: "variant-1",
      version: 4,
    });
  });

  it("새 옵션은 id와 version을 모두 null로 만든다", () => {
    expect(blankVariant(2)).toMatchObject({ id: null, version: null, sortOrder: 2 });
  });

  it.each([
    ["PRODUCT_MUST_BE_UNPUBLISHED", "숨김 또는 판매 종료"],
    ["PRODUCT_IN_USE", "홈의 상품 링크"],
    ["PRODUCT_HAS_ORDER_HISTORY", "한 번 더 삭제를 승인"],
    ["PRODUCT_HAS_ACTIVE_RESERVATION", "결제와 재고 예약이 종료된 후"],
    ["PRODUCT_DELETE_CONFLICT", "새로고침"],
  ])("삭제 충돌 %s에 맞는 해결 방법을 안내한다", (code, expected) => {
    expect(productDeleteConflictMessage({ status: 409, code })).toContain(expected);
  });

  it("상품 삭제 정책 오류가 아니면 별도 안내를 만들지 않는다", () => {
    expect(productDeleteConflictMessage({ status: 409, code: "OPTIMISTIC_LOCK_CONFLICT" }))
      .toBeNull();
  });

  it("주문 이력 충돌만 조건부 추가 확인 대상으로 구분한다", () => {
    expect(isProductOrderHistoryConflict({ status: 409, code: "PRODUCT_HAS_ORDER_HISTORY" }))
      .toBe(true);
    expect(isProductOrderHistoryConflict({ status: 409, code: "PRODUCT_HAS_ACTIVE_RESERVATION" }))
      .toBe(false);
    expect(isProductOrderHistoryConflict(new Error("network"))).toBe(false);
  });
});
