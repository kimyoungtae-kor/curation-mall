"use client";

import { useEffect, useRef, useState } from "react";
import { SafeMedia } from "@/components/safe-media";
import { ContentLinkView } from "./content-link-view";
import type { HeroSlide } from "./types";

const fallbackSlide: HeroSlide = {
  id: "fallback-hero",
  title: "함께 사는 공간을 다정하게",
  description: "반려동물의 행동과 사람의 취향을 함께 생각한 제품을 만나보세요.",
  image: null,
  link: { type: "PRODUCTS", value: "", href: "/products", external: false },
  sortOrder: 0,
};

const AUTOPLAY_DELAY_MS = 5_000;
const SWIPE_THRESHOLD_PX = 44;

export function HeroCarousel({ slides }: { slides: HeroSlide[] }) {
  const items = slides.length > 0 ? slides : [fallbackSlide];
  const [currentIndex, setCurrentIndex] = useState(0);
  const [isHovered, setIsHovered] = useState(false);
  const [hasFocusWithin, setHasFocusWithin] = useState(false);
  const [prefersReducedMotion, setPrefersReducedMotion] = useState(false);
  const [timerResetKey, setTimerResetKey] = useState(0);
  const touchStart = useRef<{ x: number; y: number } | null>(null);
  const safeIndex = Math.min(currentIndex, items.length - 1);
  const autoplayPaused = isHovered || hasFocusWithin || prefersReducedMotion;

  useEffect(() => {
    const mediaQuery = window.matchMedia("(prefers-reduced-motion: reduce)");
    const updatePreference = () => setPrefersReducedMotion(mediaQuery.matches);

    updatePreference();
    mediaQuery.addEventListener("change", updatePreference);
    return () => mediaQuery.removeEventListener("change", updatePreference);
  }, []);

  useEffect(() => {
    if (items.length <= 1 || autoplayPaused) return;

    const timeoutId = window.setTimeout(() => {
      setCurrentIndex((index) => (index + 1) % items.length);
    }, AUTOPLAY_DELAY_MS);

    return () => window.clearTimeout(timeoutId);
  }, [autoplayPaused, items.length, safeIndex, timerResetKey]);

  function move(delta: number) {
    setCurrentIndex((index) => (index + delta + items.length) % items.length);
    setTimerResetKey((key) => key + 1);
  }

  function select(index: number) {
    setCurrentIndex(index);
    setTimerResetKey((key) => key + 1);
  }

  function handleTouchStart(event: React.TouchEvent<HTMLElement>) {
    const touch = event.touches[0];
    if (!touch) return;
    touchStart.current = { x: touch.clientX, y: touch.clientY };
  }

  function handleTouchEnd(event: React.TouchEvent<HTMLElement>) {
    const start = touchStart.current;
    const touch = event.changedTouches[0];
    touchStart.current = null;
    if (!start || !touch) return;

    const deltaX = touch.clientX - start.x;
    const deltaY = touch.clientY - start.y;
    if (Math.abs(deltaX) < SWIPE_THRESHOLD_PX || Math.abs(deltaX) <= Math.abs(deltaY)) return;

    move(deltaX > 0 ? -1 : 1);
  }

  return (
    <section
      className="home-hero"
      aria-roledescription="carousel"
      aria-label="추천 기획전"
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      onTouchStart={handleTouchStart}
      onTouchEnd={handleTouchEnd}
      onTouchCancel={() => {
        touchStart.current = null;
      }}
      onFocusCapture={() => setHasFocusWithin(true)}
      onBlurCapture={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
          setHasFocusWithin(false);
        }
      }}
    >
      <div className="home-hero__slides">
        {items.map((item, index) => {
          const active = index === safeIndex;
          return (
            <div
              className="home-hero__slide"
              data-active={active}
              role="group"
              aria-roledescription="slide"
              aria-label={`${index + 1} / ${items.length}`}
              aria-hidden={!active}
              inert={!active}
              key={item.id}
            >
              <div
                className="home-hero__copy"
                aria-live={active && autoplayPaused ? "polite" : "off"}
              >
                <p className="eyebrow">PET LIFESTYLE CURATION</p>
                <h1>{item.title}</h1>
                <p>{item.description}</p>
                <ContentLinkView link={item.link} className="button button--primary">
                  지금 만나보기
                </ContentLinkView>
              </div>
              <div className="home-hero__media">
                <SafeMedia
                  src={item.image?.url ?? null}
                  alt={item.image?.alt ?? item.title}
                  fallbackLabel={item.title}
                  className="home-hero__image"
                  eager={index === 0}
                />
              </div>
            </div>
          );
        })}
      </div>

      {items.length > 1 ? (
        <div className="home-hero__controls">
          <button type="button" onClick={() => move(-1)} aria-label="이전 슬라이드">
            ←
          </button>
          <div className="home-hero__dots" aria-label="슬라이드 선택">
            {items.map((item, index) => (
              <button
                key={item.id}
                type="button"
                onClick={() => select(index)}
                aria-label={`${index + 1}번 슬라이드: ${item.title}`}
                aria-current={index === safeIndex ? "true" : undefined}
              />
            ))}
          </div>
          <button type="button" onClick={() => move(1)} aria-label="다음 슬라이드">
            →
          </button>
        </div>
      ) : null}
    </section>
  );
}
