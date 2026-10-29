"""템플릿 결과를 자동 점검 — python check_output.py
log_summary 템플릿의 '기대 형식'을 코드로 적어 두고, 모델 출력이 지키는지 검사합니다.
템플릿을 고칠 때마다 이 점검을 돌려 회귀(형식 깨짐)를 잡습니다. API 없이 아래 예시 출력으로도 실행됩니다.
"""
import re
import sys

# sample_log.txt 를 log_summary 템플릿에 넣었을 때 기대하는 출력 예 (팀이 합의한 '정답 모양')
EXPECTED_EXAMPLE = """STG 적재가 테이블스페이스 부족으로 실패해 일별 집계까지 건너뛰었습니다.

| 발생 시각 | 작업명 | 오류 코드 | 원인(확인/추정) | 업무 영향 |
|---|---|---|---|---|
| 02:11:57 | JOB_STG_TRD | ORA-01653 | TS_STG 테이블스페이스 공간 부족 (확인) | 거래 STG 적재 롤백 |
| 03:30:00 | JOB_ADW_DAILY | dependency timeout | 선행 작업 JOB_STG_TRD 실패 (확인) | 일별 집계 미생성, 아침 리포트 지연 |

1. TS_STG 데이터파일 증설 후 JOB_STG_TRD 를 3단계부터 재실행합니다.
2. 재실행 완료 후 JOB_ADW_DAILY 를 수동 실행하고 현업에 지연을 공지합니다."""

LOG_KEYS = ["ORA-01653", "JOB_STG_TRD", "JOB_ADW_DAILY"]   # 로그에 있어 반드시 언급돼야 할 것
HEADER = ["발생 시각", "작업명", "오류 코드", "원인(확인/추정)", "업무 영향"]


def check(text: str) -> list[str]:
    problems = []
    lines = [ln.strip() for ln in text.strip().splitlines() if ln.strip()]
    if not lines or lines[0].startswith("|") or len(lines[0]) > 60:
        problems.append("첫 줄이 50자 안팎의 한 줄 요약이 아님")
    header = next((ln for ln in lines if ln.startswith("|") and "작업명" in ln), "")
    cols = [c.strip() for c in header.strip("|").split("|")]
    if cols != HEADER:
        problems.append(f"표 머리글이 다름: {cols}")
    rows = [ln for ln in lines if ln.startswith("|") and not set(ln) <= set("|-: ") and ln != header]
    for r in rows:
        if not re.search(r"\((확인|추정)\)", r):
            problems.append(f"원인에 (확인/추정) 표시 없음: {r[:40]}")
    actions = [ln for ln in lines if re.match(r"^\d+\.", ln)]
    if not 1 <= len(actions) <= 2:
        problems.append(f"조치 제안이 1~2개가 아님: {len(actions)}개")
    for k in LOG_KEYS:
        if k not in text:
            problems.append(f"로그 핵심어 누락: {k}")
    if re.search(r"\d{6}-\d{7}|\d{3}-\d{2}-\d{6}", text):
        problems.append("주민번호·계좌번호 형태의 숫자가 가려지지 않음")
    return problems


if __name__ == "__main__":
    text = open(sys.argv[1], encoding="utf-8").read() if len(sys.argv) > 1 else EXPECTED_EXAMPLE
    found = check(text)
    print("통과" if not found else "\n".join("- " + f for f in found))
