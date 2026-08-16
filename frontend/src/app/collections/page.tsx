import type { Metadata } from "next";
import { CollectionCatalog } from "@/features/collections/collection-catalog";

export const metadata: Metadata = {
  title: "상황별 기획전",
  description: "계절과 생활 장면에 맞춰 고른 반려동물용품 기획전을 만나보세요.",
};

type CollectionSearchParams = { page?: string | string[] };

function pageNumber(value: string | string[] | undefined) {
  const raw = Array.isArray(value) ? value[0] : value;
  const parsed = Number.parseInt(raw ?? "0", 10);
  return Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : 0;
}

export default async function CollectionsPage({
  searchParams,
}: {
  searchParams: Promise<CollectionSearchParams>;
}) {
  const page = pageNumber((await searchParams).page);
  return (
    <main className="collections-page" id="main-content">
      <header className="collections-hero">
        <p className="eyebrow">SHOP BY MOMENT</p>
        <h1>생활의 장면부터<br />골라보세요</h1>
        <p>여름철 음수 습관부터 안전한 드라이브까지, 상황에 필요한 제품을 한데 모았어요.</p>
      </header>
      <CollectionCatalog key={page} page={page} />
    </main>
  );
}
