python -m venv .venv && source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
export ANTHROPIC_API_KEY=sk-ant-...      # zero_shot.py · compare.py 에서만 필요
python train_predict.py                  # (a) 학습 → 예측
python zero_shot.py                      # (b) LLM 제로샷
python compare.py                        # 두 방식 비교
