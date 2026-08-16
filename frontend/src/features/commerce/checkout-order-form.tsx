"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useRef, useState } from "react";
import { useAuth } from "@/features/account/auth-provider";
import { createOrder, getAuthState, quoteOrder } from "./api";
import { syncCartAfterOrderCreation } from "./cart-count-sync";
import { commerceErrorMessage } from "./error-message";
import { digitsOnly, formatWon } from "./format";
import { IdempotencyKeyManager, requestFingerprint } from "./idempotency";
import { saveCheckoutHandoff } from "./storage";
import type {
  AuthState,
  CreateOrderRequest,
  OrderQuote,
  OrderType,
} from "./types";

type CheckoutFormValues = {
  buyerName: string;
  buyerEmail: string;
  buyerPhone: string;
  recipientName: string;
  recipientPhone: string;
  postalCode: string;
  address1: string;
  address2: string;
  deliveryMessage: string;
  purchaseTermsAccepted: boolean;
  privacyCollectionAccepted: boolean;
};

type FieldErrors = Partial<Record<keyof CheckoutFormValues, string>>;

const EMPTY_FORM: CheckoutFormValues = {
  buyerName: "",
  buyerEmail: "",
  buyerPhone: "",
  recipientName: "",
  recipientPhone: "",
  postalCode: "",
  address1: "",
  address2: "",
  deliveryMessage: "",
  purchaseTermsAccepted: false,
  privacyCollectionAccepted: false,
};

function validate(values: CheckoutFormValues) {
  const errors: FieldErrors = {};
  if (values.buyerName.trim().length < 2) errors.buyerName = "구매자 이름을 입력해 주세요.";
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.buyerEmail.trim())) {
    errors.buyerEmail = "올바른 이메일 주소를 입력해 주세요.";
  }
  if (!/^\d{10,11}$/.test(values.buyerPhone)) {
    errors.buyerPhone = "연락처 숫자 10~11자리를 입력해 주세요.";
  }
  if (values.recipientName.trim().length < 2) {
    errors.recipientName = "받는 분 이름을 입력해 주세요.";
  }
  if (!/^\d{10,11}$/.test(values.recipientPhone)) {
    errors.recipientPhone = "받는 분 연락처 숫자 10~11자리를 입력해 주세요.";
  }
  if (!/^\d{5}$/.test(values.postalCode)) {
    errors.postalCode = "우편번호 숫자 5자리를 입력해 주세요.";
  }
  if (values.address1.trim().length < 5) errors.address1 = "기본 주소를 입력해 주세요.";
  if (!values.purchaseTermsAccepted) {
    errors.purchaseTermsAccepted = "구매 조건에 동의해야 주문할 수 있습니다.";
  }
  if (!values.privacyCollectionAccepted) {
    errors.privacyCollectionAccepted = "개인정보 수집에 동의해야 주문할 수 있습니다.";
  }
  return errors;
}

function fieldErrorId(field: keyof CheckoutFormValues) {
  return `checkout-${field}-error`;
}

export function CheckoutOrderForm({
  cartItemIds,
  orderType,
}: {
  cartItemIds: string[];
  orderType: OrderType;
}) {
  const router = useRouter();
  const { cartCount, refresh: refreshAuth, setCounts } = useAuth();
  const idempotency = useRef(new IdempotencyKeyManager());
  const [auth, setAuth] = useState<AuthState | null>(null);
  const [quote, setQuote] = useState<OrderQuote | null>(null);
  const [values, setValues] = useState(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    async function prepare() {
      setLoading(true);
      setLoadError(null);
      try {
        const nextAuth = await getAuthState(controller.signal);
        if (orderType === "MEMBER" && !nextAuth.authenticated) {
          throw new Error("MEMBER_AUTH_REQUIRED");
        }
        setAuth(nextAuth);
        if (nextAuth.user) {
          setValues((current) => ({
            ...current,
            buyerName: nextAuth.user?.name ?? "",
            buyerEmail: nextAuth.user?.email ?? "",
            buyerPhone: nextAuth.user?.phone ?? "",
            recipientName: nextAuth.user?.name ?? "",
            recipientPhone: nextAuth.user?.phone ?? "",
          }));
        }
        setQuote(
          await quoteOrder({ orderType, cartItemIds }, controller.signal),
        );
      } catch (reason) {
        if (!controller.signal.aborted) {
          setLoadError(
            reason instanceof Error && reason.message === "MEMBER_AUTH_REQUIRED"
              ? "회원 주문을 계속하려면 로그인해 주세요."
              : commerceErrorMessage(reason, "주문 금액을 확인하지 못했습니다."),
          );
        }
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }
    void prepare();
    return () => controller.abort();
  }, [cartItemIds, orderType]);

  function update<K extends keyof CheckoutFormValues>(
    field: K,
    value: CheckoutFormValues[K],
  ) {
    setValues((current) => ({ ...current, [field]: value }));
    setFieldErrors((current) => ({ ...current, [field]: undefined }));
    setSubmitError(null);
  }

  function renderError(field: keyof CheckoutFormValues) {
    const message = fieldErrors[field];
    return message ? (
      <span className="commerce-field-error" id={fieldErrorId(field)}>
        {message}
      </span>
    ) : null;
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!quote || submitting) return;
    const errors = validate(values);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      setSubmitError("입력하지 않았거나 올바르지 않은 항목을 확인해 주세요.");
      return;
    }

    const request: CreateOrderRequest = {
      orderType,
      cartItemIds,
      buyer: {
        name: values.buyerName.trim(),
        email: values.buyerEmail.trim().toLowerCase(),
        phone: values.buyerPhone,
      },
      shipping: {
        recipientName: values.recipientName.trim(),
        recipientPhone: values.recipientPhone,
        postalCode: values.postalCode,
        address1: values.address1.trim(),
        address2: values.address2.trim(),
        deliveryMessage: values.deliveryMessage.trim() || null,
      },
      agreements: {
        purchaseTermsAccepted: values.purchaseTermsAccepted,
        privacyCollectionAccepted: values.privacyCollectionAccepted,
      },
    };

    setSubmitting(true);
    setSubmitError(null);
    try {
      const result = await createOrder(
        request,
        idempotency.current.keyFor(requestFingerprint(request)),
      );
      saveCheckoutHandoff({
        version: 1,
        order: result.order,
        payment: result.payment,
        guestLookupToken: result.guestLookupToken,
        confirmation: null,
        savedAt: Date.now(),
      });
      await syncCartAfterOrderCreation({
        currentCartCount: cartCount,
        purchasedCartItemIds: cartItemIds,
        setCartCount: (nextCartCount) =>
          setCounts({ cartCount: nextCartCount }),
        refresh: refreshAuth,
      });
      router.push(
        `/payments/result?orderNumber=${encodeURIComponent(result.order.orderNumber)}`,
      );
    } catch (reason) {
      setSubmitError(
        commerceErrorMessage(
          reason,
          "주문 생성 결과를 확인하지 못했습니다. 같은 내용으로 다시 시도해 주세요.",
        ),
      );
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return <div className="commerce-state" role="status">주문 금액을 확인하고 있습니다.</div>;
  }

  if (loadError || !quote) {
    const loginNext = `/checkout/order?items=${encodeURIComponent(cartItemIds.join(","))}&type=${orderType}`;
    return (
      <div className="commerce-state" role="alert">
        <h1>주문서를 준비하지 못했습니다</h1>
        <p>{loadError}</p>
        <div className="commerce-actions">
          {orderType === "MEMBER" && !auth?.authenticated ? (
            <Link
              className="commerce-button commerce-button--primary"
              href={`/login?next=${encodeURIComponent(loginNext)}`}
            >
              로그인하기
            </Link>
          ) : null}
          <Link className="commerce-button commerce-button--secondary" href="/cart">
            장바구니 확인
          </Link>
        </div>
      </div>
    );
  }

  return (
    <form className="commerce-checkout" onSubmit={submit} noValidate>
      <header className="commerce-page-heading commerce-page-heading--left">
        <p className="commerce-eyebrow">CHECKOUT</p>
        <h1>{orderType === "MEMBER" ? "회원 주문서" : "비회원 주문서"}</h1>
        <p>최종 가격과 재고는 주문 생성 시 서버에서 한 번 더 확인합니다.</p>
      </header>

      <div className="commerce-checkout__layout">
        <div className="commerce-checkout__forms">
          <fieldset className="commerce-panel commerce-form-section">
            <legend>구매자 정보</legend>
            <div className="commerce-form-grid">
              <label>
                <span>이름</span>
                <input
                  value={values.buyerName}
                  onChange={(event) => update("buyerName", event.target.value.slice(0, 50))}
                  aria-invalid={Boolean(fieldErrors.buyerName)}
                  aria-describedby={fieldErrors.buyerName ? fieldErrorId("buyerName") : undefined}
                  autoComplete="name"
                />
                {renderError("buyerName")}
              </label>
              <label>
                <span>이메일</span>
                <input
                  type="email"
                  value={values.buyerEmail}
                  onChange={(event) => update("buyerEmail", event.target.value.slice(0, 120))}
                  aria-invalid={Boolean(fieldErrors.buyerEmail)}
                  aria-describedby={fieldErrors.buyerEmail ? fieldErrorId("buyerEmail") : undefined}
                  autoComplete="email"
                />
                {renderError("buyerEmail")}
              </label>
              <label>
                <span>연락처</span>
                <input
                  inputMode="numeric"
                  value={values.buyerPhone}
                  onChange={(event) => update("buyerPhone", digitsOnly(event.target.value, 11))}
                  placeholder="01012345678"
                  aria-invalid={Boolean(fieldErrors.buyerPhone)}
                  aria-describedby={fieldErrors.buyerPhone ? fieldErrorId("buyerPhone") : undefined}
                  autoComplete="tel"
                />
                {renderError("buyerPhone")}
              </label>
            </div>
          </fieldset>

          <fieldset className="commerce-panel commerce-form-section">
            <legend>배송 정보</legend>
            <div className="commerce-form-grid">
              <label>
                <span>받는 분</span>
                <input
                  value={values.recipientName}
                  onChange={(event) => update("recipientName", event.target.value.slice(0, 50))}
                  aria-invalid={Boolean(fieldErrors.recipientName)}
                  aria-describedby={fieldErrors.recipientName ? fieldErrorId("recipientName") : undefined}
                  autoComplete="shipping name"
                />
                {renderError("recipientName")}
              </label>
              <label>
                <span>연락처</span>
                <input
                  inputMode="numeric"
                  value={values.recipientPhone}
                  onChange={(event) => update("recipientPhone", digitsOnly(event.target.value, 11))}
                  aria-invalid={Boolean(fieldErrors.recipientPhone)}
                  aria-describedby={fieldErrors.recipientPhone ? fieldErrorId("recipientPhone") : undefined}
                  autoComplete="shipping tel"
                />
                {renderError("recipientPhone")}
              </label>
              <label className="commerce-form-grid__short">
                <span>우편번호</span>
                <input
                  inputMode="numeric"
                  value={values.postalCode}
                  onChange={(event) => update("postalCode", digitsOnly(event.target.value, 5))}
                  aria-invalid={Boolean(fieldErrors.postalCode)}
                  aria-describedby={fieldErrors.postalCode ? fieldErrorId("postalCode") : undefined}
                  autoComplete="shipping postal-code"
                />
                {renderError("postalCode")}
              </label>
              <label className="commerce-form-grid__wide">
                <span>기본 주소</span>
                <input
                  value={values.address1}
                  onChange={(event) => update("address1", event.target.value.slice(0, 160))}
                  aria-invalid={Boolean(fieldErrors.address1)}
                  aria-describedby={fieldErrors.address1 ? fieldErrorId("address1") : undefined}
                  autoComplete="shipping address-line1"
                />
                {renderError("address1")}
              </label>
              <label className="commerce-form-grid__wide">
                <span>상세 주소</span>
                <input
                  value={values.address2}
                  onChange={(event) => update("address2", event.target.value.slice(0, 100))}
                  autoComplete="shipping address-line2"
                />
              </label>
              <label className="commerce-form-grid__wide">
                <span>배송 요청사항 (선택)</span>
                <input
                  value={values.deliveryMessage}
                  onChange={(event) => update("deliveryMessage", event.target.value.slice(0, 100))}
                />
              </label>
            </div>
          </fieldset>

          <fieldset className="commerce-panel commerce-form-section commerce-agreements">
            <legend>주문 동의</legend>
            <label>
              <input
                type="checkbox"
                checked={values.purchaseTermsAccepted}
                onChange={(event) => update("purchaseTermsAccepted", event.target.checked)}
                aria-invalid={Boolean(fieldErrors.purchaseTermsAccepted)}
                aria-describedby={
                  fieldErrors.purchaseTermsAccepted
                    ? fieldErrorId("purchaseTermsAccepted")
                    : undefined
                }
              />
              <span>상품, 가격, 배송 조건을 확인했으며 구매에 동의합니다. (필수)</span>
            </label>
            {renderError("purchaseTermsAccepted")}
            <label>
              <input
                type="checkbox"
                checked={values.privacyCollectionAccepted}
                onChange={(event) => update("privacyCollectionAccepted", event.target.checked)}
                aria-invalid={Boolean(fieldErrors.privacyCollectionAccepted)}
                aria-describedby={
                  fieldErrors.privacyCollectionAccepted
                    ? fieldErrorId("privacyCollectionAccepted")
                    : undefined
                }
              />
              <span>주문 처리를 위한 개인정보 수집 및 이용에 동의합니다. (필수)</span>
            </label>
            {renderError("privacyCollectionAccepted")}
          </fieldset>
        </div>

        <aside className="commerce-panel commerce-order-summary" aria-labelledby="order-summary-title">
          <h2 id="order-summary-title">주문 요약</h2>
          <div className="commerce-quote-lines">
            {quote.lines.map((line) => (
              <div key={line.cartItemId}>
                <span>
                  <strong>{line.productName}</strong>
                  <small>{line.optionLabel} · {line.quantity}개</small>
                </span>
                <span>{formatWon(line.lineAmount)}</span>
              </div>
            ))}
          </div>
          {quote.warnings.length > 0 ? (
            <div className="commerce-alert" role="status">
              {quote.warnings.map((warning) => <p key={warning}>{warning}</p>)}
            </div>
          ) : null}
          <dl className="commerce-price-list">
            <div><dt>상품 금액</dt><dd>{formatWon(quote.itemsAmount)}</dd></div>
            <div><dt>할인</dt><dd>-{formatWon(quote.discountAmount)}</dd></div>
            <div><dt>배송비</dt><dd>{formatWon(quote.shippingAmount)}</dd></div>
            <div className="commerce-price-list__total">
              <dt>결제 예정 금액</dt><dd>{formatWon(quote.totalAmount)}</dd>
            </div>
          </dl>
          {submitError ? <p className="commerce-alert" role="alert">{submitError}</p> : null}
          <button
            className="commerce-button commerce-button--primary commerce-button--full"
            type="submit"
            disabled={submitting}
          >
            {submitting ? "주문을 만들고 있습니다…" : `${formatWon(quote.totalAmount)} 주문하기`}
          </button>
          <p className="commerce-help-text">
            다음 화면의 결제는 실제 청구가 없는 시연용 결제입니다.
          </p>
        </aside>
      </div>
    </form>
  );
}
