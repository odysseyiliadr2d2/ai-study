# AGENTS.md — dw-batch-tools (Python)

정보계 야간 배치 점검 스크립트 모음입니다.

## 환경
- Python 3.11+, 가상환경 `.venv`, 의존성은 `requirements.txt` 에만 추가합니다.
- Oracle 접속은 `oracledb` thin 모드, 접속 정보는 환경변수 `DW_DSN`, `DW_USER`, `DW_PASSWORD`.
- 계정은 읽기 전용입니다. `INSERT/UPDATE/DELETE/MERGE/DDL` 문을 코드에 쓰지 않습니다.

## 명령
- 테스트: `pytest -q`
- 정적 검사: `ruff check .`

## 규칙
- 함수에는 타입힌트와 한 줄 docstring.
- SQL 은 `sql/` 폴더의 `.sql` 파일로 분리하고, 바인드 변수(`:base_dt`)만 씁니다.
- 로그에 계좌번호·고객명 같은 값이 찍히지 않게 건수와 키 해시만 남깁니다.
- 테스트 데이터는 `tests/fixtures/` 의 가짜 데이터만 씁니다.
