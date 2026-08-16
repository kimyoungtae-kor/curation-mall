import type { Metadata } from "next";
import Link from "next/link";
import { CheckoutOrderForm } from "@/features/commerce/checkout-order-form";
import {
  parseCartItemIds,
  parseOrderType,
} from "@/features/commerce/checkout-query";

export const metadata: Metadata = {
  title: "주문서",
  robots: { index: false, follow: false },
};

export default async function CheckoutOrderPage({
  searchParams,
}: {
  searchParams: Promise<{
    items?: string | string[];
    type?: string | string[];
  }>;
}) {
  const query = await searchParams;
  const cartItemIds = parseCartItemIds(query.items);
  const orderType = parseOrderType(query.type);

  if (cartItemIds.length === 0 || !orderType) {
    return (
      <div className="commerce-state" role="alert">
        <h1>올바른 주문 정보가 아닙니다</h1>
        <p>장바구니에서 주문할 상품과 주문 방법을 다시 선택해 주세요.</p>
        <Link className="commerce-button commerce-button--primary" href="/cart">
          장바구니로 이동
        </Link>
      </div>
    );
  }

  return <CheckoutOrderForm cartItemIds={cartItemIds} orderType={orderType} />;
}
