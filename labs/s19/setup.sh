python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp ../s18/ops_server.py .                         # 세션 18 MCP 서버
cp ../s17/ops_tools.py ../s17/make_mock.py .      # 세션 17 도구 함수·모의 데이터 생성기
python make_mock.py
export ANTHROPIC_API_KEY=sk-ant-...
export OPS_TODAY=2026-10-29     # 모의 데이터 기준 오늘 (기본값). 시스템 프롬프트의 '어젯밤' 계산에 씀
