// app/api/requests/route.ts — 로그인 확인 → 검증 → AI 분석 → 저장 → (응답 후) n8n 알림
import { after, type NextRequest } from "next/server";
import { z } from "zod";
import { analyzeRequest } from "@/lib/ai";
import {
  createRequest,
  createRequestSchema,
  listQuerySchema,
  listRequests,
  type WorkRequest,
} from "@/lib/requests";
import { createClient } from "@/lib/supabase/server";

/** 쿠키의 세션을 검증해 사용자 ID 를 얻습니다. getSession() 이 아니라 getClaims() 를 씁니다. */
async function currentUser() {
  const supabase = await createClient();
  const { data } = await supabase.auth.getClaims();
  return { supabase, userId: (data?.claims?.sub as string | undefined) ?? null };
}

const unauthorized = () => Response.json({ message: "로그인이 필요합니다" }, { status: 401 });

export async function GET(request: NextRequest) {
  const { supabase, userId } = await currentUser();
  if (!userId) return unauthorized();

  const parsed = listQuerySchema.safeParse({
    status: request.nextUrl.searchParams.get("status") ?? undefined,
  });
  if (!parsed.success) {
    return Response.json(
      { message: "조회 조건을 확인하세요", errors: z.flattenError(parsed.error).fieldErrors },
      { status: 400 },
    );
  }
  return Response.json(await listRequests(supabase, parsed.data.status));
}

export async function POST(request: NextRequest) {
  const { supabase, userId } = await currentUser();
  if (!userId) return unauthorized();

  const parsed = createRequestSchema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) {
    return Response.json(
      { message: "입력값을 확인하세요", errors: z.flattenError(parsed.error).fieldErrors },
      { status: 400 },
    );
  }

  // AI 분석은 저장 전에. 실패해도 null 이 와서 등록은 계속됩니다.
  const analysis = await analyzeRequest(parsed.data.title, parsed.data.body);

  let created: WorkRequest;
  try {
    created = await createRequest(supabase, parsed.data, userId, analysis);
  } catch (err) {
    console.error("요청 저장 실패", err);
    return Response.json({ message: "저장하지 못했습니다" }, { status: 500 });
  }

  // 응답을 먼저 보내고 알림은 뒤에서 (알림 실패가 등록 실패가 되지 않게)
  after(() => notifyN8n(created));

  return Response.json(created, {
    status: 201,
    headers: { Location: `/api/requests/${created.id}` },
  });
}

async function notifyN8n(item: WorkRequest) {
  const url = process.env.N8N_WEBHOOK_URL;
  if (!url) return;
  try {
    const res = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Webhook-Secret": process.env.N8N_WEBHOOK_SECRET ?? "",
      },
      body: JSON.stringify({
        id: item.id,
        title: item.title,
        category: item.category,
        priority: item.priority,
        summary: item.summary,
        link: `${process.env.APP_BASE_URL ?? ""}/requests`,
      }),
      signal: AbortSignal.timeout(5000),
    });
    if (!res.ok) console.error("n8n 알림 응답 오류", res.status);
  } catch (err) {
    console.error("n8n 알림 실패", err);
  }
}
