import type { Metadata } from "next";
import { CheckoutEntry } from "@/features/commerce/checkout-entry";
import { parseCartItemIds } from "@/features/commerce/checkout-query";

export const metadata: Metadata = {
  title: "주문 방법 선택",
  robots: { index: false, follow: false },
};

export default async function CheckoutPage({
  searchParams,
}: {
  searchParams: Promise<{ items?: string | string[] }>;
}) {
  const cartItemIds = parseCartItemIds((await searchParams).items);
  return <CheckoutEntry cartItemIds={cartItemIds} />;
}
