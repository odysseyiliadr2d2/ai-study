#!/usr/bin/env bash
# 한 작업 단위의 흐름: 브랜치 → 계획 → 작은 지시 → diff 검토 → 테스트 → 커밋 → 교차 리뷰
set -euo pipefail

git switch -c feat/status-filter

# 1) 계획만 받기 (코드 수정 없음)
claude --permission-mode plan "$(cat prompts/status-filter.md)"

# 2) 승인 후 일반 모드에서 수정하게 하고, 사람이 diff 를 읽습니다
claude "계획 1~2단계만 진행해 주세요"
git diff --stat
git diff                       # 한 줄씩 읽기. 이해 안 되는 줄이 있으면 커밋하지 않습니다

# 3) 테스트 — 커밋은 사람이 합니다 (에이전트가 커밋해도 훅이 테스트를 강제)
./mvnw -q verify
git add -p                     # 의도한 변경만 골라 담기
git commit -m "feat: 요청 목록 상태 필터 추가"

# 4) 교차 리뷰 — 작성하지 않은 도구가 읽기 전용으로 검토
codex exec --sandbox read-only \
  "git diff main...HEAD 를 리뷰해 줘. 버그, 누락된 검증, 테스트 빈틈, 보안 문제만 번호 목록으로."
# 또는 Codex 대화창에서 /review, Antigravity 에서 같은 요청

git push -u origin feat/status-filter   # PR 에 리뷰 결과를 붙여 사람이 최종 승인
