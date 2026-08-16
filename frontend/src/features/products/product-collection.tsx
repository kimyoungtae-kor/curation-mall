"use client";

import { useEffect, useState } from "react";
import { ApiError } from "@/lib/api/client";
import { getFeaturedProducts } from "./api";
import { ProductCard } from "./product-card";
import type { ProductSummary } from "./types";

type ProductState =
  | { status: "loading" }
  | { status: "success"; products: ProductSummary[] }
  | { status: "error"; message: string };

function ProductSkeleton() {
  return (
    <div className="product-grid" aria-label="상품을 불러오는 중" aria-busy="true">
      {Array.from({ length: 4 }, (_, index) => (
        <div className="skeleton-card" key={index} aria-hidden="true">
          <div className="skeleton-card__media" />
          <div className="skeleton-card__line skeleton-card__line--short" />
          <div className="skeleton-card__line" />
        </div>
      ))}
    </div>
  );
}

function userMessage(error: unknown) {
  if (error instanceof ApiError && error.status === undefined) {
    return "상품 서버에 아직 연결되지 않았습니다. Spring Boot 실행 상태와 API 주소를 확인해 주세요.";
  }

  return "상품 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.";
}

export function ProductCollection() {
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<ProductState>({ status: "loading" });

  useEffect(() => {
    const controller = new AbortController();

    getFeaturedProducts(controller.signal)
      .then((products) => setState({ status: "success", products }))
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setState({ status: "error", message: userMessage(error) });
        }
      });

    return () => controller.abort();
  }, [attempt]);

  return (
    <section
      className="product-section"
      id="featured-products"
      aria-labelledby="featured-products-title"
    >
      <div className="section-heading">
        <div>
          <p className="eyebrow">FIRST SELECTION</p>
          <h2 id="featured-products-title">처음 만나는 큐레이션</h2>
        </div>
        <p className="section-heading__description">
          실제 상품 데이터가 연결되면 이곳에서 최신 큐레이션을 바로 확인할
          수 있습니다.
        </p>
      </div>

      {state.status === "loading" ? <ProductSkeleton /> : null}

      {state.status === "success" && state.products.length > 0 ? (
        <div className="product-grid">
          {state.products.map((product) => (
            <ProductCard key={product.id} product={product} />
          ))}
        </div>
      ) : null}

      {state.status === "success" && state.products.length === 0 ? (
        <div className="state-panel">
          <div className="state-panel__content">
            <h3>아직 공개된 상품이 없어요</h3>
            <p>관리자에서 첫 상품을 공개하면 이 공간에 표시됩니다.</p>
          </div>
        </div>
      ) : null}

      {state.status === "error" ? (
        <div className="state-panel" role="alert">
          <div className="state-panel__content">
            <h3>상품을 불러오지 못했어요</h3>
            <p>{state.message}</p>
            <button
              className="button button--secondary"
              type="button"
              onClick={() => {
                setState({ status: "loading" });
                setAttempt((current) => current + 1);
              }}
            >
              다시 시도
            </button>
          </div>
        </div>
      ) : null}
    </section>
  );
}
