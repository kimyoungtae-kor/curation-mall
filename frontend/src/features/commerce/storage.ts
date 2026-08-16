import type {
  CheckoutHandoff,
  GuestLookupHandoff,
  PaymentConfirmation,
} from "./types";

const CHECKOUT_PREFIX = "pet-curation:checkout:";
const GUEST_LOOKUP_PREFIX = "pet-curation:guest-order:";
const MAX_AGE_MS = 2 * 60 * 60 * 1000;

type StorageLike = Pick<Storage, "getItem" | "setItem" | "removeItem">;

function sessionStorageOrNull(): StorageLike | null {
  try {
    return typeof window === "undefined" ? null : window.sessionStorage;
  } catch {
    return null;
  }
}

function readJson<T extends { version: 1; savedAt: number }>(
  storage: StorageLike | null,
  key: string,
  now = Date.now(),
): T | null {
  if (!storage) return null;
  try {
    const raw = storage.getItem(key);
    if (!raw) return null;
    const value = JSON.parse(raw) as Partial<T>;
    if (value.version !== 1 || typeof value.savedAt !== "number") {
      storage.removeItem(key);
      return null;
    }
    if (now - value.savedAt > MAX_AGE_MS) {
      storage.removeItem(key);
      return null;
    }
    return value as T;
  } catch {
    storage.removeItem(key);
    return null;
  }
}

export function saveCheckoutHandoff(
  handoff: CheckoutHandoff,
  storage = sessionStorageOrNull(),
) {
  storage?.setItem(
    `${CHECKOUT_PREFIX}${handoff.order.orderNumber}`,
    JSON.stringify(handoff),
  );
}

export function readCheckoutHandoff(
  orderNumber: string,
  storage = sessionStorageOrNull(),
  now = Date.now(),
) {
  const value = readJson<CheckoutHandoff>(
    storage,
    `${CHECKOUT_PREFIX}${orderNumber}`,
    now,
  );
  return value?.order.orderNumber === orderNumber ? value : null;
}

export function updateCheckoutConfirmation(
  orderNumber: string,
  confirmation: PaymentConfirmation,
  storage = sessionStorageOrNull(),
) {
  const current = readCheckoutHandoff(orderNumber, storage);
  if (!current) return;
  saveCheckoutHandoff({ ...current, confirmation, savedAt: Date.now() }, storage);
}

export function saveGuestLookupHandoff(
  handoff: GuestLookupHandoff,
  storage = sessionStorageOrNull(),
) {
  storage?.setItem(
    `${GUEST_LOOKUP_PREFIX}${handoff.orderNumber}`,
    JSON.stringify(handoff),
  );
}

export function readGuestLookupHandoff(
  orderNumber: string,
  storage = sessionStorageOrNull(),
  now = Date.now(),
) {
  const value = readJson<GuestLookupHandoff>(
    storage,
    `${GUEST_LOOKUP_PREFIX}${orderNumber}`,
    now,
  );
  return value?.orderNumber === orderNumber ? value : null;
}

export const checkoutStorageInternals = {
  checkoutKey: (orderNumber: string) => `${CHECKOUT_PREFIX}${orderNumber}`,
  maxAgeMs: MAX_AGE_MS,
};
