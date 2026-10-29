python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
export ANTHROPIC_API_KEY=sk-ant-...        # count_tokens.py · hallucination_probe.py
# temperature 실험은 로컬 Ollama 모델로 합니다 (https://ollama.com 에서 설치)
ollama pull gemma4:e4b                      # 다른 모델을 쓰면 OLLAMA_MODEL 로 지정
python count_tokens.py
python temperature.py
python embed_similarity.py
python hallucination_probe.py
