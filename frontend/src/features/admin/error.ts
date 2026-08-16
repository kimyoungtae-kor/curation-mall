import { ApiError } from "@/lib/api/client";

export function adminErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 401) return "로그인이 만료되었습니다. 다시 로그인해 주세요.";
    if (error.status === 403) return "관리자 권한이 필요한 요청입니다.";
    if (error.code === "OPTIMISTIC_LOCK_CONFLICT") {
      return "다른 작업에서 먼저 변경했습니다. 새로고침 후 다시 시도해 주세요.";
    }
    return error.message;
  }
  return "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";
}
