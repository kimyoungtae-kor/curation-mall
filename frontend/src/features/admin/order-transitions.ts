import type { OrderStatus } from "@/features/commerce/types";

const NEXT_STATUS: Partial<Record<OrderStatus, OrderStatus>> = {
  PENDING_PAYMENT: "CANCELLED",
  PAID: "PREPARING",
  PREPARING: "SHIPPED",
  SHIPPED: "DELIVERED",
};

export function allowedNextOrderStatus(status: OrderStatus) {
  return NEXT_STATUS[status] ?? null;
}
