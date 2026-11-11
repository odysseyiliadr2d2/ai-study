# 0) DDL · 가상 데이터 · 용어사전 · 예시는 Python 트랙(이 세션 실습)의 파일을 그대로 씁니다
cp -r ../sql ../glossary.json ../examples.json .

# 1) 실습용 Oracle Free 컨테이너 (Python 트랙과 같음. 이미 띄웠다면 건너뜀)
docker run -d --name t2s-oracle -p 1521:1521 \
  -e ORACLE_PASSWORD=change_me_sys \
  -e NLS_LANG=AMERICAN_AMERICA.AL32UTF8 \
  -v "$PWD/sql":/container-entrypoint-initdb.d \
  gvenzl/oracle-free:23-slim
docker logs -f t2s-oracle        # "DATABASE IS READY TO USE!" 가 나오면 Ctrl+C

# 2) 접속 정보는 환경변수로 (코드에 비밀번호를 넣지 않습니다) — 읽기 전용 계정 T2S_RO
export ORA_DSN=localhost:1521/FREEPDB1
export ORA_USER=T2S_RO
export ORA_PASSWORD=change_me_ro
export ANTHROPIC_API_KEY=sk-ant-...

# 3) 순서대로 실행
J="./mvnw -q compile exec:java"
$J -Dexec.mainClass=study.s11.Metadata -Dexec.args="지난달 비대면 거래금액"
$J -Dexec.mainClass=study.s11.SqlGuard
$J -Dexec.mainClass=study.s11.TextToSql -Dexec.args="'지난달 영업일 기준 채널별 거래금액' --base-dt 20261029"
