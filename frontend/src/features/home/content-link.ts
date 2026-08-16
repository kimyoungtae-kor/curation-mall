import type { ContentLink, ContentLinkWire } from "./types";

const slugPattern = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

function safeSlug(value: string) {
  return slugPattern.test(value) ? value : null;
}

export function normalizeContentLink(value: unknown): ContentLink {
  const wire =
    typeof value === "object" && value !== null
      ? (value as Partial<ContentLinkWire>)
      : {};
  const type = typeof wire.type === "string" ? wire.type.toUpperCase() : "PRODUCTS";
  const rawValue = typeof wire.value === "string" ? wire.value.trim() : "";
  const slug = safeSlug(rawValue);

  if (type === "COLLECTION" && slug) {
    return { type, value: rawValue, href: `/collections/${slug}`, external: false };
  }
  if (type === "PRODUCT" && slug) {
    return { type, value: rawValue, href: `/products/${slug}`, external: false };
  }
  if (["SPECIES", "CATEGORY", "BRAND"].includes(type) && slug) {
    return {
      type,
      value: rawValue,
      href: `/products?${type.toLowerCase()}=${encodeURIComponent(slug)}`,
      external: false,
    };
  }
  if (type === "HELP" && slug) {
    return { type, value: rawValue, href: `/help/${slug}`, external: false };
  }
  if (type === "CONTENT") {
    return { type, value: rawValue, href: "/collections", external: false };
  }
  if (type === "EXTERNAL") {
    try {
      const url = new URL(rawValue);
      if (url.protocol === "http:" || url.protocol === "https:") {
        return { type, value: rawValue, href: url.toString(), external: true };
      }
    } catch {
      // Invalid external links fall through to the safe catalog route.
    }
  }

  return { type: "PRODUCTS", value: "", href: "/products", external: false };
}
