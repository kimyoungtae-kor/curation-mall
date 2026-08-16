import { ApiError } from "@/lib/api/client";

const CODE_MESSAGES: Record<string, string> = {
  AUTHENTICATION_REQUIRED: "로그인이 만료되었습니다. 다시 로그인해 주세요.",
  CSRF_INVALID: "보안 확인이 만료되었습니다. 다시 시도해 주세요.",
  STOCK_CONFLICT: "주문하려는 상품의 재고가 부족합니다. 장바구니를 확인해 주세요.",
  PRODUCT_UNAVAILABLE: "현재 구매할 수 없는 상품이 포함되어 있습니다.",
  IDEMPOTENCY_CONFLICT: "이전 요청과 내용이 달라 처리할 수 없습니다. 화면을 새로 열어 주세요.",
  PAYMENT_AMOUNT_MISMATCH: "결제 금액이 서버 주문 금액과 일치하지 않습니다.",
  GUEST_ORDER_VERIFICATION_FAILED: "주문번호 또는 조회 토큰을 확인해 주세요.",
  RATE_LIMITED: "조회 시도가 많습니다. 잠시 후 다시 시도해 주세요.",
};

export function commerceErrorMessage(
  error: unknown,
  fallback = "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.",
) {
  if (error instanceof ApiError) {
    return (error.code && CODE_MESSAGES[error.code]) || error.message || fallback;
  }
  return fallback;
}
