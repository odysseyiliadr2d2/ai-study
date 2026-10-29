#!/usr/bin/env python3
"""세션 데이터 검증: python3 tools/validate.py [세션번호 ...]

- curriculum.json 과 세션 파일의 id·제목·요약이 맞는지
- 필수 항목(개념·이론·실습·예제)이 다 있는지
- 도식 파일과 도식 사양이 있는지
- Python 코드는 문법 검사, JSON 코드는 파싱 검사
- 본문에 허용하지 않은 HTML 태그가 없는지
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LANGS = {"python", "java", "typescript", "tsx", "javascript", "json", "bash", "sql", "yaml", "text",
         "xml", "properties", "dockerfile", "markdown", "plsql", "groovy", "kotlin", "ini", "toml", "diff"}
ALLOWED_TAG = re.compile(r"</?(b|strong|em|code|br|sub|sup)>|<a href=\"[^\"]+\">|</a>")
ANY_TAG = re.compile(r"</?[a-zA-Z][^>]*>")


def texts(obj, path=""):
    """code.src 를 뺀 모든 문자열을 (경로, 값)으로."""
    if isinstance(obj, dict):
        for k, v in obj.items():
            if k in ("src",):
                continue
            yield from texts(v, f"{path}.{k}")
    elif isinstance(obj, list):
        for i, v in enumerate(obj):
            yield from texts(v, f"{path}[{i}]")
    elif isinstance(obj, str):
        yield path, obj


def codes(obj, path=""):
    if isinstance(obj, dict):
        if "src" in obj and "lang" in obj:
            yield path, obj
        for k, v in obj.items():
            yield from codes(v, f"{path}.{k}")
    elif isinstance(obj, list):
        for i, v in enumerate(obj):
            yield from codes(v, f"{path}[{i}]")


def check(sid, meta):
    errs, warns = [], []
    f = ROOT / "data" / "sessions" / f"s{sid:02d}.json"
    if not f.exists():
        return [f"파일 없음: {f.relative_to(ROOT)}"], []
    try:
        s = json.loads(f.read_text(encoding="utf-8"))
    except Exception as e:
        return [f"JSON 오류: {e}"], []

    for k in ("id", "part", "title", "summary", "level", "tags"):
        if s.get(k) != meta.get(k):
            errs.append(f"{k} 가 curriculum.json 과 다릅니다: {s.get(k)!r} ≠ {meta.get(k)!r}")
    if not s.get("duration"):
        warns.append("duration 없음")
    if not (3 <= len(s.get("goals", [])) <= 5):
        errs.append("goals 는 3~5개")
    if not s.get("output"):
        errs.append("output 없음")

    c = s.get("concept", {})
    if not c.get("lead"): errs.append("concept.lead 없음")
    if len(c.get("body", [])) < 2: errs.append("concept.body 2문단 이상")
    if len(c.get("terms", [])) < 3: errs.append("concept.terms 3개 이상")
    t = s.get("theory", {})
    if not t.get("lead"): errs.append("theory.lead 없음")
    if len(t.get("sections", [])) < 2: errs.append("theory.sections 2개 이상")
    for key, d in (("concept", c.get("diagram")), ("theory", t.get("diagram"))):
        if not d or not d.get("src"):
            errs.append(f"{key}.diagram 없음")
            continue
        svg = ROOT / d["src"]
        spec = ROOT / "tools" / "diagrams" / (Path(d["src"]).stem + ".json")
        if not svg.exists(): errs.append(f"도식 파일 없음: {d['src']}")
        if not spec.exists(): errs.append(f"도식 사양 없음: {spec.relative_to(ROOT)}")
        if not d.get("caption"): warns.append(f"{key}.diagram.caption 없음")
    l = s.get("lab", {})
    steps = l.get("steps", [])
    if len(steps) < 3: errs.append("lab.steps 3개 이상")
    if sum(1 for st in steps if st.get("code", {}).get("src")) < 2: errs.append("lab 코드가 있는 단계 2개 이상")
    if not l.get("env"): warns.append("lab.env 없음")
    if len(l.get("check", [])) < 2: errs.append("lab.check 2개 이상")
    x = s.get("example", {})
    for k in ("title", "context"):
        if not x.get(k): errs.append(f"example.{k} 없음")
    if len(x.get("flow", [])) < 3: errs.append("example.flow 3단계 이상")
    if len(x.get("tips", [])) < 2: errs.append("example.tips 2개 이상")
    if len(x.get("pitfalls", [])) < 2: errs.append("example.pitfalls 2개 이상")
    refs = s.get("refs", [])
    if len(refs) < 2: errs.append("refs 2개 이상")
    for r in refs:
        if not str(r.get("url", "")).startswith("https://"):
            errs.append(f"refs url 은 https: {r}")

    for p, v in texts(s):
        bad = [m for m in ANY_TAG.findall(v) if not ALLOWED_TAG.fullmatch(m)]
        if bad:
            errs.append(f"허용하지 않는 태그 {bad[:3]} @ {p}")
    for p, cobj in codes(s):
        lang = cobj.get("lang", "").lower()
        if lang not in LANGS:
            errs.append(f"lang '{lang}' 미지원 @ {p}")
        src = cobj.get("src", "")
        if lang == "python":
            try:
                compile(src, cobj.get("file", p), "exec")
            except SyntaxError as e:
                errs.append(f"Python 문법 오류 @ {p}: {e}")
        if lang == "json":
            try:
                json.loads(src)
            except Exception as e:
                errs.append(f"JSON 코드 파싱 오류 @ {p}: {e}")
        fl = cobj.get("file", "")
        if fl and not re.fullmatch(r"labs/s\d\d/[\w./-]+|[\w./-]+", fl):
            warns.append(f"file 경로 확인: {fl}")
    return errs, warns


def check_extras(ids):
    """퀴즈·자료·용어 연결 (data/quiz.json, media.json, terms.json, glossary.json)"""
    errs = []
    load = lambda n: json.loads((ROOT / "data" / n).read_text(encoding="utf-8"))
    quiz, media, terms, gloss = load("quiz.json"), load("media.json"), load("terms.json"), load("glossary.json")
    for sid in ids:
        k = str(sid)
        qs = quiz.get(k, [])
        if len(qs) < 4: errs.append(f"s{sid:02d} 퀴즈 {len(qs)}문항 (4문항 이상 필요)")
        for i, q in enumerate(qs, 1):
            opts = q.get("options", [])
            if not q.get("q") or len(opts) != 4 or len(set(opts)) != 4:
                errs.append(f"s{sid:02d} 퀴즈 Q{i}: 질문과 서로 다른 보기 4개가 필요")
            if not isinstance(q.get("answer"), int) or not 0 <= q["answer"] < len(opts):
                errs.append(f"s{sid:02d} 퀴즈 Q{i}: answer는 0~{len(opts) - 1}")
            if not q.get("explain"): errs.append(f"s{sid:02d} 퀴즈 Q{i}: 해설(explain) 없음")
        for t in terms.get(k, []):
            if t not in gloss: errs.append(f"s{sid:02d} 용어 '{t}'가 glossary.json에 없음")
        for m in media.get(k, []):
            if not str(m.get("url", "")).startswith("https://") or not m.get("title"):
                errs.append(f"s{sid:02d} 자료 항목에 title과 https url 필요: {m.get('title')}")
    return errs


def main(argv):
    cur = json.loads((ROOT / "data" / "curriculum.json").read_text(encoding="utf-8"))
    want = {int(a) for a in argv[1:]} or {m["id"] for m in cur["sessions"]}
    total_err = 0
    for meta in cur["sessions"]:
        if meta["id"] not in want:
            continue
        errs, warns = check(meta["id"], meta)
        mark = "OK " if not errs else "ERR"
        print(f"[{mark}] s{meta['id']:02d} {meta['title']}")
        for e in errs: print("   ✗", e)
        for w in warns: print("   ·", w)
        total_err += len(errs)
    extra = check_extras(sorted(want))
    print(f"[{'OK ' if not extra else 'ERR'}] 퀴즈·자료·용어")
    for e in extra: print("   ✗", e)
    total_err += len(extra)
    print("오류", total_err, "건")
    sys.exit(1 if total_err else 0)


if __name__ == "__main__":
    main(sys.argv)
