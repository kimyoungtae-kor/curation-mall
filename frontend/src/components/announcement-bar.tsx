import Link from "next/link";

type AnnouncementBarProps = {
  text?: string;
  href?: string;
  external?: boolean;
};

export function AnnouncementBar({
  text = "반려생활을 위한 첫 번째 큐레이션을 준비하고 있어요.",
  href,
  external = false,
}: AnnouncementBarProps) {
  const content = href ? (
    external ? (
      <a href={href} target="_blank" rel="noreferrer">
        {text}
        <span aria-hidden="true"> ↗</span>
      </a>
    ) : (
      <Link href={href}>{text}</Link>
    )
  ) : (
    text
  );

  return (
    <div className="announcement" role="status">
      {content}
    </div>
  );
}
