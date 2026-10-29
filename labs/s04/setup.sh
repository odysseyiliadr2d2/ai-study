python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
# 1) prices.json 의 null 을 공식 가격 페이지 값으로 채운 뒤
python cost_calc.py
# 2) 세 곳에 같은 프롬프트 보내기 (키가 없는 곳은 건너뜀)
export ANTHROPIC_API_KEY=sk-ant-...
export OPENAI_API_KEY=sk-...
ollama pull gemma4:e4b
python compare_models.py
