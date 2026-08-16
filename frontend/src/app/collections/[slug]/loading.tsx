export default function CollectionDetailLoading() {
  return (
    <main className="collection-detail-page" id="main-content" aria-busy="true">
      <div className="collection-detail-hero collection-detail-hero--loading" aria-label="기획전을 불러오는 중" />
      <div className="product-grid" aria-hidden="true">
        {Array.from({ length: 4 }, (_, index) => (
          <div className="skeleton-card" key={index}>
            <div className="skeleton-card__media" />
            <div className="skeleton-card__line" />
          </div>
        ))}
      </div>
    </main>
  );
}
