import { describe, expect, it } from "vitest";
import { blankVariant, productToDraft } from "./product-editor-model";
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
});
