#!/usr/bin/env bash
# .claude/hooks/test-before-commit.sh
# Claude Code 가 git commit 을 실행하기 직전에 호출됩니다 (PreToolUse 훅).
# 종료 코드 2 = 도구 실행을 막고, stderr 내용이 에이전트에게 전달됩니다.
# chmod +x .claude/hooks/test-before-commit.sh
set -uo pipefail

cd "${CLAUDE_PROJECT_DIR:-.}"
LOG=$(mktemp)

if [ -x ./mvnw ]; then
  ./mvnw -q test >"$LOG" 2>&1
elif [ -f pyproject.toml ] || [ -f requirements.txt ]; then
  python -m pytest -q >"$LOG" 2>&1
else
  exit 0
fi
STATUS=$?

if [ $STATUS -ne 0 ]; then
  echo "테스트 실패로 커밋을 막았습니다. 아래 로그를 보고 원인을 고친 뒤 다시 커밋하세요." >&2
  tail -n 40 "$LOG" >&2
  rm -f "$LOG"
  exit 2
fi
rm -f "$LOG"
exit 0
