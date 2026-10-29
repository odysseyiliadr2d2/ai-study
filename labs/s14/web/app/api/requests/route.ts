// app/api/requests/route.ts — 목록 조회(GET)와 등록(POST)
import type { NextRequest } from "next/server";
import { z } from "zod";
import { createRequest, createRequestSchema, listQuerySchema, listRequests } from "@/lib/requests";

export async function GET(request: NextRequest) {
  const parsed = listQuerySchema.safeParse({
    status: request.nextUrl.searchParams.get("status") ?? undefined,
  });
  if (!parsed.success) {
    return Response.json(
      { message: "조회 조건을 확인하세요", errors: z.flattenError(parsed.error).fieldErrors },
      { status: 400 },
    );
  }
  return Response.json(listRequests(parsed.data.status));
}

export async function POST(request: NextRequest) {
  let json: unknown;
  try {
    json = await request.json();
  } catch {
    return Response.json({ message: "JSON 형식이 아닙니다" }, { status: 400 });
  }

  const parsed = createRequestSchema.safeParse(json);
  if (!parsed.success) {
    return Response.json(
      { message: "입력값을 확인하세요", errors: z.flattenError(parsed.error).fieldErrors },
      { status: 400 },
    );
  }

  // 로그인은 세션 15에서 붙입니다. 오늘은 고정 사용자로 기록합니다.
  const created = createRequest(parsed.data, "demo-user");
  return Response.json(created, {
    status: 201,
    headers: { Location: `/api/requests/${created.id}` },
  });
}
