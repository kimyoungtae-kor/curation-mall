export default function ProductDetailLoading() {
  return (
    <main className="product-detail-page" id="main-content" aria-busy="true">
      <p className="breadcrumbs">상품 정보를 불러오는 중</p>
      <div className="product-detail">
        <div className="skeleton-detail__media" aria-hidden="true" />
        <div className="skeleton-detail__copy" aria-hidden="true">
          <div className="skeleton-card__line skeleton-card__line--short" />
          <div className="skeleton-card__line" />
          <div className="skeleton-card__line" />
        </div>
      </div>
    </main>
  );
}
