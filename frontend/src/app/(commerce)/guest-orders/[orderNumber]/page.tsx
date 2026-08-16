import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { isOrderNumber } from "@/features/commerce/checkout-query";
import { GuestOrderDetail } from "@/features/commerce/guest-order-detail";

export const metadata: Metadata = {
  title: "비회원 주문 상세",
  robots: { index: false, follow: false },
};

export default async function GuestOrderDetailPage({
  params,
}: {
  params: Promise<{ orderNumber: string }>;
}) {
  const { orderNumber } = await params;
  if (!isOrderNumber(orderNumber)) notFound();
  return <GuestOrderDetail orderNumber={orderNumber} />;
}
