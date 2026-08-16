import { describe, expect, it, vi } from "vitest";
import {
  cartCountAfterOrderCreation,
  syncCartAfterOrderCreation,
} from "./cart-count-sync";

describe("cart count synchronization after order creation", () => {
  it("optimistically removes only distinct purchased cart lines", () => {
    expect(cartCountAfterOrderCreation(4, ["item-a", "item-b", "item-b"])).toBe(2);
    expect(cartCountAfterOrderCreation(1, ["item-a", "item-b"])).toBe(0);
  });

  it("updates immediately and then asks the server for the authoritative count", async () => {
    const setCartCount = vi.fn();
    const refresh = vi.fn().mockResolvedValue(undefined);

    await syncCartAfterOrderCreation({
      currentCartCount: 3,
      purchasedCartItemIds: ["item-a", "item-b"],
      setCartCount,
      refresh,
    });

    expect(setCartCount).toHaveBeenCalledWith(1);
    expect(refresh).toHaveBeenCalledOnce();
  });

  it("keeps the optimistic count when a follow-up refresh fails", async () => {
    const setCartCount = vi.fn();

    await expect(
      syncCartAfterOrderCreation({
        currentCartCount: 1,
        purchasedCartItemIds: ["item-a"],
        setCartCount,
        refresh: vi.fn().mockRejectedValue(new Error("offline")),
      }),
    ).resolves.toBeUndefined();

    expect(setCartCount).toHaveBeenCalledWith(0);
  });
});
