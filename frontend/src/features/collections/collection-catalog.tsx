"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ApiError } from "@/lib/api/client";
import { getCollections } from "./api";
import { CollectionCard } from "./collection-card";
import type { CollectionListPage } from "./types";

type CatalogState =
  | { status: "loading" }
  | { status: "success"; result: CollectionListPage }
  | { status: "error"; message: string };

function CollectionSkeleton() {
  return (
    <div className="collection-grid" aria-label="기획전을 불러오는 중" aria-busy="true">
      {Array.from({ length: 6 }, (_, index) => (
        <div className="collection-skeleton" key={index} aria-hidden="true">
          <div className="skeleton-card__media" />
          <div className="skeleton-card__line skeleton-card__line--short" />
          <div className="skeleton-card__line" />
        </div>
      ))}
    </div>
  );
}

function errorMessage(error: unknown) {
  return error instanceof ApiError && error.status === undefined
    ? "기획전 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."
    : "기획전을 불러오는 중 문제가 생겼습니다.";
}

export function CollectionCatalog({ page }: { page: number }) {
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<CatalogState>({ status: "loading" });

  useEffect(() => {
    const controller = new AbortController();
    getCollections(page, controller.signal)
      .then((result) => setState({ status: "success", result }))
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setState({ status: "error", message: errorMessage(error) });
        }
      });
    return () => controller.abort();
  }, [attempt, page]);

  if (state.status === "loading") return <CollectionSkeleton />;

  if (state.status === "error") {
    return (
      <div className="state-panel" role="alert">
        <div className="state-panel__content">
          <h2>기획전을 불러오지 못했어요</h2>
          <p>{state.message}</p>
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
        </div>
      </div>
    );
  }

  return (
    <section aria-labelledby="collection-results-title">
      <div className="collection-results__heading">
        <h2 id="collection-results-title">진행 중인 기획전</h2>
        <p aria-live="polite">총 {state.result.page.totalElements}개</p>
      </div>

      {state.result.collections.length > 0 ? (
        <div className="collection-grid">
          {state.result.collections.map((collection) => (
            <CollectionCard key={collection.id} collection={collection} />
          ))}
        </div>
      ) : (
        <div className="state-panel">
          <div className="state-panel__content">
            <h3>공개된 기획전이 없어요</h3>
            <p>새로운 생활 장면을 준비하고 있습니다.</p>
            <Link className="button button--secondary" href="/products">
              전체 상품 보기
            </Link>
          </div>
        </div>
      )}

      {state.result.page.totalPages > 1 ? (
        <nav className="pagination" aria-label="기획전 목록 페이지">
          {state.result.page.first ? (
            <span className="pagination__disabled">이전</span>
          ) : (
            <Link href={`/collections?page=${state.result.page.number - 1}`}>이전</Link>
          )}
          <span aria-current="page">
            {state.result.page.number + 1} / {state.result.page.totalPages}
          </span>
          {state.result.page.last ? (
            <span className="pagination__disabled">다음</span>
          ) : (
            <Link href={`/collections?page=${state.result.page.number + 1}`}>다음</Link>
          )}
        </nav>
      ) : null}
    </section>
  );
}
