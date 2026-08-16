import Link from "next/link";
import { SafeMedia } from "@/components/safe-media";
import { CollectionCard } from "@/features/collections/collection-card";
import { ProductCard } from "@/features/products/product-card";
import { ContentLinkView } from "./content-link-view";
import type { ExploreItem, HomeData } from "./types";

function SectionHeading({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow: string;
  title: string;
  description: string;
  action?: { href: string; label: string };
}) {
  return (
    <div className="home-section__heading">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h2>{title}</h2>
      </div>
      <div>
        <p>{description}</p>
        {action ? <Link href={action.href}>{action.label} →</Link> : null}
      </div>
    </div>
  );
}

export function FeaturedCollections({
  collections,
}: {
  collections: HomeData["featuredCollections"];
}) {
  return (
    <section className="home-section" aria-labelledby="featured-collections-title">
      <SectionHeading
        eyebrow="CURATED FOR THE MOMENT"
        title="상황에 딱 맞는 기획전"
        description="계절과 생활 장면을 먼저 생각하고, 지금 필요한 제품을 골랐어요."
        action={{ href: "/collections", label: "모든 기획전" }}
      />
      <span className="sr-only" id="featured-collections-title">
        상황별 추천 기획전
      </span>
      {collections.length > 0 ? (
        <div className="collection-grid collection-grid--featured">
          {collections.map((collection) => (
            <CollectionCard key={collection.id} collection={collection} />
          ))}
        </div>
      ) : (
        <div className="home-empty">새로운 기획전을 준비하고 있어요.</div>
      )}
    </section>
  );
}

function ProductGroup({
  id,
  eyebrow,
  title,
  description,
  products,
}: {
  id: string;
  eyebrow: string;
  title: string;
  description: string;
  products: HomeData["popularProducts"];
}) {
  return (
    <section className="home-product-group" aria-labelledby={id}>
      <div className="home-product-group__heading">
        <div>
          <p className="eyebrow">{eyebrow}</p>
          <h2 id={id}>{title}</h2>
        </div>
        <p>{description}</p>
      </div>
      {products.length > 0 ? (
        <div className="product-grid home-product-grid">
          {products.map((product) => (
            <ProductCard key={product.id} product={product} />
          ))}
        </div>
      ) : (
        <div className="home-empty">상품을 준비하고 있어요.</div>
      )}
    </section>
  );
}

export function HomeProducts({
  popularProducts,
  newProducts,
}: Pick<HomeData, "popularProducts" | "newProducts">) {
  return (
    <div className="home-products" aria-label="인기 상품과 신상품">
      <ProductGroup
        id="popular-products-title"
        eyebrow="MOST LOVED"
        title="지금 많이 찾는 제품"
        description="다른 반려가족이 먼저 고른 인기 아이템이에요."
        products={popularProducts}
      />
      <ProductGroup
        id="new-products-title"
        eyebrow="JUST ARRIVED"
        title="새로 들어온 제품"
        description="공간과 생활에 새로운 감각을 더해보세요."
        products={newProducts}
      />
    </div>
  );
}

const exploreLabels: Record<ExploreItem["kind"], string> = {
  species: "반려동물",
  category: "카테고리",
  brand: "브랜드",
};

export function ExploreProducts({ explore }: Pick<HomeData, "explore">) {
  return (
    <section className="home-section home-explore" aria-labelledby="explore-title">
      <SectionHeading
        eyebrow="FIND YOUR FAVORITE"
        title="취향대로 둘러보기"
        description="함께 사는 동물, 필요한 용도, 좋아하는 브랜드에서 시작해 보세요."
      />
      <span className="sr-only" id="explore-title">
        동물, 카테고리, 브랜드별 상품 탐색
      </span>
      {explore.length > 0 ? (
        <div className="explore-grid">
          {explore.map((item) => (
            <Link
              className="explore-chip"
              href={`/products?${item.kind}=${encodeURIComponent(item.slug)}`}
              key={item.key}
            >
              <span>{exploreLabels[item.kind]}</span>
              <strong>{item.name}</strong>
              <span aria-hidden="true">↗</span>
            </Link>
          ))}
        </div>
      ) : (
        <div className="home-empty">탐색 메뉴를 준비하고 있어요.</div>
      )}
    </section>
  );
}

export function LifestyleContents({
  lifestyleContents,
}: Pick<HomeData, "lifestyleContents">) {
  return (
    <section
      className="home-section home-lifestyle"
      id="pet-room"
      aria-labelledby="pet-room-title"
    >
      <SectionHeading
        eyebrow="PET ROOM JOURNAL"
        title="함께 머무는 공간 이야기"
        description="용품을 넘어 집 안의 풍경이 되는 반려생활을 제안합니다."
      />
      <span className="sr-only" id="pet-room-title">
        펫룸 공간 콘텐츠
      </span>
      {lifestyleContents.length > 0 ? (
        <div className="lifestyle-grid">
          {lifestyleContents.map((content, index) => (
            <article className="lifestyle-card" key={content.id}>
              <div className="lifestyle-card__media">
                <SafeMedia
                  src={content.image?.url ?? null}
                  alt={content.image?.alt ?? content.title}
                  fallbackLabel={content.title}
                  className="lifestyle-card__image"
                />
              </div>
              <div className="lifestyle-card__body">
                <p>ROOM {String(index + 1).padStart(2, "0")}</p>
                <h3>{content.title}</h3>
                <span>{content.description}</span>
                <ContentLinkView link={content.link}>공간 속 제품 보기 →</ContentLinkView>
              </div>
            </article>
          ))}
        </div>
      ) : (
        <div className="home-empty">새로운 공간 이야기를 준비하고 있어요.</div>
      )}
    </section>
  );
}

const wonFormatter = new Intl.NumberFormat("ko-KR", {
  style: "currency",
  currency: "KRW",
  maximumFractionDigits: 0,
});

export const serviceLinkLabels: Record<string, { href: string; label: string }> = {
  "shipping-returns": { href: "/help/shipping-returns", label: "배송·교환·반품" },
  terms: { href: "/terms", label: "이용약관" },
  privacy: { href: "/privacy", label: "개인정보처리방침" },
};

export function ServiceGuide({ serviceGuide }: Pick<HomeData, "serviceGuide">) {
  return (
    <section className="service-guide" id="service-guide" aria-labelledby="service-title">
      <div>
        <p className="eyebrow">SERVICE GUIDE</p>
        <h2 id="service-title">편안한 쇼핑을 위한 안내</h2>
      </div>
      <dl>
        <div>
          <dt>배송비</dt>
          <dd>{wonFormatter.format(serviceGuide.shippingFee)}</dd>
        </div>
        <div>
          <dt>무료배송</dt>
          <dd>{wonFormatter.format(serviceGuide.freeShippingThreshold)} 이상</dd>
        </div>
      </dl>
      <nav aria-label="서비스 정책">
        {serviceGuide.links.map((slug) => {
          const link = serviceLinkLabels[slug];
          return link ? (
            <Link key={slug} href={link.href}>
              {link.label} →
            </Link>
          ) : null;
        })}
      </nav>
    </section>
  );
}
