"""app/main.py — 로그인 확인 → 검증 → AI 분석 → 저장 → (응답 후) n8n 알림.

    uvicorn app.main:app --port 8000      # http://localhost:8000/docs 의 Authorize 로 로그인 후 호출
schemas.py 는 세션 14 그대로 두고 WorkRequestOut 의 id 타입만 int 로 바꿉니다 (Oracle IDENTITY).
"""
import json
import logging
import os
import urllib.error
import urllib.request
from typing import Annotated

from fastapi import BackgroundTasks, Depends, FastAPI, HTTPException, Query, Request, Response
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from fastapi.security import OAuth2PasswordRequestForm
from sqlalchemy.exc import SQLAlchemyError

from app import ai, auth, db
from app.auth import CurrentUser, HandlerUser
from app.schemas import CreateRequest, Status, StatusChange, WorkRequestOut

log = logging.getLogger("ticketdesk")
app = FastAPI(title="ticket-desk-py")

MESSAGES = {"missing": "필수 항목입니다", "string_type": "문자열로 보내야 합니다", "literal_error": "허용하지 않는 값입니다"}


@app.exception_handler(RequestValidationError)
async def validation_error(request: Request, exc: RequestValidationError) -> JSONResponse:
    errs = exc.errors()
    if any(e["type"] == "json_invalid" for e in errs):
        return JSONResponse({"message": "JSON 형식이 아닙니다"}, status_code=400)
    fields: dict[str, list[str]] = {}
    for e in errs:
        name = str(e["loc"][-1]) if len(e["loc"]) > 1 else "body"
        fields.setdefault(name, []).append(MESSAGES.get(e["type"], e["msg"]))
    where = {e["loc"][0] for e in errs}
    message = ("조회 조건을 확인하세요" if "query" in where
               else "상태 값을 확인하세요" if request.method == "PATCH" else "입력값을 확인하세요")
    return JSONResponse({"message": message, "errors": fields}, status_code=400)


@app.exception_handler(HTTPException)
async def http_error(_request: Request, exc: HTTPException) -> JSONResponse:
    return JSONResponse({"message": exc.detail}, status_code=exc.status_code, headers=exc.headers)


def not_found() -> HTTPException:
    return HTTPException(status_code=404, detail="요청을 찾을 수 없습니다")


@app.post("/api/token")
def login(form: Annotated[OAuth2PasswordRequestForm, Depends()]) -> dict:
    user = auth.authenticate(form.username, form.password)
    if user is None:
        raise HTTPException(status_code=401, detail="아이디 또는 비밀번호가 맞지 않습니다")
    return {"access_token": auth.issue_token(user), "token_type": "bearer"}


@app.get("/api/requests", response_model=list[WorkRequestOut])
def list_api(user: CurrentUser, status: Annotated[Status | None, Query()] = None):
    return db.list_requests(user, status)                 # 본인 것만, 담당자는 전체


@app.post("/api/requests", response_model=WorkRequestOut, status_code=201)
def create_api(body: CreateRequest, response: Response, background: BackgroundTasks, user: CurrentUser):
    analysis = ai.analyze_request(body.title, body.body)   # 저장 전, 트랜잭션 밖. 실패하면 None
    try:
        created = db.create_request(body, user.name, analysis)
    except SQLAlchemyError:
        log.exception("요청 저장 실패 requester=%s", user.name)
        raise HTTPException(status_code=500, detail="저장하지 못했습니다") from None
    log.info("요청 등록 id=%s analyzed=%s category=%s", created.id, analysis is not None, created.category)
    background.add_task(notify_n8n, created)               # 응답을 보낸 뒤 실행 (알림 실패 ≠ 등록 실패)
    response.headers["Location"] = f"/api/requests/{created.id}"
    return created


@app.get("/api/requests/{request_id}", response_model=WorkRequestOut)
def get_api(request_id: int, user: CurrentUser):
    item = db.get_request(user, request_id)                # 남의 요청이면 None → 404
    if item is None:
        raise not_found()
    return item


@app.patch("/api/requests/{request_id}", response_model=WorkRequestOut)
def change_status_api(request_id: int, body: StatusChange, user: HandlerUser):   # 담당자가 아니면 403
    updated = db.update_status(user, request_id, body.status)
    if updated is None:
        raise not_found()
    return updated


def notify_n8n(item: db.WorkRequest) -> None:
    """본문 대신 요약과 링크만 보냅니다. n8n 은 X-Webhook-Secret 헤더를 확인합니다."""
    url = os.getenv("N8N_WEBHOOK_URL")
    if not url:
        return
    payload = {"id": item.id, "title": item.title, "category": item.category, "priority": item.priority,
               "summary": item.summary, "link": f"{os.getenv('APP_BASE_URL', '')}/requests"}
    req = urllib.request.Request(url, data=json.dumps(payload, ensure_ascii=False).encode(), method="POST",
                                 headers={"Content-Type": "application/json",
                                          "X-Webhook-Secret": os.getenv("N8N_WEBHOOK_SECRET", "")})
    try:
        with urllib.request.urlopen(req, timeout=5) as res:
            if res.status >= 300:
                log.warning("n8n 알림 응답 오류 %s", res.status)
    except (urllib.error.URLError, TimeoutError) as e:
        log.warning("n8n 알림 실패 id=%s: %s", item.id, e)
