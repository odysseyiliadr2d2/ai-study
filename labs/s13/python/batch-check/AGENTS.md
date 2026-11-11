# AGENTS.md — batch-check (Python)

모든 코딩 에이전트(Claude Code, Codex CLI, Antigravity CLI)가 공통으로 읽는 규칙입니다. 100줄 이내로 유지합니다.

## 프로젝트
- 야간 배치 로그 요약 도구. Python 3.11+, 표준 라이브러리만 사용합니다.
- 코드: `batchcheck/`, 테스트: `tests/` (pytest). 테스트 데이터는 테스트 파일 안의 가짜 로그만 씁니다.

## 명령
- 준비: `python -m venv .venv && source .venv/bin/activate && pip install -r requirements.txt`
- 테스트: `pytest -q`
- 정적 검사: `ruff check .`
- 둘 다 통과해야 완료입니다: `ruff check . && pytest -q`

## 코딩 규칙
- 공개 함수에는 타입힌트와 한 줄 docstring.
- 기존 공개 함수(`parse_line`, `count_by_level`)의 이름 · 인자 · 반환 형태는 바꾸지 않습니다.
- 새 기능에는 테스트를 함께 씁니다 (정상 1개 이상 + 경계 1개 이상).

## 하지 말 것
- `requirements.txt` 에 실행 의존성을 추가하지 않습니다. 필요하면 이유를 적어 먼저 물어봅니다.
- 테스트를 지우거나 `skip` 으로 통과시키지 않습니다.
- 로그에 계좌번호 · 고객명 같은 값을 예시로 쓰지 않습니다.

## 완료 기준
- `ruff check . && pytest -q` 통과, 바꾼 파일 목록과 남은 위험을 3줄 이내로 보고.
