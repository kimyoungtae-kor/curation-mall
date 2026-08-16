import type { OrderType } from "./types";

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function first(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value;
}

export function parseCartItemIds(value: string | string[] | undefined) {
  const raw = first(value);
  if (!raw) return [];

  return Array.from(
    new Set(
      raw
        .split(",")
        .map((item) => item.trim())
        .filter((item) => UUID_PATTERN.test(item)),
    ),
  ).slice(0, 100);
}

export function parseOrderType(
  value: string | string[] | undefined,
): OrderType | null {
  const raw = first(value);
  return raw === "MEMBER" || raw === "GUEST" ? raw : null;
}

export function buildCheckoutHref(
  cartItemIds: string[],
  orderType?: OrderType,
) {
  const params = new URLSearchParams({ items: cartItemIds.join(",") });
  if (orderType) params.set("type", orderType);
  return `/checkout/order?${params.toString()}`;
}

export function isOrderNumber(value: string) {
  return /^P\d{8}-[A-Z0-9]{8}$/.test(value);
}
