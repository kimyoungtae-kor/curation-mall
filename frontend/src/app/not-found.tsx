import Link from "next/link";

export default function NotFound() {
  return (
    <main className="state-panel">
      <div className="state-panel__content">
        <h1>페이지를 찾을 수 없어요</h1>
        <p>주소를 확인하거나 홈으로 돌아가 주세요.</p>
        <Link className="button button--primary" href="/">
          홈으로 돌아가기
        </Link>
      </div>
    </main>
  );
}
