"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { buildCheckoutHref } from "./checkout-query";
import { getAuthState } from "./api";
import { commerceErrorMessage } from "./error-message";
import type { AuthState } from "./types";

export function CheckoutEntry({ cartItemIds }: { cartItemIds: string[] }) {
  const [auth, setAuth] = useState<AuthState | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    getAuthState(controller.signal)
      .then(setAuth)
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) {
          setError(commerceErrorMessage(reason, "로그인 상태를 확인하지 못했습니다."));
        }
      });
    return () => controller.abort();
  }, []);

  if (cartItemIds.length === 0) {
    return (
      <div className="commerce-state" role="status">
        <p className="commerce-eyebrow">CHECKOUT</p>
        <h1>주문할 상품이 없습니다</h1>
        <p>장바구니에서 구매할 상품을 확인한 뒤 다시 시작해 주세요.</p>
        <Link className="commerce-button commerce-button--primary" href="/cart">
          장바구니로 이동
        </Link>
      </div>
    );
  }

  const guestHref = buildCheckoutHref(cartItemIds, "GUEST");
  const memberHref = buildCheckoutHref(cartItemIds, "MEMBER");
  const loginHref = `/login?next=${encodeURIComponent(memberHref)}`;

  return (
    <div className="commerce-narrow">
      <header className="commerce-page-heading">
        <p className="commerce-eyebrow">CHECKOUT</p>
        <h1>어떻게 주문하시겠어요?</h1>
        <p>장바구니 {cartItemIds.length}개 항목을 주문합니다.</p>
      </header>

      {error ? <p className="commerce-alert" role="alert">{error}</p> : null}

      <div className="commerce-choice-grid" aria-busy={!auth && !error}>
        {auth?.authenticated ? (
          <section className="commerce-choice-card commerce-choice-card--recommended">
            <span className="commerce-badge">추천</span>
            <h2>회원 주문</h2>
            <p>
              {auth.user?.name}님 계정으로 주문하고 마이페이지에서 배송 상태를
              확인할 수 있습니다.
            </p>
            <Link className="commerce-button commerce-button--primary" href={memberHref}>
              회원으로 계속
            </Link>
          </section>
        ) : (
          <section className="commerce-choice-card">
            <h2>로그인 후 주문</h2>
            <p>주문 내역과 찜 목록을 한곳에서 관리할 수 있습니다.</p>
            <Link className="commerce-button commerce-button--secondary" href={loginHref}>
              로그인하기
            </Link>
          </section>
        )}

        <section className="commerce-choice-card">
          <h2>비회원 주문</h2>
          <p>
            가입 없이 주문합니다. 완료 후 제공되는 조회 토큰을 안전하게 보관해야
            합니다.
          </p>
          <Link className="commerce-button commerce-button--secondary" href={guestHref}>
            비회원으로 계속
          </Link>
        </section>
      </div>
      {!auth && !error ? (
        <p className="commerce-loading" role="status">로그인 상태를 확인하고 있습니다.</p>
      ) : null}
    </div>
  );
}
