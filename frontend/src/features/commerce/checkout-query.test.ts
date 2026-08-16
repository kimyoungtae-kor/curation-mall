import { describe, expect, it } from "vitest";
import {
  buildCheckoutHref,
  isOrderNumber,
  parseCartItemIds,
  parseOrderType,
} from "./checkout-query";

const FIRST_ID = "05a15437-dd1f-46cd-8263-966257335d4d";
const SECOND_ID = "b34067dd-a808-4a2b-bef2-8f97e96026ae";

describe("checkout query", () => {
  it("keeps only unique UUID cart item identifiers", () => {
    expect(
      parseCartItemIds(`${FIRST_ID},not-an-id,${SECOND_ID},${FIRST_ID}`),
    ).toEqual([FIRST_ID, SECOND_ID]);
  });

  it("accepts only explicit member and guest order types", () => {
    expect(parseOrderType("MEMBER")).toBe("MEMBER");
    expect(parseOrderType("GUEST")).toBe("GUEST");
    expect(parseOrderType("ADMIN")).toBeNull();
  });

  it("builds a checkout URL without any guest lookup secret", () => {
    const href = buildCheckoutHref([FIRST_ID], "GUEST");
    expect(href).toContain("items=");
    expect(href).toContain("type=GUEST");
    expect(href).not.toContain("token");
  });

  it("validates public order number paths", () => {
    expect(isOrderNumber("P20260812-7K9M4Q2X")).toBe(true);
    expect(isOrderNumber("../../secret")).toBe(false);
  });
});
