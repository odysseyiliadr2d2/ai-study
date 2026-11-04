# 보안 점검 기록

## 2026-11-04 시큐어 코딩 점검

| 구분 | 점검 항목 | 결과 · 조치 |
| --- | --- | --- |
| DB 권한 | anon·authenticated 역할에 TRUNCATE·TRIGGER·REFERENCES 권한이 남아 있음 | **조치**: anon 권한 전부 회수, authenticated 는 SELECT 만 (RLS 로 본인 행만) |
| DB 함수 | 내부 판별 함수 `study_is_admin`·`study_is_approved` 가 RPC 로 직접 호출됨 | **조치**: 실행 권한 회수 (다른 함수 안에서만 사용) |
| 입력 검증 | `study_save` 가 퀴즈 점수·세션 번호 범위를 검사하지 않음 (대시보드 점수 조작 가능) | **조치**: 세션 1~24, 문항 수 1~20, 0 ≤ 점수 ≤ 문항 수 검사 |
| 입력 검증 | 이름·소속에 제어 문자·HTML 특수문자 허용 | **조치**: `study_check_profile` 로 길이·문자 검사 (서버), 같은 규칙을 화면에서도 먼저 검사 |
| 출력 | 화면에 그리는 사용자 값 | 모두 `esc()` 로 이스케이프 확인 (이름·소속·말풍선·입력칸 value) |
| XSS 방어 | 정적 사이트라 응답 헤더를 못 붙임 | **조치**: 모든 페이지에 CSP meta (`script-src 'self'`, 외부 연결은 Supabase 만), 인라인 스크립트 제거(`assets/js/go.js`) |
| 리다이렉트 | 로그인 후 돌아갈 화면(localStorage) 값을 그대로 이동 | **조치**: 사이트 안 `*.html` 형식만 허용, 그 외는 내 진도로 |
| OAuth | 카카오 로그인 state·nonce | 확인: state 비교, nonce(SHA-256) 를 Supabase 가 ID 토큰과 대조 |
| Edge Function | `kakao-token` 입력 | **조치**: 허용 Origin 외 403, code 형식 검사, `no-store`·`nosniff` 헤더. access_token 은 돌려주지 않음 |
| 비밀값 | 저장소의 키 | 확인: publishable 키·카카오 REST 키(둘 다 공개용)만 있음. Client Secret 은 Edge Function 비밀값 |
| 라이브러리 | Prism 1.29.0 (CVE-2024-53382, DOM clobbering) | **조치**: 1.30.0 으로 교체 |
| Referrer | 외부로 나가는 링크 | `strict-origin-when-cross-origin`, 새 창 링크는 `rel="noopener"` |

### 대시보드에서 직접 해야 하는 것 (코드로 바꿀 수 없음)
- Supabase **Authentication → Sign In / Providers → Email** 끄기 — 카카오만 쓰므로 이메일 가입 경로를 닫습니다.
  (점검 도구가 'Leaked password protection' 경고를 내는 것도 이메일 로그인이 켜져 있어서입니다.)
- `increment_hit` 함수는 실무 AI 용어집의 조회수 집계용으로, 로그인 없이 부르는 것이 의도된 동작입니다.
