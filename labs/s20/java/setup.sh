# labs/s20/java 에서 실행합니다. MCP 서버·모의 데이터는 세션 17·18 Python 파일 그대로, 에이전트·평가는 Java.
python -m venv .venv && .venv/bin/pip install "mcp>=2.3,<3"
cp ../../s17/ops_tools.py ../../s17/make_mock.py ../../s18/ops_server.py .
cp ../scenarios.yaml .                                 # Python 트랙과 같은 평가 시나리오 9개
.venv/bin/python make_mock.py                          # mock/            정상 데이터
.venv/bin/python make_mock.py --inject                 # mock_injection/  로그·정의서에 지시문이 숨어 있는 데이터

# 세션 19 Java 트랙의 MCP 클라이언트를 패키지 이름만 바꿔 재사용합니다
mkdir -p src/main/java/study/s20
sed 's/^package study.s19;/package study.s20;/' ../../s19/java/src/main/java/study/s19/OpsMcp.java \
  > src/main/java/study/s20/OpsMcp.java

# Maven Wrapper(mvnw)는 이 폴더에 들어 있습니다 — 따로 만들 필요 없음
export ANTHROPIC_API_KEY=sk-ant-...
export PRICE_IN_PER_MTOK=...  PRICE_OUT_PER_MTOK=...   # 공식 가격표의 모델 단가 (비용 집계용, 없으면 0)
