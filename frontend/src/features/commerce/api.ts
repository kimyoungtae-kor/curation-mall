import { apiFetch, apiMutation } from "@/lib/api/client";
import type {
  AuthState,
  AuthStateResponse,
  CreateOrderRequest,
  CreateOrderResponse,
  CreateOrderResult,
  GuestOrderLookupRequest,
  OrderDetail,
  OrderDetailResponse,
  OrderListResponse,
  OrderQuote,
  OrderQuoteRequest,
  OrderQuoteResponse,
  PaymentConfirmation,
  PaymentConfirmationResponse,
  SimulatedPaymentRequest,
} from "./types";

export async function getAuthState(signal?: AbortSignal): Promise<AuthState> {
  const response = await apiFetch<AuthStateResponse>("/auth/me", { signal });
  return response.data;
}

export async function quoteOrder(
  request: OrderQuoteRequest,
  signal?: AbortSignal,
): Promise<OrderQuote> {
  const response = await apiMutation<OrderQuoteResponse>("/orders/quote", {
    method: "POST",
    body: request,
    signal,
  });
  return response.data;
}

export async function createOrder(
  request: CreateOrderRequest,
  idempotencyKey: string,
  signal?: AbortSignal,
): Promise<CreateOrderResult> {
  const response = await apiMutation<CreateOrderResponse>("/orders", {
    method: "POST",
    body: request,
    headers: { "Idempotency-Key": idempotencyKey },
    signal,
  });
  return response.data;
}

export async function confirmSimulatedPayment(
  request: SimulatedPaymentRequest,
  idempotencyKey: string,
  signal?: AbortSignal,
): Promise<PaymentConfirmation> {
  const response = await apiMutation<PaymentConfirmationResponse>(
    "/payments/confirm",
    {
      method: "POST",
      body: request,
      headers: { "Idempotency-Key": idempotencyKey },
      signal,
    },
  );
  return response.data;
}

export async function getMemberOrders(
  page = 0,
  size = 20,
  signal?: AbortSignal,
) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
    sort: "createdAt,desc",
  });
  return apiFetch<OrderListResponse>(`/orders?${params.toString()}`, { signal });
}

export async function getMemberOrder(
  orderNumber: string,
  signal?: AbortSignal,
): Promise<OrderDetail> {
  const response = await apiFetch<OrderDetailResponse>(
    `/orders/${encodeURIComponent(orderNumber)}`,
    { signal },
  );
  return response.data;
}

export async function lookupGuestOrder(
  request: GuestOrderLookupRequest,
  signal?: AbortSignal,
): Promise<OrderDetail> {
  const response = await apiMutation<OrderDetailResponse>(
    "/guest-orders/lookup",
    {
      method: "POST",
      body: request,
      signal,
    },
  );
  return response.data;
}
