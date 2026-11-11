# labs/s19/java 에서 실행합니다. MCP 서버(세션 18)는 Python 그대로 띄우고, 에이전트만 Java 로 만듭니다.
python -m venv .venv && .venv/bin/pip install "mcp>=2.3,<3"
cp ../../s18/ops_server.py .                          # 세션 18 MCP 서버
cp ../../s17/ops_tools.py ../../s17/make_mock.py .    # 세션 17 도구 함수 · 모의 데이터 생성기
.venv/bin/python make_mock.py                         # mock/ 생성

# Maven Wrapper(mvnw)는 이 폴더에 들어 있습니다 — 따로 만들 필요 없음
export ANTHROPIC_API_KEY=sk-ant-...
export OPS_TODAY=2026-10-29       # 모의 데이터 기준 오늘 (기본값). 시스템 프롬프트의 '어젯밤' 계산에 씀

# (선택) stdio 대신 HTTP 로 띄운 서버(세션 18 의 ops_server_http.py 또는 Spring AI 서버)에 붙일 때
# export OPS_MCP_URL=http://127.0.0.1:8765/mcp
# export OPS_MCP_TOKEN=...        # Bearer 토큰을 요구하는 서버만
