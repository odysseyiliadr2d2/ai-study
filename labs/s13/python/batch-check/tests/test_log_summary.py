"""tests/test_log_summary.py — pytest -q"""
import pytest

from batchcheck.log_summary import count_by_level, parse_line

LINES = [
    "2026-10-29T01:10:04 INFO ADW_ACCT_TXN_DAILY STG_LOAD STG 적재 시작",
    "2026-10-29T01:12:31 ERROR ADW_ACCT_TXN_DAILY STG_LOAD ORA-12899 value too large for column",
    "",
    "2026-10-29T01:12:32 WARN ADW_ACCT_TXN_DAILY ADW_MERGE 선행 블록 실패로 실행하지 않음",
    "2026-10-29T02:00:01 INFO ADW_BRNCH_DIM DIM_LOAD 적재 완료 412건",
]


def test_parse_line_reads_five_fields():
    r = parse_line(LINES[1])
    assert (r.level, r.job, r.step) == ("ERROR", "ADW_ACCT_TXN_DAILY", "STG_LOAD")
    assert r.message == "ORA-12899 value too large for column"


def test_parse_line_rejects_bad_format():
    with pytest.raises(ValueError, match="로그 형식 오류"):
        parse_line("2026-10-29 DEBUG 형식이 다름")


def test_count_by_level_skips_blank_lines():
    assert count_by_level(LINES) == {"INFO": 2, "WARN": 1, "ERROR": 1}
