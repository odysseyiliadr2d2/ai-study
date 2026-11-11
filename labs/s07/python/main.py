"""문의 메일 처리: 에이전트 → 사람 승인 → 답장 파일 → 기록 — python main.py
inbox.jsonl 의 새 메일만 처리합니다. 승인 전에는 outbox/ 에 아무것도 쓰지 않습니다.
"""
import csv
import datetime as dt
import json
import traceback
from pathlib import Path

from agent import MODEL, run_agent

INBOX, OUTBOX = Path("inbox.jsonl"), Path("outbox")
LOG, DONE, ERRORS = Path("log.csv"), Path("processed.txt"), Path("errors.log")
FIELDS = ["processed_at", "mail_id", "from", "subject", "category", "urgency", "faq_used", "result"]


def ask_approval(mail: dict, out: dict) -> bool:
    """승인 담당자에게 초안을 보여 주고 y 를 받아야만 True. 입력이 없거나(EOF) 다른 값이면 '직접 처리'."""
    print("\n" + "=" * 60)
    print(f"[승인 요청] {mail['subject']}  ({out['category']}/{out['urgency']})")
    print(f"보낸 사람: {mail['from']}\n요약: {out['summary']}")
    print(f"사람 확인 필요: {out['needs_human']}  · 참고 FAQ: {out['faq_used'] or '없음'}")
    print("---- 답장 초안 ----\n" + out["reply_draft"])
    try:
        return input("발송 승인? [y = 발송 승인 / 그 밖 = 직접 처리] ").strip().lower() == "y"
    except EOFError:                         # python main.py < /dev/null 처럼 무응답이면 발송하지 않음
        return False


def write_reply(mail: dict, out: dict) -> Path:
    """실습에서는 SMTP 대신 파일로 '발송'합니다. 운영에서는 이 함수만 smtplib 발송으로 바꿉니다."""
    OUTBOX.mkdir(exist_ok=True)
    path = OUTBOX / f"{mail['id']}.txt"
    path.write_text(f"To: {mail['from']}\nSubject: Re: {mail['subject']}\nIn-Reply-To: {mail['thread_id']}\n\n"
                    f"{out['reply_draft']}\n", encoding="utf-8")
    return path


def log(mail: dict, out: dict, result: str):
    new = not LOG.exists()
    with open(LOG, "a", newline="", encoding="utf-8-sig") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS)
        if new:
            w.writeheader()
        w.writerow({"processed_at": dt.datetime.now().isoformat(timespec="seconds"), "mail_id": mail["id"],
                    "from": mail["from"], "subject": mail["subject"], "category": out.get("category", ""),
                    "urgency": out.get("urgency", ""), "faq_used": out.get("faq_used", ""), "result": result})


def main():
    done = set(DONE.read_text(encoding="utf-8").split()) if DONE.exists() else set()
    mails = [json.loads(line) for line in INBOX.read_text(encoding="utf-8").splitlines() if line.strip()]
    for mail in [m for m in mails if m["id"] not in done]:
        try:
            out, calls = run_agent(mail)
            print(f"\n[{mail['id']}] 도구 호출 {len(calls)}회: " + ", ".join(c["input"].get("query", "") for c in calls))
            if ask_approval(mail, out):
                print("→ 발송:", write_reply(mail, out))
                log(mail, out, "자동 발송")
            else:
                log(mail, out, "수동 처리")
        except Exception as e:               # 이 메일만 실패로 남기고 다음 메일은 계속 (n8n 의 Error Workflow)
            with open(ERRORS, "a", encoding="utf-8") as f:
                f.write(f"{dt.datetime.now().isoformat(timespec='seconds')} {mail['id']} {MODEL} "
                        f"{type(e).__name__}: {e}\n{traceback.format_exc(limit=2)}\n")
            log(mail, {}, "오류")
            print(f"[{mail['id']}] 오류 → errors.log ({type(e).__name__})")
            continue                         # processed.txt 에 넣지 않으므로 다음 실행에서 다시 처리
        with open(DONE, "a", encoding="utf-8") as f:
            f.write(mail["id"] + "\n")


if __name__ == "__main__":
    main()
