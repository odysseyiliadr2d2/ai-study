cat > requirements.txt <<'EOF'
anthropic>=2.3
openai>=2.0        # 선택: Responses API 비교 실습
EOF
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
export ANTHROPIC_API_KEY=sk-ant-...      # 실습용 키
export OPS_TODAY=2026-10-29              # 모의 데이터 기준 '오늘' (기본값과 같음). 실제 시스템이면 OPS_TODAY= 로 비움
python make_mock.py                      # mock/ 에 모의 배치 로그·건수·정의서 생성
python -c "import ops_tools as t; print(t.get_batch_log('ADW_ACCT_TXN_DAILY', '20261028', 'ERROR'))"
