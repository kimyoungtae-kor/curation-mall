import "server-only";

import { cache } from "react";
import { notFound } from "next/navigation";
import { ApiError } from "@/lib/api/client";
import { getProductDetail } from "./api";

export const getProductForPage = cache(async (slug: string) => {
  if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(slug)) {
    notFound();
  }

  try {
    return await getProductDetail(slug);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      notFound();
    }
    throw error;
  }
});
