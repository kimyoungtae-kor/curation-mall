"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { getAuthSnapshot, logout as logoutRequest } from "./api";
import { getAuthOwnerKey } from "./owner-scope";
import type { AuthResult, AuthSnapshot } from "./types";

type AuthState = AuthSnapshot & {
  loading: boolean;
  error: string | null;
};

type AuthContextValue = AuthState & {
  ownerKey: string | null;
  refresh: () => Promise<void>;
  acceptAuthResult: (result: AuthResult) => void;
  setCounts: (
    counts: { cartCount?: number; wishlistCount?: number },
    expectedOwnerKey?: string,
  ) => void;
  logout: () => Promise<void>;
};

const initialState: AuthState = {
  authenticated: false,
  user: null,
  cartCount: 0,
  wishlistCount: 0,
  loading: true,
  error: null,
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>(initialState);
  const authRequestRevision = useRef(0);

  const refresh = useCallback(async () => {
    const revision = ++authRequestRevision.current;
    try {
      const snapshot = await getAuthSnapshot();
      if (authRequestRevision.current === revision) {
        setState({ ...snapshot, loading: false, error: null });
      }
    } catch {
      if (authRequestRevision.current === revision) {
        setState((current) => ({
          ...current,
          loading: false,
          error: "로그인 상태를 확인하지 못했습니다.",
        }));
      }
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    const revision = ++authRequestRevision.current;
    getAuthSnapshot(controller.signal)
      .then((snapshot) => {
        if (!controller.signal.aborted && authRequestRevision.current === revision) {
          setState({ ...snapshot, loading: false, error: null });
        }
      })
      .catch(() => {
        if (!controller.signal.aborted && authRequestRevision.current === revision) {
          setState((current) => ({
            ...current,
            loading: false,
            error: "로그인 상태를 확인하지 못했습니다.",
          }));
        }
      });
    return () => {
      controller.abort();
      if (authRequestRevision.current === revision) {
        authRequestRevision.current += 1;
      }
    };
  }, []);

  const acceptAuthResult = useCallback((result: AuthResult) => {
    authRequestRevision.current += 1;
    setState({
      authenticated: true,
      user: result.user,
      cartCount: result.mergeResult.cartItemCount,
      wishlistCount: result.mergeResult.wishlistCount,
      loading: false,
      error: null,
    });
  }, []);

  const setCounts = useCallback(
    (
      counts: { cartCount?: number; wishlistCount?: number },
      expectedOwnerKey?: string,
    ) => {
      setState((current) => {
        if (
          expectedOwnerKey !== undefined &&
          getAuthOwnerKey(current) !== expectedOwnerKey
        ) {
          return current;
        }
        const cartCount = counts.cartCount ?? current.cartCount;
        const wishlistCount = counts.wishlistCount ?? current.wishlistCount;
        if (cartCount === current.cartCount && wishlistCount === current.wishlistCount) {
          return current;
        }
        return { ...current, cartCount, wishlistCount };
      });
    },
    [],
  );

  const logout = useCallback(async () => {
    const revision = ++authRequestRevision.current;
    setState({ ...initialState, loading: true });
    try {
      await logoutRequest();
      if (authRequestRevision.current === revision) {
        setState({ ...initialState, loading: false });
      }
    } catch (error) {
      if (authRequestRevision.current === revision) await refresh();
      throw error;
    }
  }, [refresh]);

  const ownerKey = getAuthOwnerKey(state);

  const value = useMemo<AuthContextValue>(
    () => ({
      ...state,
      ownerKey,
      refresh,
      acceptAuthResult,
      setCounts,
      logout,
    }),
    [acceptAuthResult, logout, ownerKey, refresh, setCounts, state],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth must be used inside AuthProvider");
  return value;
}
