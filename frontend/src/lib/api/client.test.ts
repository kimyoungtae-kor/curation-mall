import { afterEach, describe, expect, it, vi } from "vitest";
import {
  ApiError,
  apiFetch,
  apiFormMutation,
  apiMutation,
  refreshCsrfToken,
  resolveApiAssetUrl,
} from "./client";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("resolveApiAssetUrl", () => {
  it("resolves backend media paths against the API origin", () => {
    expect(resolveApiAssetUrl("/media/products/demo.webp")).toBe(
      "http://localhost:8080/media/products/demo.webp",
    );
  });

  it("keeps external HTTP images", () => {
    expect(resolveApiAssetUrl("https://cdn.example.com/product.webp")).toBe(
      "https://cdn.example.com/product.webp",
    );
  });

  it("rejects non-HTTP schemes", () => {
    expect(resolveApiAssetUrl("javascript:alert(1)")).toBeNull();
    expect(resolveApiAssetUrl("data:image/svg+xml;base64,PHN2Zz4=")).toBeNull();
    expect(resolveApiAssetUrl("//untrusted.example.com/product.webp")).toBeNull();
    expect(resolveApiAssetUrl("media/products/demo.webp")).toBeNull();
  });
});

describe("apiMutation", () => {
  it("loads a CSRF token and sends JSON mutations with credentials", async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            data: { headerName: "X-XSRF-TOKEN", token: "csrf-demo-token" },
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ data: { ok: true } }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      );
    vi.stubGlobal("fetch", fetchMock);

    await expect(
      apiMutation<{ data: { ok: boolean } }>("/cart/items", {
        method: "POST",
        body: { variantId: "variant-1", quantity: 2 },
      }),
    ).resolves.toEqual({ data: { ok: true } });

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      "http://localhost:8080/api/v1/auth/csrf",
    );
    const mutation = fetchMock.mock.calls[1];
    expect(mutation?.[0]).toBe("http://localhost:8080/api/v1/cart/items");
    const options = mutation?.[1];
    expect(options?.credentials).toBe("include");
    expect(options?.body).toBe('{"variantId":"variant-1","quantity":2}');
    const headers = options?.headers as Headers;
    expect(headers.get("X-XSRF-TOKEN")).toBe("csrf-demo-token");
    expect(headers.get("Content-Type")).toBe("application/json");
  });
});

describe("apiFormMutation", () => {
  it("multipart 경계는 브라우저에 맡기고 세션과 CSRF 헤더를 전송한다", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>().mockResolvedValueOnce(
        new Response(
          JSON.stringify({ data: { headerName: "X-XSRF-TOKEN", token: "upload-token" } }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      ),
    );
    await refreshCsrfToken();

    class FakeXmlHttpRequest {
      static latest: FakeXmlHttpRequest | null = null;
      method = "";
      url = "";
      status = 0;
      responseText = "";
      withCredentials = false;
      requestBody: XMLHttpRequestBodyInit | Document | null = null;
      headers = new Map<string, string>();
      upload = {
        onprogress: null as ((event: ProgressEvent) => void) | null,
        onload: null as ((event: ProgressEvent) => void) | null,
      };
      onload: (() => void) | null = null;
      onerror: (() => void) | null = null;
      onabort: (() => void) | null = null;

      constructor() {
        FakeXmlHttpRequest.latest = this;
      }

      open(method: string, url: string) {
        this.method = method;
        this.url = url;
      }

      setRequestHeader(name: string, value: string) {
        this.headers.set(name.toLowerCase(), value);
      }

      send(body: XMLHttpRequestBodyInit | Document | null) {
        this.requestBody = body;
        queueMicrotask(() => {
          this.upload.onload?.({} as ProgressEvent);
          this.status = 201;
          this.responseText = JSON.stringify({ data: { storageKey: "products/demo.webp" } });
          this.onload?.();
        });
      }

      abort() {
        this.onabort?.();
      }
    }
    vi.stubGlobal("XMLHttpRequest", FakeXmlHttpRequest);

    const progress: number[] = [];
    const form = new FormData();
    form.append("file", new Blob(["image"], { type: "image/webp" }), "demo.webp");
    await expect(apiFormMutation<{ data: { storageKey: string } }>("/admin/media/images", {
      method: "POST",
      body: form,
      headers: { "Content-Type": "should-be-removed" },
      onUploadProgress: ({ percent }) => progress.push(percent),
    })).resolves.toEqual({ data: { storageKey: "products/demo.webp" } });

    const request = FakeXmlHttpRequest.latest;
    expect(request?.method).toBe("POST");
    expect(request?.url).toBe("http://localhost:8080/api/v1/admin/media/images");
    expect(request?.withCredentials).toBe(true);
    expect(request?.requestBody).toBe(form);
    expect(request?.headers.get("x-xsrf-token")).toBe("upload-token");
    expect(request?.headers.has("content-type")).toBe(false);
    expect(progress).toContain(100);
  });
});

describe("API validation errors", () => {
  it("keeps Problem Details field errors on ApiError", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>().mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            code: "VALIDATION_ERROR",
            detail: "하나 이상의 입력값이 유효하지 않습니다.",
            fieldErrors: [
              {
                field: "phone",
                code: "Pattern",
                message: "휴대전화 번호가 올바르지 않습니다.",
              },
            ],
          }),
          { status: 400, headers: { "Content-Type": "application/problem+json" } },
        ),
      ),
    );

    const caught = await apiFetch("/auth/signup").catch((error: unknown) => error);

    expect(caught).toBeInstanceOf(ApiError);
    expect(caught).toMatchObject({
      status: 400,
      code: "VALIDATION_ERROR",
      fieldErrors: [
        {
          field: "phone",
          code: "Pattern",
          message: "휴대전화 번호가 올바르지 않습니다.",
        },
      ],
    });
  });
});
