-- AI Tech 스터디: 카카오 로그인 + 운영자 승인 (Supabase 프로젝트 ai-glossary)
-- 적용된 마이그레이션: study_kakao_auth_approval
create table public.study_members (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null unique references auth.users(id) on delete cascade,
  name text not null check (char_length(name) between 1 and 20),
  org text check (char_length(org) <= 30),
  status text not null default 'pending' check (status in ('pending','approved','rejected')),
  is_admin boolean not null default false,
  created_at timestamptz not null default now(),
  approved_at timestamptz
);
create table public.study_progress (
  member_id uuid not null references public.study_members(id) on delete cascade,
  week smallint not null check (week between 1 and 16),
  notes_done boolean not null default false,
  lab_done boolean not null default false,
  quiz_score smallint,
  quiz_total smallint,
  updated_at timestamptz not null default now(),
  primary key (member_id, week)
);
-- RLS: 본인 행 select만 허용. 쓰기는 아래 security definer 함수로만 (authenticated 전용)
-- study_join(name, org)            가입 신청 → pending
-- study_save(week, notes, lab, score, total)   승인 멤버만
-- study_dashboard()                승인 멤버만, 승인된 멤버 현황
-- study_admin_members()            운영자만, 전체 신청 목록
-- study_set_status(member, status) 운영자만, 승인·거절·승인 취소 (본인 제외)
