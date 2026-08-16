import Link from "next/link";
import type { ReactNode } from "react";
import type { ContentLink } from "./types";

type ContentLinkViewProps = {
  link: ContentLink;
  className?: string;
  children: ReactNode;
  ariaLabel?: string;
};

export function ContentLinkView({
  link,
  className,
  children,
  ariaLabel,
}: ContentLinkViewProps) {
  if (link.external) {
    return (
      <a
        className={className}
        href={link.href}
        target="_blank"
        rel="noreferrer"
        aria-label={ariaLabel}
      >
        {children}
      </a>
    );
  }

  return (
    <Link className={className} href={link.href} aria-label={ariaLabel}>
      {children}
    </Link>
  );
}
