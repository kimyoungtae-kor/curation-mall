import type { Metadata } from "next";
import Link from "next/link";
import { SafeMedia } from "@/components/safe-media";
import { getCollectionForPage } from "@/features/collections/collection-detail-loader";
import { ProductCard } from "@/features/products/product-card";

type CollectionDetailPageProps = {
  params: Promise<{ slug: string }>;
};

export async function generateMetadata({
  params,
}: CollectionDetailPageProps): Promise<Metadata> {
  const collection = await getCollectionForPage((await params).slug);
  return {
    title: collection.title,
    description: collection.description.slice(0, 150),
  };
}

export default async function CollectionDetailPage({
  params,
}: CollectionDetailPageProps) {
  const collection = await getCollectionForPage((await params).slug);

  return (
    <main className="collection-detail-page" id="main-content">
      <nav className="breadcrumbs" aria-label="현재 위치">
        <Link href="/">홈</Link>
        <span aria-hidden="true">/</span>
        <Link href="/collections">기획전</Link>
        <span aria-hidden="true">/</span>
        <span aria-current="page">{collection.title}</span>
      </nav>

      <header className="collection-detail-hero">
        <div className="collection-detail-hero__copy">
          <p className="eyebrow">CURATED EDIT</p>
          <h1>{collection.title}</h1>
          <p>{collection.description || "생활의 한 장면에 어울리는 제품을 모았어요."}</p>
          <a className="button button--primary" href="#collection-products">
            제품 둘러보기
          </a>
        </div>
        <div className="collection-detail-hero__media">
          <SafeMedia
            src={collection.image?.url ?? null}
            alt={collection.image?.alt ?? collection.title}
            fallbackLabel={collection.title}
            className="collection-detail-hero__image"
            eager
          />
        </div>
      </header>

      <section
        className="collection-products"
        id="collection-products"
        aria-labelledby="collection-products-title"
      >
        <div className="collection-results__heading">
          <div>
            <p className="eyebrow">SELECTED PRODUCTS</p>
            <h2 id="collection-products-title">이 장면을 위한 제품</h2>
          </div>
          <p>총 {collection.products.length}개</p>
        </div>

        {collection.products.length > 0 ? (
          <div className="product-grid">
            {collection.products.map((product) => (
              <ProductCard key={product.id} product={product} />
            ))}
          </div>
        ) : (
          <div className="state-panel">
            <div className="state-panel__content">
              <h3>연결된 상품을 준비하고 있어요</h3>
              <p>대신 전체 상품에서 새로운 아이템을 둘러보세요.</p>
              <Link className="button button--secondary" href="/products">
                전체 상품 보기
              </Link>
            </div>
          </div>
        )}
      </section>
    </main>
  );
}
