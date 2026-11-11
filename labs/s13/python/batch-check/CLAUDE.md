# CLAUDE.md — batch-check (Python)

@AGENTS.md

## Claude Code 전용
- 작업 지시가 오면 먼저 plan 모드로 계획(바꿀 파일 · 추가할 테스트)만 제시하고 승인 후 수정합니다.
- 수정이 끝나면 `ruff check . && pytest -q` 를 직접 실행해 결과 끝부분을 붙여 보고합니다.
- 커밋 전 테스트는 `.claude/settings.json` 의 훅이 돌립니다 (`pytest` 를 실행하는 test-before-commit.sh).
