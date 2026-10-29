"""반입 꾸러미 해시 매니페스트 — 밖에서 make, 안에서 verify

  python manifest.py make bundle > bundle/MANIFEST.json
  python manifest.py verify bundle bundle/MANIFEST.json
"""
import hashlib
import json
import sys
from datetime import date
from pathlib import Path


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):   # 1MB 씩 읽어 대용량 파일도 처리
            h.update(block)
    return h.hexdigest()


def make(root: str):
    base = Path(root)
    files = {}
    for p in sorted(base.rglob("*")):
        if p.is_file() and p.name != "MANIFEST.json" and ".cache" not in p.parts:
            files[str(p.relative_to(base))] = {"sha256": sha256(p), "bytes": p.stat().st_size}
    licenses = [k for k in files if Path(k).name.upper().startswith("LICENSE")]
    manifest = {
        "created": date.today().isoformat(),
        "file_count": len(files),
        "total_gb": round(sum(v["bytes"] for v in files.values()) / 1e9, 2),
        "license_files": licenses,          # 심의 자료에 라이선스 원문을 첨부합니다
        "files": files,
    }
    print(json.dumps(manifest, ensure_ascii=False, indent=1))
    if not licenses:
        print("경고: LICENSE 파일이 없습니다. 모델 카드에서 라이선스를 확인하세요.", file=sys.stderr)


def verify(root: str, manifest_path: str) -> int:
    base = Path(root)
    manifest = json.load(open(manifest_path, encoding="utf-8"))
    bad = 0
    for rel, meta in manifest["files"].items():
        p = base / rel
        if not p.exists():
            print("없음  ", rel)
            bad += 1
        elif sha256(p) != meta["sha256"]:
            print("불일치", rel)
            bad += 1
    print(f"검증 {manifest['file_count']}개 중 문제 {bad}개")
    return 1 if bad else 0


if __name__ == "__main__":
    cmd, *args = sys.argv[1:]
    sys.exit(make(*args) if cmd == "make" else verify(*args))
