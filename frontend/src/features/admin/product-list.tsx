"use client";

import Link from "next/link";
import { useEffect, useState, type FormEvent } from "react";
import { formatDateTime, formatWon } from "@/features/commerce/format";
import {
  changeAdminProductStatus,
  changeAdminVariantStock,
  getAdminProduct,
  getAdminProducts,
} from "./api";
import { AdminGuard } from "./admin-shell";
import { AdminEmpty, AdminLoading } from "./dashboard";
import { adminErrorMessage } from "./error";
import type {
  AdminProductDetail,
  AdminProductSummary,
  AdminVariant,
  ProductStatus,
} from "./types";

const PRODUCT_STATUSES: { value: ProductStatus | ""; label: string }[] = [
  { value: "", label: "전체 상태" },
  { value: "DRAFT", label: "초안" },
  { value: "PUBLISHED", label: "판매 중" },
  { value: "HIDDEN", label: "숨김" },
  { value: "DISCONTINUED", label: "판매 종료" },
];

const statusLabel = (status: ProductStatus) =>
  PRODUCT_STATUSES.find((option) => option.value === status)?.label ?? status;

export function AdminProductListPage() {
  return (
    <AdminGuard
      title="상품 · 재고"
      description="상품을 검색하고 판매 상태와 옵션별 재고를 관리합니다."
      actions={<Link className="admin-button admin-button--primary" href="/admin/products/new">새 상품 등록</Link>}
    >
      <ProductList />
    </AdminGuard>
  );
}

function ProductList() {
  const [draftQuery, setDraftQuery] = useState("");
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState<ProductStatus | "">("");
  const [page, setPage] = useState(0);
  const [products, setProducts] = useState<AdminProductSummary[]>([]);
  const [pageInfo, setPageInfo] = useState({ totalElements: 0, totalPages: 0, first: true, last: true });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busyProductId, setBusyProductId] = useState<string | null>(null);
  const [detail, setDetail] = useState<AdminProductDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    getAdminProducts({ q: query, status, page, size: 20 }, controller.signal)
      .then((response) => {
        setProducts(response.data);
        setPageInfo(response.page);
        setError(null);
      })
      .catch((caught) => {
        if (!controller.signal.aborted) setError(adminErrorMessage(caught));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [page, query, status]);

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPage(0);
    setQuery(draftQuery.trim());
  }

  async function openDetail(productId: string) {
    if (detail?.id === productId) {
      setDetail(null);
      return;
    }
    setDetailLoading(true);
    setDetailError(null);
    try {
      setDetail(await getAdminProduct(productId));
    } catch (caught) {
      setDetailError(adminErrorMessage(caught));
    } finally {
      setDetailLoading(false);
    }
  }

  async function changeStatus(product: AdminProductSummary, nextStatus: ProductStatus) {
    setBusyProductId(product.id);
    setError(null);
    try {
      const updated = await changeAdminProductStatus(product.id, nextStatus, product.version);
      setProducts((current) => current.map((item) => item.id === product.id
        ? { ...item, status: updated.status, version: updated.version }
        : item));
      if (detail?.id === product.id) setDetail(updated);
    } catch (caught) {
      setError(adminErrorMessage(caught));
    } finally {
      setBusyProductId(null);
    }
  }

  async function saveStock(variant: AdminVariant, stockQuantity: number) {
    setDetailError(null);
    try {
      const updated = await changeAdminVariantStock(variant.id, stockQuantity, variant.version);
      setDetail((current) => current ? {
        ...current,
        variants: current.variants.map((item) => item.id === updated.id ? updated : item),
      } : current);
      setProducts((current) => current.map((product) => product.id === detail?.id
        ? {
            ...product,
            totalStock: product.totalStock - variant.stockQuantity + updated.stockQuantity,
          }
        : product));
    } catch (caught) {
      setDetailError(adminErrorMessage(caught));
      throw caught;
    }
  }

  return (
    <div className="admin-stack">
      <form className="admin-toolbar" onSubmit={search}>
        <label className="admin-toolbar__search">
          <span className="admin-sr-only">상품 검색</span>
          <input
            type="search"
            value={draftQuery}
            onChange={(event) => setDraftQuery(event.target.value)}
            placeholder="상품명 또는 슬러그 검색"
          />
        </label>
        <label>
          <span className="admin-sr-only">판매 상태</span>
          <select
            value={status}
            onChange={(event) => {
              setPage(0);
              setStatus(event.target.value as ProductStatus | "");
            }}
          >
            {PRODUCT_STATUSES.map((option) => <option key={option.value || "all"} value={option.value}>{option.label}</option>)}
          </select>
        </label>
        <button className="admin-button" type="submit">검색</button>
        <span className="admin-toolbar__count">총 {pageInfo.totalElements.toLocaleString("ko-KR")}개</span>
      </form>

      {error ? <p className="admin-alert admin-alert--error" role="alert">{error}</p> : null}
      {loading ? <AdminLoading label="상품 목록을 불러오는 중입니다." /> : products.length ? (
        <div className="admin-table-wrap">
          <table className="admin-table">
            <thead>
              <tr>
                <th>상품</th>
                <th>판매가</th>
                <th>총 재고</th>
                <th>상태</th>
                <th>최근 수정</th>
                <th><span className="admin-sr-only">관리</span></th>
              </tr>
            </thead>
            <tbody>
              {products.map((product) => (
                <tr key={product.id} data-selected={detail?.id === product.id}>
                  <td>
                    <strong>{product.name}</strong>
                    <span>{product.brandName} · {product.slug}</span>
                  </td>
                  <td>{formatWon(product.minimumPrice)}~</td>
                  <td><strong className="admin-stock" data-low={product.totalStock <= 5}>{product.totalStock}개</strong></td>
                  <td>
                    <select
                      className="admin-status-select"
                      aria-label={`${product.name} 판매 상태`}
                      value={product.status}
                      disabled={busyProductId === product.id}
                      onChange={(event) => void changeStatus(product, event.target.value as ProductStatus)}
                    >
                      {PRODUCT_STATUSES.filter((option) => option.value).map((option) => (
                        <option key={option.value} value={option.value}>{option.label}</option>
                      ))}
                    </select>
                  </td>
                  <td>{formatDateTime(product.updatedAt)}</td>
                  <td>
                    <div className="admin-row-actions">
                      <button type="button" onClick={() => void openDetail(product.id)}>
                        {detail?.id === product.id ? "닫기" : "옵션 · 재고"}
                      </button>
                      <Link href={`/admin/products/${encodeURIComponent(product.id)}`}>수정</Link>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : <AdminEmpty label="조건에 맞는 상품이 없습니다." />}

      {pageInfo.totalPages > 1 ? (
        <nav className="admin-pagination" aria-label="상품 페이지">
          <button type="button" disabled={pageInfo.first} onClick={() => setPage((current) => Math.max(0, current - 1))}>이전</button>
          <span>{page + 1} / {pageInfo.totalPages}</span>
          <button type="button" disabled={pageInfo.last} onClick={() => setPage((current) => current + 1)}>다음</button>
        </nav>
      ) : null}

      {detailLoading ? <AdminLoading label="상품 옵션을 불러오는 중입니다." /> : null}
      {detailError ? <p className="admin-alert admin-alert--error" role="alert">{detailError}</p> : null}
      {detail ? (
        <section className="admin-panel admin-product-detail" aria-labelledby="stock-heading">
          <div className="admin-panel__heading">
            <div>
              <p className="admin-kicker">{statusLabel(detail.status)}</p>
              <h2 id="stock-heading">{detail.name} 옵션 재고</h2>
              <p>재고는 저장 즉시 고객 상품 상세와 주문 가능 수량에 반영됩니다.</p>
            </div>
            <Link href={`/admin/products/${encodeURIComponent(detail.id)}`}>상품 정보 전체 수정</Link>
          </div>
          <div className="admin-variant-list">
            {detail.variants.map((variant) => (
              <VariantStockRow key={`${variant.id}-${variant.version}`} variant={variant} onSave={saveStock} />
            ))}
          </div>
        </section>
      ) : null}
    </div>
  );
}

function VariantStockRow({ variant, onSave }: { variant: AdminVariant; onSave: (variant: AdminVariant, quantity: number) => Promise<void> }) {
  const [quantity, setQuantity] = useState(variant.stockQuantity);
  const [saving, setSaving] = useState(false);
  return (
    <article className="admin-variant-row">
      <div>
        <strong>{variant.optionLabel}</strong>
        <span>{variant.sku} · {formatWon(variant.price)} · {variant.status === "ACTIVE" ? "판매 가능" : "비활성"}</span>
      </div>
      <label>
        <span>재고</span>
        <input
          type="number"
          min={0}
          step={1}
          value={quantity}
          onChange={(event) => setQuantity(Math.max(0, Number(event.target.value) || 0))}
        />
      </label>
      <button
        className="admin-button"
        type="button"
        disabled={saving || quantity === variant.stockQuantity}
        onClick={() => {
          setSaving(true);
          void onSave(variant, quantity).finally(() => setSaving(false));
        }}
      >
        {saving ? "저장 중…" : "재고 저장"}
      </button>
    </article>
  );
}
