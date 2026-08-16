import Link from "next/link";
import { productPageHref } from "./query";
import type { ProductListQuery } from "./types";

const sortOptions = [
  { value: "newest,desc", label: "추천·신상품순" },
  { value: "price,asc", label: "낮은 가격순" },
  { value: "price,desc", label: "높은 가격순" },
  { value: "name,asc", label: "상품명순" },
] as const;

export function ProductFilters({ query }: { query: ProductListQuery }) {
  return (
    <form className="catalog-filters" action="/products" method="get">
      {query.brand ? <input type="hidden" name="brand" value={query.brand} /> : null}
      {query.category ? (
        <input type="hidden" name="category" value={query.category} />
      ) : null}
      <div className="catalog-filters__search">
        <label htmlFor="catalog-query">상품 또는 브랜드 찾기</label>
        <input
          id="catalog-query"
          name="q"
          type="search"
          defaultValue={query.q}
          placeholder="예: 보울, 멜로우테일"
          maxLength={100}
        />
      </div>

      <div className="catalog-filters__field">
        <label htmlFor="catalog-species">반려동물</label>
        <select id="catalog-species" name="species" defaultValue={query.species ?? ""}>
          <option value="">전체</option>
          <option value="dog">강아지</option>
          <option value="cat">고양이</option>
          {query.species && !["dog", "cat"].includes(query.species) ? (
            <option value={query.species}>{query.species}</option>
          ) : null}
        </select>
      </div>

      <div className="catalog-filters__field">
        <label htmlFor="catalog-sort">정렬</label>
        <select id="catalog-sort" name="sort" defaultValue={query.sort}>
          {sortOptions.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </div>

      <label className="catalog-filters__check">
        <input
          type="checkbox"
          name="inStock"
          value="true"
          defaultChecked={query.inStock}
        />
        구매 가능한 상품만
      </label>

      <div className="catalog-filters__actions">
        <button className="button button--primary" type="submit">
          적용하기
        </button>
        <Link className="catalog-filters__reset" href="/products">
          초기화
        </Link>
      </div>

      {query.brand || query.category ? (
        <div className="catalog-filters__active" aria-label="적용 중인 상세 필터">
          {query.brand ? (
            <span>
              브랜드: {query.brand}
              <Link
                href={productPageHref(
                  { ...query, brand: undefined, page: 0 },
                  0,
                )}
                aria-label={`${query.brand} 브랜드 필터 해제`}
              >
                ×
              </Link>
            </span>
          ) : null}
          {query.category ? (
            <span>
              카테고리: {query.category}
              <Link
                href={productPageHref(
                  { ...query, category: undefined, page: 0 },
                  0,
                )}
                aria-label={`${query.category} 카테고리 필터 해제`}
              >
                ×
              </Link>
            </span>
          ) : null}
        </div>
      ) : null}
    </form>
  );
}
