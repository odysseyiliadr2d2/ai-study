# 학습은 Python 트랙 그대로입니다 (GPU 서버, labs/s22)
python train_lora.py                     # → out/report-style-lora (어댑터만, 수십 MB)

# 기본 모델 하나에 어댑터를 붙여 띄웁니다. 요청의 model 값으로 기본 모델/어댑터를 고릅니다.
export VLLM_API_KEY=change-me
vllm serve Qwen/Qwen3-0.6B \
  --served-model-name qwen3-0.6b \
  --enable-lora \
  --lora-modules report-style=./out/report-style-lora \
  --max-lora-rank 16 \
  --max-model-len 4096
# 폐쇄망에서는 Qwen/Qwen3-0.6B 자리에 반입한 로컬 폴더 경로를 줍니다 (세션 21)

# 목록에 기본 모델(qwen3-0.6b)과 어댑터(report-style)가 함께 보이면 준비 완료
curl -s http://localhost:8000/v1/models -H "Authorization: Bearer $VLLM_API_KEY"
