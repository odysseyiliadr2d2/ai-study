# CLAUDE.md — ticket-desk-api

@AGENTS.md

## Claude Code 전용 추가 규칙
- 3개 파일 이상 바꾸는 작업은 먼저 plan 모드로 계획만 제시하고 승인 후 수정합니다.
- 커밋 전 테스트는 `.claude/settings.json` 의 훅이 자동으로 돌립니다. 훅이 막으면 우회하지 말고 원인을 보고합니다.
- SQL 변경 리뷰는 `sql-reviewer` 서브에이전트에게 맡깁니다.
