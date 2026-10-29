cat > requirements.txt <<'EOF'
anthropic>=2.3
pyyaml>=6.0
claude-agent-sdk>=0.2.170   # guard_hooks.py 를 세션 19 에이전트에 붙일 때
EOF
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp ../s17/ops_tools.py ../s17/tool_specs.py ../s17/make_mock.py .
cp ../s18/ops_server.py ../s19/ops_agent.py .     # 마지막 훅 단계(guard_hooks.py)용
export OPS_TODAY=2026-10-29      # 모의 데이터 기준 오늘 (시나리오의 today 가 있으면 그 값 우선)
python make_mock.py              # mock/            정상 데이터
python make_mock.py --inject     # mock_injection/  로그·정의서에 지시문이 숨어 있는 데이터
export ANTHROPIC_API_KEY=sk-ant-...
export PRICE_IN_PER_MTOK=...  PRICE_OUT_PER_MTOK=...   # 공식 가격표의 모델 단가 (비용 집계용)
