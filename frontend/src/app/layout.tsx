import type { Metadata } from "next";
import { Noto_Sans_KR } from "next/font/google";
import type { ReactNode } from "react";
import { AuthProvider } from "@/features/account/auth-provider";
import "./globals.css";
import "./home-collections.css";
import "./account-commerce.css";
import "./commerce.css";

const notoSansKr = Noto_Sans_KR({
  display: "swap",
  preload: false,
  variable: "--font-noto-sans-kr",
});

export const metadata: Metadata = {
  title: {
    default: "Pet Curation",
    template: "%s | Pet Curation",
  },
  description:
    "반려동물의 행동과 사람의 공간을 함께 생각하는 펫 라이프스타일 큐레이션몰",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="ko" className={notoSansKr.variable} data-scroll-behavior="smooth">
      <body>
        <AuthProvider>{children}</AuthProvider>
      </body>
    </html>
  );
}
