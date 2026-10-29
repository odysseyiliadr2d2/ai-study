// app/api/requests/[id]/route.ts — 상세 조회(GET)와 상태 변경(PATCH)
// Next.js 15부터 params 는 Promise 입니다. 반드시 await 하세요.
// (타입 생성 후에는 RouteContext<'/api/requests/[id]'> 도우미 타입을 써도 됩니다)
import { z } from "zod";
import { getRequest, statusChangeSchema, updateStatus } from "@/lib/requests";

type Ctx = { params: Promise<{ id: string }> };

export async function GET(_request: Request, { params }: Ctx) {
  const { id } = await params;
  const item = getRequest(id);
  if (!item) return Response.json({ message: "요청을 찾을 수 없습니다" }, { status: 404 });
  return Response.json(item);
}

export async function PATCH(request: Request, { params }: Ctx) {
  const { id } = await params;
  const parsed = statusChangeSchema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) {
    return Response.json(
      { message: "상태 값을 확인하세요", errors: z.flattenError(parsed.error).fieldErrors },
      { status: 400 },
    );
  }
  const updated = updateStatus(id, parsed.data.status);
  if (!updated) return Response.json({ message: "요청을 찾을 수 없습니다" }, { status: 404 });
  return Response.json(updated);
}
