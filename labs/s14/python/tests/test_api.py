"""tests/test_api.py — API 규격(이론 1절) 회귀 시험: pytest -q"""
import pytest
from fastapi.testclient import TestClient

from app import store
from app.main import app

client = TestClient(app)
VALID = {"title": "월말 잔고 추출 요청", "body": "11월 말 기준 상품별 잔고를 엑셀로 받고 싶습니다"}


@pytest.fixture(autouse=True)
def empty_store():
    store.clear()


def test_create_returns_201_and_location():
    res = client.post("/api/requests", json=VALID)
    assert res.status_code == 201
    body = res.json()
    assert res.headers["location"] == f"/api/requests/{body['id']}"
    assert body["status"] == "RECEIVED" and body["dueDate"] is None and "createdAt" in body


def test_short_title_is_400_with_field_message():
    res = client.post("/api/requests", json={"title": "짧"})
    assert res.status_code == 400
    body = res.json()
    assert body["message"] == "입력값을 확인하세요"
    assert body["errors"]["title"] == ["제목은 5자 이상 입력하세요"]
    assert body["errors"]["body"] == ["필수 항목입니다"]


def test_past_due_date_and_bad_json():
    res = client.post("/api/requests", json={**VALID, "dueDate": "2020-01-01"})
    assert res.json()["errors"]["dueDate"] == ["마감일은 오늘 이후여야 합니다"]
    res = client.post("/api/requests", content="{not json", headers={"Content-Type": "application/json"})
    assert res.status_code == 400 and res.json() == {"message": "JSON 형식이 아닙니다"}


def test_wrong_status_400_and_unknown_id_404():
    assert client.get("/api/requests?status=WRONG").status_code == 400
    assert client.get("/api/requests/nope").status_code == 404
    assert client.patch("/api/requests/nope", json={"status": "DONE"}).status_code == 404


def test_status_change_and_filter():
    created = client.post("/api/requests", json=VALID).json()
    res = client.patch(f"/api/requests/{created['id']}", json={"status": "IN_PROGRESS"})
    assert res.status_code == 200 and res.json()["status"] == "IN_PROGRESS"
    assert [r["id"] for r in client.get("/api/requests?status=IN_PROGRESS").json()] == [created["id"]]
    assert client.get("/api/requests?status=RECEIVED").json() == []


def test_page_renders():
    client.post("/api/requests", json=VALID)
    res = client.get("/requests")
    assert res.status_code == 200 and "월말 잔고 추출 요청" in res.text
