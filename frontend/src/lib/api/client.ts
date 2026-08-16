const DEFAULT_API_BASE_URL = "http://localhost:8080/api/v1";

type ApiErrorOptions = {
  status?: number;
  code?: string;
  fieldErrors?: readonly ApiFieldError[];
  cause?: unknown;
};

export type ApiFieldError = {
  readonly field: string;
  readonly code?: string;
  readonly message: string;
};

export class ApiError extends Error {
  readonly status?: number;
  readonly code?: string;
  readonly fieldErrors: readonly ApiFieldError[];

  constructor(message: string, options: ApiErrorOptions = {}) {
    super(message, { cause: options.cause });
    this.name = "ApiError";
    this.status = options.status;
    this.code = options.code;
    this.fieldErrors = options.fieldErrors ? [...options.fieldErrors] : [];
  }
}

function apiBaseUrl() {
  const configured = process.env.NEXT_PUBLIC_API_BASE_URL?.trim();
  return (configured || DEFAULT_API_BASE_URL).replace(/\/+$/, "");
}

export function buildApiUrl(path: string) {
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  return `${apiBaseUrl()}${normalizedPath}`;
}

export function resolveApiAssetUrl(path: string | null) {
  if (!path) return null;
  try {
    if (/^https?:\/\//i.test(path)) {
      return new URL(path).toString();
    }
    if (!path.startsWith("/") || path.startsWith("//")) {
      return null;
    }
    const apiOrigin = new URL(apiBaseUrl()).origin;
    return new URL(path, apiOrigin).toString();
  } catch {
    return null;
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function parseFieldErrors(value: unknown): ApiFieldError[] {
  if (!Array.isArray(value)) return [];

  return value.flatMap((item) => {
    if (!isRecord(item) || typeof item.field !== "string" || typeof item.message !== "string") {
      return [];
    }
    return [{
      field: item.field,
      code: typeof item.code === "string" ? item.code : undefined,
      message: item.message,
    }];
  });
}

async function parseError(response: Response) {
  try {
    const body: unknown = await response.json();
    if (isRecord(body)) {
      return {
        code: typeof body.code === "string" ? body.code : undefined,
        fieldErrors: parseFieldErrors(body.fieldErrors),
        message:
          typeof body.message === "string"
            ? body.message
            : typeof body.detail === "string"
              ? body.detail
              : typeof body.title === "string"
                ? body.title
            : "요청을 처리하지 못했습니다.",
      };
    }
  } catch {
    // The API may return an empty or non-JSON error body.
  }

  return { message: "요청을 처리하지 못했습니다.", fieldErrors: [] };
}

type CsrfResponse = {
  data: {
    headerName: string;
    token: string;
  };
};

type ApiMutationOptions = Omit<RequestInit, "body" | "method"> & {
  method: "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
};

let csrfPromise: Promise<CsrfResponse["data"]> | null = null;

async function getCsrfToken(forceRefresh = false) {
  if (forceRefresh) csrfPromise = null;
  csrfPromise ??= apiFetch<CsrfResponse>("/auth/csrf").then(
    (response) => response.data,
  );
  try {
    return await csrfPromise;
  } catch (error) {
    csrfPromise = null;
    throw error;
  }
}

async function parseSuccess<T>(response: Response): Promise<T> {
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export async function apiFetch<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json");

  try {
    const response = await fetch(buildApiUrl(path), {
      ...options,
      headers,
      credentials: "include",
      cache: "no-store",
    });

    if (!response.ok) {
      const problem = await parseError(response);
      throw new ApiError(problem.message, {
        status: response.status,
        code: problem.code,
        fieldErrors: problem.fieldErrors,
      });
    }

    return await parseSuccess<T>(response);
  } catch (error) {
    if (error instanceof ApiError || options.signal?.aborted) {
      throw error;
    }

    throw new ApiError("API 서버에 연결할 수 없습니다.", { cause: error });
  }
}

export async function apiMutation<T>(
  path: string,
  options: ApiMutationOptions,
): Promise<T> {
  const csrf = await getCsrfToken();
  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json");
  headers.set(csrf.headerName, csrf.token);
  if (options.body !== undefined) {
    headers.set("Content-Type", "application/json");
  }

  try {
    const response = await fetch(buildApiUrl(path), {
      ...options,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      headers,
      credentials: "include",
      cache: "no-store",
    });

    if (!response.ok) {
      const problem = await parseError(response);
      if (problem.code === "CSRF_INVALID") csrfPromise = null;
      throw new ApiError(problem.message, {
        status: response.status,
        code: problem.code,
        fieldErrors: problem.fieldErrors,
      });
    }

    return await parseSuccess<T>(response);
  } catch (error) {
    if (error instanceof ApiError || options.signal?.aborted) throw error;
    throw new ApiError("API 서버에 연결할 수 없습니다.", { cause: error });
  }
}

export async function refreshCsrfToken() {
  await getCsrfToken(true);
}
