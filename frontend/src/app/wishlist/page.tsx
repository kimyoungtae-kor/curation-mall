import type { Metadata } from "next";
import { CustomerPageShell } from "@/components/customer-page-shell";
import { WishlistPage } from "@/features/account/wishlist-page";

export const metadata: Metadata = { title: "찜 목록" };
export default function Page() { return <CustomerPageShell><main className="commerce-page" id="main-content"><div className="commerce-page__heading"><p className="eyebrow">WISHLIST</p><h1>찜 목록</h1></div><WishlistPage /></main></CustomerPageShell>; }
