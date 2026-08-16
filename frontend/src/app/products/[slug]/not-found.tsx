import Link from "next/link";

export default function ProductNotFound() {
  return (
    <main className="catalog-page" id="main-content">
      <div className="state-panel">
        <div className="state-panel__content">
          <p className="eyebrow">PRODUCT NOT FOUND</p>
          <h1>판매 중인 상품을 찾을 수 없어요</h1>
          <p>주소가 바뀌었거나 공개가 종료된 상품일 수 있습니다.</p>
          <Link className="button button--primary" href="/products">
            판매 중인 상품 보기
          </Link>
        </div>
      </div>
    </main>
  );
}
