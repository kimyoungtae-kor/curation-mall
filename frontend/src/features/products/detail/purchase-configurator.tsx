"use client";

import { useEffect, useMemo, useReducer, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { addCartItem, addWishlistItem, getWishlist, removeWishlistItem } from "@/features/account/api";
import { useAuth } from "@/features/account/auth-provider";
import {
  ownerScopedResourceReducer,
  selectOwnerScopedResource,
} from "@/features/account/owner-scope";
import { ApiError } from "@/lib/api/client";
import type { ProductVariantDto } from "../types";

const wonFormatter = new Intl.NumberFormat("ko-KR", {
  style: "currency",
  currency: "KRW",
  maximumFractionDigits: 0,
});

export function PurchaseConfigurator({
  productId,
  variants,
  initiallyWishlisted,
}: {
  productId: string;
  variants: ProductVariantDto[];
  initiallyWishlisted: boolean;
}) {
  const pathname = usePathname();
  const router = useRouter();
  const {
    authenticated,
    loading: authLoading,
    ownerKey,
    setCounts,
    user,
    wishlistCount,
  } = useAuth();
  const isCustomer = authenticated && user?.roles.includes("CUSTOMER") === true;
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [quantity, setQuantity] = useState(1);
  const [wishlistResource, dispatchWishlist] = useReducer(
    ownerScopedResourceReducer<boolean>,
    {
      ownerKey: null,
      value: initiallyWishlisted,
      loading: true,
      error: null,
    },
  );
  const currentWishlist = selectOwnerScopedResource(wishlistResource, ownerKey);
  const wishlisted = currentWishlist.value ?? false;
  const [pending, setPending] = useState<{
    operation: "cart" | "buy" | "wishlist";
    ownerKey: string | null;
  } | null>(null);
  const pendingOperation = pending?.ownerKey === ownerKey ? pending.operation : null;
  const [feedback, setFeedback] = useState<{
    ownerKey: string | null;
    message: string | null;
    error: string | null;
  }>({ ownerKey: null, message: null, error: null });
  const message = feedback.ownerKey === ownerKey ? feedback.message : null;
  const error = feedback.ownerKey === ownerKey ? feedback.error : null;
  const selectedVariant = variants.find((variant) => variant.id === selectedId);
  const purchasableVariants = variants.filter(
    (variant) => variant.purchasable && variant.maxPurchaseQuantity > 0,
  );
  const soldOut = purchasableVariants.length === 0;
  const priceFrom = useMemo(
    () => Math.min(...variants.map((variant) => variant.salePrice)),
    [variants],
  );

  function selectVariant(id: string) {
    setSelectedId(id);
    setQuantity(1);
    setFeedback({ ownerKey, message: null, error: null });
  }

  async function addToCart(buyNow = false) {
    if (!selectedVariant) return;
    const operation = buyNow ? "buy" : "cart";
    const expectedOwnerKey = ownerKey;
    setPending({ operation, ownerKey: expectedOwnerKey });
    setFeedback({ ownerKey: expectedOwnerKey, message: null, error: null });
    try {
      const cart = await addCartItem(selectedVariant.id, quantity);
      setCounts({ cartCount: cart.itemCount }, expectedOwnerKey ?? undefined);
      const item = cart.items.find((candidate) => candidate.variantId === selectedVariant.id);
      if (buyNow && item) {
        router.push(`/checkout?items=${encodeURIComponent(item.id)}`);
      } else if (buyNow) {
        router.push("/cart");
      } else {
        setFeedback({
          ownerKey: expectedOwnerKey,
          message: "장바구니에 담았습니다.",
          error: null,
        });
      }
    } catch (caught) {
      setFeedback({
        ownerKey: expectedOwnerKey,
        message: null,
        error: caught instanceof ApiError ? caught.message : "장바구니에 담지 못했습니다.",
      });
    } finally {
      setPending((active) =>
        active?.operation === operation && active.ownerKey === expectedOwnerKey
          ? null
          : active,
      );
    }
  }

  async function toggleWishlist() {
    if (!isCustomer || !ownerKey) return;
    const expectedOwnerKey = ownerKey;
    setPending({ operation: "wishlist", ownerKey: expectedOwnerKey });
    setFeedback({ ownerKey: expectedOwnerKey, message: null, error: null });
    dispatchWishlist({ type: "clear-error", ownerKey: expectedOwnerKey });
    try {
      if (wishlisted) {
        await removeWishlistItem(productId);
        dispatchWishlist({
          type: "success",
          ownerKey: expectedOwnerKey,
          value: false,
        });
        setCounts(
          { wishlistCount: Math.max(0, wishlistCount - 1) },
          expectedOwnerKey,
        );
        router.refresh();
      } else {
        const result = await addWishlistItem(productId);
        dispatchWishlist({
          type: "success",
          ownerKey: expectedOwnerKey,
          value: true,
        });
        setCounts({ wishlistCount: result.wishlistCount }, expectedOwnerKey);
      }
    } catch (caught) {
      setFeedback({
        ownerKey: expectedOwnerKey,
        message: null,
        error: caught instanceof ApiError ? caught.message : "찜 상태를 변경하지 못했습니다.",
      });
    } finally {
      setPending((active) =>
        active?.operation === "wishlist" && active.ownerKey === expectedOwnerKey
          ? null
          : active,
      );
    }
  }

  const maxQuantity = selectedVariant?.maxPurchaseQuantity ?? 1;
  const total = selectedVariant ? selectedVariant.salePrice * quantity : null;

  useEffect(() => {
    if (authLoading || !isCustomer || !ownerKey) return;
    const controller = new AbortController();
    const expectedOwnerKey = ownerKey;
    dispatchWishlist({ type: "start", ownerKey: expectedOwnerKey });
    getWishlist(controller.signal)
      .then((page) => {
        if (!controller.signal.aborted) {
          dispatchWishlist({
            type: "success",
            ownerKey: expectedOwnerKey,
            value: page.data.some((item) => item.productId === productId),
          });
          setCounts(
            { wishlistCount: page.page.totalElements },
            expectedOwnerKey,
          );
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          dispatchWishlist({
            type: "failure",
            ownerKey: expectedOwnerKey,
            error: "찜 상태를 불러오지 못했습니다.",
          });
        }
      });
    return () => controller.abort();
  }, [authLoading, isCustomer, ownerKey, productId, setCounts]);

  return (
    <div className="purchase-configurator">
      <div className="purchase-price" aria-live="polite">
        <span>{selectedVariant ? "선택 옵션 가격" : "판매가"}</span>
        <strong>
          {Number.isFinite(selectedVariant?.salePrice ?? priceFrom)
            ? wonFormatter.format(selectedVariant?.salePrice ?? priceFrom)
            : "가격 준비 중"}
        </strong>
        {selectedVariant && selectedVariant.listPrice > selectedVariant.salePrice ? (
          <del>{wonFormatter.format(selectedVariant.listPrice)}</del>
        ) : null}
      </div>

      <fieldset className="variant-picker" disabled={soldOut}>
        <legend>상품 옵션</legend>
        {variants.length > 0 ? (
          <div className="variant-picker__list">
            {variants.map((variant) => {
              const available =
                variant.purchasable && variant.maxPurchaseQuantity > 0;
              return (
                <label
                  className="variant-option"
                  data-selected={selectedId === variant.id}
                  data-disabled={!available}
                  key={variant.id}
                >
                  <input
                    type="radio"
                    name="product-variant"
                    value={variant.id}
                    checked={selectedId === variant.id}
                    disabled={!available}
                    onChange={() => selectVariant(variant.id)}
                  />
                  <span className="variant-option__label">
                    <strong>{variant.optionLabel}</strong>
                    <small>{available ? `재고 ${variant.stockQuantity}개` : "품절"}</small>
                  </span>
                  <span className="variant-option__price">
                    {wonFormatter.format(variant.salePrice)}
                  </span>
                </label>
              );
            })}
          </div>
        ) : (
          <p className="purchase-message purchase-message--danger">
            판매 가능한 옵션이 없습니다.
          </p>
        )}
      </fieldset>

      {soldOut ? (
        <p className="purchase-message purchase-message--danger" role="status">
          현재 모든 옵션이 품절되었습니다.
        </p>
      ) : selectedVariant ? (
        <div className="selection-summary" aria-live="polite">
          <div>
            <span>선택한 옵션</span>
            <strong>{selectedVariant.optionLabel}</strong>
          </div>
          <div className="quantity-stepper" aria-label="수량 선택">
            <button
              type="button"
              onClick={() => {
                setQuantity((current) => Math.max(1, current - 1));
                setFeedback({ ownerKey, message: null, error: null });
              }}
              disabled={quantity <= 1}
              aria-label="수량 줄이기"
            >
              −
            </button>
            <output aria-label={`현재 수량 ${quantity}개`}>{quantity}</output>
            <button
              type="button"
              onClick={() => {
                setQuantity((current) => Math.min(maxQuantity, current + 1));
                setFeedback({ ownerKey, message: null, error: null });
              }}
              disabled={quantity >= maxQuantity}
              aria-label="수량 늘리기"
            >
              +
            </button>
          </div>
          <strong className="selection-summary__total">
            총 {wonFormatter.format(total ?? 0)}
          </strong>
        </div>
      ) : (
        <p className="purchase-message" role="status">
          구매할 옵션을 먼저 선택해 주세요.
        </p>
      )}

      {isCustomer ? (
        <button className="wishlist-button" type="button" aria-pressed={wishlisted} disabled={pendingOperation !== null || currentWishlist.loading} onClick={() => void toggleWishlist()}>
          <span aria-hidden="true">{wishlisted ? "♥" : "♡"}</span>
          {wishlisted ? "찜한 상품" : "찜하기"}
        </button>
      ) : !authLoading && !authenticated ? (
        <p className="purchase-preview-note">
          <Link href={`/login?next=${encodeURIComponent(pathname)}`}>로그인</Link>하면 상품을 찜할 수 있습니다.
        </p>
      ) : null}
      <div className="purchase-actions" aria-describedby="purchase-result-note">
        <button
          className="button button--secondary"
          type="button"
          disabled={!selectedVariant || soldOut || pendingOperation !== null}
          onClick={() => void addToCart(false)}
        >
          {pendingOperation === "cart" ? "담는 중…" : "장바구니 담기"}
        </button>
        <button
          className="button button--primary"
          type="button"
          disabled={!selectedVariant || soldOut || pendingOperation !== null}
          onClick={() => void addToCart(true)}
        >
          {pendingOperation === "buy" ? "준비 중…" : "바로 구매"}
        </button>
      </div>
      <p className={error ? "purchase-preview-note purchase-preview-note--error" : "purchase-preview-note"} id="purchase-result-note" aria-live="polite">
        {error ?? message ?? "옵션과 수량을 선택하면 장바구니에 담을 수 있습니다."}
        {message ? <Link href="/cart"> 장바구니 보기</Link> : null}
      </p>
    </div>
  );
}
