./mvnw -q compile exec:java -Dexec.args="어젯밤 배치 왜 실패했어?"

# 예시 출력 (실행마다 다름)
MCP 도구: [get_batch_log, get_row_count, search_table_spec, request_rerun]
→ investigate {"task":"ADW_ACCT_TXN_DAILY 20261028 실패 원인 조사: ERROR 로그, STG_ACCT_TXN 건수, 관련 컬럼 정의서"}
  (서브)→ get_batch_log {"job_name":"ADW_ACCT_TXN_DAILY","run_date":"20261028","level":"ERROR"}
  (서브)→ get_row_count {"table_name":"STG.STG_ACCT_TXN","base_date":"20261028"}
  (서브)→ search_table_spec {"keyword":"BRNCH_CD"}
① 현상 … ② 원인 … ③ 조치안 … ④ 재실행: 컬럼 확장 후 STG_LOAD 부터
— 모델 호출 6회 · 38.4s · 토큰 21,530 (입력+출력)

# 승인 경로 시험: 재실행을 직접 요청해 보고 n 을 고릅니다
./mvnw -q compile exec:java -Dexec.args="컬럼 확장 끝났어. STG_LOAD 부터 재실행 요청 등록해줘"
cat outbox/rerun_requests.jsonl 2>/dev/null || echo "요청 없음 (거절됨)"

# 조사 담당 모델을 바꿔 비용·품질 비교
CLAUDE_SUB_MODEL=claude-sonnet-5-5 ./mvnw -q compile exec:java
