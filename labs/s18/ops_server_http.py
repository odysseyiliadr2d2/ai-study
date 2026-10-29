"""원격용 Streamable HTTP + Bearer 토큰 — n8n 처럼 다른 프로세스·컨테이너에서 붙을 때.

    export OPS_MCP_TOKEN=$(python -c "import secrets; print(secrets.token_urlsafe(32))")
    export OPS_MCP_ALLOWED_HOSTS="127.0.0.1:*,localhost:*,host.docker.internal:*"
    python ops_server_http.py            # http://0.0.0.0:8765/mcp

토큰 하나로 막는 최소 구성입니다. 운영에서는 사내 인증 프록시(OAuth·SSO) 뒤에 두고, 사용자별 권한을 서버에서 다시 확인합니다.
"""
import os
import secrets

import uvicorn
from mcp.server.transport_security import TransportSecuritySettings
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.responses import JSONResponse

from ops_server import mcp  # 같은 도구 정의를 그대로 재사용

TOKEN = os.environ["OPS_MCP_TOKEN"]
HOSTS = os.getenv("OPS_MCP_ALLOWED_HOSTS", "127.0.0.1:*,localhost:*").split(",")


class BearerAuth(BaseHTTPMiddleware):
    async def dispatch(self, request, call_next):
        sent = request.headers.get("authorization", "")
        if not secrets.compare_digest(sent, f"Bearer {TOKEN}"):
            return JSONResponse({"error": "unauthorized"}, status_code=401)
        return await call_next(request)


app = mcp.streamable_http_app(
    host="0.0.0.0",
    # 외부 바인딩이면 DNS 리바인딩 방어를 직접 설정합니다 (허용할 Host 헤더 목록)
    transport_security=TransportSecuritySettings(enable_dns_rebinding_protection=True, allowed_hosts=HOSTS),
)
app.add_middleware(BearerAuth)

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=int(os.getenv("PORT", "8765")))
