-- supabase/migrations/001_work_requests.sql
-- 업무 요청 테이블 + RLS(행 수준 보안). SQL Editor 또는 supabase CLI 로 실행합니다.

create table public.work_requests (
  id           uuid primary key default gen_random_uuid(),
  title        text not null check (char_length(title) between 5 and 100),
  body         text not null check (char_length(body) between 10 and 4000),
  due_date     date,
  status       text not null default 'RECEIVED'
               check (status in ('RECEIVED', 'IN_PROGRESS', 'DONE')),
  category     text check (category in ('DATA_EXTRACT', 'BATCH_ISSUE', 'ACCESS', 'REPORT', 'ETC')),
  priority     text check (priority in ('LOW', 'MEDIUM', 'HIGH')),
  summary      text check (char_length(summary) <= 1000),
  requester_id uuid not null default auth.uid() references auth.users (id),
  created_at   timestamptz not null default now()
);

-- 정책에 쓰는 컬럼에는 인덱스 (RLS 성능 권장 사항)
create index work_requests_requester_idx on public.work_requests (requester_id, created_at desc);

-- SQL 로 만든 테이블은 RLS 를 직접 켜야 합니다. 켜고 정책이 없으면 아무도 못 읽습니다.
alter table public.work_requests enable row level security;
grant select, insert, update on public.work_requests to authenticated;

-- 담당자 여부는 app_metadata.role 로 판단합니다 (user_metadata 는 사용자가 바꿀 수 있어 쓰면 안 됨)
create policy "본인 요청 또는 담당자만 조회"
on public.work_requests for select
to authenticated
using (
  (select auth.uid()) = requester_id
  or (select auth.jwt() -> 'app_metadata' ->> 'role') = 'handler'
);

create policy "본인 이름으로, 접수 상태로만 등록"
on public.work_requests for insert
to authenticated
with check (
  (select auth.uid()) = requester_id
  and status = 'RECEIVED'
);

create policy "담당자만 수정"
on public.work_requests for update
to authenticated
using ((select auth.jwt() -> 'app_metadata' ->> 'role') = 'handler')
with check ((select auth.jwt() -> 'app_metadata' ->> 'role') = 'handler');

-- delete 정책은 만들지 않습니다 = 아무도 삭제할 수 없음 (이력 보존)

-- 담당자 지정 (관리자가 SQL Editor 에서 실행, 다시 로그인해야 토큰에 반영)
-- update auth.users
--    set raw_app_meta_data = raw_app_meta_data || '{"role": "handler"}'
--  where email = 'handler@example.com';
