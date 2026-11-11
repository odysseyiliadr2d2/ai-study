# labs/s18/java/run.sh — 준비 · 실행 · Inspector 검증 · 클라이언트 등록 (labs/s18/java 폴더에서)

# 0) 세션 17 의 OpsTools 를 패키지만 바꿔 복사하고, 같은 모의 데이터를 만듭니다
mkdir -p src/main/java/study/s18
sed 's/^package study.s17;/package study.s18;/' \
  ../../s17/java/src/main/java/study/s17/OpsTools.java > src/main/java/study/s18/OpsTools.java
python3 ../../s17/make_mock.py           # ./mock/ 생성 (Python 트랙과 같은 파일)

# 1) 모의 데이터판 실행 — http://127.0.0.1:8765/mcp
./mvnw -q spring-boot:run
OPS_ENABLE_WRITE=true ./mvnw -q spring-boot:run          # 쓰기 도구까지 공개

# 2) MCP Inspector 로 시험 (Node 22.19 이상, 설치 없이 npx) — 다른 터미널에서
npx @modelcontextprotocol/inspector --cli http://127.0.0.1:8765/mcp --transport http --method tools/list
npx @modelcontextprotocol/inspector --cli http://127.0.0.1:8765/mcp --transport http \
  --method tools/call --tool-name get_row_count \
  --tool-arg tableName=STG.STG_ACCT_TXN --tool-arg baseDate=20261028
npx @modelcontextprotocol/inspector --cli http://127.0.0.1:8765/mcp --transport http \
  --method tools/call --tool-name get_row_count \
  --tool-arg tableName=STG.NOPE --tool-arg baseDate=20261028      # isError 결과 확인
npx @modelcontextprotocol/inspector --cli http://127.0.0.1:8765/mcp --transport http --method resources/list

# 3) Claude Code · Codex 에 HTTP 서버로 등록
claude mcp add --transport http --scope project ops-dw-java http://127.0.0.1:8765/mcp
claude mcp list                          # Connected 확인
codex mcp add ops-dw-java --url http://127.0.0.1:8765/mcp

# 4) Oracle 읽기 전용판 — 같은 도구 이름, 실제 DW 조회
export ORA_URL=jdbc:oracle:thin:@//dwhost:1521/DWPDB ORA_USER=ai_ro ORA_PASSWORD=...
./mvnw -q spring-boot:run -Dspring-boot.run.profiles=oracle
