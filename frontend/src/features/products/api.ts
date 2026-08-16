import { apiFetch } from "@/lib/api/client";
import {
  normalizeProductDetail,
  normalizeProductList,
  normalizeProductPage,
} from "./normalize-products";
import { serializeProductQuery } from "./query";
import type {
  ProductDetailDto,
  ProductListPage,
  ProductListQuery,
  ProductSummary,
  PublicProductDetailResponse,
  PublicProductListResponse,
} from "./types";

export async function getFeaturedProducts(
  signal?: AbortSignal,
): Promise<ProductSummary[]> {
  const payload = await apiFetch<PublicProductListResponse>(
    "/catalog/products?page=0&size=8",
    { signal },
  );
  return normalizeProductList(payload);
}

export async function getProducts(
  query: ProductListQuery,
  signal?: AbortSignal,
): Promise<ProductListPage> {
  const params = serializeProductQuery(query);
  const payload = await apiFetch<PublicProductListResponse>(
    `/catalog/products?${params.toString()}`,
    { signal },
  );
  return normalizeProductPage(payload);
}

export async function getProductDetail(slug: string): Promise<ProductDetailDto> {
  const payload = await apiFetch<PublicProductDetailResponse>(
    `/catalog/products/${encodeURIComponent(slug)}`,
  );
  return normalizeProductDetail(payload);
}
