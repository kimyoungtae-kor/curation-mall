import type { Metadata } from "next";
import { ProductCatalog } from "@/features/products/product-catalog";
import {
  parseProductQuery,
  serializeProductQuery,
  type ProductSearchParams,
} from "@/features/products/query";

export const metadata: Metadata = {
  title: "전체 상품",
  description: "상황과 공간에 맞춰 고른 반려동물 라이프스타일 상품을 만나보세요.",
};

export default async function ProductsPage({
  searchParams,
}: {
  searchParams: Promise<ProductSearchParams>;
}) {
  const query = parseProductQuery(await searchParams);
  const requestKey = serializeProductQuery(query).toString();

  return (
    <main className="catalog-page" id="main-content">
      <header className="catalog-hero">
        <p className="eyebrow">SHOP THE CURATION</p>
        <h1>모든 상품</h1>
        <p>
          반려동물의 하루와 집 안의 풍경을 함께 생각한 제품을 골라보세요.
        </p>
      </header>

      <ProductCatalog key={requestKey} query={query} />
    </main>
  );
}
