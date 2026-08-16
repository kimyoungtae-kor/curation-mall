import {
  normalizeCollectionImage,
  normalizeCollectionSummary,
} from "../collections/normalize-collections";
import { normalizeProduct } from "../products/normalize-products";
import { normalizeContentLink } from "./content-link";
import type { ExploreItem, HomeData, LifestyleContent } from "./types";

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

function array(value: unknown) {
  return Array.isArray(value) ? value : [];
}

function normalizeExploreItems(
  value: unknown,
  kind: ExploreItem["kind"],
): ExploreItem[] {
  return array(value).flatMap((item, index) => {
    if (!isRecord(item)) return [];
    const slug = text(item.slug, item.code)?.toLowerCase();
    const name = text(item.name);
    if (!slug || !name || !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(slug)) return [];
    return [
      {
        key: text(item.id) ?? `${kind}-${slug}-${index}`,
        slug,
        name,
        kind,
        image: normalizeCollectionImage(item.image),
      },
    ];
  });
}

function normalizeLifestyle(value: unknown): LifestyleContent[] {
  return array(value)
    .flatMap((item, index) => {
      if (!isRecord(item)) return [];
      const id = text(item.id) ?? `lifestyle-${index}`;
      const title = text(item.title, item.name);
      if (!title) return [];
      return [
        {
          id,
          title,
          description: text(item.description, item.summary) ?? "",
          image: normalizeCollectionImage(item.image),
          link: normalizeContentLink(item.link),
          sortOrder: numberValue(item.sortOrder) ?? index,
        },
      ];
    })
    .sort((a, b) => a.sortOrder - b.sortOrder);
}

export function normalizeHome(payload: unknown): HomeData {
  const value = isRecord(payload) && isRecord(payload.data) ? payload.data : payload;
  if (!isRecord(value)) throw new Error("홈 응답이 올바르지 않습니다.");
  const announcement = isRecord(value.announcement)
    ? {
        text: text(value.announcement.text) ?? "새로운 큐레이션을 만나보세요.",
        link: normalizeContentLink(value.announcement.link),
      }
    : null;
  const explore = isRecord(value.explore) ? value.explore : {};
  const serviceGuide = isRecord(value.serviceGuide) ? value.serviceGuide : {};

  return {
    announcement,
    heroSlides: array(value.heroSlides)
      .flatMap((item, index) => {
        if (!isRecord(item)) return [];
        const id = text(item.id) ?? `hero-${index}`;
        const title = text(item.title);
        if (!title) return [];
        return [
          {
            id,
            title,
            description: text(item.description) ?? "",
            image: normalizeCollectionImage(item.image),
            link: normalizeContentLink(item.link),
            sortOrder: numberValue(item.sortOrder) ?? index,
          },
        ];
      })
      .sort((a, b) => a.sortOrder - b.sortOrder)
      .slice(0, 3),
    featuredCollections: array(value.featuredCollections)
      .map(normalizeCollectionSummary)
      .sort((a, b) => a.sortOrder - b.sortOrder),
    popularProducts: array(value.popularProducts).map(normalizeProduct),
    newProducts: array(value.newProducts).map(normalizeProduct),
    explore: [
      ...normalizeExploreItems(explore.species, "species"),
      ...normalizeExploreItems(explore.categories, "category"),
      ...normalizeExploreItems(explore.brands, "brand"),
    ],
    lifestyleContents: normalizeLifestyle(value.lifestyleContents),
    serviceGuide: {
      shippingFee: Math.max(0, numberValue(serviceGuide.shippingFee) ?? 3000),
      freeShippingThreshold: Math.max(
        0,
        numberValue(serviceGuide.freeShippingThreshold) ?? 50000,
      ),
      links: array(serviceGuide.links).filter(
        (link): link is string =>
          typeof link === "string" && /^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(link),
      ),
    },
  };
}
