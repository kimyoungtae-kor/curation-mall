"use client";

import Link from "next/link";
import { useState } from "react";
import { resolveApiAssetUrl } from "@/lib/api/client";
import type { ProductSummary } from "./types";

const wonFormatter = new Intl.NumberFormat("ko-KR", {
  style: "currency",
  currency: "KRW",
  maximumFractionDigits: 0,
});

export function ProductCard({ product }: { product: ProductSummary }) {
  const [imageFailed, setImageFailed] = useState(false);
  const imageUrl = resolveApiAssetUrl(product.thumbnailUrl);
  const showImage = Boolean(imageUrl) && !imageFailed;

  return (
    <article className="product-card" data-product-slug={product.slug}>
      <Link
        className="product-card__link"
        href={`/products/${product.slug}`}
        aria-label={`${product.name} 상세 보기`}
      >
        <div className="product-card__media">
          {showImage ? (
            // The storage host is selected at deployment time, so a native image
            // is used until that host can be safely allow-listed in Next config.
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={imageUrl!}
              alt={product.thumbnailAlt}
              loading="lazy"
              onError={() => setImageFailed(true)}
            />
          ) : (
            <span className="product-card__placeholder" aria-hidden="true">
              {product.name.slice(0, 1)}
            </span>
          )}
          {product.soldOut ? (
            <span className="product-card__status">품절</span>
          ) : null}
        </div>
        <div className="product-card__body">
          <p className="product-card__brand">{product.brandName}</p>
          <h3 className="product-card__name">{product.name}</h3>
          {product.summary ? (
            <p className="product-card__summary">{product.summary}</p>
          ) : null}
          <p className="product-card__price">
            <span>{wonFormatter.format(product.price)}</span>
            {product.originalPrice ? (
              <del>{wonFormatter.format(product.originalPrice)}</del>
            ) : null}
          </p>
        </div>
      </Link>
    </article>
  );
}
