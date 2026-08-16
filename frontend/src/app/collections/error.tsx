"use client";

export default function CollectionsError({ reset }: { reset: () => void }) {
  return (
    <main className="collections-page" id="main-content">
      <div className="state-panel" role="alert">
        <div className="state-panel__content">
          <h1>기획전 페이지를 열지 못했어요</h1>
          <p>잠시 후 다시 시도해 주세요.</p>
          <button className="button button--primary" type="button" onClick={reset}>
            다시 시도
          </button>
        </div>
      </div>
    </main>
  );
}
