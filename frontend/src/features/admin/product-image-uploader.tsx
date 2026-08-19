"use client";

import { useEffect, useRef, useState, type ChangeEvent } from "react";
import { resolveApiAssetUrl } from "@/lib/api/client";
import { uploadAdminProductImage } from "./api";
import { adminErrorMessage } from "./error";
import {
  MAX_PRODUCT_IMAGES,
  PRODUCT_IMAGE_ACCEPT,
  completedUploadsInSelectionOrder,
  imageAltFromFileName,
  mediaPathForStorageKey,
  moveProductImage,
  normalizeProductImages,
  selectProductImageFiles,
} from "./product-image-model";
import type { ProductImageInput } from "./types";

type UploadStatus = "queued" | "uploading" | "processing" | "done" | "error";

type PendingUpload = {
  clientId: string;
  file: File;
  previewUrl: string;
  alt: string;
  status: UploadStatus;
  progress: number;
  error: string | null;
};

type ProductImageUploaderProps = {
  images: ProductImageInput[];
  productName: string;
  disabled?: boolean;
  onChange: (updater: (current: ProductImageInput[]) => ProductImageInput[]) => void;
  onUploadStateChange: (uploading: boolean) => void;
};

const statusLabels: Record<UploadStatus, string> = {
  queued: "업로드 대기",
  uploading: "업로드 중",
  processing: "이미지 처리 중",
  done: "업로드 완료",
  error: "업로드 실패",
};

function createClientId() {
  return globalThis.crypto?.randomUUID?.() ?? `upload-${Date.now()}-${Math.random()}`;
}

function ImagePreview({ src, alt }: { src: string | null; alt: string }) {
  const [failedSrc, setFailedSrc] = useState<string | null>(null);
  if (!src || failedSrc === src) {
    return <span className="admin-image-card__placeholder" aria-hidden="true">IMG</span>;
  }
  return (
    // Blob previews and the deployment-selected media origin cannot be statically allow-listed.
    // eslint-disable-next-line @next/next/no-img-element
    <img src={src} alt={alt} onError={() => setFailedSrc(src)} />
  );
}

export function ProductImageUploader({
  images,
  productName,
  disabled = false,
  onChange,
  onUploadStateChange,
}: ProductImageUploaderProps) {
  const [uploads, setUploads] = useState<PendingUpload[]>([]);
  const [selectionErrors, setSelectionErrors] = useState<string[]>([]);
  const [uploadedPreviews, setUploadedPreviews] = useState<Record<string, string>>({});
  const [recentlyUploaded, setRecentlyUploaded] = useState<Set<string>>(() => new Set());
  const [uploading, setUploading] = useState(false);
  const [manualKey, setManualKey] = useState("");
  const [manualAlt, setManualAlt] = useState("");
  const ownedUrls = useRef(new Set<string>());
  const controllers = useRef(new Map<string, AbortController>());
  const mounted = useRef(true);
  const busy = useRef(false);

  useEffect(() => {
    onUploadStateChange(uploading);
  }, [onUploadStateChange, uploading]);

  useEffect(() => {
    mounted.current = true;
    const activeControllers = controllers.current;
    const activeUrls = ownedUrls.current;
    return () => {
      mounted.current = false;
      activeControllers.forEach((controller) => controller.abort());
      activeUrls.forEach((url) => URL.revokeObjectURL(url));
      activeUrls.clear();
    };
  }, []);

  function updateUpload(clientId: string, change: Partial<PendingUpload>) {
    if (!mounted.current) return;
    setUploads((current) => current.map((item) => (
      item.clientId === clientId ? { ...item, ...change } : item
    )));
  }

  async function uploadOne(item: PendingUpload) {
    const controller = new AbortController();
    controllers.current.set(item.clientId, controller);
    updateUpload(item.clientId, { status: "uploading", progress: 0, error: null });
    try {
      const result = await uploadAdminProductImage(item.file, {
        signal: controller.signal,
        onProgress: ({ percent }) => {
          updateUpload(item.clientId, {
            status: percent >= 100 ? "processing" : "uploading",
            progress: percent,
          });
        },
      });
      if (!mounted.current) return;
      updateUpload(item.clientId, { status: "done", progress: 100 });
      return result;
    } catch (caught) {
      if (!mounted.current || controller.signal.aborted) return undefined;
      updateUpload(item.clientId, {
        status: "error",
        error: adminErrorMessage(caught),
      });
      return undefined;
    } finally {
      controllers.current.delete(item.clientId);
    }
  }

  async function runUploads(items: PendingUpload[]) {
    if (!items.length || busy.current) return;
    busy.current = true;
    setUploading(true);
    let nextIndex = 0;
    const results = new Map<string, Awaited<ReturnType<typeof uploadAdminProductImage>>>();
    const workerCount = Math.min(2, items.length);
    try {
      await Promise.all(Array.from({ length: workerCount }, async () => {
        while (nextIndex < items.length) {
          const item = items[nextIndex];
          nextIndex += 1;
          const result = await uploadOne(item);
          if (result) results.set(item.clientId, result);
        }
      }));
      if (mounted.current && results.size) {
        const completed = completedUploadsInSelectionOrder(items, results);
        setUploadedPreviews((current) => {
          const next = { ...current };
          completed.forEach(({ item, result }) => {
            next[result.storageKey] = item.previewUrl;
          });
          return next;
        });
        setRecentlyUploaded((current) => {
          const next = new Set(current);
          completed.forEach(({ result }) => next.add(result.storageKey));
          return next;
        });
        setUploads((current) => current.filter((candidate) => !results.has(candidate.clientId)));
        onChange((current) => normalizeProductImages([
          ...current,
          ...completed.map(({ item, result }, index) => ({
            storageKey: result.storageKey,
            alt: item.alt.trim() || imageAltFromFileName(item.file.name) || productName.trim(),
            sortOrder: current.length + index + 1,
          })),
        ]));
      }
    } finally {
      busy.current = false;
      if (mounted.current) setUploading(false);
    }
  }

  function handleFileSelection(event: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.currentTarget.files ?? []);
    event.currentTarget.value = "";
    const selection = selectProductImageFiles(files, images.length + uploads.length);
    setSelectionErrors(selection.errors);
    const items = selection.accepted.map((file) => {
      const previewUrl = URL.createObjectURL(file);
      ownedUrls.current.add(previewUrl);
      return {
        clientId: createClientId(),
        file,
        previewUrl,
        alt: imageAltFromFileName(file.name) || productName.trim(),
        status: "queued" as const,
        progress: 0,
        error: null,
      };
    });
    if (!items.length) return;
    setUploads((current) => [...current, ...items]);
    void runUploads(items);
  }

  function retryUpload(item: PendingUpload) {
    setSelectionErrors([]);
    updateUpload(item.clientId, { status: "queued", progress: 0, error: null });
    void runUploads([{ ...item, status: "queued", progress: 0, error: null }]);
  }

  function removePending(item: PendingUpload) {
    if (item.status === "uploading" || item.status === "processing" || item.status === "done") return;
    URL.revokeObjectURL(item.previewUrl);
    ownedUrls.current.delete(item.previewUrl);
    setUploads((current) => current.filter((candidate) => candidate.clientId !== item.clientId));
  }

  function unlinkImage(index: number, storageKey: string) {
    const localUrl = uploadedPreviews[storageKey];
    if (localUrl) {
      URL.revokeObjectURL(localUrl);
      ownedUrls.current.delete(localUrl);
      setUploadedPreviews((current) => {
        const next = { ...current };
        delete next[storageKey];
        return next;
      });
    }
    setRecentlyUploaded((current) => {
      const next = new Set(current);
      next.delete(storageKey);
      return next;
    });
    onChange((current) => normalizeProductImages(current.filter((_, imageIndex) => imageIndex !== index)));
  }

  function addExistingStorageKey() {
    const storageKey = manualKey.trim();
    const alt = manualAlt.trim();
    if (images.length + uploads.length >= MAX_PRODUCT_IMAGES) {
      setSelectionErrors([`상품 이미지는 최대 ${MAX_PRODUCT_IMAGES}장까지 등록할 수 있습니다.`]);
      return;
    }
    if (!mediaPathForStorageKey(storageKey)) {
      setSelectionErrors(["기존 이미지의 저장소 키 형식이 올바르지 않습니다."]);
      return;
    }
    if (!alt) {
      setSelectionErrors(["기존 이미지의 대체 텍스트를 입력해 주세요."]);
      return;
    }
    if (images.some((image) => image.storageKey.trim() === storageKey)) {
      setSelectionErrors(["이미 연결된 저장소 이미지입니다."]);
      return;
    }

    onChange((current) => normalizeProductImages([
      ...current,
      { storageKey, alt, sortOrder: current.length + 1 },
    ]));
    setManualKey("");
    setManualAlt("");
    setSelectionErrors([]);
  }

  const occupiedCount = images.length + uploads.length;
  const pickerDisabled = disabled || uploading || occupiedCount >= MAX_PRODUCT_IMAGES;

  return (
    <div className="admin-image-uploader">
      <div className="admin-image-picker">
        <div>
          <strong>사진 선택</strong>
          <p>PC에서는 파일 탐색기, 휴대폰에서는 갤러리가 열립니다.</p>
          <span>JPEG · PNG · WebP / 장당 8MB 이하 / 최대 {MAX_PRODUCT_IMAGES}장</span>
        </div>
        <label className={`admin-button admin-image-picker__button${pickerDisabled ? " is-disabled" : ""}`}>
          이미지 선택
          <input
            className="admin-sr-only"
            type="file"
            accept={PRODUCT_IMAGE_ACCEPT}
            multiple
            disabled={pickerDisabled}
            onChange={handleFileSelection}
          />
        </label>
        <span className="admin-image-picker__count" aria-live="polite">
          {occupiedCount} / {MAX_PRODUCT_IMAGES}장
        </span>
      </div>

      {selectionErrors.length ? (
        <div className="admin-alert admin-alert--error" role="alert">
          <ul>
            {selectionErrors.map((message, index) => <li key={`${message}-${index}`}>{message}</li>)}
          </ul>
        </div>
      ) : null}

      {images.length || uploads.length ? (
        <div className="admin-image-list" aria-label="상품 이미지 목록">
          {images.map((image, index) => {
            const remotePath = mediaPathForStorageKey(image.storageKey);
            const previewUrl = uploadedPreviews[image.storageKey]
              ?? resolveApiAssetUrl(remotePath);
            return (
              <article className="admin-image-card" key={image.id ?? image.storageKey}>
                <div className="admin-image-card__preview">
                  <ImagePreview src={previewUrl} alt={image.alt} />
                </div>
                <div className="admin-image-card__body">
                  <div className="admin-image-card__status-row">
                    <strong>{index === 0 ? "대표 이미지" : `${index + 1}번째 이미지`}</strong>
                    <span className="admin-image-status admin-image-status--done">
                      {recentlyUploaded.has(image.storageKey) ? "업로드 완료" : "등록됨"}
                    </span>
                  </div>
                  <label>
                    대체 텍스트
                    <input
                      required
                      maxLength={300}
                      value={image.alt}
                      disabled={disabled}
                      onChange={(event) => onChange((current) => current.map((candidate, imageIndex) => (
                        imageIndex === index ? { ...candidate, alt: event.target.value } : candidate
                      )))}
                    />
                  </label>
                  <code title={image.storageKey}>{image.storageKey}</code>
                  <div className="admin-image-card__actions">
                    <button
                      className="admin-text-button"
                      type="button"
                      disabled={disabled || index === 0}
                      aria-label={`${index + 1}번째 이미지를 앞으로 이동`}
                      onClick={() => onChange((current) => moveProductImage(current, index, index - 1))}
                    >앞으로</button>
                    <button
                      className="admin-text-button"
                      type="button"
                      disabled={disabled || index === images.length - 1}
                      aria-label={`${index + 1}번째 이미지를 뒤로 이동`}
                      onClick={() => onChange((current) => moveProductImage(current, index, index + 1))}
                    >뒤로</button>
                    <button
                      className="admin-text-button admin-text-button--danger"
                      type="button"
                      disabled={disabled}
                      onClick={() => unlinkImage(index, image.storageKey)}
                    >상품에서 제외</button>
                  </div>
                </div>
              </article>
            );
          })}

          {uploads.map((item) => (
            <article className="admin-image-card" key={item.clientId}>
              <div className="admin-image-card__preview">
                <ImagePreview src={item.previewUrl} alt="업로드할 이미지 미리보기" />
              </div>
              <div className="admin-image-card__body">
                <div className="admin-image-card__status-row">
                  <strong title={item.file.name}>{item.file.name}</strong>
                  <span className={`admin-image-status admin-image-status--${item.status}`}>
                    {statusLabels[item.status]}
                    {item.status === "uploading" && item.progress > 0 ? ` ${item.progress}%` : ""}
                  </span>
                </div>
                <label>
                  대체 텍스트
                  <input
                    maxLength={300}
                    value={item.alt}
                    disabled={item.status !== "error"}
                    onChange={(event) => updateUpload(item.clientId, { alt: event.target.value })}
                  />
                </label>
                {item.status === "uploading" ? (
                  <progress max={100} value={item.progress || undefined} aria-label={`${item.file.name} 업로드 진행률`} />
                ) : null}
                {item.error ? <p className="admin-image-card__error" role="alert">{item.error}</p> : null}
                {item.status === "error" ? (
                  <div className="admin-image-card__actions">
                    <button className="admin-text-button" type="button" disabled={uploading} onClick={() => retryUpload(item)}>다시 시도</button>
                    <button className="admin-text-button admin-text-button--danger" type="button" disabled={uploading} onClick={() => removePending(item)}>제거</button>
                  </div>
                ) : null}
              </div>
            </article>
          ))}
        </div>
      ) : (
        <p className="admin-empty admin-empty--compact">등록된 이미지가 없습니다.</p>
      )}

      <p className="admin-image-uploader__note">목록의 첫 번째 이미지가 고객 화면의 대표 이미지로 표시됩니다.</p>

      <details className="admin-image-key-fallback">
        <summary>기존 저장소 이미지 연결</summary>
        <p>이미 서버에 저장된 이미지가 있을 때만 저장소 키를 직접 입력합니다.</p>
        <div>
          <label>
            저장소 키
            <input
              maxLength={500}
              placeholder="demo/products/pet-bed.webp"
              value={manualKey}
              disabled={disabled}
              onChange={(event) => setManualKey(event.target.value)}
            />
          </label>
          <label>
            대체 텍스트
            <input
              maxLength={300}
              value={manualAlt}
              disabled={disabled}
              onChange={(event) => setManualAlt(event.target.value)}
            />
          </label>
          <button className="admin-button" type="button" disabled={disabled} onClick={addExistingStorageKey}>연결</button>
        </div>
      </details>
    </div>
  );
}
