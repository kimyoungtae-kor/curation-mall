import { afterEach, describe, expect, it } from "vitest";
import { renderToString } from "react-dom/server";
import { checkoutStorageInternals } from "./storage";
import type { CheckoutHandoff } from "./types";
import { useCheckoutHandoff } from "./use-checkout-handoff";

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

const originalWindow = Object.getOwnPropertyDescriptor(globalThis, "window");

afterEach(() => {
  if (originalWindow) {
    Object.defineProperty(globalThis, "window", originalWindow);
  } else {
    Reflect.deleteProperty(globalThis, "window");
  }
});

function HandoffProbe({ orderNumber }: { orderNumber: string }) {
  const { handoff, loading } = useCheckoutHandoff(orderNumber);
  return <output>{loading ? "loading" : handoff?.order.orderNumber ?? "missing"}</output>;
}

describe("useCheckoutHandoff", () => {
  it("keeps the server and hydration render identical even when session storage has data", () => {
    const orderNumber = "P20260813-TPHJK7ST";
    const handoff: CheckoutHandoff = {
      version: 1,
      savedAt: Date.now(),
      order: {
        orderNumber,
        orderType: "MEMBER",
        orderStatus: "PAID",
        paymentStatus: "APPROVED",
        itemsAmount: 32900,
        discountAmount: 0,
        shippingAmount: 3000,
        totalAmount: 35900,
        currency: "KRW",
        reservationExpiresAt: "2026-08-13T10:30:00+09:00",
        createdAt: "2026-08-13T10:10:00+09:00",
      },
      payment: {
        paymentAttemptId: "8958fa7a-d0c4-479c-9138-31365be44b40",
        provider: "SIMULATED",
        amount: 35900,
        status: "APPROVED",
      },
      guestLookupToken: null,
      confirmation: null,
    };

    Reflect.deleteProperty(globalThis, "window");
    const serverMarkup = renderToString(<HandoffProbe orderNumber={orderNumber} />);

    const storage = new MemoryStorage();
    storage.setItem(
      checkoutStorageInternals.checkoutKey(orderNumber),
      JSON.stringify(handoff),
    );
    Object.defineProperty(globalThis, "window", {
      configurable: true,
      value: { sessionStorage: storage },
    });
    const hydrationMarkup = renderToString(<HandoffProbe orderNumber={orderNumber} />);

    expect(serverMarkup).toContain("loading");
    expect(hydrationMarkup).toBe(serverMarkup);
    expect(hydrationMarkup).not.toContain(orderNumber);
  });
});
