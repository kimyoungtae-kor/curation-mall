"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { login, logout as logoutRequest } from "@/features/account/api";
import { useAuth } from "@/features/account/auth-provider";
import { AdminBrand } from "./admin-brand";
import { adminErrorMessage } from "./error";

export function AdminLogin() {
  const router = useRouter();
  const auth = useAuth();
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const alreadyAdmin = Boolean(auth.user?.roles.includes("ADMIN"));

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    const form = new FormData(event.currentTarget);
    try {
      const result = await login({
        email: String(form.get("email") ?? "").trim(),
        password: String(form.get("password") ?? ""),
      });
      if (!result.user.roles.includes("ADMIN")) {
        await logoutRequest();
        await auth.refresh();
        setError("이 계정에는 관리자 권한이 없습니다.");
        return;
      }
      auth.acceptAuthResult(result);
      router.replace("/admin");
      router.refresh();
    } catch (caught) {
      setError(adminErrorMessage(caught));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="admin-login-page">
      <section className="admin-login-panel">
        <AdminBrand login />
        <p className="admin-kicker">SECURE SIGN IN</p>
        <h1>관리자 로그인</h1>
        <p>등록된 관리자 계정으로 로그인해 주세요.</p>

        {alreadyAdmin ? (
          <div className="admin-login-current">
            <strong>{auth.user?.name}님으로 로그인되어 있습니다.</strong>
            <Link className="admin-button admin-button--primary" href="/admin">
              대시보드로 이동
            </Link>
          </div>
        ) : (
          <form className="admin-form" onSubmit={submit}>
            <label>
              이메일
              <input name="email" type="email" required autoComplete="username" maxLength={254} />
            </label>
            <label>
              비밀번호
              <input
                name="password"
                type="password"
                required
                minLength={8}
                maxLength={100}
                autoComplete="current-password"
              />
            </label>
            {error ? <p className="admin-alert admin-alert--error" role="alert">{error}</p> : null}
            <button className="admin-button admin-button--primary admin-button--full" type="submit" disabled={submitting}>
              {submitting ? "권한 확인 중…" : "로그인"}
            </button>
          </form>
        )}
        <Link className="admin-login-back" href="/">← 쇼핑몰로 돌아가기</Link>
      </section>
    </main>
  );
}
