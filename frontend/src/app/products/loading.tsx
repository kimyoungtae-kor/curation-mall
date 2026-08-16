export default function ProductsLoading() {
  return (
    <main className="catalog-page" id="main-content" aria-busy="true">
      <header className="catalog-hero">
        <p className="eyebrow">SHOP THE CURATION</p>
        <h1>상품을 준비하고 있어요</h1>
      </header>
      <div className="product-grid" aria-hidden="true">
        {Array.from({ length: 8 }, (_, index) => (
          <div className="skeleton-card" key={index}>
            <div className="skeleton-card__media" />
            <div className="skeleton-card__line skeleton-card__line--short" />
            <div className="skeleton-card__line" />
          </div>
        ))}
      </div>
    </main>
  );
}
