# 1) 임베딩 모델: Python 트랙과 같은 multilingual-e5-small 의 ONNX 파일 (세션 8 Java 트랙에서 받았다면 재사용)
mkdir -p models/multilingual-e5-small
for f in model.onnx tokenizer.json; do
  curl -L -o models/multilingual-e5-small/$f \
    https://huggingface.co/intfloat/multilingual-e5-small/resolve/main/onnx/$f
done
export E5_DIR=models/multilingual-e5-small

# 2) 실습용 키와 문서
export ANTHROPIC_API_KEY=sk-ant-...      # 실습용 키
mkdir -p docs && cp sample/*.md docs/

# 3) 세 단계 실행
J="./mvnw -q compile exec:java"
$J -Dexec.mainClass=study.s09.Chunker -Dexec.args=docs > chunks.jsonl
$J -Dexec.mainClass=study.s09.Indexer -Dexec.args=chunks.jsonl
$J -Dexec.mainClass=study.s09.Ask -Dexec.args="야간 배치가 실패하면 누구에게 연락하나요?"
