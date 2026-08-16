import { describe, expect, it } from "vitest";
import { checkoutStorageInternals, readCheckoutHandoff, saveCheckoutHandoff } from "./storage";
import type { CheckoutHandoff } from "./types";

class MemoryStorage {
  private readonly data = new Map<string, string>();
  getItem(key: string) {
    return this.data.get(key) ?? null;
  }
  setItem(key: string, value: string) {
    this.data.set(key, value);
  }
  removeItem(key: string) {
    this.data.delete(key);
  }
}

const handoff: CheckoutHandoff = {
  version: 1,
  savedAt: 100,
  order: {
    orderNumber: "P20260812-7K9M4Q2X",
    orderType: "GUEST",
    orderStatus: "PENDING_PAYMENT",
    paymentStatus: "READY",
    itemsAmount: 32900,
    discountAmount: 0,
    shippingAmount: 3000,
    totalAmount: 35900,
    currency: "KRW",
    reservationExpiresAt: "2026-08-12T10:30:00+09:00",
    createdAt: "2026-08-12T10:10:00+09:00",
  },
  payment: {
    paymentAttemptId: "8958fa7a-d0c4-479c-9138-31365be44b40",
    provider: "SIMULATED",
    amount: 35900,
    status: "READY",
  },
  guestLookupToken: "secret-token",
  confirmation: null,
};

describe("checkout handoff storage", () => {
  it("keeps the guest secret in session storage rather than a URL", () => {
    const storage = new MemoryStorage();
    saveCheckoutHandoff(handoff, storage);

    expect(readCheckoutHandoff(handoff.order.orderNumber, storage, 200)).toEqual(
      handoff,
    );
    expect(checkoutStorageInternals.checkoutKey(handoff.order.orderNumber)).not.toContain(
      handoff.guestLookupToken,
    );
  });

  it("removes an expired checkout handoff", () => {
    const storage = new MemoryStorage();
    saveCheckoutHandoff(handoff, storage);

    expect(
      readCheckoutHandoff(
        handoff.order.orderNumber,
        storage,
        handoff.savedAt + checkoutStorageInternals.maxAgeMs + 1,
      ),
    ).toBeNull();
  });
});
