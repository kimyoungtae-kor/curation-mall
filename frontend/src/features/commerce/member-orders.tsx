"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { getAuthState, getMemberOrders } from "./api";
import { commerceErrorMessage } from "./error-message";
import {
  formatDateTime,
  formatWon,
  orderStatusLabel,
  paymentStatusLabel,
} from "./format";
import type { AuthState, OrderListItem, PageMetadata } from "./types";

type MemberOrdersState = {
  auth: AuthState | null;
  orders: OrderListItem[];
  page: PageMetadata | null;
};

export function MemberOrders({ pageSize = 10 }: { pageSize?: number }) {
  const [pageNumber, setPageNumber] = useState(0);
  const [state, setState] = useState<MemberOrdersState>({
    auth: null,
    orders: [],
    page: null,
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    async function load() {
      setLoading(true);
      setError(null);
      try {
        const auth = await getAuthState(controller.signal);
        if (!auth.authenticated || !auth.user?.roles.includes("CUSTOMER")) {
          setState({ auth, orders: [], page: null });
          return;
        }
        const response = await getMemberOrders(pageNumber, pageSize, controller.signal);
        setState({ auth, orders: response.data, page: response.page });
      } catch (reason) {
        if (!controller.signal.aborted) {
          setError(commerceErrorMessage(reason, "주문 내역을 불러오지 못했습니다."));
        }
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }
    void load();
    return () => controller.abort();
  }, [pageNumber, pageSize]);

  if (loading && !state.auth) {
    return <div className="commerce-state" role="status">주문 내역을 불러오고 있습니다.</div>;
  }

  if (!state.auth?.authenticated || !state.auth.user?.roles.includes("CUSTOMER")) {
    return (
      <div className="commerce-state">
        <p className="commerce-eyebrow">MY ORDERS</p>
        <h1>로그인이 필요합니다</h1>
        <p>회원 주문 내역은 로그인한 본인만 확인할 수 있습니다.</p>
        <Link
          className="commerce-button commerce-button--primary"
          href="/login?next=%2Fmypage%2Forders"
        >
          로그인하기
        </Link>
      </div>
    );
  }

  return (
    <div className="commerce-page">
      <header className="commerce-page-heading commerce-page-heading--left">
        <p className="commerce-eyebrow">MY ORDERS</p>
        <h1>주문 내역</h1>
        <p>{state.auth.user.name}님의 주문과 배송 상태입니다.</p>
      </header>

      {error ? <p className="commerce-alert" role="alert">{error}</p> : null}
      {state.orders.length === 0 && !error ? (
        <div className="commerce-state commerce-panel" role="status">
          <h2>아직 주문 내역이 없습니다</h2>
          <p>상황과 공간에 맞는 반려동물 용품을 둘러보세요.</p>
          <Link className="commerce-button commerce-button--primary" href="/products">
            상품 둘러보기
          </Link>
        </div>
      ) : (
        <div className="commerce-order-list" aria-busy={loading}>
          {state.orders.map((order) => (
            <OrderListCard key={order.orderNumber} order={order} />
          ))}
        </div>
      )}

      {state.page && state.page.totalPages > 1 ? (
        <nav className="commerce-pagination" aria-label="주문 내역 페이지">
          <button
            type="button"
            disabled={state.page.first || loading}
            onClick={() => setPageNumber((current) => Math.max(0, current - 1))}
          >
            이전
          </button>
          <span>{state.page.number + 1} / {state.page.totalPages}</span>
          <button
            type="button"
            disabled={state.page.last || loading}
            onClick={() => setPageNumber((current) => current + 1)}
          >
            다음
          </button>
        </nav>
      ) : null}
    </div>
  );
}

export function OrderListCard({ order }: { order: OrderListItem }) {
  const orderedAt = order.orderedAt ?? order.createdAt;
  return (
    <article className="commerce-order-card commerce-panel">
      <div className="commerce-order-card__header">
        <div>
          <time dateTime={orderedAt}>{formatDateTime(orderedAt)}</time>
          <strong>{order.orderNumber}</strong>
        </div>
        <span data-tone={order.orderStatus === "CANCELLED" ? "danger" : "brand"}>
          {orderStatusLabel(order.orderStatus)}
        </span>
      </div>
      <div className="commerce-order-card__body">
        <div>
          <h2>{order.representativeItemName ?? "반려동물 라이프스타일 상품"}</h2>
          <p>
            {typeof order.itemCount === "number" ? `${order.itemCount}개 · ` : ""}
            {paymentStatusLabel(order.paymentStatus)}
          </p>
        </div>
        <strong>{formatWon(order.totalAmount)}</strong>
      </div>
      <Link
        className="commerce-button commerce-button--secondary"
        href={`/mypage/orders/${encodeURIComponent(order.orderNumber)}`}
      >
        주문 상세
      </Link>
    </article>
  );
}
