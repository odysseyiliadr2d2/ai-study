// app/requests/request-form.tsx — 등록 폼 (클라이언트 컴포넌트)
// 화면은 API 만 부릅니다. 같은 API 를 n8n 이나 다른 시스템도 그대로 씁니다.
"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

type FieldErrors = Record<string, string[] | undefined>;

export function RequestForm() {
  const router = useRouter();
  const [errors, setErrors] = useState<FieldErrors>({});
  const [message, setMessage] = useState("");
  const [pending, setPending] = useState(false);

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const data = Object.fromEntries(new FormData(form));
    if (!data.dueDate) delete data.dueDate; // 빈 날짜는 보내지 않음

    setPending(true);
    const res = await fetch("/api/requests", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    });
    setPending(false);

    if (res.status === 400) {
      const body = await res.json();
      setErrors(body.errors ?? {});
      setMessage(body.message ?? "입력값을 확인하세요");
      return;
    }
    if (!res.ok) {
      setMessage("저장하지 못했습니다. 잠시 후 다시 시도하세요.");
      return;
    }
    form.reset();
    setErrors({});
    setMessage("접수되었습니다.");
    router.refresh(); // 서버 컴포넌트 목록 다시 그리기
  }

  return (
    <form onSubmit={onSubmit} style={{ display: "grid", gap: 8 }}>
      <label>제목 <input name="title" required minLength={5} maxLength={100} /></label>
      {errors.title && <small style={{ color: "crimson" }}>{errors.title.join(", ")}</small>}

      <label>내용 <textarea name="body" rows={5} required minLength={10} maxLength={4000} /></label>
      {errors.body && <small style={{ color: "crimson" }}>{errors.body.join(", ")}</small>}

      <label>마감일 <input name="dueDate" type="date" /></label>
      {errors.dueDate && <small style={{ color: "crimson" }}>{errors.dueDate.join(", ")}</small>}

      <button type="submit" disabled={pending}>{pending ? "저장 중…" : "접수"}</button>
      {message && <p role="status">{message}</p>}
    </form>
  );
}
