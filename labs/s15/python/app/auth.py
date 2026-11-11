"""app/auth.py — 로그인(OAuth2 password → JWT)과 역할 확인.

실습용 사용자 3명은 비밀번호 하나(DEMO_PASSWORD)를 함께 씁니다. 사내 적용 시 LDAP · AD · SSO(OIDC)로 바꿉니다.
"""
import os
import secrets
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Annotated

import jwt
from fastapi import Depends, HTTPException
from fastapi.security import OAuth2PasswordBearer

ALGORITHM = "HS256"
TOKEN_TTL = timedelta(hours=1)
USERS = {"staff1": ("USER",), "staff2": ("USER",), "handler1": ("USER", "HANDLER")}

oauth2 = OAuth2PasswordBearer(tokenUrl="/api/token", auto_error=False)   # /docs 의 Authorize 버튼이 이 주소로 로그인


@dataclass(frozen=True)
class User:
    name: str
    roles: tuple[str, ...]

    @property
    def is_handler(self) -> bool:
        return "HANDLER" in self.roles


def _secret() -> str:
    return os.environ["JWT_SECRET"]          # 32바이트 이상 임의 문자열. 없으면 기동 후 첫 요청에서 실패


def authenticate(username: str, password: str) -> User | None:
    demo = os.getenv("DEMO_PASSWORD", "")
    if username not in USERS or not demo or not secrets.compare_digest(password.encode(), demo.encode()):
        return None
    return User(username, USERS[username])


def issue_token(user: User) -> str:
    claims = {"sub": user.name, "exp": datetime.now(timezone.utc) + TOKEN_TTL}
    return jwt.encode(claims, _secret(), algorithm=ALGORITHM)


def unauthorized() -> HTTPException:
    return HTTPException(status_code=401, detail="로그인이 필요합니다", headers={"WWW-Authenticate": "Bearer"})


def current_user(token: Annotated[str | None, Depends(oauth2)]) -> User:
    if not token:
        raise unauthorized()
    try:
        claims = jwt.decode(token, _secret(), algorithms=[ALGORITHM])   # 서명 · 만료를 매번 검증
    except jwt.InvalidTokenError:
        raise unauthorized() from None
    name = claims.get("sub")
    if name not in USERS:
        raise unauthorized()
    return User(name, USERS[name])            # 역할은 토큰이 아니라 서버의 사용자 목록에서 다시 읽습니다


def handler_only(user: Annotated[User, Depends(current_user)]) -> User:
    if not user.is_handler:
        raise HTTPException(status_code=403, detail="담당자만 상태를 바꿀 수 있습니다")
    return user


CurrentUser = Annotated[User, Depends(current_user)]
HandlerUser = Annotated[User, Depends(handler_only)]
