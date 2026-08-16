import type { Metadata } from "next";
import { CustomerPageShell } from "@/components/customer-page-shell";
import { AccountPage } from "@/features/account/account-page";

export const metadata: Metadata = { title: "내정보" };
export default function Page() { return <CustomerPageShell><main className="commerce-page" id="main-content"><div className="commerce-page__heading"><p className="eyebrow">MY ACCOUNT</p><h1>내정보</h1></div><AccountPage /></main></CustomerPageShell>; }
