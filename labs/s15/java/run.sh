# labs/s15/java/run.sh — Oracle 컨테이너 · 테이블 · 실행 (Python 트랙과 같은 DB)
export ORACLE_SYS_PASSWORD='change-me-sys' DB_PASSWORD='change-me-app'

docker run -d --name oracle-free -p 1521:1521 \
  -e ORACLE_PASSWORD="$ORACLE_SYS_PASSWORD" \
  -e APP_USER=ticketdesk -e APP_USER_PASSWORD="$DB_PASSWORD" \
  gvenzl/oracle-free:23-slim
docker logs -f oracle-free        # "DATABASE IS READY TO USE!" 확인 후 Ctrl+C

docker exec -i oracle-free sqlplus -s "ticketdesk/$DB_PASSWORD@localhost/FREEPDB1" < db/V1__work_request.sql

# application.yml(이론 5절)이 읽는 환경변수
export DB_URL=jdbc:oracle:thin:@localhost:1521/FREEPDB1 DB_USER=ticketdesk
export ANTHROPIC_API_KEY=sk-ant-xxx DEMO_PASSWORD=change-me CLAUDE_MODEL=claude-sonnet-5-5

./mvnw -q test                    # 단위 테스트 (DB · 키 없이)
./mvnw spring-boot:run            # http://localhost:8080/requests → 로그인 화면 (staff1 / handler1)

# 확인: staff1 로 등록 → handler1 로 로그인해 보이는지, staff1 의 상태 변경(PATCH)이 403 인지
