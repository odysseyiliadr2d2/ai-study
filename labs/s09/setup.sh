python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
export ANTHROPIC_API_KEY=sk-ant-...   # 실습용 키
mkdir -p docs && cp sample/*.md docs/
