python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
export ANTHROPIC_API_KEY=sk-ant-...
python templates.py                       # 템플릿 목록과 렌더링 예시만 출력 (API 호출 없음)
python run_template.py log_summary sample_log.txt
python compare.py                         # 나쁜 프롬프트 vs 고친 프롬프트
