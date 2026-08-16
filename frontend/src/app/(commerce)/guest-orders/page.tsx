import type { Metadata } from "next";
import { GuestOrderLookup } from "@/features/commerce/guest-order-lookup";

export const metadata: Metadata = {
  title: "비회원 주문 조회",
  robots: { index: false, follow: false },
};

export default function GuestOrdersPage() {
  return <GuestOrderLookup />;
}
