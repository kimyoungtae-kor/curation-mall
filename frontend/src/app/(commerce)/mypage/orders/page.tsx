import type { Metadata } from "next";
import { MemberOrders } from "@/features/commerce/member-orders";

export const metadata: Metadata = {
  title: "주문 내역",
  robots: { index: false, follow: false },
};

export default function MemberOrdersPage() {
  return <MemberOrders />;
}
