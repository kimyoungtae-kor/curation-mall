import { describe, expect, it } from "vitest";
import { parseProductQuery, productPageHref, serializeProductQuery } from "./query";

describe("product query", () => {
  it("keeps supported filters and rejects unsafe values", () => {
    expect(
      parseProductQuery({
        q: "  보울  ",
        brand: "mellow-tail",
        category: "feeding",
        species: "dog",
        inStock: "true",
        sort: "price,asc",
        page: "2",
      }),
    ).toEqual({
      q: "보울",
      brand: "mellow-tail",
      category: "feeding",
      species: "dog",
      inStock: true,
      sort: "price,asc",
      page: 2,
      size: 12,
    });

    expect(parseProductQuery({ brand: "../admin", sort: "unknown", page: "-2" })).toEqual(
      expect.objectContaining({
        brand: undefined,
        sort: "newest,desc",
        page: 0,
      }),
    );
  });

  it("serializes API and browser pagination queries", () => {
    const query = parseProductQuery({ brand: "mellow-tail", species: "cat" });
    expect(serializeProductQuery(query).get("size")).toBe("12");
    expect(productPageHref(query, 1)).toBe(
      "/products?brand=mellow-tail&species=cat&page=1",
    );
  });
});
