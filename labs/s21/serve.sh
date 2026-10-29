# 1) 설치 (인터넷 되는 실습 PC, NVIDIA GPU) — vLLM 공식 빠른 시작 기준
uv pip install vllm --torch-backend=auto

# 2) 모델을 미리 받아 둡니다 (폐쇄망에서는 이 폴더를 반입)
hf download Qwen/Qwen3.5-9B --local-dir ./models/Qwen3.5-9B

# 3) OpenAI 호환 서버 실행 (기본 포트 8000)
export VLLM_API_KEY=change-me            # 키를 요구하게 합니다
vllm serve ./models/Qwen3.5-9B \
  --served-model-name qwen3.5-9b \
  --max-model-len 16384 \
  --gpu-memory-utilization 0.90 \
  --reasoning-parser qwen3

# 4) 확인
curl -s http://localhost:8000/v1/models -H "Authorization: Bearer $VLLM_API_KEY"

# 노트북(GPU 없음)에서 연습할 때는 Ollama 도 OpenAI 호환 주소를 줍니다
#   base_url = http://localhost:11434/v1   (api_key 는 아무 값)
# llama.cpp 는 GGUF 파일로:  llama-server -m model.gguf -c 8192  (기본 포트 8080)
