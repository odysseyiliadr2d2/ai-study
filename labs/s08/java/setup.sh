# 1) pgvector 컨테이너 (Python 트랙과 같은 이미지 · 같은 DB)
docker run -d --name pgvector \
  -e POSTGRES_PASSWORD=study \
  -e POSTGRES_DB=meta \
  -p 5432:5432 \
  -v pgvector_data:/var/lib/postgresql \
  pgvector/pgvector:pg18-trixie

# 2) 스키마 (다음 단계의 schema.sql)
docker exec -i pgvector psql -U postgres -d meta < schema.sql

# 3) 임베딩 모델: Python 트랙과 같은 intfloat/multilingual-e5-small 의 ONNX 파일 (약 0.5GB)
#    폐쇄망에서는 외부에서 받은 두 파일을 사내 파일 서버로 옮기고 E5_DIR 만 바꿉니다.
mkdir -p models/multilingual-e5-small
for f in model.onnx tokenizer.json; do
  curl -L -o models/multilingual-e5-small/$f \
    https://huggingface.co/intfloat/multilingual-e5-small/resolve/main/onnx/$f
done
export E5_DIR=models/multilingual-e5-small
export PGURL="jdbc:postgresql://localhost:5432/meta?user=postgres&password=study"

# 4) 빌드 확인
./mvnw -q compile
