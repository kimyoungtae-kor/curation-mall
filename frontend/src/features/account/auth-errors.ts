import { ApiError } from "../../lib/api/client";

export const INVALID_PHONE_MESSAGE = "휴대전화 번호가 올바르지 않습니다.";

export function phoneFieldErrorMessage(error: unknown) {
  if (!(error instanceof ApiError)) return null;
  return error.fieldErrors.find((fieldError) => fieldError.field === "phone")?.message ?? null;
}
