# 격리된 VM/별도 OS 계정에서만 실행하세요 (회사 PC·업무 계정 금지). 명령은 docs.openclaw.ai 에서 최신 확인.
npm install -g openclaw@latest --allow-scripts=openclaw   # Node 24.x 이상 필요
openclaw onboard --install-daemon                         # 모델·채널(Telegram 등) 연결, 게이트웨이 상시 실행

# 워크스페이스(기본 ~/.openclaw/workspace)에 지침·스킬 복사
cp AGENTS.md ~/.openclaw/workspace/
cp -r skills/morning-ops-brief ~/.openclaw/workspace/skills/

# 세션 18의 MCP 서버를 읽기 전용으로 연결 (OPS_ENABLE_WRITE 를 주지 않으므로 쓰기 도구 없음)
openclaw mcp add ops --command python3 --arg /opt/ops-mcp/ops_server.py --cwd /opt/ops-mcp
openclaw mcp doctor ops --probe

# 평일 07:30 브리핑을 채널로 발송
openclaw cron add --name "아침 운영 브리핑" --cron "30 7 * * 1-5" --tz "Asia/Seoul" \
  --session isolated --message "morning-ops-brief 스킬로 어젯밤 배치 브리핑을 작성해 줘." \
  --announce --channel telegram --to "<운영 채널 ID>"

openclaw security audit                                   # 설정이 기본 보안 권고에서 벗어났는지 점검
