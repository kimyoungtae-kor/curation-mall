import {
  apiFetch,
  apiFormMutation,
  apiMutation,
  type UploadProgress,
} from "@/lib/api/client";
import type {
  AdminEnvelope,
  AdminMediaUpload,
  AdminOrderDetail,
  AdminOrderSummary,
  AdminPageResponse,
  AdminProductDetail,
  AdminProductSummary,
  AdminUserSummary,
  AdminVariant,
  HeroSlide,
  HomeSection,
  ProductStatus,
  ProductUpsertInput,
  ReferenceItem,
} from "./types";

type ListFilters = { q?: string; status?: string; page?: number; size?: number };

function listQuery(filters: ListFilters) {
  const query = new URLSearchParams();
  if (filters.q?.trim()) query.set("q", filters.q.trim());
  if (filters.status?.trim()) query.set("status", filters.status.trim());
  query.set("page", String(filters.page ?? 0));
  query.set("size", String(filters.size ?? 20));
  return query.toString();
}

export function getAdminProducts(filters: ListFilters = {}, signal?: AbortSignal) {
  return apiFetch<AdminPageResponse<AdminProductSummary>>(
    `/admin/products?${listQuery(filters)}`,
    { signal },
  );
}

export async function getAdminProduct(productId: string, signal?: AbortSignal) {
  const response = await apiFetch<AdminEnvelope<AdminProductDetail>>(
    `/admin/products/${encodeURIComponent(productId)}`,
    { signal },
  );
  return response.data;
}

export async function createAdminProduct(input: ProductUpsertInput) {
  const response = await apiMutation<AdminEnvelope<AdminProductDetail>>("/admin/products", {
    method: "POST",
    body: input,
  });
  return response.data;
}

export async function updateAdminProduct(productId: string, input: ProductUpsertInput) {
  const response = await apiMutation<AdminEnvelope<AdminProductDetail>>(
    `/admin/products/${encodeURIComponent(productId)}`,
    { method: "PUT", body: input },
  );
  return response.data;
}

export async function deleteAdminProduct(
  productId: string,
  version: number,
  confirmOrderHistory = false,
) {
  const query = new URLSearchParams({ version: String(version) });
  if (confirmOrderHistory) query.set("confirmOrderHistory", "true");
  await apiMutation<void>(
    `/admin/products/${encodeURIComponent(productId)}?${query.toString()}`,
    { method: "DELETE" },
  );
}

export async function uploadAdminProductImage(
  file: File,
  options: {
    signal?: AbortSignal;
    onProgress?: (progress: UploadProgress) => void;
  } = {},
) {
  const body = new FormData();
  body.append("file", file);
  const response = await apiFormMutation<AdminEnvelope<AdminMediaUpload>>(
    "/admin/media/images",
    {
      method: "POST",
      body,
      signal: options.signal,
      onUploadProgress: options.onProgress,
    },
  );
  return response.data;
}

export async function changeAdminProductStatus(
  productId: string,
  status: ProductStatus,
  version: number,
) {
  const response = await apiMutation<AdminEnvelope<AdminProductDetail>>(
    `/admin/products/${encodeURIComponent(productId)}/status`,
    { method: "PATCH", body: { status, version } },
  );
  return response.data;
}

export async function changeAdminVariantStock(
  variantId: string,
  stockQuantity: number,
  version: number,
) {
  const response = await apiMutation<AdminEnvelope<AdminVariant>>(
    `/admin/variants/${encodeURIComponent(variantId)}/stock`,
    { method: "PATCH", body: { stockQuantity, version } },
  );
  return response.data;
}

export async function getAdminReferences(type: "brands" | "categories" | "species", signal?: AbortSignal) {
  const response = await apiFetch<AdminEnvelope<ReferenceItem[]>>(`/admin/${type}`, { signal });
  return response.data;
}

export async function getAdminHomeSections(signal?: AbortSignal) {
  const response = await apiFetch<AdminEnvelope<HomeSection[]>>("/admin/home-sections", { signal });
  return response.data;
}

export async function updateAdminHomeSection(
  sectionId: string,
  input: Pick<HomeSection, "title" | "content" | "sortOrder" | "version">,
) {
  const response = await apiMutation<AdminEnvelope<HomeSection>>(
    `/admin/home-sections/${encodeURIComponent(sectionId)}`,
    { method: "PUT", body: input },
  );
  return response.data;
}

export async function getAdminHeroSlides(signal?: AbortSignal) {
  const response = await apiFetch<AdminEnvelope<HeroSlide[]>>("/admin/hero-slides", { signal });
  return response.data;
}

export async function updateAdminHeroSlide(slide: HeroSlide) {
  const response = await apiMutation<AdminEnvelope<HeroSlide>>(
    `/admin/hero-slides/${encodeURIComponent(slide.id)}`,
    {
      method: "PUT",
      body: {
        title: slide.title,
        description: slide.description,
        imageStorageKey: slide.imageStorageKey,
        imageAlt: slide.imageAlt,
        linkType: slide.linkType,
        linkValue: slide.linkValue,
        status: slide.status,
        sortOrder: slide.sortOrder,
        version: slide.version,
      },
    },
  );
  return response.data;
}

export function getAdminOrders(filters: ListFilters = {}, signal?: AbortSignal) {
  return apiFetch<AdminPageResponse<AdminOrderSummary>>(
    `/admin/orders?${listQuery(filters)}`,
    { signal },
  );
}

export async function getAdminOrder(orderNumber: string, signal?: AbortSignal) {
  const response = await apiFetch<AdminEnvelope<AdminOrderDetail>>(
    `/admin/orders/${encodeURIComponent(orderNumber)}`,
    { signal },
  );
  return response.data;
}

export async function transitionAdminOrder(
  orderNumber: string,
  toStatus: string,
  reason: string,
  version: number,
) {
  const response = await apiMutation<AdminEnvelope<AdminOrderDetail>>(
    `/admin/orders/${encodeURIComponent(orderNumber)}/transitions`,
    { method: "POST", body: { toStatus, reason, version } },
  );
  return response.data;
}

export function getAdminUsers(filters: Omit<ListFilters, "status"> = {}, signal?: AbortSignal) {
  return apiFetch<AdminPageResponse<AdminUserSummary>>(
    `/admin/users?${listQuery(filters)}`,
    { signal },
  );
}
