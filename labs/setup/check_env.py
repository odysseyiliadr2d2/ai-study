"""실습 환경 점검 (Python 트랙)

    python labs/setup/check_env.py

설치한 도구의 버전과 API 키 설정 여부를 한 번에 보여 줍니다. 네트워크 호출은 하지 않습니다.
"""
import os
import platform
import shutil
import subprocess
import sys


def ver(cmd):
    exe = shutil.which(cmd[0])
    if not exe:
        return None
    try:
        out = subprocess.run(cmd, capture_output=True, text=True, timeout=15)
        return (out.stdout or out.stderr).strip().splitlines()[0]
    except Exception as e:  # 실행은 되지만 버전 출력이 이상한 경우
        return f"(확인 실패: {e})"


def row(name, value, need):
    mark = "OK " if value else ("-- " if not need else "없음")
    print(f"[{mark}] {name:<22} {value or ('선택' if not need else '설치 필요')}")


print(f"OS: {platform.system()} {platform.release()} / Python {platform.python_version()} ({sys.executable})")
ok = sys.version_info >= (3, 11)
row("Python 3.11 이상", platform.python_version() if ok else None, True)
row("가상환경(venv)", sys.prefix if sys.prefix != sys.base_prefix else None, False)
row("pip", ver([sys.executable, "-m", "pip", "--version"]), True)
row("Git", ver(["git", "--version"]), True)
row("Java (JDK 21)", ver(["java", "-version"]), False)
row("Node.js", ver(["node", "--version"]), False)
row("Docker", ver(["docker", "--version"]), False)
row("Ollama", ver(["ollama", "--version"]), False)

for pkg in ("anthropic", "openai", "pydantic", "sentence_transformers", "fastapi"):
    try:
        mod = __import__(pkg)
        row(f"패키지 {pkg}", getattr(mod, "__version__", "설치됨"), False)
    except ImportError:
        row(f"패키지 {pkg}", None, False)

for key in ("ANTHROPIC_API_KEY", "OPENAI_API_KEY"):
    v = os.getenv(key)
    row(key, f"설정됨 ({v[:7]}…)" if v else None, key == "ANTHROPIC_API_KEY")
