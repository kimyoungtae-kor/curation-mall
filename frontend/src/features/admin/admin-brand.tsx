import Image from "next/image";
import Link from "next/link";

type AdminBrandProps = {
  login?: boolean;
};

export function AdminBrand({ login = false }: AdminBrandProps) {
  return (
    <Link
      className={`admin-brand${login ? " admin-brand--login" : ""}`}
      href={login ? "/" : "/admin"}
      aria-label={login ? "Zabre 쇼핑몰 홈" : "Zabre 관리자 대시보드"}
    >
      <Image
        className="admin-brand__logo"
        src="/brand/zabre-logo.png"
        alt=""
        width={250}
        height={150}
        priority
      />
      <small>ADMIN CONSOLE</small>
    </Link>
  );
}
