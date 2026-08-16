import type { Metadata } from "next";
import { Suspense } from "react";
import { CustomerPageShell } from "@/components/customer-page-shell";
import { AuthForm } from "@/features/account/auth-form";

export const metadata: Metadata = { title: "회원가입" };

export default function SignupPage() {
  return (
    <CustomerPageShell>
      <main className="auth-page" id="main-content">
        <section className="auth-panel" aria-labelledby="auth-title">
          <p className="eyebrow">JOIN US</p>
          <h1 id="auth-title">회원가입</h1>
          <p>기본 정보만 입력하면 바로 쇼핑을 시작할 수 있어요.</p>
          <Suspense fallback={<p>가입 화면을 준비하는 중…</p>}><AuthForm mode="signup" /></Suspense>
        </section>
      </main>
    </CustomerPageShell>
  );
}
