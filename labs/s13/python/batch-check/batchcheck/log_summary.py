"""batchcheck/log_summary.py — 야간 배치 로그를 읽어 요약합니다.

로그 한 줄 형식: '시각 수준 작업 단계 메시지'
예) 2026-10-29T01:12:31 ERROR ADW_ACCT_TXN_DAILY STG_LOAD ORA-12899 value too large for column
"""
from collections import Counter
from collections.abc import Iterable
from dataclasses import dataclass

LEVELS = ("INFO", "WARN", "ERROR")


@dataclass(frozen=True)
class LogRecord:
    ts: str
    level: str
    job: str
    step: str
    message: str


def parse_line(line: str) -> LogRecord:
    """로그 한 줄을 읽습니다. 형식이 틀리면 ValueError."""
    parts = line.strip().split(maxsplit=4)
    if len(parts) < 5 or parts[1] not in LEVELS:
        raise ValueError(f"로그 형식 오류: {line.strip()!r}")
    return LogRecord(*parts)


def count_by_level(lines: Iterable[str]) -> dict[str, int]:
    """수준별 건수를 돌려줍니다. 빈 줄은 건너뜁니다."""
    counts = Counter(parse_line(line).level for line in lines if line.strip())
    return {level: counts.get(level, 0) for level in LEVELS}
