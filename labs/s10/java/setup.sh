# 0) 세션 9 Java 트랙의 Chunker · Indexer · Ask 를 이 프로젝트에 복사 (패키지 study.s09 그대로)
S09=../../s09/java                       # 세션 9 Java 트랙 폴더
mkdir -p src/main/java/study/s09 && cp $S09/src/main/java/study/s09/*.java src/main/java/study/s09/

# 1) 샘플 문서 · 평가셋은 Python 트랙(이 세션 실습)의 파일을 그대로 씁니다 (데이터라 두 번 만들지 않음)
python3 ../make_corpus.py                # → sample/*.md 6개 문서, 22개 섹션
cp ../eval.jsonl .

# 2) 모델 파일 — 임베딩(세션 8·9 와 같음)과 리랭커
export E5_DIR=models/multilingual-e5-small          # 세션 9 에서 받은 폴더 경로로
mkdir -p models/bge-reranker-v2-m3
#    BAAI 원본 저장소에는 ONNX 파일이 없어 onnx-community 의 변환본을 씁니다 (fp32 약 2.2GB, 두 파일이 같은 폴더에 있어야 함)
R=https://huggingface.co/onnx-community/bge-reranker-v2-m3-ONNX/resolve/main
curl -L -o models/bge-reranker-v2-m3/model.onnx      $R/onnx/model.onnx
curl -L -o models/bge-reranker-v2-m3/model.onnx_data $R/onnx/model.onnx_data
curl -L -o models/bge-reranker-v2-m3/tokenizer.json  $R/tokenizer.json
export RERANK_DIR=models/bge-reranker-v2-m3
#    사내 반입 규정상 외부 변환본을 못 쓰면 Hugging Face optimum 으로 원본을 직접 ONNX 로 내보냅니다

# 3) 세션 9 클래스로 색인 → index-java.json
J="./mvnw -q compile exec:java"
$J -Dexec.mainClass=study.s09.Chunker -Dexec.args=sample > chunks.jsonl
$J -Dexec.mainClass=study.s09.Indexer -Dexec.args=chunks.jsonl
export ANTHROPIC_API_KEY=sk-ant-...
