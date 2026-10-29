// lib/requests.ts — 세션 14의 타입·스키마는 그대로, 저장소만 Supabase 로 바꿉니다
import type { SupabaseClient } from "@supabase/supabase-js";
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
  dueDate: string | null;
  status: Status;
  category: Category | null;
  priority: Priority | null;
  summary: string | null;
  requesterId: string;
  createdAt: string;
};

const today = () => new Date().toISOString().slice(0, 10);

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

/** AI 분석 결과 중 저장하는 값 */
export type AnalysisFields = { summary: string; category: Category; priority: Priority };

// ── DB 행(snake_case) ↔ 앱 객체(camelCase)
type Row = {
  id: string;
  title: string;
  body: string;
  due_date: string | null;
  status: Status;
  category: Category | null;
  priority: Priority | null;
  summary: string | null;
  requester_id: string;
  created_at: string;
};

const COLUMNS = "id, title, body, due_date, status, category, priority, summary, requester_id, created_at";

function fromRow(r: Row): WorkRequest {
  return {
    id: r.id,
    title: r.title,
    body: r.body,
    dueDate: r.due_date,
    status: r.status,
    category: r.category,
    priority: r.priority,
    summary: r.summary,
    requesterId: r.requester_id,
    createdAt: r.created_at,
  };
}

// 모든 함수는 '로그인한 사용자의' 클라이언트를 받습니다 → RLS 가 자동으로 걸립니다
export async function listRequests(sb: SupabaseClient, status?: Status): Promise<WorkRequest[]> {
  let query = sb.from("work_requests").select(COLUMNS);
  if (status) query = query.eq("status", status);
  const { data, error } = await query.order("created_at", { ascending: false }).limit(100);
  if (error) throw new Error(error.message);
  return (data as unknown as Row[]).map(fromRow);
}

export async function getRequest(sb: SupabaseClient, id: string): Promise<WorkRequest | null> {
  const { data, error } = await sb.from("work_requests").select(COLUMNS).eq("id", id).maybeSingle();
  if (error) throw new Error(error.message);
  return data ? fromRow(data as unknown as Row) : null;   // 남의 요청이면 RLS 때문에 null
}

export async function createRequest(
  sb: SupabaseClient,
  input: CreateRequestInput,
  requesterId: string,
  analysis: AnalysisFields | null,
): Promise<WorkRequest> {
  const { data, error } = await sb
    .from("work_requests")
    .insert({
      title: input.title,
      body: input.body,
      due_date: input.dueDate ?? null,
      requester_id: requesterId,
      summary: analysis?.summary ?? null,
      category: analysis?.category ?? null,
      priority: analysis?.priority ?? null,
    })
    .select(COLUMNS)
    .single();
  if (error) throw new Error(error.message);
  return fromRow(data as unknown as Row);
}

export async function updateStatus(sb: SupabaseClient, id: string, status: Status): Promise<WorkRequest | null> {
  const { data, error } = await sb
    .from("work_requests")
    .update({ status })
    .eq("id", id)
    .select(COLUMNS)
    .maybeSingle();
  if (error) throw new Error(error.message);
  return data ? fromRow(data as unknown as Row) : null;   // 담당자가 아니면 RLS 때문에 null → 404
}
