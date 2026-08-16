import Link from "next/link";
import { SafeMedia } from "@/components/safe-media";
import type { CollectionSummary } from "./types";

export function CollectionCard({ collection }: { collection: CollectionSummary }) {
  return (
    <article className="collection-card">
      <Link
        className="collection-card__link"
        href={`/collections/${collection.slug}`}
        aria-label={`${collection.title} 기획전 보기`}
      >
        <div className="collection-card__media">
          <SafeMedia
            src={collection.image?.url ?? null}
            alt={collection.image?.alt ?? collection.title}
            fallbackLabel={collection.title}
            className="collection-card__image"
          />
        </div>
        <div className="collection-card__body">
          <p>CURATED EDIT</p>
          <h3>{collection.title}</h3>
          {collection.description ? <span>{collection.description}</span> : null}
        </div>
      </Link>
    </article>
  );
}
