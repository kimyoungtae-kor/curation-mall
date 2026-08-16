import { describe, expect, it } from "vitest";
import { normalizeHome } from "./normalize-home";

function product(id: string) {
  return {
    id,
    slug: id,
    name: `상품 ${id}`,
    brand: { id: "brand-1", slug: "mellow", name: "멜로우" },
    summary: "공간에 어울리는 제품",
    thumbnail: { url: `/media/${id}.webp`, alt: `상품 ${id}` },
    listPrice: 20000,
    salePrice: 18000,
    currency: "KRW",
    inStock: true,
    wishlisted: false,
  };
}

describe("normalizeHome", () => {
  it("normalizes and orders the complete home contract", () => {
    const result = normalizeHome({
      data: {
        announcement: {
          text: "여름 기획전을 만나보세요",
          link: { type: "COLLECTION", value: "summer-hydration" },
        },
        heroSlides: [
          {
            id: "hero-2",
            title: "두 번째",
            description: "설명",
            image: { url: "/media/hero-2.webp", alt: "두 번째" },
            link: { type: "COLLECTION", value: "safe-drive" },
            sortOrder: 2,
          },
          {
            id: "hero-1",
            title: "첫 번째",
            description: "설명",
            image: { url: "/media/hero-1.webp", alt: "첫 번째" },
            link: { type: "COLLECTION", value: "summer-hydration" },
            sortOrder: 1,
          },
        ],
        featuredCollections: [
          {
            id: "collection-1",
            slug: "summer-hydration",
            title: "여름 음수 습관",
            description: "물 마시기",
            heroImage: { url: "/media/water.webp", alt: "음수 기획전" },
            sortOrder: 1,
          },
        ],
        popularProducts: [product("popular-product")],
        newProducts: [product("new-product")],
        explore: {
          species: [{ slug: "dog", name: "강아지" }],
          categories: [{ slug: "feeding", name: "식기·음수" }],
          brands: [{ id: "brand-1", slug: "mellow", name: "멜로우" }],
        },
        lifestyleContents: [
          {
            id: "room-1",
            title: "창가의 펫룸",
            description: "햇살 아래 쉬는 공간",
            image: { url: "/media/room.webp", alt: "펫룸" },
            link: { type: "COLLECTION", value: "summer-hydration" },
            sortOrder: 1,
          },
        ],
        serviceGuide: {
          shippingFee: 3000,
          freeShippingThreshold: 50000,
          links: ["shipping-returns", "terms", "privacy"],
        },
      },
    });

    expect(result.announcement?.link.href).toBe("/collections/summer-hydration");
    expect(result.heroSlides.map((slide) => slide.id)).toEqual(["hero-1", "hero-2"]);
    expect(result.featuredCollections[0].image?.url).toBe("/media/water.webp");
    expect(result.popularProducts[0].price).toBe(18000);
    expect(result.explore.map((item) => item.kind)).toEqual([
      "species",
      "category",
      "brand",
    ]);
    expect(result.serviceGuide.freeShippingThreshold).toBe(50000);
  });

  it("limits hero slides to the first three by sort order", () => {
    const result = normalizeHome({
      data: {
        heroSlides: [4, 3, 2, 1].map((sortOrder) => ({
          id: `hero-${sortOrder}`,
          title: `히어로 ${sortOrder}`,
          sortOrder,
          link: { type: "COLLECTION", value: "summer" },
        })),
      },
    });

    expect(result.heroSlides.map((slide) => slide.sortOrder)).toEqual([1, 2, 3]);
  });
});
