"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { lookupGuestOrder } from "./api";
import { commerceErrorMessage } from "./error-message";
import { OrderDetailView } from "./order-detail-view";
import {
  readCheckoutHandoff,
  readGuestLookupHandoff,
  saveGuestLookupHandoff,
} from "./storage";
import type { OrderDetail } from "./types";

export function GuestOrderDetail({ orderNumber }: { orderNumber: string }) {
  const [detail, setDetail] = useState<OrderDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    async function load() {
      const lookupHandoff = readGuestLookupHandoff(orderNumber);
      const checkoutHandoff = readCheckoutHandoff(orderNumber);
      const token =
        lookupHandoff?.guestLookupToken ?? checkoutHandoff?.guestLookupToken ?? null;

      if (!token) {
        setError("이 탭에 조회 토큰이 없습니다. 비회원 주문 조회 화면에서 다시 확인해 주세요.");
        setLoading(false);
        return;
      }

      if (lookupHandoff?.detail) setDetail(lookupHandoff.detail);
      try {
        const nextDetail = await lookupGuestOrder(
          { orderNumber, guestLookupToken: token },
          controller.signal,
        );
        setDetail(nextDetail);
        saveGuestLookupHandoff({
          version: 1,
          orderNumber,
          guestLookupToken: token,
          detail: nextDetail,
          savedAt: Date.now(),
        });
      } catch (reason) {
        if (!controller.signal.aborted) {
          setError(commerceErrorMessage(reason, "주문 상세를 새로 확인하지 못했습니다."));
        }
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }
    void load();
    return () => controller.abort();
  }, [orderNumber]);

  if (loading && !detail) {
    return <div className="commerce-state" role="status">비회원 주문을 확인하고 있습니다.</div>;
  }

  if (!detail) {
    return (
      <div className="commerce-state" role="alert">
        <h1>주문을 표시할 수 없습니다</h1>
        <p>{error}</p>
        <Link className="commerce-button commerce-button--primary" href="/guest-orders">
          주문번호와 토큰 입력
        </Link>
      </div>
    );
  }

  return (
    <div className="commerce-page">
      {error ? <p className="commerce-alert" role="status">{error}</p> : null}
      <OrderDetailView order={detail} />
      <div className="commerce-actions">
        <Link className="commerce-button commerce-button--secondary" href="/guest-orders">
          다른 주문 조회
        </Link>
        <Link className="commerce-button commerce-button--secondary" href="/">
          홈으로 이동
        </Link>
      </div>
    </div>
  );
}
