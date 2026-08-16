import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, apiFetch, apiMutation, resolveApiAssetUrl } from "./client";

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
