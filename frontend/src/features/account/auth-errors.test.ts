import { describe, expect, it } from "vitest";
import { ApiError } from "../../lib/api/client";
import { phoneFieldErrorMessage } from "./auth-errors";

describe("phoneFieldErrorMessage", () => {
  it("returns the backend phone validation message", () => {
    const error = new ApiError("하나 이상의 입력값이 유효하지 않습니다.", {
      code: "VALIDATION_ERROR",
      fieldErrors: [
        {
          field: "phone",
          code: "Pattern",
          message: "휴대전화 번호가 올바르지 않습니다.",
        },
      ],
    });

    expect(phoneFieldErrorMessage(error)).toBe("휴대전화 번호가 올바르지 않습니다.");
  });

  it("ignores validation errors for other fields", () => {
    const error = new ApiError("입력값 오류", {
      fieldErrors: [{ field: "email", message: "이메일이 올바르지 않습니다." }],
    });

    expect(phoneFieldErrorMessage(error)).toBeNull();
  });
});
