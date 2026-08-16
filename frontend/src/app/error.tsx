"use client";

export default function ErrorPage({ retry }: { retry: () => void }) {
  return (
    <main className="state-panel" role="alert">
      <div className="state-panel__content">
        <h1>페이지를 표시하지 못했어요</h1>
        <p>잠시 후 다시 시도해 주세요.</p>
        <button className="button button--secondary" type="button" onClick={retry}>
          다시 시도
        </button>
      </div>
    </main>
  );
}
