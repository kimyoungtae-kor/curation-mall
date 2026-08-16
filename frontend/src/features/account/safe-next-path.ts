const INTERNAL_ORIGIN = "https://pet-curation.invalid";

export function safeNextPath(value: string | null) {
  if (!value?.startsWith("/")) {
    return "/";
  }

  try {
    const decoded = decodeURIComponent(value);
    if (decoded.startsWith("//") || decoded.includes("\\")) return "/";
    const target = new URL(value, INTERNAL_ORIGIN);
    if (target.origin !== INTERNAL_ORIGIN) return "/";
    return `${target.pathname}${target.search}${target.hash}`;
  } catch {
    return "/";
  }
}
