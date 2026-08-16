"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ApiError } from "@/lib/api/client";
import { getProducts } from "./api";
import { ProductCard } from "./product-card";
import { ProductFilters } from "./product-filters";
import { productPageHref } from "./query";
import type { ProductListPage, ProductListQuery } from "./types";

type CatalogState =
  | { status: "loading" }
  | { status: "success"; result: ProductListPage }
  | { status: "error"; message: string };

function ProductGridSkeleton() {
  return (
    <div className="product-grid" aria-label="상품을 불러오는 중" aria-busy="true">
      {Array.from({ length: 8 }, (_, index) => (
        <div className="skeleton-card" key={index} aria-hidden="true">
          <div className="skeleton-card__media" />
          <div className="skeleton-card__line skeleton-card__line--short" />
          <div className="skeleton-card__line" />
        </div>
      ))}
    </div>
  );
}

function errorMessage(error: unknown) {
  if (error instanceof ApiError && error.status === undefined) {
    return "상품 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.";
  }
  if (error instanceof ApiError && error.status === 400) {
    return "검색 조건을 처리할 수 없습니다. 필터를 초기화해 주세요.";
  }
  return "상품을 불러오는 중 문제가 생겼습니다.";
}

export function ProductCatalog({ query }: { query: ProductListQuery }) {
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<CatalogState>({ status: "loading" });

  useEffect(() => {
    const controller = new AbortController();
    getProducts(query, controller.signal)
      .then((result) => setState({ status: "success", result }))
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setState({ status: "error", message: errorMessage(error) });
        }
      });
    return () => controller.abort();
  }, [attempt, query]);

  return (
    <>
      <ProductFilters query={query} />

      {state.status === "loading" ? <ProductGridSkeleton /> : null}

      {state.status === "success" ? (
        <section aria-labelledby="catalog-results-title">
          <div className="catalog-results__heading">
            <h2 id="catalog-results-title">
              {query.q ? `“${query.q}” 검색 결과` : "큐레이션 상품"}
            </h2>
            <p aria-live="polite">총 {state.result.page.totalElements}개</p>
          </div>

          {state.result.products.length > 0 ? (
            <div className="product-grid">
              {state.result.products.map((product) => (
                <ProductCard key={product.id} product={product} />
              ))}
            </div>
          ) : (
            <div className="state-panel">
              <div className="state-panel__content">
                <h3>조건에 맞는 상품이 없어요</h3>
                <p>검색어를 줄이거나 다른 반려동물을 선택해 보세요.</p>
                <Link className="button button--secondary" href="/products">
                  모든 상품 보기
                </Link>
              </div>
            </div>
          )}

          {state.result.page.totalPages > 1 ? (
            <nav className="pagination" aria-label="상품 목록 페이지">
              {state.result.page.first ? (
                <span className="pagination__disabled">이전</span>
              ) : (
                <Link href={productPageHref(query, state.result.page.number - 1)}>
                  이전
                </Link>
              )}
              <span aria-current="page">
                {state.result.page.number + 1} / {state.result.page.totalPages}
              </span>
              {state.result.page.last ? (
                <span className="pagination__disabled">다음</span>
              ) : (
                <Link href={productPageHref(query, state.result.page.number + 1)}>
                  다음
                </Link>
              )}
            </nav>
          ) : null}
        </section>
      ) : null}

      {state.status === "error" ? (
        <div className="state-panel" role="alert">
          <div className="state-panel__content">
            <h3>상품을 불러오지 못했어요</h3>
            <p>{state.message}</p>
            <div className="state-panel__actions">
              <button
                className="button button--primary"
                type="button"
                onClick={() => {
                  setState({ status: "loading" });
                  setAttempt((current) => current + 1);
                }}
              >
                다시 시도
              </button>
              <Link className="button button--secondary" href="/products">
                필터 초기화
              </Link>
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
}
