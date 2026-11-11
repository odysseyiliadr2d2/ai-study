"""app/health.py — 헬스체크: 앱이 떠 있고 필수 설정이 있는지만 확인 (DB · LLM 은 부르지 않음).

main.py 에 두 줄을 더합니다:
    from app import health
    app.include_router(health.router)
"""
import logging
import os

from fastapi import APIRouter
from fastapi.responses import JSONResponse

log = logging.getLogger("ticketdesk")
router = APIRouter()
REQUIRED = ["DATABASE_URL", "DB_PASSWORD", "JWT_SECRET", "ANTHROPIC_API_KEY"]


@router.get("/api/health", include_in_schema=False)
def health() -> JSONResponse:
    missing = [name for name in REQUIRED if not os.getenv(name)]
    if missing:
        # 어떤 변수가 빠졌는지는 로그에만, 응답에는 상태만
        log.error("필수 환경변수 누락: %s", ", ".join(missing))
        return JSONResponse({"status": "DOWN"}, status_code=503)
    return JSONResponse({"status": "UP", "version": os.getenv("APP_VERSION", "dev")})
