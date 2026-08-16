import type { Metadata } from "next";
import Link from "next/link";
import { isOrderNumber } from "@/features/commerce/checkout-query";
import { PaymentResult } from "@/features/commerce/payment-result";

export const metadata: Metadata = {
  title: "테스트 결제",
  robots: { index: false, follow: false },
};

export default async function PaymentResultPage({
  searchParams,
}: {
  searchParams: Promise<{ orderNumber?: string | string[] }>;
}) {
  const raw = (await searchParams).orderNumber;
  const orderNumber = Array.isArray(raw) ? raw[0] : raw;
  if (!orderNumber || !isOrderNumber(orderNumber)) {
    return (
      <div className="commerce-state" role="alert">
        <h1>결제할 주문을 찾지 못했습니다</h1>
        <Link className="commerce-button commerce-button--primary" href="/cart">
          장바구니로 이동
        </Link>
      </div>
    );
  }
  return <PaymentResult orderNumber={orderNumber} />;
}
