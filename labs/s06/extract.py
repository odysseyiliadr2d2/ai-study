"""2·3단계: 구조화 출력 + 검증 + 재시도.  python extract.py"""
import os
import time

import anthropic
from anthropic import Anthropic
from pydantic import ValidationError

from schema import Inquiry

MODEL = os.getenv("CLAUDE_MODEL", "claude-sonnet-5-5")
# timeout: 요청 하나의 상한(초) / max_retries: SDK 가 429·5xx·연결 오류를 자동 재시도하는 횟수 (기본 2)
client = Anthropic(timeout=30.0, max_retries=3)

SYSTEM = """당신은 SI 회사 고객지원팀의 문의 분류기입니다.
- 메일 본문에 있는 사실만 추출합니다. 없는 값은 null 로 둡니다.
- 오류 코드·시스템 이름은 본문 표기를 그대로 옮깁니다."""


def extract(mail: str, max_attempts: int = 2) -> Inquiry:
    """messages.parse 가 스키마를 output_config.format 으로 보내고 pydantic 으로 검증합니다."""
    base = f"다음 문의 메일을 분류하세요.\n\n{mail}"
    messages = [{"role": "user", "content": base}]
    last_error = None
    for attempt in range(1, max_attempts + 1):
        try:
            resp = client.messages.parse(
                model=MODEL,
                max_tokens=4096,                  # thinking 토큰도 이 안에 포함됩니다
                system=SYSTEM,
                messages=messages,
                output_format=Inquiry,            # → output_config.format 으로 전송
                output_config={"effort": "low"},  # 분류는 깊은 생각이 필요 없음
            )
            if resp.stop_reason == "max_tokens":
                raise ValueError("출력이 max_tokens 에서 잘렸습니다")
            if resp.parsed_output is None:
                raise ValueError(f"파싱 결과 없음 (stop_reason={resp.stop_reason})")
            return resp.parsed_output
        except (ValidationError, ValueError) as e:
            # 형식은 맞지만 업무 규칙(길이·범위)에 어긋난 경우: 오류를 알려 주고 다시 요청
            last_error = e
            messages = [{
                "role": "user",
                "content": f"{base}\n\n직전 결과가 검증에 실패했습니다: {e}\n규칙을 지켜 다시 분류하세요.",
            }]
        except anthropic.RateLimitError:
            time.sleep(5 * attempt)   # SDK 재시도 후에도 429 면 조금 더 기다립니다
    raise RuntimeError(f"{max_attempts}회 시도 후 실패: {last_error}")


SAMPLE = """제목: [긴급] 정산 보고서 화면 오류
안녕하세요. 오늘 아침부터 '월별 정산 보고서' 화면에서 조회 버튼을 누르면
ERR-5031 오류가 나고 결과가 안 나옵니다. 내일(2026-11-05) 오전 결재에 필요해서
그 전까지 꼭 확인 부탁드립니다. — 재무팀 담당자"""

if __name__ == "__main__":
    result = extract(SAMPLE)
    print(result.model_dump_json(indent=2))
