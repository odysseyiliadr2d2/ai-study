# 1) MCP Inspector 로 먼저 시험 (Node 22.19 이상, 설치 없이 npx)
npx @modelcontextprotocol/inspector python ops_server.py                    # 웹 UI
npx @modelcontextprotocol/inspector --cli python ops_server.py --method tools/list
npx @modelcontextprotocol/inspector --cli python ops_server.py \
  --method tools/call --tool-name get_row_count \
  --tool-arg table_name=STG.STG_ACCT_TXN --tool-arg base_date=20261028

# 2) Claude Code 에 등록 (-- 뒤가 서버 실행 명령). 범위: local(기본) · project(.mcp.json 공유) · user
claude mcp add --transport stdio --scope project ops-dw -- python "$(pwd)/ops_server.py"
claude mcp list                     # 상태가 Connected 인지 확인
# 원격(HTTP)으로 띄운 경우
claude mcp add --transport http ops-dw-http http://127.0.0.1:8765/mcp --header "Authorization: Bearer $OPS_MCP_TOKEN"

# 3) Codex CLI 에 등록 (~/.codex/config.toml 에 기록됨)
codex mcp add ops-dw -- python "$(pwd)/ops_server.py"
