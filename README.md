# AX Study · AI 풀스택 스터디 사이트

AI 업무자동화부터 서비스·에이전트 개발까지 **6파트 24세션** 스터디 사이트입니다.
16주 AI 기술 스터디 커리큘럼과 「AI 업무자동화·서비스·에이전트 개발 로드맵」을 병합했고,
세션마다 **개념 · 이론(도식) · 실습 코드 · 실무 예제 · 퀴즈·자료** 다섯 장의 카드로 구성됩니다.
카카오 로그인 · 운영자 승인 · 진도 대시보드는 이전 16주 사이트에서 그대로 옮겨 왔습니다.

- 현재 버전: **v1.1** (2026-10-29) — 변경 내역은 [CHANGELOG.md](CHANGELOG.md)와 사이트의 '버전 기록' 페이지
- 디자인: AX Study 디자인 시스템 (차콜 + 오렌지 한 가지 강조색, Pretendard)
- 외부 CDN 없이 동작합니다. 글꼴(Pretendard 서브셋), 코드 하이라이터(Prism), supabase-js를 `assets/vendor/`에 포함했습니다. 로그인 서버에 닿지 않으면 진도는 브라우저에만 저장됩니다.

## 파트 구성

| 파트 | 세션 | 주제 | 산출물 |
| --- | --- | --- | --- |
| 1. AI 기초 | 1–4 | AI 개념, LLM 원리, 프롬프트, 도구 지도 | 업무용 프롬프트 템플릿 5종 |
| 2. 업무자동화 | 5–8 | n8n, LLM API, AI Agent 노드, 임베딩·벡터DB | 문의 메일 처리 n8n 에이전트 |
| 3. RAG · 데이터 | 9–12 | RAG 기초·고급·평가, Text-to-SQL, 메타데이터 | 사내 문서 Q&A RAG + Text-to-SQL 데모 |
| 4. AI 코딩 · 풀스택 | 13–16 | 코딩 에이전트, 화면·API, DB·인증·AI, 배포 | 웹 서비스 v1 (기본 트랙 / Java 트랙) |
| 5. 에이전트 · MCP | 17–20 | 도구 호출 루프, MCP 서버, Agent SDK·OpenClaw, 운영·평가 | MCP 서버 + 상시 운영 에이전트 |
| 6. 전문 · 사업화 | 21–24 | 폐쇄망 LLM, 파인튜닝 vs RAG, 금융 AI 거버넌스, 사업화 | 팀별 AX 제안서 |

## 폴더 구조

```
index.html            홈: 파트별 세션 카드, 검색, 내 진도
session.html          세션 상세: ?id=9#lab 처럼 탭까지 바로 열기
me.html               내 진도: 카카오 로그인, 가입 신청, 세션별 진도, 운영자 승인 관리
dashboard.html        대시보드: 승인 멤버의 세션별 완료 인원, 멤버 × 세션 표
changelog.html        버전 기록
config.js             용어집 · 저장소 · Supabase 주소
supabase/schema.sql   테이블·함수 정의 (참고용, 이미 적용됨)
data/
  curriculum.json     파트·세션 목록 (카드에 쓰는 제목·요약·태그)
  version.json        현재 버전과 이력
  sessions/s01..s24.json   세션 본문 (개념·이론·실습·예제)
  quiz.json           세션별 확인 퀴즈 {세션: [{q, options[4], answer(0부터), explain}]}
  media.json          세션별 추천 영상·글
  terms.json          세션별 용어집 연결 (glossary.json의 id)
  glossary.json       실무 AI 용어집 사본 (용어 이름·한 줄 설명)
diagrams/             세션 도식 SVG (생성물)
labs/sNN/             실습 코드 파일 (세션 JSON에서 꺼낸 생성물)
assets/               CSS·JS·글꼴·Prism
tools/
  diagram.py          도식 생성기 (tools/diagrams/*.json → diagrams/*.svg)
  diagrams/           도식 사양 JSON
  validate.py         세션 데이터 검증
  export_labs.py      실습 코드를 labs/ 로 내보내기
```

## 로컬에서 보기

데이터를 `fetch`로 읽기 때문에 파일을 더블클릭하지 말고 간단한 서버로 엽니다.

```bash
cd ai-study
python3 -m http.server 8000
# 브라우저에서 http://localhost:8000
```

## GitHub Pages로 공개

이 저장소(`odysseyiliadr2d2/ai-study`)의 `main` 브랜치 루트가 그대로 Pages로 배포됩니다.
`main`에 올리면 1–2분 뒤 https://odysseyiliadr2d2.github.io/ai-study/ 에 반영됩니다.

- 이전 16주 사이트: `claude/legacy-16week` 브랜치
- 이전 주소 `#w3`, `#dashboard`, `#me`는 새 세션·대시보드·내 진도 화면으로 자동 연결됩니다.

## 내용 고치기

세션 본문은 `data/sessions/sNN.json` 한 파일에 다 있습니다.

```jsonc
{
  "id": 9, "part": 3, "title": "RAG 기초", "summary": "...", "level": "중급", "tags": ["Python", "RAG"],
  "duration": "120분 (설명 40 · 실습 60 · 토론 20)",
  "goals": ["...", "...", "..."],
  "output": "이 세션의 산출물",
  "concept": { "lead": "...", "body": ["문단", "..."], "diagram": {"src": "diagrams/s09-concept.svg", "caption": "..."},
               "terms": [{"term": "...", "desc": "..."}] },
  "theory":  { "lead": "...", "diagram": {...}, "sections": [{"title": "...", "body": ["..."], "bullets": ["..."], "code": {"lang": "python", "src": "..."}}] },
  "lab":     { "title": "...", "lead": "...", "env": ["Python 3.11+"], "steps": [{"title": "...", "body": ["..."], "code": {"lang": "python", "file": "labs/s09/ask.py", "src": "..."}}], "check": ["..."] },
  "example": { "title": "...", "context": "...", "diagram": {...}, "flow": ["..."], "code": {...}, "tips": ["..."], "pitfalls": ["..."] },
  "refs":    [{"label": "...", "url": "https://..."}]
}
```

- 본문에 쓸 수 있는 HTML 태그는 `<b> <strong> <em> <code> <br> <sub> <sup> <a href="...">` 뿐입니다. 나머지는 글자 그대로 보입니다.
- `id`, `part`, `title`, `summary`, `level`, `tags`는 `data/curriculum.json`과 같아야 합니다 (검증기가 확인).
- 코드의 `file`을 `labs/sNN/...`로 쓰면 `export_labs.py`가 실제 파일로 꺼냅니다.

고친 뒤에는 항상:

```bash
python3 tools/diagram.py          # 도식 사양을 바꿨다면
python3 tools/export_labs.py      # 실습 코드를 바꿨다면
python3 tools/validate.py         # 오류 0건 확인
```

`main` 브랜치에 올리면 GitHub Actions(`.github/workflows/validate.yml`)가 같은 검증을 돌립니다.

## 도식 사양

도식은 그림 파일을 직접 고치지 않고 `tools/diagrams/sNN-*.json` 사양을 고친 뒤 다시 생성합니다.
격자(cols × rows)에 박스를 놓고, 화살표·그룹·번호 배지·아이콘을 지정합니다. 형식은 `tools/diagram.py` 맨 위 설명을 보세요.
연결선이 다른 박스를 지나가면 생성기가 경고를 냅니다.

```json
{
  "title": "요점 한 문장",
  "cols": 5, "rows": 2,
  "nodes": [{"id": "q", "col": 0, "row": 1, "label": "질문", "sub": "설명", "icon": "user", "color": "orange", "badge": 1}],
  "edges": [{"from": "q", "to": "r", "label": "검색", "dashed": true}],
  "groups": [{"label": "질의 단계", "col": 0, "row": 1, "cols": 5, "rows": 1, "color": "orange"}]
}
```

## 버전 관리 규칙

로드맵 문서와 이 사이트는 같은 버전 번호를 씁니다.

- **v1.x** — 도구·문구·예제 보강, 출처 갱신 (세션 구성은 그대로)
- **v2.0** — 세션 구성이나 순서가 바뀔 때

버전을 올릴 때 바꿀 곳: 로드맵 문서의 '버전 이력' 표 → `VERSION` → `data/version.json` → `data/curriculum.json`의 `version`·`updated` → `CHANGELOG.md` → `git tag vX.Y`.
이전 버전 사이트가 필요하면 태그를 체크아웃해서 보면 됩니다.

## 로그인 · 진도 · 대시보드

- **로그인**: Supabase Auth의 카카오 로그인(PKCE). 돌아오는 주소는 사이트 첫 화면 하나로 고정되어 있어
  Supabase **Authentication → URL Configuration**의 Redirect URL 목록을 바꿀 필요가 없습니다.
- **가입 · 승인**: 로그인 후 '내 진도'에서 가입 신청 → 운영자(`study_members.is_admin`)가 같은 화면에서 승인합니다.
- **진도**: 세션 화면의 '개념·이론 학습 완료', '실습 완료' 버튼과 퀴즈 점수(최고 점수)를 저장합니다.
  로그인 전·승인 전에는 브라우저에만 저장되고, 승인되면 서버로 옮겨집니다.
- **DB**: Supabase 프로젝트 `ai-glossary`의 `study_members`, `study_progress` 테이블과
  `study_join / study_save / study_dashboard / study_admin_members / study_set_status` 함수.
  `study_progress.week` 열이 세션 번호(1–24)입니다. v1.1에서 범위를 1–16에서 1–24로 넓혔습니다 (교체 시점에 저장된 기록 없음).
- **첫 운영자 지정**: 처음 가입 신청한 뒤 Supabase SQL 편집기에서 한 번 실행합니다.
  ```sql
  update study_members set status = 'approved', is_admin = true where name = '<내 이름>';
  ```
- `config.js`의 publishable 키는 공개용 키입니다. 데이터 보호는 RLS와 함수 안의 승인·운영자 검사로 합니다.

## 라이선스

- 콘텐츠: AX 비즈니스 전환 스터디 내부 학습용
- Pretendard: SIL Open Font License 1.1 (`assets/vendor/pretendard/LICENSE.txt`)
- Prism: MIT (`assets/vendor/prism/LICENSE.txt`)
- supabase-js: MIT (`assets/vendor/supabase/LICENSE.txt`)
