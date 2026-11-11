# labs/s14/java/check.sh — 프로젝트 만들기와 API 확인 (Next.js · Python 트랙과 같은 curl, 포트만 8080)

# 0) Spring Initializr 로 뼈대(메인 클래스 · Maven Wrapper) 생성 후 pom.xml 을 1단계 내용으로 교체
curl -s https://start.spring.io/starter.zip \
  -d type=maven-project -d language=java -d javaVersion=21 \
  -d groupId=com.example -d artifactId=ticket-desk-api -d name=ticket-desk-api \
  -d packageName=com.example.ticketdesk \
  -d dependencies=web,validation,thymeleaf,actuator \
  -o ticket-desk-api.zip
unzip ticket-desk-api.zip -d ticket-desk-api && cd ticket-desk-api

# 1) 테스트 → 실행
./mvnw -q test
./mvnw spring-boot:run                     # http://localhost:8080/requests

# 2) API 확인 (다른 터미널)
curl -s -i -X POST localhost:8080/api/requests -H 'Content-Type: application/json' \
  -d '{"title":"월말 잔고 추출 요청","body":"11월 말 기준 상품별 잔고를 엑셀로 받고 싶습니다"}'   # 201 + Location
curl -s 'localhost:8080/api/requests?status=RECEIVED'
curl -s -X POST localhost:8080/api/requests -H 'Content-Type: application/json' -d '{"title":"짧"}'   # 400
curl -s -o /dev/null -w '%{http_code}\n' 'localhost:8080/api/requests?status=WRONG'                # 400
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/api/requests/9999                           # 404
