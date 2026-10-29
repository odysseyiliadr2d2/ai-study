"""정보계 운영 도구 (모의 데이터판) — 세션 17~20 공통.

실제 환경에서는 각 함수 안쪽만 바꿉니다.
- get_batch_log      → 배치 스케줄러 로그 테이블 / 로그 수집 시스템 조회
- get_row_count      → Oracle 읽기 전용 계정으로 SELECT COUNT(*)
- search_table_spec  → 테이블 정의서(엑셀·위키) 또는 데이터 사전 뷰
- request_rerun      → 운영 요청 시스템(ITSM)·메신저에 재실행 요청 등록 (쓰기)
"""
import csv
import json
import os
import re
from datetime import date, datetime, timedelta
from pathlib import Path

DATA = Path(os.getenv("OPS_MOCK_DIR", Path(__file__).parent / "mock"))
OUTBOX = Path(os.getenv("OPS_OUTBOX", Path(__file__).parent / "outbox"))

# 모의 데이터는 2026-10-28 밤 배치 기준으로 고정되어 있으므로 '오늘'도 고정합니다.
# 실제 시스템에 붙일 때는 OPS_TODAY 를 비워 두면 됩니다: OPS_TODAY= python agent_loop.py
_today = os.getenv("OPS_TODAY", "2026-10-29")
TODAY = date.fromisoformat(_today) if _today else date.today()
YESTERDAY = (TODAY - timedelta(days=1)).strftime("%Y%m%d")

JOB_RE = re.compile(r"^[A-Z0-9_]{3,40}$")
TABLE_RE = re.compile(r"^[A-Z0-9_]+\.[A-Z0-9_]+$")
DATE_RE = re.compile(r"^\d{8}$")


class ToolError(Exception):
    """모델에게 그대로 보여 줄 오류. 무엇이 틀렸고 어떻게 고칠지 적습니다."""


def get_batch_log(job_name: str, run_date: str, level: str = "ALL", limit: int = 20) -> str:
    """배치 작업 로그를 시간순으로 돌려줍니다 (읽기 전용)."""
    job_name = job_name.strip().upper()
    if not JOB_RE.match(job_name):
        raise ToolError(f"job_name 형식 오류: {job_name!r}. 예: ADW_ACCT_TXN_DAILY")
    if not DATE_RE.match(run_date):
        raise ToolError(f"run_date 는 YYYYMMDD 8자리입니다. 받은 값: {run_date!r}")
    rows = [json.loads(line) for line in open(DATA / "batch_log.jsonl", encoding="utf-8")]
    hits = [r for r in rows if r["job"] == job_name and r["run_date"] == run_date
            and (level == "ALL" or r["level"] == level)]
    if not hits:
        jobs = sorted({r["job"] for r in rows if r["run_date"] == run_date})
        return f"{job_name} / {run_date} 로그 없음. 이 날짜에 로그가 있는 작업: {', '.join(jobs) or '없음'}"
    hits = sorted(hits, key=lambda r: r["ts"])[: max(1, min(limit, 100))]
    return "\n".join(f"{r['ts']} [{r['level']}] {r['step']}: {r['msg']}" for r in hits)


def get_row_count(table_name: str, base_date: str) -> str:
    """테이블의 기준일자별 적재 건수를 돌려줍니다 (읽기 전용). 전일 건수도 함께 줍니다."""
    table_name = table_name.strip().upper()
    if not TABLE_RE.match(table_name):
        raise ToolError(f"table_name 은 '스키마.테이블' 형식입니다. 받은 값: {table_name!r}")
    if not DATE_RE.match(base_date):
        raise ToolError(f"base_date 는 YYYYMMDD 8자리입니다. 받은 값: {base_date!r}")
    with open(DATA / "row_counts.csv", encoding="utf-8") as f:
        rows = [r for r in csv.DictReader(f) if r["table_name"] == table_name]
    if not rows:
        raise ToolError(f"{table_name} 은 건수 조회 대상이 아닙니다. search_table_spec 으로 테이블명을 먼저 확인하세요.")
    by_date = {r["base_date"]: int(r["row_cnt"]) for r in rows}
    prev = max((d for d in by_date if d < base_date), default=None)
    cur = by_date.get(base_date)
    parts = [f"{table_name} {base_date}: {cur:,}건" if cur is not None else f"{table_name} {base_date}: 적재 이력 없음"]
    if prev:
        parts.append(f"직전 기준일 {prev}: {by_date[prev]:,}건")
    return " / ".join(parts)


def search_table_spec(keyword: str) -> str:
    """테이블 정의서에서 테이블명·한글명·컬럼명·설명으로 검색합니다 (읽기 전용)."""
    kw = keyword.strip().upper()
    if len(kw) < 2:
        raise ToolError("keyword 는 2자 이상으로 주세요. 예: STG_ACCT_TXN, BRNCH_CD, 지점코드")
    specs = json.load(open(DATA / "table_specs.json", encoding="utf-8"))
    out = []
    for t in specs:
        blob = json.dumps(t, ensure_ascii=False).upper()
        if kw in blob:
            cols = ", ".join(f"{c['col']} {c['type']}({c['desc']})" for c in t["columns"])
            out.append(f"■ {t['table']} [{t['name']}] 적재: {t['load_job']} · 원천: {t['source']}\n"
                       f"  컬럼: {cols}\n  비고: {t['notes']}")
    return "\n".join(out[:5]) if out else f"'{keyword}' 와 일치하는 정의서 항목 없음"


def request_rerun(job_name: str, run_date: str, from_step: str, reason: str) -> str:
    """재실행 요청을 운영 요청함(outbox)에 등록합니다 (쓰기 · 사람 승인 후에만 호출)."""
    job_name = job_name.strip().upper()
    if not JOB_RE.match(job_name) or not DATE_RE.match(run_date):
        raise ToolError("job_name 또는 run_date 형식 오류. 예: ADW_ACCT_TXN_DAILY, 20261028")
    if len(reason) < 10:
        raise ToolError("reason 은 원인과 조치를 10자 이상으로 적어 주세요.")
    OUTBOX.mkdir(parents=True, exist_ok=True)
    req = {"requested_at": datetime.now().isoformat(timespec="seconds"), "job": job_name,
           "run_date": run_date, "from_step": from_step, "reason": reason, "status": "REQUESTED"}
    with open(OUTBOX / "rerun_requests.jsonl", "a", encoding="utf-8") as f:
        f.write(json.dumps(req, ensure_ascii=False) + "\n")
    return f"재실행 요청 등록 완료: {job_name} {run_date} ({from_step}부터). 실제 실행은 운영 담당자가 합니다."


READ_TOOLS = {"get_batch_log", "get_row_count", "search_table_spec"}
WRITE_TOOLS = {"request_rerun"}
FUNCS = {f.__name__: f for f in (get_batch_log, get_row_count, search_table_spec, request_rerun)}
