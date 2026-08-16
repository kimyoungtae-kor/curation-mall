"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { useAuth } from "@/features/account/auth-provider";

function SearchIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      <circle cx="11" cy="11" r="6.5" />
      <path d="m16 16 4 4" />
    </svg>
  );
}

function PersonIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      <circle cx="12" cy="8" r="3.5" />
      <path d="M5.5 20a6.5 6.5 0 0 1 13 0" />
    </svg>
  );
}

function CartIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      <path d="M3.5 4.5h2l1.7 9.1a2 2 0 0 0 2 1.6h7.9a2 2 0 0 0 1.9-1.5l1.2-5.4H6.3" />
      <circle cx="9.5" cy="19" r="1" />
      <circle cx="17.5" cy="19" r="1" />
    </svg>
  );
}

export function SiteHeader() {
  const { authenticated, user, cartCount, loading, logout } = useAuth();
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);
  const isCustomer = user?.roles.includes("CUSTOMER") ?? false;
  const isAdmin = user?.roles.includes("ADMIN") ?? false;

  useEffect(() => {
    function closeFromOutside(event: PointerEvent) {
      if (!menuRef.current?.contains(event.target as Node)) setMenuOpen(false);
    }
    function closeFromEscape(event: KeyboardEvent) {
      if (event.key === "Escape") setMenuOpen(false);
    }
    document.addEventListener("pointerdown", closeFromOutside);
    document.addEventListener("keydown", closeFromEscape);
    return () => {
      document.removeEventListener("pointerdown", closeFromOutside);
      document.removeEventListener("keydown", closeFromEscape);
    };
  }, []);

  return (
    <header className="site-header">
      <Link className="site-header__brand" href="/" aria-label="Pet Curation 홈">
        <span className="site-header__brand-mark" aria-hidden="true">
          P
        </span>
        PET CURATION
      </Link>

      <nav className="site-header__nav" aria-label="주요 메뉴">
        <Link href="/products?species=dog">강아지</Link>
        <Link href="/products?species=cat">고양이</Link>
        <Link href="/products">전체 상품</Link>
        <Link href="/collections">기획전</Link>
        <Link href="/#pet-room">펫룸 이야기</Link>
      </nav>

      <div className="site-header__actions" aria-label="헤더 메뉴">
        <Link className="icon-button" href="/products" aria-label="상품 검색">
          <SearchIcon />
        </Link>
        {!loading && !authenticated ? (
          <Link className="site-header__login" href="/login">
            로그인
          </Link>
        ) : null}
        {!loading && authenticated ? (
          <>
            {isCustomer ? (
              <Link className="icon-button icon-button--count" href="/cart" aria-label={`장바구니 ${cartCount}개`}>
                <CartIcon />
                {cartCount > 0 ? <span>{cartCount > 99 ? "99+" : cartCount}</span> : null}
              </Link>
            ) : null}
            <div className="account-menu" ref={menuRef}>
              <button
                className="icon-button"
                type="button"
                aria-label="내 정보 메뉴"
                aria-expanded={menuOpen}
                aria-haspopup="menu"
                onClick={() => setMenuOpen((open) => !open)}
              >
                <PersonIcon />
              </button>
              {menuOpen ? (
                <div className="account-menu__panel" role="menu">
                  <Link href={isCustomer ? "/mypage" : "/account"} role="menuitem" onClick={() => setMenuOpen(false)}>내정보</Link>
                  {isCustomer ? (
                    <>
                      <Link href="/wishlist" role="menuitem" onClick={() => setMenuOpen(false)}>찜 목록</Link>
                      <Link href="/mypage/orders" role="menuitem" onClick={() => setMenuOpen(false)}>주문 내역</Link>
                    </>
                  ) : null}
                  {isAdmin ? (
                    <Link href="/admin" role="menuitem" onClick={() => setMenuOpen(false)}>관리자 페이지</Link>
                  ) : null}
                  <button
                    type="button"
                    role="menuitem"
                    onClick={() => {
                      void logout()
                        .catch(() => undefined)
                        .finally(() => setMenuOpen(false));
                    }}
                  >
                    로그아웃
                  </button>
                </div>
              ) : null}
            </div>
          </>
        ) : null}
      </div>

      <nav className="site-header__mobile-categories" aria-label="모바일 상품 카테고리">
        <Link href="/products?species=dog">강아지용품</Link>
        <Link href="/products?species=cat">고양이용품</Link>
        <Link href="/products">전체상품</Link>
        <Link href="/collections">기획전</Link>
      </nav>
    </header>
  );
}
