import type {
  PageMetadata,
  ProductDetailDto,
  ProductListPage,
  ProductSummary,
} from "./types";

type UnknownRecord = Record<string, unknown>;

export function isRecord(value: unknown): value is UnknownRecord {
  return typeof value === "object" && value !== null;
}

function stringValue(...values: unknown[]) {
  return values.find(
    (value): value is string => typeof value === "string" && value.trim() !== "",
  );
}

function numberValue(...values: unknown[]) {
  return values.find(
    (value): value is number => typeof value === "number" && Number.isFinite(value),
  );
}

function nestedString(value: unknown, key: string) {
  return isRecord(value) ? stringValue(value[key]) : undefined;
}

function productItems(payload: unknown): unknown[] {
  if (Array.isArray(payload)) {
    return payload;
  }

  if (!isRecord(payload)) {
    throw new Error("상품 목록 응답이 객체 또는 배열이 아닙니다.");
  }

  if (Array.isArray(payload.content)) return payload.content;
  if (Array.isArray(payload.items)) return payload.items;

  if (isRecord(payload.data)) {
    if (Array.isArray(payload.data.content)) return payload.data.content;
    if (Array.isArray(payload.data.items)) return payload.data.items;
  }

  if (Array.isArray(payload.data)) return payload.data;

  throw new Error("상품 목록 배열을 응답에서 찾지 못했습니다.");
}

export function normalizeProduct(value: unknown): ProductSummary {
  if (!isRecord(value)) {
    throw new Error("상품 항목이 객체가 아닙니다.");
  }

  const id = stringValue(value.id, numberValue(value.id)?.toString());
  const name = stringValue(value.name, value.productName, value.title);
  const price = numberValue(value.salePrice, value.price, value.currentPrice);

  if (!id || !name || price === undefined) {
    throw new Error("상품 항목의 id, name, price가 필요합니다.");
  }

  const slug = stringValue(value.slug) ?? id;
  const brandName =
    stringValue(value.brandName, nestedString(value.brand, "name")) ?? "SELECTED";
  const thumbnailUrl =
    stringValue(
      value.thumbnailUrl,
      value.imageUrl,
      nestedString(value.thumbnail, "url"),
      nestedString(value.mainImage, "url"),
    ) ?? null;
  const originalPrice =
    numberValue(
      value.listPrice,
      value.originalPrice,
      value.regularPrice,
      value.compareAtPrice,
    ) ?? null;
  const stockQuantity = numberValue(value.stockQuantity);
  const status = stringValue(value.status);
  const soldOut =
    typeof value.inStock === "boolean"
      ? !value.inStock
      : stockQuantity !== undefined
        ? stockQuantity <= 0
        : status === "SOLD_OUT";

  return {
    id,
    slug,
    name,
    summary: stringValue(value.summary, value.shortDescription) ?? null,
    brandName,
    thumbnailUrl,
    thumbnailAlt:
      stringValue(
        value.thumbnailAlt,
        value.imageAlt,
        nestedString(value.thumbnail, "alt"),
        nestedString(value.mainImage, "alt"),
      ) ?? `${name} 상품 이미지`,
    price,
    originalPrice:
      originalPrice !== null && originalPrice > price ? originalPrice : null,
    soldOut,
  };
}

export function normalizeProductList(payload: unknown): ProductSummary[] {
  return productItems(payload).map(normalizeProduct);
}

function normalizePage(payload: unknown, productCount: number): PageMetadata {
  if (!isRecord(payload) || !isRecord(payload.page)) {
    return {
      number: 0,
      size: productCount,
      totalElements: productCount,
      totalPages: productCount > 0 ? 1 : 0,
      first: true,
      last: true,
    };
  }

  const page = payload.page;
  const number = numberValue(page.number) ?? 0;
  const size = numberValue(page.size) ?? productCount;
  const totalElements = numberValue(page.totalElements) ?? productCount;
  const totalPages = numberValue(page.totalPages) ?? (productCount > 0 ? 1 : 0);

  return {
    number,
    size,
    totalElements,
    totalPages,
    first: typeof page.first === "boolean" ? page.first : number === 0,
    last:
      typeof page.last === "boolean"
        ? page.last
        : totalPages === 0 || number >= totalPages - 1,
  };
}

export function normalizeProductPage(payload: unknown): ProductListPage {
  const products = normalizeProductList(payload);
  return { products, page: normalizePage(payload, products.length) };
}

function normalizeTaxonomies(value: unknown) {
  if (!Array.isArray(value)) return [];
  return value.flatMap((item) => {
    if (!isRecord(item)) return [];
    const slug = stringValue(item.slug, item.code);
    const name = stringValue(item.name);
    return slug && name ? [{ slug, name }] : [];
  });
}

function normalizeImages(value: unknown) {
  if (!Array.isArray(value)) return [];
  return value.flatMap((item, index) => {
    if (!isRecord(item)) return [];
    const url = stringValue(item.url);
    if (!url) return [];
    return [
      {
        url,
        alt: stringValue(item.alt) ?? "상품 이미지",
        sortOrder: numberValue(item.sortOrder) ?? index + 1,
      },
    ];
  });
}

function normalizeVariants(value: unknown) {
  if (!Array.isArray(value)) return [];
  return value.flatMap((item) => {
    if (!isRecord(item)) return [];
    const id = stringValue(item.id, numberValue(item.id)?.toString());
    const sku = stringValue(item.sku);
    const optionLabel = stringValue(item.optionLabel, item.name);
    const salePrice = numberValue(item.salePrice, item.price);
    if (!id || !sku || !optionLabel || salePrice === undefined) return [];
    const listPrice = numberValue(item.listPrice) ?? salePrice;
    const stockQuantity = Math.max(0, numberValue(item.stockQuantity) ?? 0);
    const maxPurchaseQuantity = Math.max(
      0,
      numberValue(item.maxPurchaseQuantity) ?? Math.min(stockQuantity, 10),
    );
    return [
      {
        id,
        sku,
        optionLabel,
        listPrice,
        salePrice,
        stockQuantity,
        purchasable:
          typeof item.purchasable === "boolean"
            ? item.purchasable
            : stockQuantity > 0,
        maxPurchaseQuantity,
      },
    ];
  });
}

export function normalizeProductDetail(payload: unknown): ProductDetailDto {
  const value = isRecord(payload) && isRecord(payload.data) ? payload.data : payload;
  if (!isRecord(value)) {
    throw new Error("상품 상세 응답이 객체가 아닙니다.");
  }

  const id = stringValue(value.id, numberValue(value.id)?.toString());
  const slug = stringValue(value.slug);
  const name = stringValue(value.name);
  const brand = isRecord(value.brand) ? value.brand : {};
  const brandId = stringValue(brand.id, numberValue(brand.id)?.toString());
  const brandSlug = stringValue(brand.slug);
  const brandName = stringValue(brand.name);

  if (!id || !slug || !name || !brandId || !brandSlug || !brandName) {
    throw new Error("상품 상세의 필수 식별 정보가 없습니다.");
  }

  return {
    id,
    slug,
    name,
    summary: stringValue(value.summary, value.shortDescription) ?? null,
    description: stringValue(value.description) ?? "",
    brand: { id: brandId, slug: brandSlug, name: brandName },
    currency: "KRW",
    wishlisted: value.wishlisted === true,
    attributes: isRecord(value.attributes) ? value.attributes : {},
    images: normalizeImages(value.images).sort(
      (a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0),
    ),
    variants: normalizeVariants(value.variants),
    species: normalizeTaxonomies(value.species),
    categories: normalizeTaxonomies(value.categories),
  };
}
