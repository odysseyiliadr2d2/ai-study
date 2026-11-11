# labs/s16/python/run.sh — 로컬에서 운영과 같은 이미지로 확인

# 1) 빌드 (커밋 SHA 를 버전으로)
docker build --build-arg APP_VERSION=$(git rev-parse --short HEAD) -t ticket-desk-py:local .

# 2) 실행 — 세션 15 의 환경변수를 파일로 (이 파일은 커밋하지 않습니다)
#    컨테이너 안에서 호스트의 Oracle 은 localhost 가 아니라 host.docker.internal 입니다
cat .env.local
# DATABASE_URL=oracle+oracledb://ticketdesk@host.docker.internal:1521/?service_name=FREEPDB1
# DB_PASSWORD=...  JWT_SECRET=...  DEMO_PASSWORD=...  ANTHROPIC_API_KEY=...
docker run --rm -p 8000:8000 --env-file .env.local ticket-desk-py:local

# 3) 헬스체크 (다른 터미널) — UP 과 버전, 컨테이너 상태 healthy
curl -s localhost:8000/api/health
docker ps --format '{{.Image}} {{.Status}}'

# 4) 필수 변수 하나를 빼고 띄우면 503 DOWN, 로그에만 변수 이름
docker run --rm -p 8001:8000 -e DATABASE_URL=sqlite+pysqlite:///:memory: ticket-desk-py:local &
sleep 3 && curl -s -i localhost:8001/api/health | head -1

# 5) 이미지 안에 비밀값 · .env 가 없는지
docker history --no-trunc ticket-desk-py:local | grep -iE "key|password|secret" || echo "비밀값 흔적 없음"
docker run --rm ticket-desk-py:local ls -a /app
