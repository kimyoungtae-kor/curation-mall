"use client";

import { useState } from "react";
import { resolveApiAssetUrl } from "@/lib/api/client";
import type { ProductImageDto } from "../types";

export function ProductGallery({
  images,
  productName,
}: {
  images: ProductImageDto[];
  productName: string;
}) {
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [failedImages, setFailedImages] = useState<Record<number, boolean>>({});
  const selectedImage = images[selectedIndex];
  const selectedUrl = resolveApiAssetUrl(selectedImage?.url ?? null);
  const showSelectedImage = Boolean(selectedUrl) && !failedImages[selectedIndex];

  function markFailed(index: number) {
    setFailedImages((current) => ({ ...current, [index]: true }));
  }

  return (
    <section className="product-gallery" aria-label={`${productName} 이미지 갤러리`}>
      <div className="product-gallery__main">
        {showSelectedImage ? (
          // Dynamic storage hosts are resolved from the configured API origin.
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={selectedUrl!}
            alt={selectedImage.alt || `${productName} 상품 이미지`}
            onError={() => markFailed(selectedIndex)}
          />
        ) : (
          <div className="product-gallery__placeholder" role="img" aria-label="이미지 준비 중">
            <strong aria-hidden="true">{productName.slice(0, 1)}</strong>
            <span>이미지를 준비하고 있어요</span>
          </div>
        )}
      </div>

      {images.length > 1 ? (
        <div className="product-gallery__thumbs" role="group" aria-label="다른 이미지 선택">
          {images.map((image, index) => {
            const thumbnailUrl = resolveApiAssetUrl(image.url);
            const showThumbnail = Boolean(thumbnailUrl) && !failedImages[index];
            return (
              <button
                className="product-gallery__thumb"
                data-selected={selectedIndex === index}
                type="button"
                key={`${image.url}-${index}`}
                onClick={() => setSelectedIndex(index)}
                aria-label={`${index + 1}번째 이미지 보기`}
                aria-pressed={selectedIndex === index}
              >
                {showThumbnail ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img
                    src={thumbnailUrl!}
                    alt=""
                    onError={() => markFailed(index)}
                  />
                ) : (
                  <span aria-hidden="true">{index + 1}</span>
                )}
              </button>
            );
          })}
        </div>
      ) : null}
    </section>
  );
}
