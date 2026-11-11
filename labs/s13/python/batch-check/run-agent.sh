# batch-check/run-agent.sh — 같은 작업을 에이전트에게 시키고 사람이 확인하는 흐름 (Python)
set -euo pipefail
cd batch-check
python -m venv .venv && source .venv/bin/activate && pip install -r requirements.txt
ruff check . && pytest -q                 # 시작 전: 3 passed 확인
git init -q 2>/dev/null; git add -A && git commit -qm "chore: batch-check 시작점" || true

git switch -c feat/failed-jobs

# 1) 계획만 받기 → 2) 승인 후 수정 (Claude Code)
claude --permission-mode plan "$(cat prompts/failed-jobs.md)"
claude "계획대로 진행해 주세요. 끝나면 ruff check . && pytest -q 결과를 붙여 주세요"

# 3) 사람이 diff 를 읽고 직접 테스트 (에이전트의 '통과했습니다' 를 그대로 믿지 않습니다)
git diff --stat && git diff
ruff check . && pytest -q                 # 6 passed

# 4) 커밋 — 훅이 테스트를 한 번 더 돌립니다
git add -p && git commit -m "feat: 실패 작업 목록 failed_jobs 추가"

# 5) 교차 리뷰 — 작성하지 않은 도구가 읽기 전용으로
codex exec --sandbox read-only "git diff main...HEAD 를 리뷰해 줘. 버그, 경계 조건, 테스트 빈틈만 번호 목록으로."
