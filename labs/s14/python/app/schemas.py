"""app/schemas.py — 업무 요청의 값 목록 · 입력 검증 · 응답 모양.

Next.js 트랙의 zod 스키마와 같은 규칙 · 같은 메시지를 씁니다 (제목 5~100자, 내용 10~4000자, 마감일 오늘 이후).
응답 필드 이름은 camelCase(dueDate, createdAt)로 맞춥니다.
"""
import re
from datetime import date, datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator
from pydantic.alias_generators import to_camel
from pydantic_core import PydanticCustomError

Status = Literal["RECEIVED", "IN_PROGRESS", "DONE"]
Category = Literal["DATA_EXTRACT", "BATCH_ISSUE", "ACCESS", "REPORT", "ETC"]
Priority = Literal["LOW", "MEDIUM", "HIGH"]

DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")


def _length(value: str, lo: int, hi: int, label: str) -> str:
    v = value.strip()
    if len(v) < lo:
        raise PydanticCustomError("too_short", f"{label}은 {lo}자 이상 입력하세요")
    if len(v) > hi:
        raise PydanticCustomError("too_long", f"{label}은 {hi}자 이하로 입력하세요")
    return v


class CreateRequest(BaseModel):
    """등록 입력 — 화면이 무엇을 보내든 서버에서 다시 검증합니다."""

    title: str
    body: str
    due_date: date | None = Field(default=None, alias="dueDate")

    @field_validator("title")
    @classmethod
    def check_title(cls, v: str) -> str:
        return _length(v, 5, 100, "제목")

    @field_validator("body")
    @classmethod
    def check_body(cls, v: str) -> str:
        return _length(v, 10, 4000, "내용")

    @field_validator("due_date", mode="before")
    @classmethod
    def check_due_date(cls, v: object) -> date | None:
        if v is None or v == "":
            return None
        if not isinstance(v, str) or not DATE_RE.match(v):
            raise PydanticCustomError("date_format", "날짜 형식은 YYYY-MM-DD 입니다")
        try:
            d = date.fromisoformat(v)
        except ValueError:
            raise PydanticCustomError("date_format", "날짜 형식은 YYYY-MM-DD 입니다") from None
        if d < date.today():
            raise PydanticCustomError("date_past", "마감일은 오늘 이후여야 합니다")
        return d


class StatusChange(BaseModel):
    status: Status


class WorkRequestOut(BaseModel):
    """응답 — 필드 이름은 camelCase 로 내보냅니다 (FastAPI 는 response_model 을 alias 로 직렬화)."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, from_attributes=True)

    id: str
    title: str
    body: str
    due_date: date | None
    status: Status
    category: Category | None
    priority: Priority | None
    summary: str | None
    requester_id: str
    created_at: datetime
