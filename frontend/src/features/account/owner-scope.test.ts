import { describe, expect, it } from "vitest";
import {
  createOwnerScopedResource,
  getAuthOwnerKey,
  ownerScopedResourceReducer,
  selectOwnerScopedResource,
  VISITOR_OWNER_KEY,
} from "./owner-scope";

describe("account owner scope", () => {
  it("distinguishes unresolved, visitor, and individual member owners", () => {
    expect(
      getAuthOwnerKey({ authenticated: false, user: null, loading: true }),
    ).toBeNull();
    expect(
      getAuthOwnerKey({ authenticated: false, user: null, loading: false }),
    ).toBe(VISITOR_OWNER_KEY);

    const member = (id: string) => ({
      authenticated: true,
      loading: false,
      user: {
        id,
        email: `${id}@example.test`,
        name: id,
        phone: "01012345678",
        roles: ["CUSTOMER"],
      },
    });

    expect(getAuthOwnerKey(member("member-a"))).toBe("user:member-a");
    expect(getAuthOwnerKey(member("member-b"))).toBe("user:member-b");
  });

  it("hides the previous owner's value before the next request starts", () => {
    let state = createOwnerScopedResource<string[]>();
    state = ownerScopedResourceReducer(state, {
      type: "start",
      ownerKey: "user:member-a",
    });
    state = ownerScopedResourceReducer(state, {
      type: "success",
      ownerKey: "user:member-a",
      value: ["member-a-item"],
    });

    const visibleToNextMember = selectOwnerScopedResource(
      state,
      "user:member-b",
    );
    expect(visibleToNextMember.value).toBeNull();
    expect(visibleToNextMember.loading).toBe(true);
    expect(visibleToNextMember.error).toBeNull();
  });

  it("ignores a previous owner's response after the next owner starts loading", () => {
    let state = createOwnerScopedResource<string[]>();
    state = ownerScopedResourceReducer(state, {
      type: "start",
      ownerKey: "user:member-a",
    });
    state = ownerScopedResourceReducer(state, {
      type: "start",
      ownerKey: "user:member-b",
    });
    state = ownerScopedResourceReducer(state, {
      type: "success",
      ownerKey: "user:member-a",
      value: ["stale-member-a-item"],
    });

    expect(state).toEqual({
      ownerKey: "user:member-b",
      value: null,
      loading: true,
      error: null,
    });
  });

  it("keeps a current owner's value when a mutation fails", () => {
    let state = createOwnerScopedResource<string[]>();
    state = ownerScopedResourceReducer(state, {
      type: "start",
      ownerKey: "visitor",
    });
    state = ownerScopedResourceReducer(state, {
      type: "success",
      ownerKey: "visitor",
      value: ["guest-cart-item"],
    });
    state = ownerScopedResourceReducer(state, {
      type: "failure",
      ownerKey: "visitor",
      error: "변경 실패",
    });

    expect(state.value).toEqual(["guest-cart-item"]);
    expect(state.error).toBe("변경 실패");
  });
});
