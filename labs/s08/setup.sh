# 1) pgvector 가 들어 있는 PostgreSQL 컨테이너 (태그는 pgvector README 의 최신 태그 확인)
docker run -d --name pgvector \
  -e POSTGRES_PASSWORD=study \
  -e POSTGRES_DB=meta \
  -p 5432:5432 \
  -v pgvector_data:/var/lib/postgresql \
  pgvector/pgvector:pg18-trixie

# 2) 접속 확인
docker exec -it pgvector psql -U postgres -d meta -c "SELECT version();"

# 3) 파이썬 패키지
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
export PGURL="postgresql://postgres:study@localhost:5432/meta"
