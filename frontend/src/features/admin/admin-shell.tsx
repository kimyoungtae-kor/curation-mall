"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import type { ReactNode } from "react";
import { useState } from "react";
import { useAuth } from "@/features/account/auth-provider";
import { AdminBrand } from "./admin-brand";

const navigation = [
  { href: "/admin", label: "대시보드", exact: true },
  { href: "/admin/products", label: "상품 · 재고" },
  { href: "/admin/orders", label: "주문" },
  { href: "/admin/home-sections", label: "홈 콘텐츠" },
];

type AdminGuardProps = {
  title: string;
  description?: string;
  actions?: ReactNode;
  children: ReactNode;
};

export function AdminGuard({ title, description, actions, children }: AdminGuardProps) {
  const pathname = usePathname();
  const router = useRouter();
  const { authenticated, user, loading, logout } = useAuth();
  const [loggingOut, setLoggingOut] = useState(false);
  const isAdmin = Boolean(user?.roles.includes("ADMIN"));

  if (loading) {
    return (
      <main className="admin-access-state" aria-live="polite">
        <span className="admin-spinner" aria-hidden="true" />
        <p>관리자 권한을 확인하고 있습니다.</p>
      </main>
    );
  }

  if (!authenticated || !user) {
    return (
      <main className="admin-access-state">
        <p className="admin-kicker">ADMIN CONSOLE</p>
        <h1>관리자 로그인이 필요합니다.</h1>
        <p>상품, 주문, 홈 콘텐츠는 관리자 계정으로만 변경할 수 있습니다.</p>
        <Link className="admin-button admin-button--primary" href="/admin/login">
          관리자 로그인
        </Link>
      </main>
    );
  }

  if (!isAdmin) {
    return (
      <main className="admin-access-state">
        <p className="admin-kicker">ACCESS DENIED</p>
        <h1>관리자 권한이 없습니다.</h1>
        <p>{user.email} 계정은 고객 권한으로 로그인되어 있습니다.</p>
        <div className="admin-access-state__actions">
          <Link className="admin-button" href="/">
            쇼핑몰로 돌아가기
          </Link>
          <button
            className="admin-button admin-button--primary"
            type="button"
            disabled={loggingOut}
            onClick={() => {
              setLoggingOut(true);
              void logout()
                .then(() => router.replace("/admin/login"))
                .finally(() => setLoggingOut(false));
            }}
          >
            {loggingOut ? "로그아웃 중…" : "로그아웃 후 관리자 로그인"}
          </button>
        </div>
      </main>
    );
  }

  return (
    <div className="admin-layout">
      <aside className="admin-sidebar">
        <AdminBrand />
        <nav className="admin-nav" aria-label="관리자 메뉴">
          {navigation.map((item) => {
            const active = item.exact ? pathname === item.href : pathname.startsWith(item.href);
            return (
              <Link key={item.href} href={item.href} aria-current={active ? "page" : undefined}>
                {item.label}
              </Link>
            );
          })}
        </nav>
        <div className="admin-sidebar__footer">
          <div>
            <strong>{user.name}</strong>
            <span>{user.email}</span>
          </div>
          <button
            type="button"
            disabled={loggingOut}
            onClick={() => {
              setLoggingOut(true);
              void logout()
                .then(() => router.replace("/admin/login"))
                .finally(() => setLoggingOut(false));
            }}
          >
            {loggingOut ? "처리 중…" : "로그아웃"}
          </button>
          <Link href="/" target="_blank">
            쇼핑몰 보기 ↗
          </Link>
        </div>
      </aside>

      <main className="admin-main">
        <header className="admin-page-heading">
          <div>
            <p className="admin-kicker">ADMIN</p>
            <h1>{title}</h1>
            {description ? <p>{description}</p> : null}
          </div>
          {actions ? <div className="admin-page-heading__actions">{actions}</div> : null}
        </header>
        {children}
      </main>
    </div>
  );
}
