"""템플릿 하나를 실행합니다 — python run_template.py log_summary sample_log.txt
두 번째 인자로 준 파일 내용이 템플릿의 '주 입력' 변수에 들어가고, 나머지 변수는 아래 기본값을 씁니다.
"""
import os
import sys

from anthropic import Anthropic

from templates import render

MODEL = os.getenv("CLAUDE_MODEL", "claude-sonnet-5-5")
client = Anthropic()

# 템플릿별 주 입력 변수와 나머지 변수의 실습용 기본값
MAIN_VAR = {"column_doc": "columns", "log_summary": "log", "action_items": "notes",
            "sql_review": "sql", "report_draft": "memo"}
DEFAULTS = {
    "column_doc": {"schema": "ADW", "table": "TB_ADW_TRD_DAILY", "table_desc": "일별 거래 집계"},
    "log_summary": {"base_date": "2026-10-28"},
    "action_items": {"meeting": "정보계 차세대 주간 회의", "date": "2026-10-28", "attendees": "PM, DW팀, ETL팀"},
    "sql_review": {"purpose": "월별 지점 실적 집계", "context": "TB_TRD_DAILY 는 일 200만 건, BASE_DT 에 파티션"},
    "report_draft": {"period": "10/20~10/24", "metrics": "배치 성공률 99.2% (지난주 98.7%)\n장애 2건 (지난주 4건)"},
}


def main(name: str, path: str):
    values = dict(DEFAULTS[name])
    values[MAIN_VAR[name]] = open(path, encoding="utf-8").read()
    system, user = render(name, **values)
    msg = client.messages.create(
        model=MODEL, max_tokens=2048, system=system,
        messages=[{"role": "user", "content": user}],
    )
    print("".join(b.text for b in msg.content if b.type == "text"))
    print(f"\n[토큰] 입력 {msg.usage.input_tokens} · 출력 {msg.usage.output_tokens}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
