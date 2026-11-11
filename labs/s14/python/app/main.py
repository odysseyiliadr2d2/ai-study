"""app/main.py — REST API(/api/requests)와 목록 화면(/requests).

    uvicorn app.main:app --reload --port 8000     # http://localhost:8000/requests · 문서 /docs

FastAPI 는 검증 실패를 기본 422 로 돌려주지만, 팀 규격(이론 1절)에 맞춰 400 과
{"message": ..., "errors": {"필드": ["메시지"]}} 모양으로 바꿉니다.
"""
from pathlib import Path
from typing import Annotated

from fastapi import FastAPI, HTTPException, Query, Request, Response
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from fastapi.templating import Jinja2Templates

from app import store
from app.schemas import CreateRequest, Status, StatusChange, WorkRequestOut

app = FastAPI(title="ticket-desk-py")
templates = Jinja2Templates(directory=Path(__file__).parent / "templates")

# pydantic 기본 메시지(영문)를 사용자 문장으로 바꾸는 표 (사용자 정의 오류는 메시지를 그대로 씀)
MESSAGES = {
    "missing": "필수 항목입니다",
    "string_type": "문자열로 보내야 합니다",
    "literal_error": "허용하지 않는 값입니다",
}


@app.exception_handler(RequestValidationError)
async def validation_error(request: Request, exc: RequestValidationError) -> JSONResponse:
    errs = exc.errors()
    if any(e["type"] == "json_invalid" for e in errs):
        return JSONResponse({"message": "JSON 형식이 아닙니다"}, status_code=400)
    fields: dict[str, list[str]] = {}
    for e in errs:
        name = str(e["loc"][-1]) if len(e["loc"]) > 1 else "body"
        msg = MESSAGES.get(e["type"], e["msg"])
        if e["type"] == "literal_error":
            msg = f"{msg}. 허용: {e['ctx']['expected']}"
        fields.setdefault(name, []).append(msg)
    where = {e["loc"][0] for e in errs}
    message = ("조회 조건을 확인하세요" if "query" in where
               else "상태 값을 확인하세요" if request.method == "PATCH" else "입력값을 확인하세요")
    return JSONResponse({"message": message, "errors": fields}, status_code=400)


def not_found() -> HTTPException:
    return HTTPException(status_code=404, detail="요청을 찾을 수 없습니다")


@app.exception_handler(HTTPException)
async def http_error(_request: Request, exc: HTTPException) -> JSONResponse:
    return JSONResponse({"message": exc.detail}, status_code=exc.status_code)


@app.get("/api/requests", response_model=list[WorkRequestOut])
def list_api(status: Annotated[Status | None, Query()] = None):
    return store.list_requests(status)


@app.post("/api/requests", response_model=WorkRequestOut, status_code=201)
def create_api(body: CreateRequest, response: Response):
    created = store.create_request(body, requester_id="demo-user")   # 로그인은 세션 15에서
    response.headers["Location"] = f"/api/requests/{created.id}"
    return created


@app.get("/api/requests/{request_id}", response_model=WorkRequestOut)
def get_api(request_id: str):
    item = store.get_request(request_id)
    if item is None:
        raise not_found()
    return item


@app.patch("/api/requests/{request_id}", response_model=WorkRequestOut)
def change_status_api(request_id: str, body: StatusChange):
    updated = store.update_status(request_id, body.status)
    if updated is None:
        raise not_found()
    return updated


@app.get("/requests", include_in_schema=False)
def requests_page(request: Request):
    """목록은 서버에서 그리고, 등록은 화면의 JavaScript 가 /api/requests 로 보냅니다."""
    return templates.TemplateResponse(request, "requests.html", {"items": store.list_requests()})
