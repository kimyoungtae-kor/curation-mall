"use client";

import { useState } from "react";
import { resolveApiAssetUrl } from "@/lib/api/client";

type SafeMediaProps = {
  src: string | null;
  alt: string;
  className?: string;
  fallbackLabel?: string;
  eager?: boolean;
};

export function SafeMedia({
  src,
  alt,
  className,
  fallbackLabel,
  eager = false,
}: SafeMediaProps) {
  const resolvedSrc = resolveApiAssetUrl(src);
  const [failedSrc, setFailedSrc] = useState<string | null>(null);
  const failed = resolvedSrc === failedSrc;

  if (!resolvedSrc || failed) {
    return (
      <span className={className} data-media-fallback="true" aria-hidden="true">
        {fallbackLabel?.slice(0, 1) ?? "P"}
      </span>
    );
  }

  return (
    // The API origin is deployment-configurable, so it cannot be allow-listed at build time.
    // eslint-disable-next-line @next/next/no-img-element
    <img
      className={className}
      src={resolvedSrc}
      alt={alt}
      loading={eager ? "eager" : "lazy"}
      fetchPriority={eager ? "high" : "auto"}
      onError={() => setFailedSrc(resolvedSrc)}
    />
  );
}
