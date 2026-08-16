import Link from "next/link";

export default function CollectionNotFound() {
  return (
    <main className="collection-detail-page" id="main-content">
      <div className="state-panel">
        <div className="state-panel__content">
          <h1>기획전을 찾을 수 없어요</h1>
          <p>종료되었거나 아직 공개되지 않은 기획전입니다.</p>
          <Link className="button button--primary" href="/collections">
            진행 중인 기획전 보기
          </Link>
        </div>
      </div>
    </main>
  );
}
