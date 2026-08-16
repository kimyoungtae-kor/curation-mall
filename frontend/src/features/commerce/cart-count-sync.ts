type CartCountSyncOptions = {
  currentCartCount: number;
  purchasedCartItemIds: string[];
  setCartCount: (cartCount: number) => void;
  refresh: () => Promise<void>;
};

export function cartCountAfterOrderCreation(
  currentCartCount: number,
  purchasedCartItemIds: string[],
) {
  const purchasedItemCount = new Set(purchasedCartItemIds).size;
  return Math.max(0, currentCartCount - purchasedItemCount);
}

export async function syncCartAfterOrderCreation({
  currentCartCount,
  purchasedCartItemIds,
  setCartCount,
  refresh,
}: CartCountSyncOptions) {
  setCartCount(
    cartCountAfterOrderCreation(currentCartCount, purchasedCartItemIds),
  );

  try {
    await refresh();
  } catch {
    // The optimistic count is still more accurate than the pre-order count.
  }
}
