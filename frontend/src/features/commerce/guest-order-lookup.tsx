"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { lookupGuestOrder } from "./api";
import { isOrderNumber } from "./checkout-query";
import { commerceErrorMessage } from "./error-message";
import { saveGuestLookupHandoff } from "./storage";

export function GuestOrderLookup() {
  const router = useRouter();
  const [orderNumber, setOrderNumber] = useState("");
  const [guestLookupToken, setGuestLookupToken] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedOrderNumber = orderNumber.trim().toUpperCase();
    const normalizedToken = guestLookupToken.trim();
    if (!isOrderNumber(normalizedOrderNumber) || normalizedToken.length < 12) {
      setError("주문번호와 조회 토큰을 정확히 입력해 주세요.");
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      const detail = await lookupGuestOrder({
        orderNumber: normalizedOrderNumber,
        guestLookupToken: normalizedToken,
      });
      saveGuestLookupHandoff({
        version: 1,
        orderNumber: normalizedOrderNumber,
        guestLookupToken: normalizedToken,
        detail,
        savedAt: Date.now(),
      });
      router.push(`/guest-orders/${encodeURIComponent(normalizedOrderNumber)}`);
    } catch (reason) {
      setError(commerceErrorMessage(reason, "비회원 주문을 확인하지 못했습니다."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="commerce-auth-card">
      <header className="commerce-page-heading">
        <p className="commerce-eyebrow">GUEST ORDER</p>
        <h1>비회원 주문 조회</h1>
        <p>주문 완료 시 받은 주문번호와 조회 토큰을 입력해 주세요.</p>
      </header>

      <form className="commerce-panel commerce-lookup-form" onSubmit={submit} noValidate>
        <label>
          <span>주문번호</span>
          <input
            value={orderNumber}
            onChange={(event) => setOrderNumber(event.target.value.toUpperCase().slice(0, 18))}
            placeholder="P20260812-7K9M4Q2X"
            autoCapitalize="characters"
            autoComplete="off"
            spellCheck={false}
          />
        </label>
        <label>
          <span>조회 토큰</span>
          <input
            type="password"
            value={guestLookupToken}
            onChange={(event) => setGuestLookupToken(event.target.value.slice(0, 200))}
            autoComplete="off"
            spellCheck={false}
          />
        </label>
        {error ? <p className="commerce-alert" role="alert">{error}</p> : null}
        <button
          className="commerce-button commerce-button--primary commerce-button--full"
          type="submit"
          disabled={submitting}
        >
          {submitting ? "주문을 확인하고 있습니다…" : "주문 조회"}
        </button>
        <p className="commerce-help-text">
          조회 토큰은 서버 요청 본문에만 전송되며 주소창이나 링크에 포함되지 않습니다.
        </p>
      </form>
      <p className="commerce-auth-card__footer">
        회원으로 주문하셨나요? <Link href="/login?next=%2Fmypage%2Forders">로그인하기</Link>
      </p>
    </div>
  );
}
