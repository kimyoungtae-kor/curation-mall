"use client";

import Link from "next/link";
import { useAuth } from "./auth-provider";

export function AccountPage() {
  const { authenticated, user, cartCount, wishlistCount, loading } = useAuth();
  if (loading) return <div className="commerce-state" aria-busy="true">회원 정보를 확인하는 중…</div>;
  if (!authenticated || !user) return <div className="commerce-state"><h2>로그인이 필요해요</h2><Link className="button button--primary" href="/login?next=/account">로그인</Link></div>;

  return (
    <div className="account-overview">
      <section className="account-profile">
        <p>반갑습니다</p><h2>{user.name}님</h2>
        <dl><div><dt>이메일</dt><dd>{user.email}</dd></div><div><dt>휴대전화</dt><dd>{user.phone}</dd></div></dl>
      </section>
      <nav className="account-shortcuts" aria-label="내 정보 바로가기">
        <Link href="/cart"><strong>{cartCount}</strong><span>장바구니</span></Link>
        <Link href="/wishlist"><strong>{wishlistCount}</strong><span>찜 목록</span></Link>
        <Link href="/mypage/orders"><strong>→</strong><span>주문 내역</span></Link>
      </nav>
    </div>
  );
}
