export default function Loading() {
  return (
    <main className="state-panel" aria-busy="true" aria-label="페이지를 불러오는 중">
      <div className="state-panel__content">
        <p>페이지를 준비하고 있어요.</p>
      </div>
    </main>
  );
}
