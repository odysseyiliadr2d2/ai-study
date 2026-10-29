"""마스킹 유틸 테스트 — python -m pytest test_pii_mask.py  (또는 python test_pii_mask.py)"""
from pii_mask import mask, unmask

CASES = [
    ("연락처 010-1234-5678", "[PHONE_1]"),
    ("번호 01012345678 입니다", "[PHONE_1]"),
    ("주민번호 900101-1234567", "[RRN_1]"),
    ("계좌 123-456-789012", "[ACCOUNT_1]"),
    ("메일 a.b@example.co.kr", "[EMAIL_1]"),
    ("사무실 02-123-4567", "[TEL_1]"),
]


def test_patterns():
    for text, token in CASES:
        masked, table = mask(text)
        assert token in masked, (text, masked)
        assert unmask(masked, table) == text


def test_no_false_positive_on_dates_and_amounts():
    text = "2026-10-29 배치 결과 1,250,000원 처리, 작업번호 JOB-0042"
    masked, table = mask(text)
    assert table == {}, table


if __name__ == "__main__":
    test_patterns()
    test_no_false_positive_on_dates_and_amounts()
    print("모든 테스트 통과")
