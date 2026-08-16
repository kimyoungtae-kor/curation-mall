import type { OrderStatus, PaymentStatus } from "./types";

const wonFormatter = new Intl.NumberFormat("ko-KR", {
  style: "currency",
  currency: "KRW",
  maximumFractionDigits: 0,
});

const dateFormatter = new Intl.DateTimeFormat("ko-KR", {
  dateStyle: "medium",
  timeStyle: "short",
});

const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  PENDING_PAYMENT: "결제 대기",
  PAID: "결제 완료",
  PREPARING: "상품 준비",
  SHIPPED: "배송 중",
  DELIVERED: "배송 완료",
  CANCEL_REQUESTED: "취소 요청",
  CANCELLED: "취소 완료",
};

const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  READY: "결제 준비",
  PROCESSING: "결제 처리 중",
  APPROVED: "승인 완료",
  FAILED: "승인 실패",
  UNKNOWN: "결과 확인 중",
  CANCELLED: "결제 취소",
  PARTIAL_CANCELLED: "부분 취소",
};

export function formatWon(amount: number) {
  return wonFormatter.format(amount);
}

export function formatDateTime(value: string | null | undefined) {
  if (!value) return "-";
  const date = new Date(value);
  return Number.isNaN(date.valueOf()) ? value : dateFormatter.format(date);
}

export function orderStatusLabel(status: OrderStatus) {
  return ORDER_STATUS_LABELS[status] ?? status;
}

export function paymentStatusLabel(status: PaymentStatus) {
  return PAYMENT_STATUS_LABELS[status] ?? status;
}

export function digitsOnly(value: string, maxLength: number) {
  return value.replace(/\D/g, "").slice(0, maxLength);
}
