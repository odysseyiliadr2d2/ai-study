# Dockerfile — ticket-desk-py (FastAPI). 빌드: docker build --build-arg APP_VERSION=$(git rev-parse --short HEAD) -t ticket-desk-py .
# 폐쇄망: BASE_IMAGE 를 사내 레지스트리 경로로, pip 은 PIP_INDEX_URL 로 사내 PyPI 미러를 봅니다.
ARG BASE_IMAGE=python:3.12-slim

FROM ${BASE_IMAGE} AS build
WORKDIR /build
COPY requirements.txt .
ARG PIP_INDEX_URL
# 의존성만 먼저 설치해 두면 코드만 바뀐 빌드에서 이 계층을 다시 쓰지 않습니다
RUN pip install --no-cache-dir --prefix=/install ${PIP_INDEX_URL:+--index-url "$PIP_INDEX_URL"} -r requirements.txt

FROM ${BASE_IMAGE}
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
COPY --from=build /install /usr/local
COPY app ./app
RUN useradd --system --uid 10001 app
USER app
ARG APP_VERSION=dev
ENV APP_VERSION=${APP_VERSION}
EXPOSE 8000
HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/api/health', timeout=3)"
# 비밀값(DB_PASSWORD, ANTHROPIC_API_KEY 등)은 이미지에 넣지 않고 실행할 때 -e / Kubernetes Secret 으로
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "2"]
