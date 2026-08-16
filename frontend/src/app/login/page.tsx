import type { Metadata } from "next";
import { Suspense } from "react";
import { CustomerPageShell } from "@/components/customer-page-shell";
import { AuthForm } from "@/features/account/auth-form";

export const metadata: Metadata = { title: "로그인" };

export default function LoginPage() {
  return (
    <CustomerPageShell>
      <main className="auth-page" id="main-content">
        <section className="auth-panel" aria-labelledby="auth-title">
          <p className="eyebrow">WELCOME BACK</p>
          <h1 id="auth-title">로그인</h1>
          <p>찜한 상품과 장바구니를 이어서 확인하세요.</p>
          <Suspense fallback={<p>로그인 화면을 준비하는 중…</p>}><AuthForm mode="login" /></Suspense>
        </section>
      </main>
    </CustomerPageShell>
  );
}
