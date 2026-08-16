"use client";

import Link from "next/link";
import { useCallback, useEffect, useReducer, useState } from "react";
import { SafeMedia } from "@/components/safe-media";
import { ApiError } from "@/lib/api/client";
import { getWishlist, removeWishlistItem } from "./api";
import { useAuth } from "./auth-provider";
import {
  createOwnerScopedResource,
  ownerScopedResourceReducer,
  selectOwnerScopedResource,
} from "./owner-scope";
import type { WishlistItem } from "./types";

const won = new Intl.NumberFormat("ko-KR", {
  style: "currency",
  currency: "KRW",
  maximumFractionDigits: 0,
});

export function WishlistPage() {
  const {
    authenticated,
    loading: authLoading,
    ownerKey,
    setCounts,
    user,
    wishlistCount,
  } = useAuth();
  const isCustomer = authenticated && user?.roles.includes("CUSTOMER") === true;
  const [resource, dispatch] = useReducer(
    ownerScopedResourceReducer<WishlistItem[]>,
    undefined,
    createOwnerScopedResource<WishlistItem[]>,
  );
  const [pending, setPending] = useState<{
    ownerKey: string;
    productId: string;
  } | null>(null);
  const current = selectOwnerScopedResource(resource, ownerKey);
  const items = current.value ?? [];
  const pendingId = pending?.ownerKey === ownerKey ? pending.productId : null;

  const loadForOwner = useCallback(async (expectedOwnerKey: string, signal?: AbortSignal) => {
    dispatch({ type: "start", ownerKey: expectedOwnerKey });
    try {
      const page = await getWishlist(signal);
      dispatch({
        type: "success",
        ownerKey: expectedOwnerKey,
        value: page.data,
      });
      setCounts({ wishlistCount: page.page.totalElements }, expectedOwnerKey);
    } catch (caught) {
      if (signal?.aborted) return;
      dispatch({
        type: "failure",
        ownerKey: expectedOwnerKey,
        error:
          caught instanceof ApiError
            ? caught.message
            : "찜 목록을 불러오지 못했습니다.",
      });
    }
  }, [setCounts]);

  useEffect(() => {
    if (authLoading || !isCustomer || !ownerKey) return;
    const controller = new AbortController();
    void loadForOwner(ownerKey, controller.signal);
    return () => controller.abort();
  }, [authLoading, isCustomer, loadForOwner, ownerKey]);

  async function remove(productId: string) {
    if (!ownerKey) return;
    const expectedOwnerKey = ownerKey;
    setPending({ ownerKey: expectedOwnerKey, productId });
    dispatch({ type: "clear-error", ownerKey: expectedOwnerKey });
    try {
      await removeWishlistItem(productId);
      const remainingItems = items.filter((item) => item.productId !== productId);
      dispatch({
        type: "success",
        ownerKey: expectedOwnerKey,
        value: remainingItems,
      });
      setCounts(
        { wishlistCount: Math.max(0, wishlistCount - 1) },
        expectedOwnerKey,
      );
    } catch (caught) {
      dispatch({
        type: "failure",
        ownerKey: expectedOwnerKey,
        error: caught instanceof ApiError ? caught.message : "찜을 해제하지 못했습니다.",
      });
    } finally {
      setPending((active) =>
        active?.ownerKey === expectedOwnerKey && active.productId === productId
          ? null
          : active,
      );
    }
  }

  if (authLoading) {
    return <div className="commerce-state" aria-busy="true">로그인 상태를 확인하는 중…</div>;
  }
  if (!authenticated) {
    return (
      <div className="commerce-state">
        <h2>로그인이 필요해요</h2>
        <p>로그인하면 찜 목록을 여러 기기에서 이어볼 수 있습니다.</p>
        <Link className="button button--primary" href="/login?next=/wishlist">
          로그인
        </Link>
      </div>
    );
  }
  if (!isCustomer) {
    return (
      <div className="commerce-state">
        <h2>고객 회원 전용 기능이에요</h2>
        <p>찜 목록은 고객 계정으로 로그인했을 때만 이용할 수 있습니다.</p>
        <Link className="button button--primary" href="/products">
          상품 보러 가기
        </Link>
      </div>
    );
  }
  if (current.loading) {
    return <div className="commerce-state" aria-busy="true">찜 목록을 불러오는 중…</div>;
  }
  if (current.error && items.length === 0) {
    return (
      <div className="commerce-state">
        <p role="alert">{current.error}</p>
        <button className="button button--secondary" onClick={() => ownerKey && void loadForOwner(ownerKey)}>
          다시 시도
        </button>
      </div>
    );
  }
  if (items.length === 0) {
    return (
      <div className="commerce-state">
        <h2>찜한 상품이 없어요</h2>
        <p>관심 있는 상품을 저장해 보세요.</p>
        <Link className="button button--primary" href="/products">
          상품 보러 가기
        </Link>
      </div>
    );
  }

  return (
    <>
      {current.error ? <p className="form-error" role="alert">{current.error}</p> : null}
      <div className="wishlist-grid">
        {items.map((item) => (
          <article className="wishlist-card" key={item.productId}>
            <Link href={`/products/${item.slug}`}>
              <SafeMedia
                src={item.thumbnailUrl}
                alt={item.name}
                fallbackLabel={item.name}
              />
              <p>{item.brandName}</p>
              <h2>{item.name}</h2>
              <strong>
                {item.minimumPrice === null
                  ? "가격 준비 중"
                  : `${won.format(item.minimumPrice)}부터`}
              </strong>
            </Link>
            <button
              type="button"
              disabled={pendingId !== null}
              onClick={() => void remove(item.productId)}
              aria-label={`${item.name} 찜 해제`}
            >
              찜 해제
            </button>
          </article>
        ))}
      </div>
    </>
  );
}
