import { describe, expect, it } from "vitest";
import {
  normalizeProductDetail,
  normalizeProductList,
} from "./normalize-products";

describe("normalizeProductList", () => {
  it("normalizes a Spring Page response", () => {
    const result = normalizeProductList({
      content: [
        {
          id: 1,
          slug: "summer-bowl",
          name: "여름 산책 보울",
          brand: { name: "Demo Brand" },
          price: 28000,
          originalPrice: 32000,
          stockQuantity: 4,
        },
      ],
    });

    expect(result).toEqual([
      expect.objectContaining({
        id: "1",
        slug: "summer-bowl",
        name: "여름 산책 보울",
        brandName: "Demo Brand",
        price: 28000,
        originalPrice: 32000,
        soldOut: false,
      }),
    ]);
  });

  it("supports an empty item response", () => {
    expect(normalizeProductList({ items: [] })).toEqual([]);
  });

  it("normalizes the documented public catalog response", () => {
    const result = normalizeProductList({
      data: [
        {
          id: "product-1",
          slug: "cat-house",
          brand: { id: "brand-1", slug: "demo", name: "Demo" },
          name: "포근한 고양이 하우스",
          thumbnail: {
            url: "https://cdn.example.com/cat-house.jpg",
            alt: "고양이 하우스",
          },
          listPrice: 49000,
          salePrice: 42000,
          currency: "KRW",
          inStock: true,
          wishlisted: false,
        },
      ],
      page: { number: 0, size: 8, totalElements: 1, totalPages: 1 },
    });

    expect(result[0]).toEqual(
      expect.objectContaining({
        id: "product-1",
        brandName: "Demo",
        price: 42000,
        originalPrice: 49000,
        thumbnailAlt: "고양이 하우스",
        soldOut: false,
      }),
    );
  });

  it("rejects an invalid product contract", () => {
    expect(() => normalizeProductList({ content: [{ id: "missing-fields" }] })).toThrow(
      "id, name, price",
    );
  });

  it("normalizes product detail variants and gallery data", () => {
    const result = normalizeProductDetail({
      data: {
        id: "product-1",
        slug: "forest-bowl",
        name: "포레스트 보울",
        summary: "낮은 식기",
        description: "공간에 어울리는 식기입니다.",
        brand: { id: "brand-1", slug: "mellow-tail", name: "멜로우테일" },
        currency: "KRW",
        wishlisted: false,
        attributes: { material: "세라믹" },
        images: [{ url: "/media/bowl.webp", alt: "연두색 보울", sortOrder: 1 }],
        variants: [
          {
            id: "variant-1",
            sku: "BOWL-GREEN",
            optionLabel: "그린",
            listPrice: 36000,
            salePrice: 32900,
            stockQuantity: 8,
            purchasable: true,
            maxPurchaseQuantity: 8,
          },
        ],
        species: [{ slug: "dog", name: "강아지" }],
        categories: [{ slug: "feeding", name: "식기·음수" }],
      },
    });

    expect(result.images[0]).toEqual(
      expect.objectContaining({ url: "/media/bowl.webp", alt: "연두색 보울" }),
    );
    expect(result.variants[0]).toEqual(
      expect.objectContaining({ optionLabel: "그린", maxPurchaseQuantity: 8 }),
    );
  });
});
