import { describe, expect, it } from "vitest";
import { normalizeContentLink } from "./content-link";

describe("normalizeContentLink", () => {
  it("maps supported API link types to public routes", () => {
    expect(
      normalizeContentLink({ type: "COLLECTION", value: "summer-hydration" }).href,
    ).toBe("/collections/summer-hydration");
    expect(normalizeContentLink({ type: "PRODUCT", value: "cool-bowl" }).href).toBe(
      "/products/cool-bowl",
    );
    expect(normalizeContentLink({ type: "HELP", value: "shipping-returns" }).href).toBe(
      "/help/shipping-returns",
    );
  });

  it("uses a safe route for future CONTENT links", () => {
    expect(normalizeContentLink({ type: "CONTENT", value: "room-story" }).href).toBe(
      "/collections",
    );
  });

  it("blocks invalid and unsafe link values", () => {
    expect(normalizeContentLink({ type: "EXTERNAL", value: "javascript:alert(1)" })).toEqual(
      expect.objectContaining({ href: "/products", external: false }),
    );
    expect(normalizeContentLink({ type: "COLLECTION", value: "../../admin" }).href).toBe(
      "/products",
    );
  });
});
