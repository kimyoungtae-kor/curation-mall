"use client";

import Link from "next/link";
import { useCallback, useEffect, useReducer, useState } from "react";
import { SafeMedia } from "@/components/safe-media";
import { ApiError } from "@/lib/api/client";
import { getCart, removeCartItem, updateCartItem } from "./api";
import { useAuth } from "./auth-provider";
import {
  createOwnerScopedResource,
  ownerScopedResourceReducer,
  selectOwnerScopedResource,
} from "./owner-scope";
import type { Cart } from "./types";

const won = new Intl.NumberFormat("ko-KR", { style: "currency", currency: "KRW", maximumFractionDigits: 0 });

export function CartPage() {
  const { ownerKey, setCounts } = useAuth();
  const [resource, dispatch] = useReducer(
    ownerScopedResourceReducer<Cart>,
    undefined,
    createOwnerScopedResource<Cart>,
  );
  const [pending, setPending] = useState<{
    ownerKey: string;
    itemId: string;
  } | null>(null);
  const current = selectOwnerScopedResource(resource, ownerKey);
  const cart = current.value;
  const pendingId = pending?.ownerKey === ownerKey ? pending.itemId : null;

  const loadForOwner = useCallback(async (expectedOwnerKey: string, signal?: AbortSignal) => {
    dispatch({ type: "start", ownerKey: expectedOwnerKey });
    try {
      const next = await getCart(signal);
      dispatch({
        type: "success",
        ownerKey: expectedOwnerKey,
        value: next,
      });
      setCounts({ cartCount: next.itemCount }, expectedOwnerKey);
    } catch (caught) {
      if (signal?.aborted) return;
      dispatch({
        type: "failure",
        ownerKey: expectedOwnerKey,
        error: caught instanceof ApiError ? caught.message : "장바구니를 불러오지 못했습니다.",
      });
    }
  }, [setCounts]);

  useEffect(() => {
    if (!ownerKey) return;
    const controller = new AbortController();
    void loadForOwner(ownerKey, controller.signal);
    return () => controller.abort();
  }, [loadForOwner, ownerKey]);

  async function mutate(itemId: string, action: () => Promise<Cart>) {
    if (!ownerKey) return;
    const expectedOwnerKey = ownerKey;
    setPending({ ownerKey: expectedOwnerKey, itemId });
    dispatch({ type: "clear-error", ownerKey: expectedOwnerKey });
    try {
      const next = await action();
      dispatch({
        type: "success",
        ownerKey: expectedOwnerKey,
        value: next,
      });
      setCounts({ cartCount: next.itemCount }, expectedOwnerKey);
    } catch (caught) {
      dispatch({
        type: "failure",
        ownerKey: expectedOwnerKey,
        error: caught instanceof ApiError ? caught.message : "장바구니를 변경하지 못했습니다.",
      });
    } finally {
      setPending((active) =>
        active?.ownerKey === expectedOwnerKey && active.itemId === itemId
          ? null
          : active,
      );
    }
  }

  if (current.loading) return <div className="commerce-state" aria-busy="true">장바구니를 불러오는 중…</div>;
  if (!cart && current.error) return <div className="commerce-state"><p role="alert">{current.error}</p><button className="button button--secondary" onClick={() => ownerKey && void loadForOwner(ownerKey)}>다시 시도</button></div>;
  if (!cart || cart.items.length === 0) return <div className="commerce-state"><h2>장바구니가 비어 있어요</h2><p>마음에 드는 상품을 담아 보세요.</p><Link className="button button--primary" href="/products">상품 보러 가기</Link></div>;

  const availableIds = cart.items.filter((item) => item.availability === "AVAILABLE" || item.availability === "PRICE_CHANGED").map((item) => item.id);
  const checkoutHref = `/checkout?items=${encodeURIComponent(availableIds.join(","))}`;

  return (
    <div className="cart-layout">
      <section className="cart-list" aria-label="장바구니 상품">
        {current.error ? <p className="form-error" role="alert">{current.error}</p> : null}
        {cart.items.map((item) => {
          const pending = pendingId === item.id;
          return (
            <article className="cart-item" key={item.id} aria-busy={pending}>
              <Link href={`/products/${item.product.slug}`} className="cart-item__media">
                <SafeMedia src={item.product.thumbnailUrl} alt={item.product.name} fallbackLabel={item.product.name} />
              </Link>
              <div className="cart-item__content">
                <p>{item.product.brandName}</p>
                <Link href={`/products/${item.product.slug}`}><h2>{item.product.name}</h2></Link>
                <span>{item.optionLabel}</span>
                {item.priceChanged ? <strong className="cart-item__warning">가격이 변경되었습니다.</strong> : null}
                {item.availability === "OUT_OF_STOCK" ? <strong className="cart-item__warning">품절된 옵션입니다.</strong> : null}
                {item.availability === "UNAVAILABLE" ? <strong className="cart-item__warning">현재 구매할 수 없습니다.</strong> : null}
              </div>
              <div className="cart-item__controls">
                <div className="quantity-stepper" aria-label={`${item.product.name} 수량`}>
                  <button type="button" disabled={pending || item.quantity <= 1} onClick={() => void mutate(item.id, () => updateCartItem(item.id, item.quantity - 1))}>−</button>
                  <output>{item.quantity}</output>
                  <button type="button" disabled={pending || item.quantity >= item.maxPurchaseQuantity} onClick={() => void mutate(item.id, () => updateCartItem(item.id, item.quantity + 1))}>+</button>
                </div>
                <strong>{won.format(item.lineAmount)}</strong>
                <button className="text-button" type="button" disabled={pending} onClick={() => void mutate(item.id, () => removeCartItem(item.id))}>삭제</button>
              </div>
            </article>
          );
        })}
      </section>
      <aside className="cart-summary" aria-label="결제 예정 금액">
        <h2>주문 예상 금액</h2>
        <dl><div><dt>상품 금액</dt><dd>{won.format(cart.itemsAmount)}</dd></div><div><dt>배송비</dt><dd>{won.format(cart.shippingAmountEstimate)}</dd></div><div><dt>총 금액</dt><dd>{won.format(cart.totalAmountEstimate)}</dd></div></dl>
        {availableIds.length > 0 ? <Link className="button button--primary" href={checkoutHref}>주문하기</Link> : <button className="button button--primary" disabled>주문할 수 있는 상품이 없습니다</button>}
        <small>최종 가격과 재고는 주문 단계에서 다시 확인합니다.</small>
      </aside>
    </div>
  );
}
