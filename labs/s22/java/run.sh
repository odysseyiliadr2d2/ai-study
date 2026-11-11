# labs/s22/java 에서. 샘플·체크리스트는 Python 트랙 파일을 그대로 씁니다.
cp ../answers.yaml ../train.sample.jsonl .
# Maven Wrapper(mvnw)는 이 폴더에 들어 있습니다 — 따로 만들 필요 없음

# 1) 판단 체크리스트 (Python decide.py 와 같은 결과인지 비교)
./mvnw -q compile exec:java -Dexec.args=answers.yaml

# 2) 데이터: 50~100건으로 늘린 all.jsonl 을 나누고 검사 → 오류 0건이 될 때까지 고침
./mvnw -q compile exec:java -Dexec.mainClass=study.s22.DatasetTool -Dexec.args="split all.jsonl 0.1 42"
./mvnw -q compile exec:java -Dexec.mainClass=study.s22.DatasetTool -Dexec.args="validate train.jsonl"
./mvnw -q compile exec:java -Dexec.mainClass=study.s22.DatasetTool -Dexec.args="validate eval.jsonl"

# 3) train.jsonl · eval.jsonl 을 GPU 서버로 옮겨 Python 트랙 train_lora.py 로 학습 → serve_lora.sh 로 서빙

# 4) 세 방식 비교
export LLM_BASE_URL=http://gpu01:8000/v1 LLM_API_KEY=$VLLM_API_KEY
./mvnw -q compile exec:java -Dexec.mainClass=study.s22.CompareEval

# 예시 출력 (모델·데이터에 따라 다름)
평가 10건 (기본 qwen3-0.6b · 어댑터 report-style)
  base     형식 준수율  20% · 글자 겹침 0.41
  fewshot  형식 준수율  80% · 글자 겹침 0.58
  lora     형식 준수율  90% · 글자 겹침 0.63
