# labs/s17/java/run.sh — Java 트랙 실행과 승인 경로 확인 (labs/s17/java 폴더에서)
# 출력 형식은 Python 트랙과 같습니다:
# [1] stop_reason=tool_use in=1873 out=96
#   → get_batch_log({"job_name":"ADW_ACCT_TXN_DAILY","run_date":"20261028","level":"ERROR"})
#   ← 2026-10-29T01:12:31 [ERROR] STG_LOAD: ORA-12899: value too large for column ...
# [2] stop_reason=tool_use ...
# [3] stop_reason=end_turn ...
# ① 현상 ... ② 원인 ... ③ 조치안 ... ④ 재실행 ...   (문장은 실행마다 조금씩 달라집니다)

export ANTHROPIC_API_KEY=sk-ant-...      # 실습용 키
export OPS_TODAY=2026-10-29              # 모의 데이터 기준 '오늘'

# 0) 모의 데이터: Python 트랙과 같은 파일을 씁니다 (표준 라이브러리만 쓰므로 python3 만 있으면 됨)
python3 ../make_mock.py                  # ./mock/ 에 batch_log.jsonl · row_counts.csv · table_specs.json

# 1) 기본 질문
./mvnw -q compile exec:java -Dexec.args="어젯밤 배치 왜 실패했어?"

# 2) 승인 거절 경로: [승인 필요] 에서 n → 모델에 '사람이 거절' 결과가 is_error 로 전달됨
./mvnw -q compile exec:java -Dexec.args="어젯밤 배치 원인 찾고, 필요하면 STG_LOAD 부터 재실행 요청까지 해 줘"

# 3) 승인 경로: y 를 누르면 요청 1건이 쌓입니다
cat outbox/rerun_requests.jsonl

# 4) 최대 반복 가드: 2회로 줄여 중단 메시지 확인
AGENT_MAX_STEPS=2 ./mvnw -q compile exec:java -Dexec.args="어젯밤 배치 왜 실패했어?"

# 5) 승인 없이 거절만 (평가·CI 용)
APPROVAL_MODE=deny ./mvnw -q compile exec:java -Dexec.args="재실행 요청까지 해 줘"
