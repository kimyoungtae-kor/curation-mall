import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { isOrderNumber } from "@/features/commerce/checkout-query";
import { MemberOrderDetail } from "@/features/commerce/member-order-detail";

export const metadata: Metadata = {
  title: "주문 상세",
  robots: { index: false, follow: false },
};

export default async function MemberOrderDetailPage({
  params,
}: {
  params: Promise<{ orderNumber: string }>;
}) {
  const { orderNumber } = await params;
  if (!isOrderNumber(orderNumber)) notFound();
  return <MemberOrderDetail orderNumber={orderNumber} />;
}
