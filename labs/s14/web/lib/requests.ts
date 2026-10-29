// lib/requests.ts — 업무 요청의 타입 · 입력 스키마 · (오늘만) 메모리 저장소
// 세션 15에서 저장소 부분을 Supabase 로 바꾸고, 타입과 스키마는 그대로 씁니다.
import { z } from "zod";

export const STATUSES = ["RECEIVED", "IN_PROGRESS", "DONE"] as const;
export const CATEGORIES = ["DATA_EXTRACT", "BATCH_ISSUE", "ACCESS", "REPORT", "ETC"] as const;
export const PRIORITIES = ["LOW", "MEDIUM", "HIGH"] as const;

export type Status = (typeof STATUSES)[number];
export type Category = (typeof CATEGORIES)[number];
export type Priority = (typeof PRIORITIES)[number];

export type WorkRequest = {
  id: string;
  title: string;
  body: string;
  dueDate: string | null;      // YYYY-MM-DD
  status: Status;
  category: Category | null;   // 세션 15에서 AI 가 채움
  priority: Priority | null;
  summary: string | null;
  requesterId: string;
  createdAt: string;           // ISO 8601
};

const today = () => new Date().toISOString().slice(0, 10);

// 등록 입력: 화면이 무엇을 보내든 서버에서 다시 검증합니다
export const createRequestSchema = z.object({
  title: z.string().trim().min(5, "제목은 5자 이상 입력하세요").max(100, "제목은 100자 이하로 입력하세요"),
  body: z.string().trim().min(10, "내용은 10자 이상 입력하세요").max(4000, "내용은 4000자 이하로 입력하세요"),
  dueDate: z.iso.date("날짜 형식은 YYYY-MM-DD 입니다")
    .refine((d) => d >= today(), "마감일은 오늘 이후여야 합니다")
    .optional(),
});
export type CreateRequestInput = z.infer<typeof createRequestSchema>;

export const statusChangeSchema = z.object({ status: z.enum(STATUSES) });
export const listQuerySchema = z.object({ status: z.enum(STATUSES).optional() });

// ── 메모리 저장소 (개발 서버가 다시 읽어도 유지되도록 globalThis 에 둡니다)
const g = globalThis as unknown as { __requests?: Map<string, WorkRequest> };
const store = (g.__requests ??= new Map<string, WorkRequest>());

export function listRequests(status?: Status): WorkRequest[] {
  return [...store.values()]
    .filter((r) => !status || r.status === status)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
}

export function getRequest(id: string): WorkRequest | undefined {
  return store.get(id);
}

export function createRequest(input: CreateRequestInput, requesterId: string): WorkRequest {
  const item: WorkRequest = {
    id: crypto.randomUUID(),
    title: input.title,
    body: input.body,
    dueDate: input.dueDate ?? null,
    status: "RECEIVED",
    category: null,
    priority: null,
    summary: null,
    requesterId,
    createdAt: new Date().toISOString(),
  };
  store.set(item.id, item);
  return item;
}

export function updateStatus(id: string, status: Status): WorkRequest | undefined {
  const item = store.get(id);
  if (!item) return undefined;
  const updated = { ...item, status };
  store.set(id, updated);
  return updated;
}
