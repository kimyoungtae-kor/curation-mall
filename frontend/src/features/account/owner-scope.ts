import type { AuthSnapshot } from "./types";

type AuthOwnerState = Pick<AuthSnapshot, "authenticated" | "user"> & {
  loading: boolean;
};

export const VISITOR_OWNER_KEY = "visitor";

export function getAuthOwnerKey(state: AuthOwnerState): string | null {
  if (state.loading) return null;
  if (state.authenticated && state.user) return `user:${state.user.id}`;
  return VISITOR_OWNER_KEY;
}

export type OwnerScopedResource<T> = {
  ownerKey: string | null;
  value: T | null;
  loading: boolean;
  error: string | null;
};

export type OwnerScopedResourceAction<T> =
  | { type: "start"; ownerKey: string }
  | { type: "success"; ownerKey: string; value: T }
  | { type: "failure"; ownerKey: string; error: string }
  | { type: "clear-error"; ownerKey: string };

export function createOwnerScopedResource<T>(): OwnerScopedResource<T> {
  return {
    ownerKey: null,
    value: null,
    loading: true,
    error: null,
  };
}

export function ownerScopedResourceReducer<T>(
  state: OwnerScopedResource<T>,
  action: OwnerScopedResourceAction<T>,
): OwnerScopedResource<T> {
  if (action.type === "start") {
    return {
      ownerKey: action.ownerKey,
      value: null,
      loading: true,
      error: null,
    };
  }

  if (state.ownerKey !== action.ownerKey) return state;

  if (action.type === "success") {
    return {
      ownerKey: action.ownerKey,
      value: action.value,
      loading: false,
      error: null,
    };
  }

  if (action.type === "failure") {
    return {
      ...state,
      loading: false,
      error: action.error,
    };
  }

  return { ...state, error: null };
}

export function selectOwnerScopedResource<T>(
  state: OwnerScopedResource<T>,
  ownerKey: string | null,
): OwnerScopedResource<T> {
  if (ownerKey !== null && state.ownerKey === ownerKey) return state;

  return {
    ownerKey,
    value: null,
    loading: true,
    error: null,
  };
}
