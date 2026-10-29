// app/requests/page.tsx — 목록 화면 (서버 컴포넌트)
// 서버 컴포넌트는 같은 서버의 API 를 fetch 하지 않고 lib 함수를 직접 부릅니다.
import { connection } from "next/server";
import { listRequests, type Status } from "@/lib/requests";
import { RequestForm } from "./request-form";

const STATUS_LABEL: Record<Status, string> = {
  RECEIVED: "접수",
  IN_PROGRESS: "처리 중",
  DONE: "완료",
};

export default async function RequestsPage() {
  await connection(); // 빌드 때 미리 그리지 말고 요청마다 그리기
  const items = listRequests();

  return (
    <main style={{ maxWidth: 960, margin: "0 auto", padding: 24 }}>
      <h1>업무 요청</h1>
      <RequestForm />
      <table style={{ width: "100%", marginTop: 24 }}>
        <thead>
          <tr><th>제목</th><th>상태</th><th>분류</th><th>요약</th><th>접수일시</th></tr>
        </thead>
        <tbody>
          {items.map((r) => (
            <tr key={r.id}>
              <td>{r.title}</td>
              <td>{STATUS_LABEL[r.status]}</td>
              <td>{r.category ?? "-"}</td>
              <td>{r.summary ?? "-"}</td>
              <td>{new Date(r.createdAt).toLocaleString("ko-KR")}</td>
            </tr>
          ))}
          {items.length === 0 && (
            <tr><td colSpan={5}>등록된 요청이 없습니다.</td></tr>
          )}
        </tbody>
      </table>
    </main>
  );
}
