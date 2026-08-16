import { describe, expect, it } from "vitest";
import { IdempotencyKeyManager, requestFingerprint } from "./idempotency";

describe("IdempotencyKeyManager", () => {
  it("reuses a key when the same network request is retried", () => {
    let sequence = 0;
    const manager = new IdempotencyKeyManager(() => `uuid-${++sequence}`);

    expect(manager.keyFor("same-payload")).toBe("uuid-1");
    expect(manager.keyFor("same-payload")).toBe("uuid-1");
    expect(sequence).toBe(1);
  });

  it("creates a new key after request contents change", () => {
    let sequence = 0;
    const manager = new IdempotencyKeyManager(() => `uuid-${++sequence}`);

    expect(manager.keyFor("first")).toBe("uuid-1");
    expect(manager.keyFor("second")).toBe("uuid-2");
  });

  it("uses a stable fingerprint for an unchanged payload", () => {
    const payload = { orderType: "GUEST", cartItemIds: ["one"] };
    expect(requestFingerprint(payload)).toBe(requestFingerprint(payload));
  });
});
