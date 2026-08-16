import { describe, expect, it } from "vitest";
import { allowedNextOrderStatus } from "./order-transitions";

describe("allowedNextOrderStatus", () => {
  it("서버가 허용하는 순방향 상태만 제안한다", () => {
    expect(allowedNextOrderStatus("PAID")).toBe("PREPARING");
    expect(allowedNextOrderStatus("PREPARING")).toBe("SHIPPED");
    expect(allowedNextOrderStatus("SHIPPED")).toBe("DELIVERED");
  });

  it("결제 대기 주문은 취소만 제안하고 종결 상태에는 작업을 제안하지 않는다", () => {
    expect(allowedNextOrderStatus("PENDING_PAYMENT")).toBe("CANCELLED");
    expect(allowedNextOrderStatus("DELIVERED")).toBeNull();
    expect(allowedNextOrderStatus("CANCELLED")).toBeNull();
  });
});
