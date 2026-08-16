"use client";

import Link from "next/link";
import { useState } from "react";
import { formatDateTime, formatWon } from "./format";
import { useCheckoutHandoff } from "./use-checkout-handoff";

export function OrderComplete({ orderNumber }: { orderNumber: string }) {
  const { handoff, loading } = useCheckoutHandoff(orderNumber);
  const [copied, setCopied] = useState<"order" | "token" | null>(null);

  async function copy(value: string, target: "order" | "token") {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(target);
    } catch {
      setCopied(null);
    }
  }

  if (loading) {
    return <div className="commerce-state" role="status">주문 완료 정보를 확인하고 있습니다.</div>;
  }

  if (!handoff) {
    return (
      <div className="commerce-state">
        <p className="commerce-eyebrow">ORDER COMPLETE</p>
        <h1>주문 완료 정보가 이 탭에 남아 있지 않습니다</h1>
        <p>회원 주문은 마이페이지에서, 비회원 주문은 주문번호와 조회 토큰으로 확인해 주세요.</p>
        <div className="commerce-actions">
          <Link className="commerce-button commerce-button--primary" href="/mypage/orders">
            회원 주문 내역
          </Link>
          <Link className="commerce-button commerce-button--secondary" href="/guest-orders">
            비회원 주문 조회
          </Link>
        </div>
      </div>
    );
  }

  const approved =
    handoff.confirmation?.payment.status === "APPROVED" &&
    handoff.confirmation.orderStatus === "PAID";
  const guest = handoff.order.orderType === "GUEST";

  return (
    <div className="commerce-narrow commerce-complete">
      <header className="commerce-page-heading">
        <span className="commerce-complete__mark" aria-hidden="true">✓</span>
        <p className="commerce-eyebrow">ORDER COMPLETE</p>
        <h1>{approved ? "주문이 완료되었습니다" : "주문이 접수되었습니다"}</h1>
        <p>반려동물과 보내는 더 좋은 시간을 위해 정성껏 준비하겠습니다.</p>
      </header>

      <section className="commerce-panel">
        <dl className="commerce-definition-list commerce-complete__summary">
          <div>
            <dt>주문번호</dt>
            <dd>
              <code>{handoff.order.orderNumber}</code>
              <button type="button" onClick={() => void copy(handoff.order.orderNumber, "order")}>
                복사
              </button>
            </dd>
          </div>
          <div><dt>결제금액</dt><dd><strong>{formatWon(handoff.order.totalAmount)}</strong></dd></div>
          <div><dt>주문일시</dt><dd>{formatDateTime(handoff.order.createdAt)}</dd></div>
        </dl>
        {copied === "order" ? <p className="commerce-copy-status" role="status">주문번호를 복사했습니다.</p> : null}
      </section>

      {guest ? (
        handoff.guestLookupToken ? (
          <section className="commerce-panel commerce-token-panel" aria-labelledby="guest-token-title">
            <p className="commerce-eyebrow">GUEST LOOKUP TOKEN</p>
            <h2 id="guest-token-title">비회원 주문 조회 토큰</h2>
            <p>
              이 토큰은 지금 주문 완료 화면에서만 안내됩니다. 주문번호와 함께 안전한 곳에 보관해 주세요.
            </p>
            <div className="commerce-token-value">
              <code>{handoff.guestLookupToken}</code>
              <button type="button" onClick={() => void copy(handoff.guestLookupToken!, "token")}>
                토큰 복사
              </button>
            </div>
            {copied === "token" ? <p className="commerce-copy-status" role="status">조회 토큰을 복사했습니다.</p> : null}
            <p className="commerce-help-text">조회 토큰은 URL이나 주소창에 포함되지 않았습니다.</p>
          </section>
        ) : (
          <p className="commerce-alert" role="alert">
            재요청으로 복구된 주문이라 비회원 조회 토큰을 다시 표시할 수 없습니다.
          </p>
        )
      ) : null}

      <div className="commerce-actions commerce-actions--center">
        <Link
          className="commerce-button commerce-button--primary"
          href={guest ? `/guest-orders/${encodeURIComponent(orderNumber)}` : `/mypage/orders/${encodeURIComponent(orderNumber)}`}
        >
          주문 상세 보기
        </Link>
        <Link className="commerce-button commerce-button--secondary" href="/">
          홈으로 이동
        </Link>
      </div>
    </div>
  );
}
