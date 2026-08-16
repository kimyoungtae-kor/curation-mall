import {
  PRODUCT_SORT_VALUES,
  type ProductListQuery,
  type ProductSort,
} from "./types";

export type ProductSearchParams = Record<
  string,
  string | string[] | undefined
>;

function firstValue(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value;
}

function isProductSort(value: string | undefined): value is ProductSort {
  return PRODUCT_SORT_VALUES.some((candidate) => candidate === value);
}

function safeSlug(value: string | undefined) {
  return value && /^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(value)
    ? value
    : undefined;
}

export function parseProductQuery(
  searchParams: ProductSearchParams,
): ProductListQuery {
  const rawPage = Number.parseInt(firstValue(searchParams.page) ?? "0", 10);
  const rawQuery = firstValue(searchParams.q)?.trim().slice(0, 100);
  const rawSort = firstValue(searchParams.sort);

  return {
    q: rawQuery || undefined,
    brand: safeSlug(firstValue(searchParams.brand)),
    category: safeSlug(firstValue(searchParams.category)),
    species: safeSlug(firstValue(searchParams.species)),
    inStock: firstValue(searchParams.inStock) === "true" || undefined,
    sort: isProductSort(rawSort) ? rawSort : "newest,desc",
    page: Number.isFinite(rawPage) && rawPage >= 0 ? rawPage : 0,
    size: 12,
  };
}

export function serializeProductQuery(query: ProductListQuery) {
  const params = new URLSearchParams();
  if (query.q) params.set("q", query.q);
  if (query.brand) params.set("brand", query.brand);
  if (query.category) params.set("category", query.category);
  if (query.species) params.set("species", query.species);
  if (query.inStock) params.set("inStock", "true");
  params.set("sort", query.sort);
  params.set("page", String(query.page));
  params.set("size", String(query.size));
  return params;
}

export function productPageHref(query: ProductListQuery, page: number) {
  const params = serializeProductQuery({ ...query, page });
  params.delete("size");
  if (page === 0) params.delete("page");
  if (query.sort === "newest,desc") params.delete("sort");
  const suffix = params.toString();
  return suffix ? `/products?${suffix}` : "/products";
}
