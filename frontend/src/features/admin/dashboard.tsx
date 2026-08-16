"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import {
  formatDateTime,
  formatWon,
  orderStatusLabel,
} from "@/features/commerce/format";
import { getAdminOrders, getAdminProducts, getAdminUsers } from "./api";
import { AdminGuard } from "./admin-shell";
import { adminErrorMessage } from "./error";
import type { AdminOrderSummary, AdminProductSummary } from "./types";

type DashboardState = {
  productTotal: number;
  userTotal: number;
  published: number;
  lowStock: AdminProductSummary[];
  recentOrders: AdminOrderSummary[];
};

export function AdminDashboard() {
  return (
    <AdminGuard title="대시보드" description="상품, 재고, 주문의 현재 상태를 빠르게 확인합니다.">
      <DashboardContent />
    </AdminGuard>
  );
}

function DashboardContent() {
  const [state, setState] = useState<DashboardState | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([
      getAdminProducts({ page: 0, size: 100 }, controller.signal),
      getAdminOrders({ page: 0, size: 5 }, controller.signal),
      getAdminUsers({ page: 0, size: 1 }, controller.signal),
    ])
      .then(([products, orders, users]) => {
        setState({
          productTotal: products.page.totalElements,
          userTotal: users.page.totalElements,
          published: products.data.filter((product) => product.status === "PUBLISHED").length,
          lowStock: products.data
            .filter((product) => product.totalStock <= 5)
            .sort((a, b) => a.totalStock - b.totalStock)
            .slice(0, 5),
          recentOrders: orders.data,
        });
      })
      .catch((caught) => {
        if (!controller.signal.aborted) setError(adminErrorMessage(caught));
      });
    return () => controller.abort();
  }, []);

  if (error) return <p className="admin-alert admin-alert--error" role="alert">{error}</p>;
  if (!state) return <AdminLoading label="대시보드 데이터를 불러오는 중입니다." />;

  return (
    <div className="admin-dashboard">
      <section className="admin-stat-grid" aria-label="운영 현황">
        <article className="admin-stat-card">
          <span>전체 상품</span>
          <strong>{state.productTotal.toLocaleString("ko-KR")}</strong>
          <small>공개 {state.published}개</small>
        </article>
        <article className="admin-stat-card">
          <span>재고 주의</span>
          <strong>{state.lowStock.length.toLocaleString("ko-KR")}</strong>
          <small>총 재고 5개 이하 상품</small>
        </article>
        <article className="admin-stat-card">
          <span>전체 회원</span>
          <strong>{state.userTotal.toLocaleString("ko-KR")}</strong>
          <small>관리자 포함 등록 계정</small>
        </article>
        <article className="admin-stat-card admin-stat-card--accent">
          <span>빠른 작업</span>
          <Link href="/admin/products/new">새 상품 등록 →</Link>
          <Link href="/admin/home-sections">홈 콘텐츠 편집 →</Link>
        </article>
      </section>

      <div className="admin-dashboard-grid">
        <section className="admin-panel">
          <div className="admin-panel__heading">
            <div>
              <h2>최근 주문</h2>
              <p>최근 접수된 주문 5건입니다.</p>
            </div>
            <Link href="/admin/orders">전체 보기</Link>
          </div>
          {state.recentOrders.length ? (
            <div className="admin-compact-list">
              {state.recentOrders.map((order) => (
                <Link href={`/admin/orders?order=${encodeURIComponent(order.orderNumber)}`} key={order.orderNumber}>
                  <div>
                    <strong>{order.orderNumber}</strong>
                    <span>{order.buyerName} · {formatDateTime(order.orderedAt)}</span>
                  </div>
                  <div className="admin-compact-list__right">
                    <strong>{formatWon(order.totalAmount)}</strong>
                    <span className="admin-status" data-status={order.orderStatus}>
                      {orderStatusLabel(order.orderStatus)}
                    </span>
                  </div>
                </Link>
              ))}
            </div>
          ) : <AdminEmpty label="아직 접수된 주문이 없습니다." />}
        </section>

        <section className="admin-panel">
          <div className="admin-panel__heading">
            <div>
              <h2>재고 주의 상품</h2>
              <p>판매 가능 옵션의 총 재고 기준입니다.</p>
            </div>
            <Link href="/admin/products">재고 관리</Link>
          </div>
          {state.lowStock.length ? (
            <div className="admin-compact-list">
              {state.lowStock.map((product) => (
                <Link href={`/admin/products?product=${encodeURIComponent(product.id)}`} key={product.id}>
                  <div>
                    <strong>{product.name}</strong>
                    <span>{product.brandName} · {product.slug}</span>
                  </div>
                  <strong className="admin-stock" data-low={product.totalStock <= 2}>
                    {product.totalStock}개
                  </strong>
                </Link>
              ))}
            </div>
          ) : <AdminEmpty label="재고가 부족한 상품이 없습니다." />}
        </section>
      </div>
    </div>
  );
}

export function AdminLoading({ label }: { label: string }) {
  return (
    <div className="admin-loading" aria-live="polite">
      <span className="admin-spinner" aria-hidden="true" />
      <p>{label}</p>
    </div>
  );
}

export function AdminEmpty({ label }: { label: string }) {
  return <p className="admin-empty">{label}</p>;
}
