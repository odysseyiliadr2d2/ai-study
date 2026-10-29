# 1) 실습용 Oracle Free 컨테이너 (개인 노트북). 사내에서는 개발 DB에 DBA가 스크립트를 적용합니다.
#    sql/ 폴더의 *.sql 은 최초 기동 때 이름 순서대로 SYS 로 실행됩니다.
#    NLS_LANG 은 스크립트 안의 한글 코멘트가 깨지지 않게 클라이언트 문자셋을 UTF-8 로 맞추는 설정입니다.
docker run -d --name t2s-oracle -p 1521:1521 \
  -e ORACLE_PASSWORD=change_me_sys \
  -e NLS_LANG=AMERICAN_AMERICA.AL32UTF8 \
  -v "$PWD/sql":/container-entrypoint-initdb.d \
  gvenzl/oracle-free:23-slim

docker logs -f t2s-oracle        # "DATABASE IS READY TO USE!" 가 나오면 Ctrl+C

# 2) 파이썬 환경
cat > requirements.txt <<'REQ'
anthropic>=2.3
oracledb>=2.0        # thin 모드: Oracle Client 설치 없이 접속
sqlglot>=25.0        # SQL 파서 (Oracle 방언)
REQ
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

# 3) 접속 정보는 환경변수로 (코드에 비밀번호를 넣지 않습니다)
export ORA_DSN=localhost:1521/FREEPDB1
export ORA_USER=T2S_RO
export ORA_PASSWORD=change_me_ro
export ANTHROPIC_API_KEY=sk-ant-...
