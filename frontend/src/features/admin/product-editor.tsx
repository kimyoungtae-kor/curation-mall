"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent } from "react";
import {
  createAdminProduct,
  deleteAdminProduct,
  getAdminProduct,
  getAdminReferences,
  updateAdminProduct,
} from "./api";
import { AdminGuard } from "./admin-shell";
import { AdminLoading } from "./dashboard";
import { adminErrorMessage } from "./error";
import { normalizeProductImages } from "./product-image-model";
import { ProductImageUploader } from "./product-image-uploader";
import {
  blankVariant,
  isProductOrderHistoryConflict,
  productDeleteConflictMessage,
  productToDraft,
  type ProductDraft,
} from "./product-editor-model";
import type {
  AdminProductDetail,
  ProductStatus,
  ProductUpsertInput,
  ProductVariantInput,
  ReferenceItem,
  VariantStatus,
} from "./types";

const blankDraft: ProductDraft = {
  brandId: "",
  slug: "",
  name: "",
  summary: "",
  description: "",
  status: "DRAFT",
  featured: false,
  categoryIds: [],
  speciesIds: [],
  variants: [blankVariant(1)],
  images: [],
};

export function AdminProductEditorPage({ productId }: { productId?: string }) {
  const editing = Boolean(productId);
  return (
    <AdminGuard
      title={editing ? "상품 수정" : "새 상품 등록"}
      description={editing ? "상품 정보, 분류, 옵션과 이미지를 수정합니다." : "판매에 필요한 기본 정보와 옵션을 등록합니다."}
      actions={<Link className="admin-button" href="/admin/products">상품 목록</Link>}
    >
      <ProductEditor productId={productId} />
    </AdminGuard>
  );
}

function ProductEditor({ productId }: { productId?: string }) {
  const router = useRouter();
  const deletingRef = useRef(false);
  const [draft, setDraft] = useState<ProductDraft>(blankDraft);
  const [persistedProduct, setPersistedProduct] = useState<Pick<AdminProductDetail, "name" | "status" | "version"> | null>(null);
  const [brands, setBrands] = useState<ReferenceItem[]>([]);
  const [categories, setCategories] = useState<ReferenceItem[]>([]);
  const [species, setSpecies] = useState<ReferenceItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [imageUploading, setImageUploading] = useState(false);
  const [deleteConfirmation, setDeleteConfirmation] = useState<"none" | "initial" | "orderHistory">("none");
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    const productRequest = productId ? getAdminProduct(productId, controller.signal) : Promise.resolve(null);
    Promise.all([
      getAdminReferences("brands", controller.signal),
      getAdminReferences("categories", controller.signal),
      getAdminReferences("species", controller.signal),
      productRequest,
    ])
      .then(([brandItems, categoryItems, speciesItems, product]) => {
        setBrands(brandItems);
        setCategories(categoryItems);
        setSpecies(speciesItems);
        if (product) {
          setDraft(productToDraft(product));
          setPersistedProduct({ name: product.name, status: product.status, version: product.version });
        } else if (brandItems.length) {
          setDraft((current) => ({ ...current, brandId: brandItems[0].id }));
        }
      })
      .catch((caught) => {
        if (!controller.signal.aborted) setError(adminErrorMessage(caught));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [productId]);

  function toggleId(field: "categoryIds" | "speciesIds", id: string) {
    setDraft((current) => ({
      ...current,
      [field]: current[field].includes(id)
        ? current[field].filter((value) => value !== id)
        : [...current[field], id],
    }));
  }

  function updateVariant(index: number, change: Partial<ProductVariantInput>) {
    setDraft((current) => ({
      ...current,
      variants: current.variants.map((variant, variantIndex) => variantIndex === index ? { ...variant, ...change } : variant),
    }));
  }

  function updateImages(updater: (current: ProductDraft["images"]) => ProductDraft["images"]) {
    setDraft((current) => ({
      ...current,
      images: updater(current.images),
    }));
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    setSaved(null);
    try {
      if (imageUploading) throw new Error("이미지 업로드가 끝난 뒤 상품을 저장해 주세요.");
      if (!draft.variants.length) throw new Error("옵션은 1개 이상 필요합니다.");
      const invalidImage = draft.images.some((image) => !image.storageKey.trim() || !image.alt.trim());
      if (invalidImage) throw new Error("이미지 경로와 대체 텍스트를 모두 입력해 주세요.");
      if (draft.status === "PUBLISHED" && !draft.images.length) {
        throw new Error("판매 중인 상품에는 대표 이미지가 1장 이상 필요합니다.");
      }
      const input: ProductUpsertInput = {
        ...draft,
        slug: draft.slug.trim(),
        name: draft.name.trim(),
        summary: draft.summary.trim(),
        description: draft.description.trim(),
        variants: draft.variants.map((variant, index) => ({
          ...variant,
          sku: variant.sku.trim(),
          optionLabel: variant.optionLabel.trim(),
          sortOrder: index + 1,
        })),
        images: normalizeProductImages(draft.images),
      };
      const result = productId
        ? await updateAdminProduct(productId, input)
        : await createAdminProduct(input);
      if (!productId) {
        router.replace(`/admin/products/${encodeURIComponent(result.id)}`);
        return;
      }
      setDraft(productToDraft(result));
      setPersistedProduct({ name: result.name, status: result.status, version: result.version });
      setDeleteConfirmation("none");
      setDeleteError(null);
      setSaved("상품 정보를 저장했습니다.");
    } catch (caught) {
      setError(caught instanceof Error && !("status" in caught) ? caught.message : adminErrorMessage(caught));
    } finally {
      setSubmitting(false);
    }
  }

  async function removeProduct(confirmOrderHistory = false) {
    if (!productId || !persistedProduct || deletingRef.current) return;
    deletingRef.current = true;
    setDeleting(true);
    setDeleteError(null);
    setError(null);
    setSaved(null);
    try {
      await deleteAdminProduct(productId, persistedProduct.version, confirmOrderHistory);
      router.replace("/admin/products");
      router.refresh();
    } catch (caught) {
      if (!confirmOrderHistory && isProductOrderHistoryConflict(caught)) {
        setDeleteConfirmation("orderHistory");
        setDeleteError(null);
      } else {
        setDeleteError(productDeleteConflictMessage(caught) ?? adminErrorMessage(caught));
      }
      deletingRef.current = false;
      setDeleting(false);
    }
  }

  if (loading) return <AdminLoading label="상품 편집 정보를 불러오는 중입니다." />;
  if (!brands.length && error) return <p className="admin-alert admin-alert--error" role="alert">{error}</p>;

  return (
    <form className="admin-editor" onSubmit={submit} aria-busy={submitting || deleting}>
      {error ? <p className="admin-alert admin-alert--error" role="alert">{error}</p> : null}
      {saved ? <p className="admin-alert admin-alert--success" role="status">{saved}</p> : null}

      <section className="admin-panel admin-editor-section">
        <div className="admin-panel__heading">
          <div><h2>기본 정보</h2><p>고객 상품 목록과 상세에 표시되는 정보입니다.</p></div>
        </div>
        <div className="admin-form-grid">
          <label>
            상품명
            <input required maxLength={200} value={draft.name} onChange={(event) => setDraft((current) => ({ ...current, name: event.target.value }))} />
          </label>
          <label>
            URL 슬러그
            <input required maxLength={160} pattern="[a-z0-9]+(?:-[a-z0-9]+)*" placeholder="cozy-pet-bed" value={draft.slug} onChange={(event) => setDraft((current) => ({ ...current, slug: event.target.value.toLowerCase() }))} />
          </label>
          <label>
            브랜드
            <select required value={draft.brandId} onChange={(event) => setDraft((current) => ({ ...current, brandId: event.target.value }))}>
              {brands.map((brand) => <option key={brand.id} value={brand.id}>{brand.name}</option>)}
            </select>
          </label>
          <label>
            판매 상태
            <select value={draft.status} onChange={(event) => setDraft((current) => ({ ...current, status: event.target.value as ProductStatus }))}>
              <option value="DRAFT">초안</option>
              <option value="PUBLISHED">판매 중</option>
              <option value="HIDDEN">숨김</option>
              <option value="DISCONTINUED">판매 종료</option>
            </select>
          </label>
          <label className="admin-form-grid__wide">
            한 줄 소개
            <input maxLength={500} value={draft.summary} onChange={(event) => setDraft((current) => ({ ...current, summary: event.target.value }))} />
          </label>
          <label className="admin-form-grid__wide">
            상세 설명
            <textarea rows={7} value={draft.description} onChange={(event) => setDraft((current) => ({ ...current, description: event.target.value }))} />
          </label>
          <label className="admin-check admin-form-grid__wide">
            <input type="checkbox" checked={draft.featured} onChange={(event) => setDraft((current) => ({ ...current, featured: event.target.checked }))} />
            추천 상품으로 표시
          </label>
        </div>
      </section>

      <section className="admin-panel admin-editor-section">
        <div className="admin-panel__heading"><div><h2>분류</h2><p>복수 선택할 수 있습니다.</p></div></div>
        <div className="admin-taxonomy-grid">
          <fieldset>
            <legend>동물 유형</legend>
            {species.map((item) => (
              <label className="admin-check" key={item.id}>
                <input type="checkbox" checked={draft.speciesIds.includes(item.id)} onChange={() => toggleId("speciesIds", item.id)} />
                {item.name}
              </label>
            ))}
          </fieldset>
          <fieldset>
            <legend>카테고리</legend>
            {categories.map((item) => (
              <label className="admin-check" key={item.id}>
                <input type="checkbox" checked={draft.categoryIds.includes(item.id)} onChange={() => toggleId("categoryIds", item.id)} />
                {item.name}
              </label>
            ))}
          </fieldset>
        </div>
      </section>

      <section className="admin-panel admin-editor-section">
        <div className="admin-panel__heading">
          <div><h2>옵션 · 가격 · 재고</h2><p>옵션은 최소 1개가 필요합니다. 기존 옵션은 목록에서 비활성화할 수 있습니다.</p></div>
          <button className="admin-button" type="button" onClick={() => setDraft((current) => ({ ...current, variants: [...current.variants, blankVariant(current.variants.length + 1)] }))}>옵션 추가</button>
        </div>
        <div className="admin-editor-rows">
          {draft.variants.map((variant, index) => (
            <div className="admin-editor-row admin-editor-row--variant" key={variant.id ?? `new-${index}`}>
              <label>옵션명<input required maxLength={150} value={variant.optionLabel} onChange={(event) => updateVariant(index, { optionLabel: event.target.value })} /></label>
              <label>SKU<input required maxLength={100} value={variant.sku} onChange={(event) => updateVariant(index, { sku: event.target.value.toUpperCase() })} /></label>
              <label>가격<input required type="number" min={0} step={100} value={variant.price} onChange={(event) => updateVariant(index, { price: Math.max(0, Number(event.target.value) || 0) })} /></label>
              <label>재고<input required type="number" min={0} step={1} value={variant.stockQuantity} onChange={(event) => updateVariant(index, { stockQuantity: Math.max(0, Number(event.target.value) || 0) })} /></label>
              <label>상태<select value={variant.status} onChange={(event) => updateVariant(index, { status: event.target.value as VariantStatus })}><option value="ACTIVE">판매 가능</option><option value="INACTIVE">비활성</option></select></label>
              {!variant.id ? <button className="admin-text-button" type="button" disabled={draft.variants.length === 1} onClick={() => setDraft((current) => ({ ...current, variants: current.variants.filter((_, itemIndex) => itemIndex !== index) }))}>삭제</button> : <span className="admin-row-note">기존 옵션</span>}
            </div>
          ))}
        </div>
      </section>

      <section className="admin-panel admin-editor-section">
        <div className="admin-panel__heading">
          <div><h2>상품 이미지</h2><p>사진을 선택하면 안전성 검사를 거쳐 저장소에 업로드합니다.</p></div>
        </div>
        <ProductImageUploader
          images={draft.images}
          productName={draft.name}
          disabled={submitting}
          onChange={updateImages}
          onUploadStateChange={setImageUploading}
        />
      </section>

      {productId && persistedProduct ? (
        <section className="admin-panel admin-danger-zone" aria-labelledby="product-delete-heading">
          <div className="admin-danger-zone__heading">
            <div>
              <h2 id="product-delete-heading">상품 삭제</h2>
              <p>
                판매 종료는 관리자 데이터를 유지하지만, 삭제는 DB의 현재 판매용 상품·옵션·분류 및 이미지 연결 정보를 제거합니다.
                업로드한 원본 이미지 파일은 삭제하지 않습니다.
              </p>
            </div>
            {deleteConfirmation === "none" ? (
              <button
                className="admin-button admin-button--danger"
                type="button"
                disabled={submitting || imageUploading || deleting || persistedProduct.status === "PUBLISHED"}
                onClick={() => {
                  setDeleteError(null);
                  setDeleteConfirmation("initial");
                }}
              >
                상품 삭제
              </button>
            ) : null}
          </div>

          {persistedProduct.status === "PUBLISHED" ? (
            <p className="admin-danger-zone__notice">
              판매 중인 상품은 바로 삭제할 수 없습니다. 위 판매 상태를 숨김 또는 판매 종료로 바꾸고 변경사항을 먼저 저장해 주세요.
            </p>
          ) : (
            <p className="admin-danger-zone__notice">
              홈 콘텐츠의 상품 링크에 사용 중인 상품은 링크 대상을 먼저 변경해야 삭제할 수 있습니다.
            </p>
          )}

          {deleteError ? <p className="admin-alert admin-alert--error" role="alert">{deleteError}</p> : null}

          {deleteConfirmation === "initial" ? (
            <div
              className="admin-danger-zone__confirmation"
              role="group"
              aria-live="polite"
              aria-labelledby="product-delete-confirm-title"
              aria-describedby="product-delete-confirm-description"
            >
              <div>
                <strong id="product-delete-confirm-title">‘{persistedProduct.name}’ 상품을 영구 삭제할까요?</strong>
                <p id="product-delete-confirm-description">이 작업은 되돌릴 수 없습니다. 표시된 상품명이 맞는지 확인해 주세요.</p>
              </div>
              <div className="admin-danger-zone__actions">
                <button
                  className="admin-button"
                  type="button"
                  disabled={deleting}
                  onClick={() => {
                    setDeleteConfirmation("none");
                    setDeleteError(null);
                  }}
                >
                  취소
                </button>
                <button
                  className="admin-button admin-button--danger-solid"
                  type="button"
                  disabled={deleting}
                  aria-label={`${persistedProduct.name} 상품 영구 삭제`}
                  onClick={() => void removeProduct()}
                >
                  {deleting ? "삭제 중…" : "확인 후 영구 삭제"}
                </button>
              </div>
            </div>
          ) : null}

          {deleteConfirmation === "orderHistory" ? (
            <div
              className="admin-danger-zone__confirmation admin-danger-zone__confirmation--critical"
              role="group"
              aria-live="assertive"
              aria-labelledby="product-order-history-confirm-title"
              aria-describedby="product-order-history-confirm-description"
            >
              <div>
                <strong id="product-order-history-confirm-title">‘{persistedProduct.name}’ 상품에 주문 이력이 있습니다.</strong>
                <p id="product-order-history-confirm-description">
                  삭제해도 기존 주문내역과 주문 당시의 상품명·옵션·가격·이미지 스냅샷은 그대로 보존됩니다.
                  현재 판매 상품 연결만 제거되며 이 상품은 더 이상 수정하거나 다시 판매할 수 없습니다. 그래도 삭제할까요?
                </p>
              </div>
              <div className="admin-danger-zone__actions">
                <button
                  className="admin-button"
                  type="button"
                  disabled={deleting}
                  onClick={() => {
                    setDeleteConfirmation("none");
                    setDeleteError(null);
                  }}
                >
                  삭제하지 않기
                </button>
                <button
                  className="admin-button admin-button--danger-solid"
                  type="button"
                  disabled={deleting}
                  aria-label={`${persistedProduct.name} 주문 이력을 보존하고 상품 영구 삭제`}
                  onClick={() => void removeProduct(true)}
                >
                  {deleting ? "삭제 중…" : "주문 이력 보존 후 삭제"}
                </button>
              </div>
            </div>
          ) : null}
        </section>
      ) : null}

      <div className="admin-editor-actions">
        <Link className="admin-button" href="/admin/products">취소</Link>
        <button className="admin-button admin-button--primary" type="submit" disabled={submitting || imageUploading || deleting}>
          {imageUploading ? "이미지 업로드 중…" : submitting ? "저장 중…" : productId ? "변경사항 저장" : "상품 등록"}
        </button>
      </div>
    </form>
  );
}
