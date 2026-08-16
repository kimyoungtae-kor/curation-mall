import Link from "next/link";

export type FooterLink = { href: string; label: string };

const defaultLinks: FooterLink[] = [
  { href: "/admin/login", label: "관리자 로그인" },
  { href: "/help/shipping-returns", label: "배송·교환 안내" },
  { href: "/help/terms", label: "이용약관" },
  { href: "/help/privacy", label: "개인정보처리방침" },
];

export function SiteFooter({ links = defaultLinks }: { links?: FooterLink[] }) {
  return (
    <footer className="site-footer">
      <p>© 2026 Pet Curation. Prototype.</p>
      <nav aria-label="정책 메뉴">
        {links.map((link) => (
          <Link key={link.href} href={link.href}>
            {link.label}
          </Link>
        ))}
      </nav>
    </footer>
  );
}
