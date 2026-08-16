"use client";

import { useEffect, useState } from "react";
import { AnnouncementBar } from "@/components/announcement-bar";
import { SiteFooter, type FooterLink } from "@/components/site-footer";
import { SiteHeader } from "@/components/site-header";
import { ApiError } from "@/lib/api/client";
import { getHome } from "./api";
import { HeroCarousel } from "./hero-carousel";
import {
  ExploreProducts,
  FeaturedCollections,
  HomeProducts,
  LifestyleContents,
  ServiceGuide,
  serviceLinkLabels,
} from "./home-sections";
import type { HomeData } from "./types";

type HomeState =
  | { status: "loading" }
  | { status: "success"; home: HomeData }
  | { status: "error"; message: string };

function errorMessage(error: unknown) {
  if (error instanceof ApiError && error.status === undefined) {
    return "홈 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.";
  }
  return "홈 콘텐츠를 불러오는 중 문제가 생겼습니다.";
}

function footerLinks(home: HomeData): FooterLink[] {
  return home.serviceGuide.links.flatMap((slug) => {
    const item = serviceLinkLabels[slug];
    return item ? [item] : [];
  });
}

function HomeSkeleton() {
  return (
    <main className="home-loading" id="main-content" aria-busy="true">
      <div className="home-hero-skeleton" aria-label="홈 콘텐츠를 불러오는 중" />
      <div className="home-card-skeletons" aria-hidden="true">
        {Array.from({ length: 4 }, (_, index) => (
          <div className="skeleton-card__media" key={index} />
        ))}
      </div>
    </main>
  );
}

export function HomeExperience() {
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<HomeState>({ status: "loading" });

  useEffect(() => {
    const controller = new AbortController();
    getHome(controller.signal)
      .then((home) => setState({ status: "success", home }))
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setState({ status: "error", message: errorMessage(error) });
        }
      });
    return () => controller.abort();
  }, [attempt]);

  const announcement = state.status === "success" ? state.home.announcement : null;

  return (
    <>
      <a className="skip-link" href="#main-content">
        본문으로 바로가기
      </a>
      <AnnouncementBar
        text={announcement?.text}
        href={announcement?.link.href}
        external={announcement?.link.external}
      />
      <SiteHeader />

      {state.status === "loading" ? <HomeSkeleton /> : null}

      {state.status === "error" ? (
        <main className="home-error" id="main-content">
          <div className="state-panel" role="alert">
            <div className="state-panel__content">
              <h1>홈을 불러오지 못했어요</h1>
              <p>{state.message}</p>
              <button
                className="button button--primary"
                type="button"
                onClick={() => {
                  setState({ status: "loading" });
                  setAttempt((current) => current + 1);
                }}
              >
                다시 시도
              </button>
            </div>
          </div>
        </main>
      ) : null}

      {state.status === "success" ? (
        <main id="main-content">
          <HeroCarousel slides={state.home.heroSlides} />
          <FeaturedCollections collections={state.home.featuredCollections} />
          <HomeProducts
            popularProducts={state.home.popularProducts}
            newProducts={state.home.newProducts}
          />
          <ExploreProducts explore={state.home.explore} />
          <LifestyleContents lifestyleContents={state.home.lifestyleContents} />
          <ServiceGuide serviceGuide={state.home.serviceGuide} />
        </main>
      ) : null}

      <SiteFooter links={state.status === "success" ? footerLinks(state.home) : undefined} />
    </>
  );
}
