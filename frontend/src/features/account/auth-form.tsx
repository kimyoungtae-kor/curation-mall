"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState, type FormEvent } from "react";
import { ApiError } from "@/lib/api/client";
import { login, signup } from "./api";
import { INVALID_PHONE_MESSAGE, phoneFieldErrorMessage } from "./auth-errors";
import { useAuth } from "./auth-provider";
import { safeNextPath } from "./safe-next-path";

type AuthMode = "login" | "signup";

function authErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.code === "INVALID_CREDENTIALS") return "이메일 또는 비밀번호가 올바르지 않습니다.";
    if (error.code === "EMAIL_ALREADY_EXISTS") return "이미 가입된 이메일입니다.";
    return error.message;
  }
  return "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";
}

export function AuthForm({ mode }: { mode: AuthMode }) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { acceptAuthResult } = useAuth();
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [phoneError, setPhoneError] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    setPhoneError(null);
    const data = new FormData(event.currentTarget);
    try {
      const result = mode === "login"
        ? await login({
            email: String(data.get("email") ?? "").trim(),
            password: String(data.get("password") ?? ""),
          })
        : await signup({
            email: String(data.get("email") ?? "").trim(),
            password: String(data.get("password") ?? ""),
            name: String(data.get("name") ?? "").trim(),
            phone: String(data.get("phone") ?? "").replace(/\D/g, ""),
            requiredTermsAccepted: data.get("requiredTermsAccepted") === "on",
          });
      acceptAuthResult(result);
      router.replace(safeNextPath(searchParams.get("next")));
      router.refresh();
    } catch (caught) {
      const nextPhoneError = phoneFieldErrorMessage(caught);
      setPhoneError(nextPhoneError);
      setError(nextPhoneError ? null : authErrorMessage(caught));
    } finally {
      setSubmitting(false);
    }
  }

  const signupMode = mode === "signup";
  return (
    <form className="auth-form" onSubmit={submit}>
      {signupMode ? (
        <>
          <label>
            이름
            <input name="name" required minLength={2} maxLength={50} autoComplete="name" />
          </label>
          <label>
            휴대전화
            <input
              name="phone"
              required
              inputMode="numeric"
              pattern="01[016789]([0-9]{7,8}|-[0-9]{3,4}-[0-9]{4}| [0-9]{3,4} [0-9]{4})"
              autoComplete="tel"
              placeholder="01012345678"
              aria-invalid={Boolean(phoneError)}
              aria-describedby={phoneError ? "signup-phone-error" : undefined}
              onChange={() => setPhoneError(null)}
              onInvalid={(event) => {
                event.preventDefault();
                setError(null);
                setPhoneError(INVALID_PHONE_MESSAGE);
              }}
            />
            {phoneError ? (
              <span className="auth-form__field-error" id="signup-phone-error" role="alert">
                {phoneError}
              </span>
            ) : null}
          </label>
        </>
      ) : null}
      <label>
        이메일
        <input name="email" required type="email" maxLength={100} autoComplete="email" />
      </label>
      <label>
        비밀번호
        <input name="password" required type="password" minLength={8} maxLength={100} autoComplete={signupMode ? "new-password" : "current-password"} />
      </label>
      {signupMode ? (
        <label className="auth-form__terms">
          <input name="requiredTermsAccepted" required type="checkbox" />
          <span>이용약관과 개인정보처리방침에 동의합니다.</span>
        </label>
      ) : null}
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      <button className="button button--primary" type="submit" disabled={submitting}>
        {submitting ? "처리 중…" : signupMode ? "가입하기" : "로그인"}
      </button>
      <p className="auth-form__switch">
        {signupMode ? "이미 회원이신가요?" : "처음 방문하셨나요?"}{" "}
        <Link href={signupMode ? "/login" : "/signup"}>{signupMode ? "로그인" : "회원가입"}</Link>
      </p>
    </form>
  );
}
