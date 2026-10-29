"""프롬프트 개인정보 마스킹 유틸 — python pii_mask.py < input.txt

주의: 아래 정규식은 학습용 '예시 패턴'입니다. 실제 계좌번호 형식은 금융회사마다 다르고,
이름·주소처럼 정규식으로 잡기 어려운 정보도 있습니다. 운영에서는 사내 표준 패턴과
검증된 개인정보 탐지 솔루션을 함께 쓰고, 마스킹 결과를 정기적으로 표본 점검하세요.
"""
import re
import sys

# 순서가 중요합니다: 더 구체적인 패턴(주민번호)을 숫자열 패턴보다 먼저 적용
PATTERNS = [
    ("RRN", re.compile(r"(?<!\d)\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\d|3[01])-?[1-8]\d{6}(?!\d)")),   # 주민·외국인등록번호 형태
    ("PHONE", re.compile(r"(?<!\d)01[016789]-?\d{3,4}-?\d{4}(?!\d)")),                                  # 휴대전화
    ("TEL", re.compile(r"(?<!\d)0(?:2|[3-6][1-5])-\d{3,4}-\d{4}(?!\d)")),                                # 지역번호 유선전화
    ("CARD", re.compile(r"(?<!\d)\d{4}-\d{4}-\d{4}-\d{4}(?!\d)")),                                      # 카드번호 형태
    # 하이픈 계좌번호 형태(예시). 2026-10-29 같은 날짜는 제외
    ("ACCOUNT", re.compile(r"(?<!\d)(?!(?:19|20)\d{2}-[01]\d-[0-3]\d(?!\d))\d{2,6}-\d{2,6}-\d{2,8}(?:-\d{1,3})?(?!\d)")),
    ("EMAIL", re.compile(r"[\w.+-]+@[\w-]+(?:\.[\w-]+)+")),
]


def mask(text: str):
    """마스킹한 문자열과, 원래 값을 복원할 수 있는 대응표를 돌려줍니다.
    대응표는 사내에만 두고 LLM 으로 보내지 않습니다."""
    mapping = {}
    counters = {}

    def repl_factory(label):
        def repl(m):
            counters[label] = counters.get(label, 0) + 1
            token = f"[{label}_{counters[label]}]"
            mapping[token] = m.group(0)
            return token
        return repl

    for label, pat in PATTERNS:
        text = pat.sub(repl_factory(label), text)
    return text, mapping


def unmask(text: str, mapping: dict) -> str:
    for token, original in mapping.items():
        text = text.replace(token, original)
    return text


if __name__ == "__main__":
    sample = ("고객 문의: 010-1234-5678 로 연락 부탁. 주민번호 900101-1234567, "
              "계좌 123-456-789012 이체 오류. 메일 test@example.com, 사무실 02-123-4567.")
    src = "" if sys.stdin.isatty() else sys.stdin.read()
    src = src or sample
    masked, table = mask(src)
    print(masked)
    print(f"\n마스킹 {len(table)}건:", ", ".join(table))
