# batch-check/run-agent.sh — 같은 작업을 에이전트에게 시키고 사람이 확인하는 흐름 (Java)
set -euo pipefail
cd batch-check
# Maven Wrapper(mvnw)는 이 폴더에 들어 있습니다 — 따로 만들 필요 없음
./mvnw -q verify                          # 시작 전: 테스트 3개 통과 확인
git init -q 2>/dev/null; git add -A && git commit -qm "chore: batch-check 시작점" || true

git switch -c feat/failed-jobs

# 1) 계획만 받기 → 2) 승인 후 수정 (Codex CLI 로 해 보고, Python 트랙은 Claude Code 로 해서 비교해도 좋습니다)
codex "$(cat prompts/failed-jobs.md) — 먼저 계획만 보여 주고, 내가 승인하면 수정해 주세요"

# 3) 사람이 diff 를 읽고 직접 테스트
git diff --stat && git diff
./mvnw -q verify                          # Tests run: 6, Failures: 0

# 4) 커밋 — 훅이 ./mvnw -q test 를 한 번 더 돌립니다 (Claude Code 로 커밋할 때)
git add -p && git commit -m "feat: 실패 작업 목록 failedJobs 추가"

# 5) 교차 리뷰 — 작성하지 않은 도구가 읽기 전용으로
claude --permission-mode plan "git diff main...HEAD 를 리뷰해 줘. 버그, 경계 조건, 테스트 빈틈만 번호 목록으로."
