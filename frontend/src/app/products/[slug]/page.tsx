import type { Metadata } from "next";
import Link from "next/link";
import { ProductGallery } from "@/features/products/detail/product-gallery";
import { PurchaseConfigurator } from "@/features/products/detail/purchase-configurator";
import { getProductForPage } from "@/features/products/product-detail-loader";

type ProductDetailPageProps = {
  params: Promise<{ slug: string }>;
};

export async function generateMetadata({
  params,
}: ProductDetailPageProps): Promise<Metadata> {
  const { slug } = await params;
  const product = await getProductForPage(slug);
  return {
    title: product.name,
    description: product.summary ?? product.description.slice(0, 150),
  };
}

function displayAttribute(value: unknown): string {
  if (typeof value === "string" || typeof value === "number") return String(value);
  if (typeof value === "boolean") return value ? "예" : "아니오";
  if (Array.isArray(value)) return value.map(displayAttribute).join(", ");
  return JSON.stringify(value);
}

export default async function ProductDetailPage({
  params,
}: ProductDetailPageProps) {
  const { slug } = await params;
  const product = await getProductForPage(slug);
  const attributes = Object.entries(product.attributes);

  return (
    <main className="product-detail-page" id="main-content">
      <nav className="breadcrumbs" aria-label="현재 위치">
        <Link href="/">홈</Link>
        <span aria-hidden="true">/</span>
        <Link href="/products">전체 상품</Link>
        <span aria-hidden="true">/</span>
        <span aria-current="page">{product.name}</span>
      </nav>

      <div className="product-detail">
        <ProductGallery images={product.images} productName={product.name} />

        <section className="product-detail__info" aria-labelledby="product-title">
          <Link
            className="product-detail__brand"
            href={`/products?brand=${product.brand.slug}`}
          >
            {product.brand.name}
          </Link>
          <h1 id="product-title">{product.name}</h1>
          {product.summary ? (
            <p className="product-detail__summary">{product.summary}</p>
          ) : null}

          <div className="taxonomy-list" aria-label="상품 분류">
            {product.species.map((species) => (
              <Link key={species.slug} href={`/products?species=${species.slug}`}>
                {species.name}
              </Link>
            ))}
            {product.categories.map((category) => (
              <Link key={category.slug} href={`/products?category=${category.slug}`}>
                {category.name}
              </Link>
            ))}
          </div>

          <PurchaseConfigurator
            productId={product.id}
            variants={product.variants}
            initiallyWishlisted={product.wishlisted}
          />

          <dl className="service-points">
            <div>
              <dt>배송비</dt>
              <dd>3,000원 · 50,000원 이상 무료</dd>
            </div>
            <div>
              <dt>안내</dt>
              <dd>테스트 상품으로 실제 결제가 이루어지지 않습니다.</dd>
            </div>
          </dl>
        </section>
      </div>

      <section className="product-story" aria-labelledby="product-story-title">
        <div>
          <p className="eyebrow">PRODUCT STORY</p>
          <h2 id="product-story-title">생활 속에 자연스럽게</h2>
        </div>
        <div className="product-story__body">
          <p>{product.description || "상품 설명을 준비하고 있습니다."}</p>
          {attributes.length > 0 ? (
            <dl className="product-attributes">
              {attributes.map(([key, value]) => (
                <div key={key}>
                  <dt>{key}</dt>
                  <dd>{displayAttribute(value)}</dd>
                </div>
              ))}
            </dl>
          ) : null}
        </div>
      </section>
    </main>
  );
}
