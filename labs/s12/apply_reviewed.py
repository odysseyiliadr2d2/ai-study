"""검토 끝난 CSV 로 확정 스크립트 만들기 — python apply_reviewed.py review_MART_....csv
'final' 열이 채워진 행만 COMMENT ON 문으로 만듭니다. 실행은 변경 요청 절차에 따라 DBA 가 합니다.
"""
import csv
import re
import sys
from pathlib import Path

from draft_comments import load_glossary, sql_literal

IDENT = re.compile(r"^[A-Z][A-Z0-9_$#]{0,127}$")


def lint(comment: str, glossary: list[dict]) -> list[str]:
    """용어집과 맞는지 기계적으로 한 번 더 봅니다: 금지표현 사용, 너무 긴 설명."""
    issues = []
    for g in glossary:
        for bad in filter(None, g["금지표현"].split("|")):
            if bad in comment and g["표준용어"] not in comment:
                issues.append(f"'{bad}' 대신 표준용어 '{g['표준용어']}'")
    if len(comment) > 200:
        issues.append("200자 초과")
    return issues


def main(path: str):
    m = re.match(r"review_(.+)_\d{8}_\d{4}\.csv$", Path(path).name)   # review_<스키마>_<일시>.csv
    if not m:
        raise SystemExit("draft_comments.py 가 만든 review_스키마_일시.csv 파일을 넣어 주세요")
    owner = m.group(1)
    glossary = load_glossary()
    ok, warn = [], []
    with open(path, encoding="utf-8-sig") as f:
        for r in csv.DictReader(f):
            final = r["final"].strip()
            if not final:
                continue
            if not (IDENT.match(owner) and IDENT.match(r["table"]) and IDENT.match(r["column"])):
                raise SystemExit(f"식별자 형식 오류: {r['table']}.{r['column']}")
            issues = lint(final, glossary)
            (warn if issues else ok).append((r, final, issues))
    out = str(Path(path).with_name(Path(path).name.replace("review_", "comments_").replace(".csv", "_FINAL.sql")))
    with open(out, "w", encoding="utf-8") as f:
        f.write(f"-- 검토 완료 코멘트 {len(ok)}건 (용어 경고 {len(warn)}건은 아래 주석 처리)\n")
        f.write("-- 실행 전 대상 DB·스키마를 확인하세요. 되돌리기: 이전 값을 담은 백업 스크립트를 함께 보관합니다.\n")
        for r, final, _ in ok:
            f.write(f"COMMENT ON COLUMN {owner}.{r['table']}.{r['column']} IS {sql_literal(final)};  -- {r['reviewer']}\n")
        for r, final, issues in warn:
            f.write(f"-- 확인 필요({'; '.join(issues)}): COMMENT ON COLUMN {owner}.{r['table']}.{r['column']} IS {sql_literal(final)};\n")
    print(f"확정 {len(ok)}건, 용어 경고 {len(warn)}건 → {out}")


if __name__ == "__main__":
    main(sys.argv[1])
