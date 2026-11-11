"""tests/test_api.py — Oracle · API 키 없이 도는 테스트: pytest -q

DB 는 SQLite 메모리, AI 분석은 가짜 함수로 바꿉니다. 같은 저장소 코드가 Oracle 에서도 돌도록
SQLAlchemy 표준 기능만 썼습니다.
"""
import os

os.environ.setdefault("DATABASE_URL", "sqlite+pysqlite:///:memory:")
os.environ.setdefault("JWT_SECRET", "test-only-secret-0123456789-abcdefghijklmnop")
os.environ.setdefault("DEMO_PASSWORD", "test-pw")

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402

from app import ai, db  # noqa: E402
from app.main import app  # noqa: E402

db.Base.metadata.create_all(db.engine)       # 테스트에서만. 운영 테이블은 DBA 가 DDL 로
client = TestClient(app)
VALID = {"title": "월말 잔고 추출 요청", "body": "11월 말 기준 상품별 잔고를 엑셀로 받고 싶습니다"}


@pytest.fixture(autouse=True)
def clean(monkeypatch):
    fake = ai.Analysis(summary="월말 잔고 추출 요청입니다.", category="DATA_EXTRACT", priority="LOW", reason="추출 요청")
    monkeypatch.setattr(ai, "analyze_request", lambda title, body: fake)
    with db.engine.begin() as conn:
        conn.execute(db.WorkRequest.__table__.delete())


def login(name: str) -> dict:
    res = client.post("/api/token", data={"username": name, "password": "test-pw"})
    return {"Authorization": f"Bearer {res.json()['access_token']}"}


def test_login_required_and_wrong_password():
    assert client.get("/api/requests").status_code == 401
    assert client.post("/api/token", data={"username": "staff1", "password": "x"}).status_code == 401


def test_create_saves_analysis():
    res = client.post("/api/requests", json=VALID, headers=login("staff1"))
    assert res.status_code == 201
    body = res.json()
    assert body["category"] == "DATA_EXTRACT" and body["requesterId"] == "staff1"
    assert res.headers["location"] == f"/api/requests/{body['id']}"


def test_ai_failure_still_creates(monkeypatch):
    monkeypatch.setattr(ai, "analyze_request", lambda title, body: None)
    res = client.post("/api/requests", json=VALID, headers=login("staff1"))
    assert res.status_code == 201 and res.json()["category"] is None


def test_rows_are_visible_to_owner_and_handler_only():
    item = client.post("/api/requests", json=VALID, headers=login("staff1")).json()
    assert [r["id"] for r in client.get("/api/requests", headers=login("staff2")).json()] == []
    assert client.get(f"/api/requests/{item['id']}", headers=login("staff2")).status_code == 404
    assert [r["id"] for r in client.get("/api/requests", headers=login("handler1")).json()] == [item["id"]]


def test_only_handler_can_change_status():
    item = client.post("/api/requests", json=VALID, headers=login("staff1")).json()
    url = f"/api/requests/{item['id']}"
    assert client.patch(url, json={"status": "DONE"}, headers=login("staff1")).status_code == 403
    res = client.patch(url, json={"status": "DONE"}, headers=login("handler1"))
    assert res.status_code == 200 and res.json()["status"] == "DONE"


def test_mask_sensitive():
    masked = ai.mask_sensitive("연락처 010-1234-5678, 계좌 123-45-678901, 주민 900101-1234567")
    assert masked == "연락처 [전화번호], 계좌 [계좌번호], 주민 [주민번호]"
