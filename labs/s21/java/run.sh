# ── 서버 띄우기와 이미지·모델 반입은 Python 트랙 serve.sh · offline_bundle.sh 그대로 (bash · docker · hf)
#    Java 트랙은 그 서버를 부르는 쪽만 다룹니다.

# 1) 노트북에서 동작 확인 (GPU 없으면 Ollama 로)
export LLM_BASE_URL=http://localhost:8000/v1 LLM_API_KEY=$VLLM_API_KEY LLM_MODEL=qwen3.5-9b
./mvnw -q compile exec:java -Dexec.args="야간 배치 재처리 절차를 요약해 주세요"
LLM_BASE_URL=http://localhost:11434/v1 LLM_API_KEY=ollama LLM_MODEL=qwen3:8b ./mvnw -q compile exec:java

# 2) 동시 요청 1 · 4 · 16 벤치마크
for c in 1 4 16; do
  ./mvnw -q compile exec:java -Dexec.mainClass=study.s21.Bench -Dexec.args="--concurrency $c --requests $((c * 5))"
done

# 3) 게이트웨이 뒤로 숨기기: 클라이언트는 게이트웨이 주소만 압니다
GATEWAY_KEY=team-key ./mvnw -q compile exec:java -Dexec.mainClass=study.s21.MiniGateway &
curl -s http://localhost:9000/healthz
LLM_BASE_URL=http://localhost:9000/v1 LLM_API_KEY=team-key ./mvnw -q compile exec:java

# ── 폐쇄망 반입: Java 쪽은 Maven 의존성 묶음을 함께 가져갑니다 (외부 PC)
./mvnw -q dependency:go-offline -Dmaven.repo.local=bundle/m2      # 플러그인·라이브러리를 bundle/m2 에 모음
./mvnw -q -o package -Dmaven.repo.local=bundle/m2                 # 오프라인(-o)으로 빌드가 되는지 확인
python ../manifest.py make bundle > bundle/MANIFEST.json         # 실무 예제의 해시 매니페스트(labs/s21/manifest.py)에 함께 포함

# ── 사내: 해시 대조 후 bundle/m2 를 사내 Nexus 에 올리거나, 개발 PC 에서 -o -Dmaven.repo.local=bundle/m2 로 빌드
