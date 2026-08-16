import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { isOrderNumber } from "@/features/commerce/checkout-query";
import { OrderComplete } from "@/features/commerce/order-complete";

export const metadata: Metadata = {
  title: "주문 완료",
  robots: { index: false, follow: false },
};

export default async function OrderCompletePage({
  params,
}: {
  params: Promise<{ orderNumber: string }>;
}) {
  const { orderNumber } = await params;
  if (!isOrderNumber(orderNumber)) notFound();
  return <OrderComplete orderNumber={orderNumber} />;
}
