"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { getAuthState, getMemberOrders } from "./api";
import { commerceErrorMessage } from "./error-message";
import { OrderListCard } from "./member-orders";
import type { AuthState, OrderListItem } from "./types";

export function MyPage() {
  const [auth, setAuth] = useState<AuthState | null>(null);
  const [recentOrders, setRecentOrders] = useState<OrderListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    async function load() {
      try {
        const nextAuth = await getAuthState(controller.signal);
        setAuth(nextAuth);
        if (nextAuth.authenticated && nextAuth.user?.roles.includes("CUSTOMER")) {
          const response = await getMemberOrders(0, 3, controller.signal);
          setRecentOrders(response.data);
        }
      } catch (reason) {
        if (!controller.signal.aborted) {
          setError(commerceErrorMessage(reason, "내 정보를 불러오지 못했습니다."));
        }
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }
    void load();
    return () => controller.abort();
  }, []);

  if (loading) {
    return <div className="commerce-state" role="status">내 정보를 불러오고 있습니다.</div>;
  }

  if (!auth?.authenticated || !auth.user?.roles.includes("CUSTOMER")) {
    return (
      <div className="commerce-state">
        <p className="commerce-eyebrow">MY PAGE</p>
        <h1>로그인이 필요합니다</h1>
        <p>주문 내역과 찜 목록은 로그인 후 확인할 수 있습니다.</p>
        <Link className="commerce-button commerce-button--primary" href="/login?next=%2Fmypage">
          로그인하기
        </Link>
      </div>
    );
  }

  return (
    <div className="commerce-page">
      <header className="commerce-page-heading commerce-page-heading--left">
        <p className="commerce-eyebrow">MY PAGE</p>
        <h1>{auth.user.name}님, 반갑습니다</h1>
        <p>주문과 관심 상품을 한곳에서 확인하세요.</p>
      </header>
      {error ? <p className="commerce-alert" role="alert">{error}</p> : null}

      <div className="commerce-account-grid">
        <section className="commerce-panel" aria-labelledby="account-info-title">
          <h2 id="account-info-title">내 정보</h2>
          <dl className="commerce-definition-list">
            <div><dt>이름</dt><dd>{auth.user.name}</dd></div>
            <div><dt>이메일</dt><dd>{auth.user.email}</dd></div>
            <div><dt>연락처</dt><dd>{auth.user.phone}</dd></div>
          </dl>
        </section>
        <nav className="commerce-panel commerce-quick-links" aria-label="마이페이지 메뉴">
          <Link href="/mypage/orders"><span>주문 내역</span><strong>전체 보기 →</strong></Link>
          <Link href="/wishlist"><span>찜 목록</span><strong>{auth.wishlistCount}개 →</strong></Link>
          <Link href="/cart"><span>장바구니</span><strong>{auth.cartCount}개 →</strong></Link>
        </nav>
      </div>

      <section className="commerce-recent" aria-labelledby="recent-orders-title">
        <div className="commerce-section-heading">
          <h2 id="recent-orders-title">최근 주문</h2>
          <Link href="/mypage/orders">전체 주문 보기</Link>
        </div>
        {recentOrders.length > 0 ? (
          <div className="commerce-order-list">
            {recentOrders.map((order) => <OrderListCard key={order.orderNumber} order={order} />)}
          </div>
        ) : (
          <div className="commerce-state commerce-panel"><p>아직 주문 내역이 없습니다.</p></div>
        )}
      </section>
    </div>
  );
}
