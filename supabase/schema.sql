-- AI Tech 스터디 진도 저장 (Supabase 프로젝트 ai-glossary에 적용됨)
create extension if not exists pgcrypto with schema extensions;

create table public.study_members (
  id uuid primary key default gen_random_uuid(),
  name text not null unique check (char_length(name) between 1 and 20),
  org text check (char_length(org) <= 30),
  pin_hash text not null,
  created_at timestamptz not null default now()
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
alter table public.study_members enable row level security;   -- 정책 없음: 직접 접근 불가
alter table public.study_progress enable row level security;

-- study_check(name, pin) → member id (내부용, 외부 실행 불가)
-- study_register(name, pin, org)  : 등록 (PIN 숫자 4~8자리, 최대 60명)
-- study_login(name, pin)          : PIN 확인
-- study_save(name, pin, week, notes, lab, quiz_score, quiz_total) : 진도 저장 (퀴즈는 최고 점수 유지)
-- study_dashboard()               : 멤버별 진도 (PIN 해시 제외)
-- 함수 본문은 Supabase 마이그레이션 study_progress_tracking, study_dashboard_as_function 참고
