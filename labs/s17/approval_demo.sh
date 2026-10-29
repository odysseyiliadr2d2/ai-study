# 예시 출력: python agent_loop.py "어젯밤 배치 왜 실패했어?"
# [1] stop_reason=tool_use in=1873 out=96
#   → get_batch_log({"job_name": "ADW_ACCT_TXN_DAILY", "run_date": "20261028", "level": "ERROR"})
#   ← 2026-10-29T01:12:31 [ERROR] STG_LOAD: ORA-12899: value too large for column "STG"."STG_ACCT_TXN"."BRNCH_CD" ...
# [2] stop_reason=tool_use ...
#   → get_row_count({"table_name": "STG.STG_ACCT_TXN", "base_date": "20261028"})
#   ← STG.STG_ACCT_TXN 20261028: 0건 / 직전 기준일 20261027: 1,251,870건
#   → search_table_spec({"keyword": "BRNCH_CD"})
#   ← ■ STG.STG_ACCT_TXN [계좌거래 스테이징] ... BRNCH_CD VARCHAR2(5) ...
# [3] stop_reason=end_turn ...
# 
# ① 현상: ADW_ACCT_TXN_DAILY 20261028 STG_LOAD 단계 실패, STG·ADW 적재 0건
# ② 원인: 지점코드 6자리 값 유입. STG.BRNCH_CD 는 VARCHAR2(5) (ORA-12899, actual 6 / max 5)
# ③ 조치안: STG_ACCT_TXN.BRNCH_CD 를 VARCHAR2(6) 으로 확장 (ADW 는 이미 6자리) — 변경 승인 필요
# ④ 재실행: 컬럼 확장 후 STG_LOAD 블록부터 재실행 필요
# (숫자·문장은 실행마다 조금씩 달라집니다)

# 1) 승인 거절 경로 확인: 에이전트에게 재실행까지 시켜 봅니다
python agent_loop.py "어젯밤 배치 원인 찾고, 필요하면 STG_LOAD 부터 재실행 요청까지 해 줘"
#   [승인 필요] request_rerun {...}
#   실행할까요? (y/N) n      ← 거절하면 모델에 '사람이 거절' 결과가 is_error 로 전달됨

# 2) 승인 경로: y 를 누르면 outbox/rerun_requests.jsonl 에 요청 1건이 쌓입니다
cat outbox/rerun_requests.jsonl

# 3) 최대 반복 가드 확인: 반복을 2회로 줄여 중단 메시지가 나오는지
AGENT_MAX_STEPS=2 python agent_loop.py "어젯밤 배치 왜 실패했어?"
