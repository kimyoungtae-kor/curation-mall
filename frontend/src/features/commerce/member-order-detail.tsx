"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { getAuthState, getMemberOrder } from "./api";
import { commerceErrorMessage } from "./error-message";
import { OrderDetailView } from "./order-detail-view";
import type { OrderDetail } from "./types";

export function MemberOrderDetail({ orderNumber }: { orderNumber: string }) {
  const [detail, setDetail] = useState<OrderDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [loginRequired, setLoginRequired] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    async function load() {
      try {
        const auth = await getAuthState(controller.signal);
        if (!auth.authenticated || !auth.user?.roles.includes("CUSTOMER")) {
          setLoginRequired(true);
          return;
        }
        setDetail(await getMemberOrder(orderNumber, controller.signal));
      } catch (reason) {
        if (!controller.signal.aborted) {
          setError(commerceErrorMessage(reason, "주문 상세를 불러오지 못했습니다."));
        }
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }
    void load();
    return () => controller.abort();
  }, [orderNumber]);

  if (loading) {
    return <div className="commerce-state" role="status">주문 상세를 불러오고 있습니다.</div>;
  }

  if (loginRequired) {
    return (
      <div className="commerce-state">
        <h1>로그인이 필요합니다</h1>
        <p>회원 주문은 로그인한 본인만 확인할 수 있습니다.</p>
        <Link
          className="commerce-button commerce-button--primary"
          href={`/login?next=${encodeURIComponent(`/mypage/orders/${orderNumber}`)}`}
        >
          로그인하기
        </Link>
      </div>
    );
  }

  if (!detail) {
    return (
      <div className="commerce-state" role="alert">
        <h1>주문을 표시할 수 없습니다</h1>
        <p>{error}</p>
        <Link className="commerce-button commerce-button--secondary" href="/mypage/orders">
          주문 내역으로 돌아가기
        </Link>
      </div>
    );
  }

  return (
    <div className="commerce-page">
      <OrderDetailView order={detail} />
      <div className="commerce-actions">
        <Link className="commerce-button commerce-button--secondary" href="/mypage/orders">
          주문 내역으로 돌아가기
        </Link>
      </div>
    </div>
  );
}
