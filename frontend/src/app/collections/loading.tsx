export default function CollectionsLoading() {
  return (
    <main className="collections-page" id="main-content" aria-busy="true">
      <div className="collections-hero collections-hero--loading" />
      <div className="collection-grid" aria-hidden="true">
        {Array.from({ length: 6 }, (_, index) => (
          <div className="skeleton-card__media" key={index} />
        ))}
      </div>
    </main>
  );
}
