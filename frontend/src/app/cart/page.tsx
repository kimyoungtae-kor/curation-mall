import type { Metadata } from "next";
import { CustomerPageShell } from "@/components/customer-page-shell";
import { CartPage } from "@/features/account/cart-page";

export const metadata: Metadata = { title: "장바구니" };
export default function Page() { return <CustomerPageShell><main className="commerce-page" id="main-content"><div className="commerce-page__heading"><p className="eyebrow">YOUR CART</p><h1>장바구니</h1></div><CartPage /></main></CustomerPageShell>; }
