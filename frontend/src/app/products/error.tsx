"use client";

import Link from "next/link";

export default function ProductsError({ retry }: { retry: () => void }) {
  return (
    <main className="catalog-page" id="main-content">
      <div className="state-panel" role="alert">
        <div className="state-panel__content">
          <p className="eyebrow">TEMPORARY ERROR</p>
          <h1>상품 페이지를 표시하지 못했어요</h1>
          <p>연결 상태를 확인한 뒤 다시 시도해 주세요.</p>
          <div className="state-panel__actions">
            <button className="button button--primary" type="button" onClick={retry}>
              다시 시도
            </button>
            <Link className="button button--secondary" href="/products">
              필터 초기화
            </Link>
          </div>
        </div>
      </div>
    </main>
  );
}
