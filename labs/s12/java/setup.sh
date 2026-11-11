# 0) SQL 스크립트 · 용어사전은 Python 트랙(이 세션 실습)의 파일을 그대로 씁니다
cp -r ../sql ../glossary.csv .

# 1) 대상 DB: 세션 11 의 Oracle Free 컨테이너(DW 스키마) 또는 사내 개발 DB 의 마트 스키마
#    모든 접속은 읽기 전용 계정으로 합니다 (세션 11 의 T2S_RO 는 DW 스키마 READ 권한)
export ORA_DSN=localhost:1521/FREEPDB1
export ORA_USER=T2S_RO
export ORA_PASSWORD=change_me_ro
export ANTHROPIC_API_KEY=sk-ant-...

# 2) 순서대로 실행 (모두 읽기만 하고, 결과는 파일로만 씁니다)
J="./mvnw -q compile exec:java"
$J -Dexec.mainClass=study.s12.CommentAudit   -Dexec.args=DW     # 정비 전 채움률 · 빈칸 목록
$J -Dexec.mainClass=study.s12.Profiler       -Dexec.args="DW TRD_DAY_F CHNL_CD"
$J -Dexec.mainClass=study.s12.DraftComments  -Dexec.args=DW     # 검토용 CSV + _DRAFT.sql
$J -Dexec.mainClass=study.s12.BackupComments -Dexec.args=DW     # 반영 전 되돌리기용 스크립트
