// 카카오 인가 코드 → ID 토큰(OIDC) 교환
// Supabase 기본 카카오 로그인은 account_email 동의항목을 항상 요청해서(비즈 앱 필요) 쓰지 않고,
// 브라우저가 카카오에서 받은 code 를 여기로 보내면 id_token 만 돌려줍니다.
// 브라우저는 그 id_token 으로 supabase.auth.signInWithIdToken({ provider: "kakao" }) 을 호출합니다.
//
// 비밀값 (Edge Functions → Secrets)
//   KAKAO_REST_API_KEY   카카오 앱 REST API 키 (필수, Supabase Kakao Client ID 와 같아야 함)
//   KAKAO_CLIENT_SECRET  카카오 로그인 Client Secret (카카오에서 '사용함'일 때 필수)

const ALLOWED_ORIGINS = [
  "https://odysseyiliadr2d2.github.io",
  "http://localhost:8000",
  "http://127.0.0.1:8000",
];
const ALLOWED_REDIRECTS = [
  "https://odysseyiliadr2d2.github.io/ai-study/",
  "http://localhost:8000/",
  "http://127.0.0.1:8000/",
];

function cors(origin: string | null) {
  const ok = origin && ALLOWED_ORIGINS.includes(origin);
  return {
    "Access-Control-Allow-Origin": ok ? origin! : ALLOWED_ORIGINS[0],
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
    "Vary": "Origin",
  };
}
const json = (body: unknown, status: number, h: Record<string, string>) =>
  new Response(JSON.stringify(body), { status, headers: { ...h, "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  const h = cors(req.headers.get("Origin"));
  if (req.method === "OPTIONS") return new Response("ok", { headers: h });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405, h);

  const clientId = Deno.env.get("KAKAO_REST_API_KEY");
  if (!clientId) return json({ error: "not_configured", message: "KAKAO_REST_API_KEY 비밀값이 없습니다" }, 500, h);

  let body: { code?: string; redirect_uri?: string };
  try { body = await req.json(); } catch { return json({ error: "bad_request" }, 400, h); }
  const { code, redirect_uri } = body;
  if (!code || typeof code !== "string" || code.length > 512) return json({ error: "bad_request", message: "code 가 없습니다" }, 400, h);
  if (!redirect_uri || !ALLOWED_REDIRECTS.includes(redirect_uri)) return json({ error: "bad_redirect" }, 400, h);

  const form = new URLSearchParams({ grant_type: "authorization_code", client_id: clientId, redirect_uri, code });
  const secret = Deno.env.get("KAKAO_CLIENT_SECRET");
  if (secret) form.set("client_secret", secret);

  const r = await fetch("https://kauth.kakao.com/oauth/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded;charset=utf-8" },
    body: form,
  });
  const t = await r.json().catch(() => ({}));
  if (!r.ok || !t.id_token) {
    // 카카오 오류 코드만 돌려줌 (토큰 등 민감값은 돌려주지 않음)
    return json({ error: t.error || "kakao_error", error_code: t.error_code, message: t.error_description || "카카오 토큰 발급 실패" }, 400, h);
  }
  // access_token 은 쓰지 않으므로 돌려주지 않습니다
  return json({ id_token: t.id_token }, 200, h);
});
