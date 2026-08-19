import type { ProductImageInput } from "./types";

export const MAX_PRODUCT_IMAGES = 8;
export const MAX_PRODUCT_IMAGE_BYTES = 8 * 1024 * 1024;
export const PRODUCT_IMAGE_ACCEPT = "image/jpeg,image/png,image/webp";

const ALLOWED_PRODUCT_IMAGE_TYPES = new Set(PRODUCT_IMAGE_ACCEPT.split(","));

export type ImageFileCandidate = {
  name: string;
  size: number;
  type: string;
};

export type ImageSelection<T extends ImageFileCandidate> = {
  accepted: T[];
  errors: string[];
};

export type UploadQueueItem = {
  clientId: string;
};

export function selectProductImageFiles<T extends ImageFileCandidate>(
  files: readonly T[],
  occupiedCount: number,
): ImageSelection<T> {
  const errors: string[] = [];
  const validFiles = files.filter((file) => {
    if (!ALLOWED_PRODUCT_IMAGE_TYPES.has(file.type.toLowerCase())) {
      errors.push(`${file.name}: JPEG, PNG, WebP 파일만 올릴 수 있습니다.`);
      return false;
    }
    if (file.size > MAX_PRODUCT_IMAGE_BYTES) {
      errors.push(`${file.name}: 파일 크기는 8MB 이하여야 합니다.`);
      return false;
    }
    if (file.size <= 0) {
      errors.push(`${file.name}: 비어 있는 파일은 올릴 수 없습니다.`);
      return false;
    }
    return true;
  });

  const remaining = Math.max(0, MAX_PRODUCT_IMAGES - occupiedCount);
  const accepted = validFiles.slice(0, remaining);
  if (validFiles.length > remaining) {
    errors.push(`상품 이미지는 최대 ${MAX_PRODUCT_IMAGES}장까지 등록할 수 있습니다.`);
  }

  return { accepted, errors };
}

export function imageAltFromFileName(fileName: string) {
  return fileName.replace(/\.[^.]+$/, "").replace(/[-_]+/g, " ").trim();
}

export function completedUploadsInSelectionOrder<TItem extends UploadQueueItem, TResult>(
  selectedItems: readonly TItem[],
  resultsByClientId: ReadonlyMap<string, TResult>,
) {
  return selectedItems.flatMap((item) => {
    const result = resultsByClientId.get(item.clientId);
    return result === undefined ? [] : [{ item, result }];
  });
}

export function normalizeProductImages(images: readonly ProductImageInput[]) {
  return images.map((image, index) => ({
    ...image,
    storageKey: image.storageKey.trim(),
    alt: image.alt.trim(),
    sortOrder: index + 1,
  }));
}

export function moveProductImage(
  images: readonly ProductImageInput[],
  fromIndex: number,
  toIndex: number,
) {
  if (
    fromIndex < 0
    || fromIndex >= images.length
    || toIndex < 0
    || toIndex >= images.length
    || fromIndex === toIndex
  ) {
    return normalizeProductImages(images);
  }

  const moved = [...images];
  const [item] = moved.splice(fromIndex, 1);
  moved.splice(toIndex, 0, item);
  return normalizeProductImages(moved);
}

export function mediaPathForStorageKey(storageKey: string) {
  const normalized = storageKey.trim().replaceAll("\\", "/");
  const segments = normalized.split("/");
  if (
    !normalized
    || normalized.startsWith("/")
    || segments.some((segment) => !segment || segment === "." || segment === "..")
  ) {
    return null;
  }
  return `/media/${segments.map(encodeURIComponent).join("/")}`;
}
