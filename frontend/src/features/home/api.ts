import { apiFetch } from "@/lib/api/client";
import { normalizeHome } from "./normalize-home";
import type { HomeData, PublicHomeResponse } from "./types";

export async function getHome(signal?: AbortSignal): Promise<HomeData> {
  const payload = await apiFetch<PublicHomeResponse>("/home", { signal });
  return normalizeHome(payload);
}
