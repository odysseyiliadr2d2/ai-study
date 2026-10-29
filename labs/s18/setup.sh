cat > requirements.txt <<'EOF'
mcp>=2.3,<3          # MCP Python SDK 2.x (MCPServer)
oracledb>=3.0        # Oracle 변형에서만
uvicorn>=0.30        # HTTP + 토큰 인증 변형에서만
EOF
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp ../s17/ops_tools.py ../s17/make_mock.py .   # 세션 17 도구 함수를 그대로 재사용
python make_mock.py
python -c "import ops_server"                 # 임포트 오류가 없으면 준비 완료
