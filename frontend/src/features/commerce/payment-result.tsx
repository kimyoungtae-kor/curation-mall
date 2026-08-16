"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { confirmSimulatedPayment } from "./api";
import { commerceErrorMessage } from "./error-message";
import { formatDateTime, formatWon, paymentStatusLabel } from "./format";
import { IdempotencyKeyManager, requestFingerprint } from "./idempotency";
import { updateCheckoutConfirmation } from "./storage";
import type {
  PaymentConfirmation,
  SimulatedPaymentRequest,
} from "./types";
import { useCheckoutHandoff } from "./use-checkout-handoff";

export function PaymentResult({ orderNumber }: { orderNumber: string }) {
  const router = useRouter();
  const idempotency = useRef(new IdempotencyKeyManager());
  const { handoff, loading: handoffLoading } =
    useCheckoutHandoff(orderNumber);
  const [confirmation, setConfirmation] = useState<PaymentConfirmation | null>(null);
  const [processing, setProcessing] = useState<"APPROVE" | "FAIL" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const currentConfirmation = confirmation ?? handoff?.confirmation ?? null;

  async function simulate(simulationResult: "APPROVE" | "FAIL") {
    if (!handoff || processing || currentConfirmation) return;
    if (handoff.order.orderType === "GUEST" && !handoff.guestLookupToken) {
      setError(
        "주문은 생성됐지만 비회원 조회 토큰을 복구하지 못해 결제를 계속할 수 없습니다.",
      );
      return;
    }

    const request: SimulatedPaymentRequest = {
      provider: "SIMULATED",
      orderNumber,
      simulationResult,
      amount: handoff.order.totalAmount,
      ...(handoff.guestLookupToken
        ? { guestLookupToken: handoff.guestLookupToken }
        : {}),
    };

    setProcessing(simulationResult);
    setError(null);
    try {
      const result = await confirmSimulatedPayment(
        request,
        idempotency.current.keyFor(requestFingerprint(request)),
      );
      updateCheckoutConfirmation(orderNumber, result);
      setConfirmation(result);
      if (result.payment.status === "APPROVED" && result.orderStatus === "PAID") {
        router.push(`/orders/complete/${encodeURIComponent(orderNumber)}`);
      }
    } catch (reason) {
      setError(
        commerceErrorMessage(
          reason,
          "결제 결과를 확인하지 못했습니다. 같은 버튼으로 다시 시도해 주세요.",
        ),
      );
    } finally {
      setProcessing(null);
    }
  }

  if (handoffLoading) {
    return <div className="commerce-state" role="status">결제 정보를 확인하고 있습니다.</div>;
  }

  if (!handoff) {
    return (
      <div className="commerce-state" role="alert">
        <p className="commerce-eyebrow">PAYMENT</p>
        <h1>결제할 주문 정보를 찾지 못했습니다</h1>
        <p>결제를 시작했던 같은 브라우저 탭에서 다시 열거나 장바구니에서 주문해 주세요.</p>
        <Link className="commerce-button commerce-button--primary" href="/cart">
          장바구니로 이동
        </Link>
      </div>
    );
  }

  const failed = currentConfirmation?.payment.status === "FAILED";
  const unknown = currentConfirmation?.payment.status === "UNKNOWN";

  return (
    <div className="commerce-narrow commerce-payment">
      <header className="commerce-page-heading">
        <p className="commerce-eyebrow">PAYMENT SIMULATOR</p>
        <h1>시연용 결제</h1>
        <p>실제 카드 승인이나 금액 청구는 발생하지 않습니다.</p>
      </header>

      <section className="commerce-panel commerce-payment-card">
        <div className="commerce-demo-notice" role="note">
          TEST PAYMENT · 실제 결제가 아닙니다
        </div>
        <dl className="commerce-definition-list">
          <div><dt>주문번호</dt><dd>{handoff.order.orderNumber}</dd></div>
          <div><dt>결제금액</dt><dd><strong>{formatWon(handoff.order.totalAmount)}</strong></dd></div>
          <div><dt>재고 예약 만료</dt><dd>{formatDateTime(handoff.order.reservationExpiresAt)}</dd></div>
          <div>
            <dt>결제 상태</dt>
            <dd>{currentConfirmation ? paymentStatusLabel(currentConfirmation.payment.status) : "결제 준비"}</dd>
          </div>
        </dl>

        {error ? <p className="commerce-alert" role="alert">{error}</p> : null}
        {failed ? (
          <div className="commerce-result commerce-result--danger" role="status">
            <h2>결제 실패 시나리오가 처리됐습니다</h2>
            <p>성공 주문으로 표시되지 않습니다. 장바구니에서 새 주문을 시작해 주세요.</p>
            <Link className="commerce-button commerce-button--secondary" href="/cart">
              장바구니로 돌아가기
            </Link>
          </div>
        ) : unknown ? (
          <div className="commerce-result" role="status">
            <h2>결제 결과를 확인하고 있습니다</h2>
            <p>중복 승인을 막기 위해 결과가 확정될 때까지 다시 결제하지 않습니다.</p>
          </div>
        ) : !currentConfirmation ? (
          <div className="commerce-payment-actions">
            <button
              className="commerce-button commerce-button--primary"
              type="button"
              disabled={Boolean(processing)}
              onClick={() => void simulate("APPROVE")}
            >
              {processing === "APPROVE" ? "승인 처리 중…" : "결제 성공 시연"}
            </button>
            <button
              className="commerce-button commerce-button--danger"
              type="button"
              disabled={Boolean(processing)}
              onClick={() => void simulate("FAIL")}
            >
              {processing === "FAIL" ? "실패 처리 중…" : "결제 실패 시연"}
            </button>
          </div>
        ) : null}
        <p className="commerce-help-text">
          같은 요청을 다시 보내도 저장된 멱등성 키를 재사용해 중복 승인과 중복 재고 반영을 막습니다.
        </p>
      </section>
    </div>
  );
}
