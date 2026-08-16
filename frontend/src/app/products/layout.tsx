import type { ReactNode } from "react";
import { AnnouncementBar } from "@/components/announcement-bar";
import { SiteFooter } from "@/components/site-footer";
import { SiteHeader } from "@/components/site-header";

export default function ProductsLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <a className="skip-link" href="#main-content">
        본문으로 바로가기
      </a>
      <AnnouncementBar />
      <SiteHeader />
      {children}
      <SiteFooter />
    </>
  );
}
