import { apiFetch } from "@/lib/api/client";
import {
  normalizeCollectionDetail,
  normalizeCollectionList,
} from "./normalize-collections";
import type {
  CollectionDetail,
  CollectionListPage,
  PublicCollectionDetailResponse,
  PublicCollectionListResponse,
} from "./types";

export async function getCollections(
  page = 0,
  signal?: AbortSignal,
): Promise<CollectionListPage> {
  const params = new URLSearchParams({
    page: String(page),
    size: "12",
    sort: "sortOrder,asc",
  });
  const payload = await apiFetch<PublicCollectionListResponse>(
    `/collections?${params.toString()}`,
    { signal },
  );
  return normalizeCollectionList(payload);
}

export async function getCollectionDetail(slug: string): Promise<CollectionDetail> {
  const payload = await apiFetch<PublicCollectionDetailResponse>(
    `/collections/${encodeURIComponent(slug)}`,
  );
  return normalizeCollectionDetail(payload);
}
