import { describe, expect, it } from "vitest";
import {
  MAX_PRODUCT_IMAGE_BYTES,
  completedUploadsInSelectionOrder,
  mediaPathForStorageKey,
  moveProductImage,
  normalizeProductImages,
  selectProductImageFiles,
} from "./product-image-model";

describe("admin product image model", () => {
  it("허용 형식과 파일 크기 및 상품당 8장 제한을 함께 검사한다", () => {
    const files = [
      { name: "one.jpg", type: "image/jpeg", size: 1200 },
      { name: "too-big.png", type: "image/png", size: MAX_PRODUCT_IMAGE_BYTES + 1 },
      { name: "vector.svg", type: "image/svg+xml", size: 300 },
      { name: "two.webp", type: "image/webp", size: 900 },
    ];

    const result = selectProductImageFiles(files, 7);

    expect(result.accepted.map((file) => file.name)).toEqual(["one.jpg"]);
    expect(result.errors).toEqual(expect.arrayContaining([
      expect.stringContaining("too-big.png"),
      expect.stringContaining("vector.svg"),
      expect.stringContaining("최대 8장"),
    ]));
  });

  it("순서를 옮기면서 API에 보낼 sortOrder를 1부터 다시 매긴다", () => {
    const images = [
      { id: "image-a", storageKey: " a.webp ", alt: " A ", sortOrder: 8 },
      { id: "image-b", storageKey: "b.webp", alt: "B", sortOrder: 9 },
    ];

    expect(moveProductImage(images, 1, 0)).toEqual([
      { id: "image-b", storageKey: "b.webp", alt: "B", sortOrder: 1 },
      { id: "image-a", storageKey: "a.webp", alt: "A", sortOrder: 2 },
    ]);
    expect(normalizeProductImages(images)[0]).toMatchObject({
      storageKey: "a.webp",
      alt: "A",
      sortOrder: 1,
    });
  });

  it("병렬 업로드 응답 순서와 무관하게 사용자가 선택한 순서로 결과를 만든다", () => {
    const first = { clientId: "selected-first", fileName: "first.webp" };
    const second = { clientId: "selected-second", fileName: "second.webp" };
    const results = new Map<string, { storageKey: string }>();

    // 두 번째 파일의 네트워크 요청이 먼저 끝난 상황을 재현한다.
    results.set(second.clientId, { storageKey: "uploads/second.webp" });
    results.set(first.clientId, { storageKey: "uploads/first.webp" });

    expect(completedUploadsInSelectionOrder([first, second], results)).toEqual([
      { item: first, result: { storageKey: "uploads/first.webp" } },
      { item: second, result: { storageKey: "uploads/second.webp" } },
    ]);
  });

  it("저장소 키를 안전한 미디어 경로로 바꾸고 상위 경로 이동은 거부한다", () => {
    expect(mediaPathForStorageKey("products/고양이 침대.webp")).toBe(
      "/media/products/%EA%B3%A0%EC%96%91%EC%9D%B4%20%EC%B9%A8%EB%8C%80.webp",
    );
    expect(mediaPathForStorageKey("../secret.webp")).toBeNull();
    expect(mediaPathForStorageKey("/absolute.webp")).toBeNull();
  });
});
