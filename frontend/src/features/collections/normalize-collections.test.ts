import { describe, expect, it } from "vitest";
import {
  normalizeCollectionDetail,
  normalizeCollectionList,
} from "./normalize-collections";

describe("collection response normalizers", () => {
  it("normalizes the documented collection list envelope", () => {
    const result = normalizeCollectionList({
      data: [
        {
          id: "collection-2",
          slug: "safe-drive",
          title: "안전한 드라이브",
          description: "차량 이동 아이템",
          heroImage: { url: "/media/home/drive.webp", alt: "차량용 카시트" },
          sortOrder: 2,
          publishedAt: "2026-08-12T00:00:00Z",
        },
      ],
      page: {
        number: 0,
        size: 12,
        totalElements: 1,
        totalPages: 1,
        first: true,
        last: true,
      },
    });

    expect(result.page.totalElements).toBe(1);
    expect(result.collections[0]).toEqual(
      expect.objectContaining({
        slug: "safe-drive",
        image: { url: "/media/home/drive.webp", alt: "차량용 카시트" },
        sortOrder: 2,
      }),
    );
  });

  it("normalizes a detail envelope and linked product cards", () => {
    const result = normalizeCollectionDetail({
      data: {
        id: "collection-1",
        slug: "summer-hydration",
        title: "여름 음수 습관",
        description: "시원하게 물 마시는 시간",
        heroImage: { url: "/media/home/water.webp", alt: "급수기" },
        products: [
          {
            id: "product-1",
            slug: "cool-bowl",
            name: "쿨링 보울",
            brand: { id: "brand-1", slug: "mellow", name: "멜로우" },
            salePrice: 25000,
            listPrice: 28000,
            inStock: true,
            thumbnail: { url: "/media/products/bowl.webp", alt: "쿨링 보울" },
          },
        ],
        publishedAt: "2026-08-12T00:00:00Z",
      },
    });

    expect(result.products[0]).toEqual(
      expect.objectContaining({ slug: "cool-bowl", price: 25000, soldOut: false }),
    );
  });

  it("rejects a malformed collection contract", () => {
    expect(() => normalizeCollectionList({ data: [{ id: "missing" }] })).toThrow(
      "id, slug, title",
    );
  });
});
