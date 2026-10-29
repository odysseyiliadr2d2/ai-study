// proxy.ts (프로젝트 루트) — Next.js 16 에서 middleware.ts 가 proxy.ts 로 바뀌었습니다
// 할 일: (1) 만료된 로그인 토큰 갱신 (2) 로그인 안 한 사용자를 /login 으로
import { createServerClient } from "@supabase/ssr";
import { NextResponse, type NextRequest } from "next/server";

export async function proxy(request: NextRequest) {
  let response = NextResponse.next({ request });

  const supabase = createServerClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY!,
    {
      cookies: {
        getAll() {
          return request.cookies.getAll();
        },
        setAll(cookiesToSet, headers) {
          cookiesToSet.forEach(({ name, value }) => request.cookies.set(name, value));
          response = NextResponse.next({ request });
          cookiesToSet.forEach(({ name, value, options }) => response.cookies.set(name, value, options));
          // 세션이 담긴 응답이 CDN 에 캐시되지 않게 하는 헤더
          Object.entries(headers).forEach(([key, value]) => response.headers.set(key, value));
        },
      },
    },
  );

  // createServerClient 와 getClaims 사이에 다른 코드를 넣지 마세요 (공식 문서 주의 사항)
  const { data } = await supabase.auth.getClaims();

  const path = request.nextUrl.pathname;
  // API 는 리다이렉트 대신 Route Handler 가 401 을 돌려줍니다
  const isPublic = path.startsWith("/login") || path.startsWith("/api");
  if (!data?.claims && !isPublic) {
    const url = request.nextUrl.clone();
    url.pathname = "/login";
    return NextResponse.redirect(url);
  }
  return response;   // 쿠키가 담긴 이 응답 객체를 그대로 돌려줘야 합니다
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp)$).*)"],
};
