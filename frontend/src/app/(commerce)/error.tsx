"use client";

import Link from "next/link";

export default function CommerceError({ reset }: { reset: () => void }) {
  return (
    <div className="commerce-state" role="alert">
      <h1>화면을 표시하지 못했습니다</h1>
      <p>연결 상태를 확인한 뒤 다시 시도해 주세요.</p>
      <div className="commerce-actions commerce-actions--center">
        <button className="commerce-button commerce-button--primary" type="button" onClick={reset}>
          다시 시도
        </button>
        <Link className="commerce-button commerce-button--secondary" href="/">
          홈으로 이동
        </Link>
      </div>
    </div>
  );
}
