# ── 기본 트랙: ticket-desk-web (세션 14 프로젝트에 이어서)
cd ticket-desk-web
npm install @supabase/supabase-js @supabase/ssr @anthropic-ai/sdk

# 환경변수 — .env.local 은 git 에 올라가지 않습니다 (create-next-app 의 .gitignore 에 .env* 포함)
cat > .env.local <<'EOF'
NEXT_PUBLIC_SUPABASE_URL=https://your-project-ref.supabase.co
NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=sb_publishable_xxx
ANTHROPIC_API_KEY=sk-ant-xxx
CLAUDE_MODEL=claude-sonnet-5-5
N8N_WEBHOOK_URL=https://n8n.example.com/webhook/ticket-created
N8N_WEBHOOK_SECRET=change-me-long-random
APP_BASE_URL=http://localhost:3000
EOF

# 서버용 Supabase 클라이언트 (Supabase 공식 Next.js 예제와 같음)
mkdir -p lib/supabase app/login
cat > lib/supabase/server.ts <<'EOF'
import { createServerClient } from "@supabase/ssr";
import { cookies } from "next/headers";

export async function createClient() {
  const cookieStore = await cookies();
  return createServerClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY!,
    {
      cookies: {
        getAll() {
          return cookieStore.getAll();
        },
        setAll(cookiesToSet) {
          try {
            cookiesToSet.forEach(({ name, value, options }) => cookieStore.set(name, value, options));
          } catch {
            // 서버 컴포넌트에서는 쿠키를 못 씁니다. proxy.ts 가 세션을 갱신하므로 무시해도 됩니다.
          }
        },
      },
    },
  );
}
EOF

# 로그인 화면 (이메일 + 비밀번호)
cat > app/login/page.tsx <<'EOF'
"use client";

import { createBrowserClient } from "@supabase/ssr";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

export default function LoginPage() {
  const router = useRouter();
  const [error, setError] = useState("");

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    const supabase = createBrowserClient(
      process.env.NEXT_PUBLIC_SUPABASE_URL!,
      process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY!,
    );
    const { error } = await supabase.auth.signInWithPassword({
      email: String(form.get("email")),
      password: String(form.get("password")),
    });
    if (error) {
      setError("이메일 또는 비밀번호가 맞지 않습니다");
      return;
    }
    router.push("/requests");
    router.refresh();
  }

  return (
    <main style={{ maxWidth: 360, margin: "80px auto" }}>
      <h1>업무 요청 로그인</h1>
      <form onSubmit={onSubmit} style={{ display: "grid", gap: 8 }}>
        <input name="email" type="email" placeholder="이메일" required />
        <input name="password" type="password" placeholder="비밀번호" required />
        <button type="submit">로그인</button>
        {error && <p role="alert">{error}</p>}
      </form>
    </main>
  );
}
EOF
# 세션 14 파일 중 저장소를 부르는 두 파일을 비동기 · Supabase 클라이언트 방식으로 덮어씁니다
# (lib/requests.ts 는 3단계, app/api/requests/route.ts 는 5단계 코드로 교체)
cat > app/requests/page.tsx <<'EOF'
// app/requests/page.tsx — 목록 화면 (서버 컴포넌트, 로그인 사용자의 RLS 가 적용된 조회)
import { connection } from "next/server";
import { listRequests, type Status } from "@/lib/requests";
import { createClient } from "@/lib/supabase/server";
import { RequestForm } from "./request-form";

const STATUS_LABEL: Record<Status, string> = {
  RECEIVED: "접수",
  IN_PROGRESS: "처리 중",
  DONE: "완료",
};

export default async function RequestsPage() {
  await connection();
  const supabase = await createClient();
  const items = await listRequests(supabase);   // 본인 것만, 담당자는 전체 (RLS)

  return (
    <main style={{ maxWidth: 960, margin: "0 auto", padding: 24 }}>
      <h1>업무 요청</h1>
      <RequestForm />
      <table style={{ width: "100%", marginTop: 24 }}>
        <thead>
          <tr><th>제목</th><th>상태</th><th>분류</th><th>우선순위</th><th>요약</th><th>접수일시</th></tr>
        </thead>
        <tbody>
          {items.map((r) => (
            <tr key={r.id}>
              <td>{r.title}</td>
              <td>{STATUS_LABEL[r.status]}</td>
              <td>{r.category ?? "-"}</td>
              <td>{r.priority ?? "-"}</td>
              <td>{r.summary ?? "-"}</td>
              <td>{new Date(r.createdAt).toLocaleString("ko-KR")}</td>
            </tr>
          ))}
          {items.length === 0 && (
            <tr><td colSpan={6}>등록된 요청이 없습니다.</td></tr>
          )}
        </tbody>
      </table>
    </main>
  );
}
EOF

mkdir -p "app/api/requests/[id]"
cat > "app/api/requests/[id]/route.ts" <<'EOF'
// app/api/requests/[id]/route.ts — 상세 조회(GET)와 상태 변경(PATCH), 로그인 + RLS 적용
import { z } from "zod";
import { getRequest, statusChangeSchema, updateStatus } from "@/lib/requests";
import { createClient } from "@/lib/supabase/server";

type Ctx = { params: Promise<{ id: string }> };

async function currentUser() {
  const supabase = await createClient();
  const { data } = await supabase.auth.getClaims();
  return { supabase, userId: (data?.claims?.sub as string | undefined) ?? null };
}

const unauthorized = () => Response.json({ message: "로그인이 필요합니다" }, { status: 401 });
const notFound = () => Response.json({ message: "요청을 찾을 수 없습니다" }, { status: 404 });

export async function GET(_request: Request, { params }: Ctx) {
  const { supabase, userId } = await currentUser();
  if (!userId) return unauthorized();
  const { id } = await params;
  const item = await getRequest(supabase, id);   // 남의 요청은 RLS 때문에 null → 404
  return item ? Response.json(item) : notFound();
}

export async function PATCH(request: Request, { params }: Ctx) {
  const { supabase, userId } = await currentUser();
  if (!userId) return unauthorized();
  const { id } = await params;
  const parsed = statusChangeSchema.safeParse(await request.json().catch(() => null));
  if (!parsed.success) {
    return Response.json(
      { message: "상태 값을 확인하세요", errors: z.flattenError(parsed.error).fieldErrors },
      { status: 400 },
    );
  }
  const updated = await updateStatus(supabase, id, parsed.data.status);   // 담당자가 아니면 null → 404
  return updated ? Response.json(updated) : notFound();
}
EOF

# Supabase 대시보드: Authentication > Users 에서 테스트 사용자 2명(요청자 · 담당자) 생성
# SQL Editor 에서 supabase/migrations/001_work_requests.sql 실행

# ── Java 트랙: ticket-desk-api — 로컬 Oracle (Oracle Database Free 컨테이너)
docker run -d --name oracle-free -p 1521:1521 \
  -e ORACLE_PASSWORD="$ORACLE_SYS_PASSWORD" \
  -e APP_USER=ticketdesk -e APP_USER_PASSWORD="$DB_PASSWORD" \
  gvenzl/oracle-free:23-slim
docker logs -f oracle-free        # "DATABASE IS READY TO USE!" 확인 후 Ctrl+C

docker exec -i oracle-free sqlplus -s "ticketdesk/$DB_PASSWORD@localhost/FREEPDB1" \
  < db/V1__work_request.sql

# 컨트롤러를 로그인 사용자(Authentication)를 넘기도록 수정 — 세션 14 파일을 덮어씁니다
PKG=src/main/java/com/example/ticketdesk/request
cat > $PKG/WorkRequestApiController.java <<'EOF'
package com.example.ticketdesk.request;

import com.example.ticketdesk.request.WorkRequestDtos.CreateRequest;
import com.example.ticketdesk.request.WorkRequestDtos.Response;
import com.example.ticketdesk.request.WorkRequestDtos.StatusChange;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/requests")
public class WorkRequestApiController {

    private final WorkRequestService service;

    public WorkRequestApiController(WorkRequestService service) {
        this.service = service;
    }

    @GetMapping
    public List<Response> list(@RequestParam(required = false) WorkRequest.Status status, Authentication auth) {
        return service.list(status, auth).stream().map(Response::from).toList();
    }

    @GetMapping("/{id}")
    public Response get(@PathVariable Long id, Authentication auth) {
        return Response.from(service.get(id, auth));
    }

    @PostMapping
    public ResponseEntity<Response> create(@Valid @RequestBody CreateRequest body, Authentication auth) {
        WorkRequest created = service.create(body, auth.getName());
        return ResponseEntity.created(URI.create("/api/requests/" + created.getId()))
                .body(Response.from(created));
    }

    @PatchMapping("/{id}")
    public Response changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChange body, Authentication auth) {
        return Response.from(service.changeStatus(id, body.status(), auth));
    }
}
EOF
cat > $PKG/WorkRequestPageController.java <<'EOF'
package com.example.ticketdesk.request;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WorkRequestPageController {

    private final WorkRequestService service;

    public WorkRequestPageController(WorkRequestService service) {
        this.service = service;
    }

    @GetMapping("/requests")
    public String list(Model model, Authentication auth) {
        model.addAttribute("items", service.list(null, auth));
        return "requests";
    }
}
EOF

export DB_URL=jdbc:oracle:thin:@localhost:1521/FREEPDB1 DB_USER=ticketdesk
export ANTHROPIC_API_KEY=sk-ant-xxx DEMO_PASSWORD=change-me
./mvnw spring-boot:run            # http://localhost:8080/requests → 로그인 화면
