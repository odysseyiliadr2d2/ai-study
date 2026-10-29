"""생성 SQL 검증: 파싱 → SELECT만 → 허용 목록 → 행 수 제한 — python sqlguard.py 로 자체 시험"""
import sqlglot
from sqlglot import exp
from sqlglot.errors import ParseError

MAX_ROWS = 200
WRITE_NODES = (exp.Insert, exp.Update, exp.Delete, exp.Merge, exp.Create, exp.Drop,
               exp.Alter, exp.TruncateTable, exp.Grant, exp.Command)
DENY_PREFIX = ("DBMS_", "UTL_", "SYS.", "OWA_", "HTP.", "APEX_")   # 패키지 호출 금지


class UnsafeSQL(ValueError):
    pass


def check_sql(sql: str, allowed_tables: set[str]) -> str:
    """안전하면 행 수 제한을 붙인 SQL 을 돌려주고, 아니면 UnsafeSQL 을 던집니다."""
    sql = sql.strip().rstrip(";").strip()
    try:
        stmts = [s for s in sqlglot.parse(sql, read="oracle") if s is not None]
    except ParseError as e:
        raise UnsafeSQL(f"파싱 실패: {str(e).splitlines()[0]}")
    if len(stmts) != 1:
        raise UnsafeSQL(f"문장은 1개만 허용합니다 (받은 문장 {len(stmts)}개)")
    tree = stmts[0]

    if not isinstance(tree, exp.Query):                       # SELECT / WITH / UNION 만
        raise UnsafeSQL(f"조회문만 허용합니다: {type(tree).__name__}")
    if any(tree.find(n) for n in WRITE_NODES):
        raise UnsafeSQL("데이터·구조 변경 구문이 들어 있습니다")
    for sel in tree.find_all(exp.Select):
        if sel.args.get("locks"):
            raise UnsafeSQL("FOR UPDATE 잠금은 허용하지 않습니다")
        if sel.args.get("into"):
            raise UnsafeSQL("SELECT INTO 는 허용하지 않습니다")

    ctes = {c.alias_or_name.upper() for c in tree.find_all(exp.CTE)}
    for t in tree.find_all(exp.Table):
        if "@" in t.name:
            raise UnsafeSQL(f"DB 링크는 허용하지 않습니다: {t.name}")
        full = f"{t.db}.{t.name}".upper() if t.db else t.name.upper()
        if full in ctes or full == "DUAL":
            continue
        if full not in allowed_tables:
            raise UnsafeSQL(f"허용 목록에 없는 테이블: {full} (스키마명을 붙여 주세요)")

    for node in tree.find_all(exp.Dot, exp.Anonymous):
        name = node.sql(dialect="oracle").upper()
        if name.startswith(DENY_PREFIX):
            raise UnsafeSQL(f"허용하지 않는 함수 호출: {name[:40]}")

    # 행 수 제한: 없거나 MAX_ROWS 보다 크면 FETCH FIRST MAX_ROWS ROWS ONLY 로 바꿉니다
    lim = tree.args.get("limit")
    count = lim.args.get("count") if lim is not None else None
    if count is None or not count.is_int or int(count.name) > MAX_ROWS:
        tree = tree.limit(MAX_ROWS)
    return tree.sql(dialect="oracle", pretty=True)


if __name__ == "__main__":
    allowed = {"DW.TRD_DAY_F", "DW.CMN_BIZ_DAY_D"}
    tests = [
        "SELECT base_dt, SUM(trd_amt) FROM dw.trd_day_f WHERE base_dt BETWEEN :from_dt AND :to_dt GROUP BY base_dt",
        "DELETE FROM dw.trd_day_f",
        "SELECT * FROM dw.trd_day_f; DROP TABLE dw.trd_day_f",
        "SELECT * FROM dw.trd_day_f FOR UPDATE",
        "SELECT * FROM cust.cust_master",
        "SELECT dbms_lock.sleep(60) FROM dual",
        "SELECT * FROM dw.trd_day_f@prod_link",
    ]
    for s in tests:
        try:
            print("통과 →", check_sql(s, allowed).replace("\n", " "))
        except UnsafeSQL as e:
            print("거부 →", e)
