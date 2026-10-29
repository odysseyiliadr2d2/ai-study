python ops_agent.py "어젯밤 배치 왜 실패했어?"

# 예시 출력 (실행마다 다름)
→ Agent {"subagent_type": "log-investigator", "prompt": "ADW_ACCT_TXN_DAILY 20261028 ..."}
  (서브)→ mcp__ops__get_batch_log {"job_name": "ADW_ACCT_TXN_DAILY", "run_date": "20261028", "level": "ERROR"}
  (서브)→ mcp__ops__get_row_count {"table_name": "STG.STG_ACCT_TXN", "base_date": "20261028"}
  (서브)→ mcp__ops__search_table_spec {"keyword": "BRNCH_CD"}
① 현상 … ② 원인 … ③ 조치안 … ④ 재실행: 컬럼 확장 후 STG_LOAD 부터
— success · 턴 4 · 41.2s · 약 $0.0xx · 세션 4f1c…

# 이어서 묻기: 같은 세션을 재개하면 앞의 조사 내용을 기억합니다
#   ClaudeAgentOptions(resume="<세션 ID>") 로 다시 실행
