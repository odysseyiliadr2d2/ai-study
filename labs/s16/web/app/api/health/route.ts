// app/api/health/route.ts — 헬스체크: 앱이 떠 있고 필수 설정이 있는지만 확인 (DB · LLM 은 부르지 않음)
export const dynamic = "force-dynamic";

const REQUIRED = ["NEXT_PUBLIC_SUPABASE_URL", "NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY", "ANTHROPIC_API_KEY"];

export function GET() {
  const missing = REQUIRED.filter((name) => !process.env[name]);
  // 어떤 변수가 빠졌는지는 로그에만, 응답에는 상태만
  if (missing.length > 0) {
    console.error("필수 환경변수 누락:", missing.join(", "));
    return Response.json({ status: "DOWN" }, { status: 503 });
  }
  return Response.json({ status: "UP", version: process.env.APP_VERSION ?? "dev" });
}
