"""문의 메일에서 뽑을 필드 — pydantic 모델이 곧 JSON Schema 입니다."""
from enum import Enum
from typing import Optional

from pydantic import BaseModel, Field, field_validator


class Category(str, Enum):
    ACCOUNT = "계정·로그인"
    BILLING = "요금·계약"
    BUG = "장애·오류"
    REQUEST = "기능 요청"
    DATA = "데이터·보고서"
    OTHER = "기타"


class Urgency(str, Enum):
    HIGH = "높음"
    MEDIUM = "보통"
    LOW = "낮음"


class Inquiry(BaseModel):
    category: Category = Field(description="문의 분류")
    urgency: Urgency = Field(description="업무 중단이면 높음, 불편이면 보통, 문의만이면 낮음")
    summary: str = Field(description="한 문장 요약, 80자 이내")
    system_name: Optional[str] = Field(None, description="언급된 시스템·화면 이름. 없으면 null")
    error_code: Optional[str] = Field(None, description="본문에 있는 오류 코드 그대로. 없으면 null")
    requested_by_date: Optional[str] = Field(None, description="요청 기한 YYYY-MM-DD. 없으면 null")
    confidence: float = Field(description="분류 확신도 0.0~1.0")

    # API 스키마는 길이·범위 제약(maxLength, minimum)을 강제하지 않으므로 여기서 검사합니다
    @field_validator("summary")
    @classmethod
    def short_summary(cls, v: str) -> str:
        if len(v) > 120:
            raise ValueError("summary 가 너무 깁니다 (120자 이하)")
        return v

    @field_validator("confidence")
    @classmethod
    def in_range(cls, v: float) -> float:
        if not 0.0 <= v <= 1.0:
            raise ValueError("confidence 는 0~1 사이")
        return v
