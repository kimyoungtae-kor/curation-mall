import { describe, expect, it } from "vitest";
import { safeNextPath } from "./safe-next-path";

describe("safeNextPath", () => {
  it("keeps an internal path with its query and hash", () => {
    expect(safeNextPath("/mypage/orders?page=2#recent")).toBe(
      "/mypage/orders?page=2#recent",
    );
  });

  it.each([
    null,
    "https://attacker.example",
    "//attacker.example",
    "/\\attacker.example",
    "/%5Cattacker.example",
    "/%2F%2Fattacker.example",
    "/products\\..\\attacker.example",
  ])("falls back to home for an unsafe target: %s", (target) => {
    expect(safeNextPath(target)).toBe("/");
  });
});
