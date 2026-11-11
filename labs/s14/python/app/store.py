"""app/store.py — (오늘만) 메모리 저장소. 세션 15에서 Oracle(SQLAlchemy)로 바꾸고 함수 이름은 그대로 둡니다."""
import threading
import uuid
from dataclasses import dataclass, field, replace
from datetime import date, datetime, timezone

from app.schemas import CreateRequest, Status


@dataclass(frozen=True)
class WorkRequest:
    id: str
    title: str
    body: str
    due_date: date | None
    requester_id: str
    status: Status = "RECEIVED"
    category: str | None = None        # 세션 15에서 AI 가 채움
    priority: str | None = None
    summary: str | None = None
    created_at: datetime = field(default_factory=lambda: datetime.now(timezone.utc))


_lock = threading.Lock()                 # uvicorn 워커 하나 안의 동시 요청 보호
_store: dict[str, WorkRequest] = {}


def list_requests(status: Status | None = None) -> list[WorkRequest]:
    items = [r for r in _store.values() if status is None or r.status == status]
    return sorted(items, key=lambda r: r.created_at, reverse=True)


def get_request(request_id: str) -> WorkRequest | None:
    return _store.get(request_id)


def create_request(data: CreateRequest, requester_id: str) -> WorkRequest:
    item = WorkRequest(id=str(uuid.uuid4()), title=data.title, body=data.body,
                       due_date=data.due_date, requester_id=requester_id)
    with _lock:
        _store[item.id] = item
    return item


def update_status(request_id: str, status: Status) -> WorkRequest | None:
    with _lock:
        item = _store.get(request_id)
        if item is None:
            return None
        _store[request_id] = updated = replace(item, status=status)
    return updated


def clear() -> None:
    """테스트용"""
    _store.clear()
