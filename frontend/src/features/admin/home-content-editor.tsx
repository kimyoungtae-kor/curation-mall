"use client";

import { useEffect, useState, type FormEvent } from "react";
import {
  getAdminHeroSlides,
  getAdminHomeSections,
  updateAdminHeroSlide,
  updateAdminHomeSection,
} from "./api";
import { AdminGuard } from "./admin-shell";
import { AdminLoading } from "./dashboard";
import { adminErrorMessage } from "./error";
import type { HeroLinkType, HeroSlide, HeroStatus, HomeSection } from "./types";

const SECTION_LABELS: Record<string, string> = {
  HERO: "메인 히어로",
  CURATED_COLLECTIONS: "기획전",
  FEATURED_PRODUCTS: "추천 상품",
  LIFESTYLE_CONTENTS: "라이프스타일 콘텐츠",
  NEW_ARRIVALS: "신상품",
  PET_ROOM: "펫룸",
  SERVICE_GUIDE: "서비스 안내",
};

export function AdminHomeContentPage() {
  return (
    <AdminGuard title="홈 콘텐츠" description="메인 슬라이드와 각 홈 섹션의 제목·JSON 설정을 관리합니다.">
      <HomeContent />
    </AdminGuard>
  );
}

function HomeContent() {
  const [sections, setSections] = useState<HomeSection[]>([]);
  const [slides, setSlides] = useState<HeroSlide[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([
      getAdminHomeSections(controller.signal),
      getAdminHeroSlides(controller.signal),
    ])
      .then(([sectionItems, slideItems]) => {
        setSections(sectionItems);
        setSlides(slideItems);
      })
      .catch((caught) => {
        if (!controller.signal.aborted) setError(adminErrorMessage(caught));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, []);

  if (loading) return <AdminLoading label="홈 콘텐츠를 불러오는 중입니다." />;
  if (error && !sections.length) return <p className="admin-alert admin-alert--error" role="alert">{error}</p>;

  return (
    <div className="admin-stack admin-home-content">
      <p className="admin-alert admin-alert--info">
        ‘공개’ 상태의 히어로 슬라이드 저장 내용은 고객 메인 페이지에 반영됩니다. JSON 설정은 저장 전 형식을 검사합니다.
      </p>
      {error ? <p className="admin-alert admin-alert--error" role="alert">{error}</p> : null}

      <section className="admin-panel">
        <div className="admin-panel__heading">
          <div><h2>히어로 슬라이드</h2><p>메인 최상단에서 5초마다 전환되는 3개 슬라이드입니다.</p></div>
        </div>
        <div className="admin-content-card-grid">
          {slides.map((slide) => (
            <HeroSlideForm
              key={slide.id}
              slide={slide}
              onSaved={(updated) => setSlides((current) => current.map((item) => item.id === updated.id ? updated : item))}
            />
          ))}
        </div>
      </section>

      <section className="admin-panel">
        <div className="admin-panel__heading">
          <div><h2>홈 섹션 설정</h2><p>섹션 제목과 API가 읽는 JSON 설정을 수정합니다.</p></div>
        </div>
        <div className="admin-content-section-list">
          {sections.map((section) => (
            <HomeSectionForm
              key={section.id}
              section={section}
              onSaved={(updated) => setSections((current) => current.map((item) => item.id === updated.id ? updated : item))}
            />
          ))}
        </div>
      </section>
    </div>
  );
}

function prettyJson(value: string) {
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

function HomeSectionForm({ section, onSaved }: { section: HomeSection; onSaved: (section: HomeSection) => void }) {
  const [title, setTitle] = useState(section.title ?? "");
  const [content, setContent] = useState(() => prettyJson(section.content));
  const [sortOrder, setSortOrder] = useState(section.sortOrder);
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setMessage(null);
    setError(null);
    try {
      const parsed: unknown = JSON.parse(content);
      const normalized = JSON.stringify(parsed);
      const updated = await updateAdminHomeSection(section.id, {
        title: title.trim(),
        content: normalized,
        sortOrder,
        version: section.version,
      });
      onSaved(updated);
      setMessage("섹션 설정을 저장했습니다.");
    } catch (caught) {
      if (caught instanceof SyntaxError) setError("JSON 형식이 올바르지 않습니다. 괄호, 쉼표, 따옴표를 확인해 주세요.");
      else setError(adminErrorMessage(caught));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="admin-content-section" onSubmit={submit}>
      <div className="admin-content-section__heading">
        <div><strong>{SECTION_LABELS[section.sectionKey] ?? section.sectionKey}</strong><span>{section.sectionKey}</span></div>
        <label>노출 순서<input type="number" min={1} value={sortOrder} onChange={(event) => setSortOrder(Math.max(1, Number(event.target.value) || 1))} /></label>
      </div>
      <label>섹션 제목<input maxLength={200} value={title} onChange={(event) => setTitle(event.target.value)} /></label>
      <label>JSON 설정<textarea required rows={8} spellCheck={false} value={content} onChange={(event) => setContent(event.target.value)} /></label>
      {error ? <p className="admin-alert admin-alert--error" role="alert">{error}</p> : null}
      {message ? <p className="admin-inline-success" role="status">{message}</p> : null}
      <div className="admin-content-section__actions">
        <button className="admin-text-button" type="button" onClick={() => setContent(prettyJson(content))}>JSON 정렬</button>
        <button className="admin-button" type="submit" disabled={submitting}>{submitting ? "저장 중…" : "섹션 저장"}</button>
      </div>
    </form>
  );
}

function HeroSlideForm({ slide, onSaved }: { slide: HeroSlide; onSaved: (slide: HeroSlide) => void }) {
  const [draft, setDraft] = useState(slide);
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setMessage(null);
    setError(null);
    try {
      const updated = await updateAdminHeroSlide({
        ...draft,
        title: draft.title.trim(),
        description: draft.description.trim(),
        imageStorageKey: draft.imageStorageKey.trim(),
        imageAlt: draft.imageAlt.trim(),
        linkValue: draft.linkValue.trim(),
      });
      setDraft(updated);
      onSaved(updated);
      setMessage("슬라이드를 저장했습니다.");
    } catch (caught) {
      setError(adminErrorMessage(caught));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="admin-hero-card" onSubmit={submit}>
      <div className="admin-hero-card__heading">
        <span>SLIDE {slide.sortOrder}</span>
        <select aria-label={`슬라이드 ${slide.sortOrder} 공개 상태`} value={draft.status} onChange={(event) => setDraft((current) => ({ ...current, status: event.target.value as HeroStatus }))}>
          <option value="PUBLISHED">공개</option>
          <option value="DRAFT">초안</option>
          <option value="HIDDEN">숨김</option>
        </select>
      </div>
      <label>제목<input required maxLength={200} value={draft.title} onChange={(event) => setDraft((current) => ({ ...current, title: event.target.value }))} /></label>
      <label>설명<textarea required rows={3} maxLength={500} value={draft.description} onChange={(event) => setDraft((current) => ({ ...current, description: event.target.value }))} /></label>
      <label>이미지 저장소 키<input required maxLength={500} value={draft.imageStorageKey} onChange={(event) => setDraft((current) => ({ ...current, imageStorageKey: event.target.value }))} /></label>
      <label>이미지 대체 텍스트<input required maxLength={300} value={draft.imageAlt} onChange={(event) => setDraft((current) => ({ ...current, imageAlt: event.target.value }))} /></label>
      <div className="admin-form-grid admin-form-grid--compact">
        <label>링크 유형<select value={draft.linkType} onChange={(event) => setDraft((current) => ({ ...current, linkType: event.target.value as HeroLinkType }))}><option value="COLLECTION">기획전</option><option value="PRODUCT">상품</option><option value="CONTENT">콘텐츠</option><option value="HELP">안내</option></select></label>
        <label>링크 값<input required maxLength={200} value={draft.linkValue} onChange={(event) => setDraft((current) => ({ ...current, linkValue: event.target.value }))} /></label>
      </div>
      <label>노출 순서<input type="number" min={1} max={3} value={draft.sortOrder} onChange={(event) => setDraft((current) => ({ ...current, sortOrder: Math.min(3, Math.max(1, Number(event.target.value) || 1)) }))} /></label>
      {error ? <p className="admin-alert admin-alert--error" role="alert">{error}</p> : null}
      {message ? <p className="admin-inline-success" role="status">{message}</p> : null}
      <button className="admin-button admin-button--primary admin-button--full" type="submit" disabled={submitting}>{submitting ? "저장 중…" : "슬라이드 저장"}</button>
    </form>
  );
}
