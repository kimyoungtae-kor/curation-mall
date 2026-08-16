import { normalizeProduct } from "../products/normalize-products";
import type { PageMetadata, ProductImageDto } from "../products/types";
import type {
  CollectionDetail,
  CollectionListPage,
  CollectionSummary,
} from "./types";

type UnknownRecord = Record<string, unknown>;

function isRecord(value: unknown): value is UnknownRecord {
  return typeof value === "object" && value !== null;
}

function text(...values: unknown[]) {
  return values.find(
    (value): value is string => typeof value === "string" && value.trim() !== "",
  );
}

function numberValue(...values: unknown[]) {
  return values.find(
    (value): value is number => typeof value === "number" && Number.isFinite(value),
  );
}

function recordValue(value: unknown, key: string) {
  return isRecord(value) && isRecord(value[key]) ? value[key] : undefined;
}

export function normalizeCollectionImage(value: unknown): ProductImageDto | null {
  if (!isRecord(value)) return null;
  const url = text(value.url);
  if (!url) return null;
  return {
    url,
    alt: text(value.alt) ?? "기획전 이미지",
    sortOrder: numberValue(value.sortOrder),
  };
}

function collectionImage(value: UnknownRecord) {
  return normalizeCollectionImage(
    recordValue(value, "heroImage") ??
      recordValue(value, "image") ??
      recordValue(value, "thumbnail"),
  );
}

export function normalizeCollectionSummary(value: unknown): CollectionSummary {
  if (!isRecord(value)) {
    throw new Error("기획전 항목이 객체가 아닙니다.");
  }

  const id = text(value.id, numberValue(value.id)?.toString());
  const slug = text(value.slug);
  const title = text(value.title, value.name);
  if (!id || !slug || !title) {
    throw new Error("기획전 항목의 id, slug, title이 필요합니다.");
  }

  return {
    id,
    slug,
    title,
    description: text(value.description, value.summary) ?? "",
    image: collectionImage(value),
    sortOrder: numberValue(value.sortOrder) ?? 0,
  };
}

function collectionItems(payload: unknown) {
  if (Array.isArray(payload)) return payload;
  if (!isRecord(payload)) throw new Error("기획전 목록 응답이 올바르지 않습니다.");
  if (Array.isArray(payload.data)) return payload.data;
  if (Array.isArray(payload.content)) return payload.content;
  if (isRecord(payload.data) && Array.isArray(payload.data.items)) {
    return payload.data.items;
  }
  throw new Error("기획전 목록 배열을 찾지 못했습니다.");
}

function pageMetadata(payload: unknown, itemCount: number): PageMetadata {
  const page = isRecord(payload) && isRecord(payload.page) ? payload.page : {};
  const number = numberValue(page.number) ?? 0;
  const totalPages = numberValue(page.totalPages) ?? (itemCount > 0 ? 1 : 0);
  return {
    number,
    size: numberValue(page.size) ?? itemCount,
    totalElements: numberValue(page.totalElements) ?? itemCount,
    totalPages,
    first: typeof page.first === "boolean" ? page.first : number === 0,
    last:
      typeof page.last === "boolean"
        ? page.last
        : totalPages === 0 || number >= totalPages - 1,
  };
}

export function normalizeCollectionList(payload: unknown): CollectionListPage {
  const collections = collectionItems(payload)
    .map(normalizeCollectionSummary)
    .sort((a, b) => a.sortOrder - b.sortOrder);
  return { collections, page: pageMetadata(payload, collections.length) };
}

export function normalizeCollectionDetail(payload: unknown): CollectionDetail {
  const value = isRecord(payload) && isRecord(payload.data) ? payload.data : payload;
  if (!isRecord(value)) throw new Error("기획전 상세 응답이 올바르지 않습니다.");
  const summary = normalizeCollectionSummary(value);
  const products = Array.isArray(value.products)
    ? value.products.map(normalizeProduct)
    : [];

  return {
    ...summary,
    products,
    publishedAt: text(value.publishedAt) ?? null,
  };
}
