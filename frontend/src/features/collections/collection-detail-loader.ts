import "server-only";

import { notFound } from "next/navigation";
import { cache } from "react";
import { ApiError } from "@/lib/api/client";
import { getCollectionDetail } from "./api";

export const getCollectionForPage = cache(async (slug: string) => {
  if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(slug)) notFound();

  try {
    return await getCollectionDetail(slug);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    throw error;
  }
});
