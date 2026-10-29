"""AI 유스케이스 위험 채점 + 심의 체크리스트 출력
python risk_assess.py usecase.yaml > review.md

채점 기준은 학습용 예시입니다. 사내 위험관리 기준(금융회사별 AI 위험관리 체계)에 맞게 가중치를 바꾸세요.
"""
import sys
from datetime import date

import yaml

REG_CHECKED = "2026-10-29"   # 아래 근거 목록을 마지막으로 확인한 날짜

DATA_SCORE = {"public": 0, "internal": 1, "confidential": 2, "personal": 3, "credit": 4}
DEPLOY_SCORE = {"on_prem": 0, "saas": 2, "external_api": 2}
DECISION_SCORE = {"draft": 0, "human_approved": 1, "automated": 3}


def score(uc: dict):
    d, m, i, c = uc["data"], uc["model"], uc["impact"], uc["controls"]
    reasons = []
    s = DATA_SCORE[d["classification"]]
    reasons.append(f"데이터 등급 {d['classification']} (+{s})")
    if d.get("contains_personal_info") and not d.get("pseudonymized"):
        s += 2
        reasons.append("가명처리 안 된 개인정보 포함 (+2)")
    ds = DEPLOY_SCORE[m["deployment"]]
    if ds:
        s += ds
        reasons.append(f"배포 형태 {m['deployment']} (+{ds})")
    if d.get("leaves_company_network"):
        s += 2
        reasons.append("데이터가 사내망 밖으로 나감 (+2)")
    if i.get("customer_facing"):
        s += 2
        reasons.append("고객 대상 (+2)")
    if i.get("affects_rights_or_obligations"):
        s += 4
        reasons.append("고객 권리·의무에 영향 (+4)")
    s += DECISION_SCORE[i["decision_type"]]
    reasons.append(f"결정 방식 {i['decision_type']} (+{DECISION_SCORE[i['decision_type']]})")
    if not c.get("human_review"):
        s += 2
        reasons.append("사람 검토 없음 (+2)")
    return s, reasons


def level(s: int, uc: dict) -> str:
    # 고객 권리·의무에 영향을 주는 AI 는 점수와 관계없이 '높음'으로 다룹니다
    if uc["impact"].get("affects_rights_or_obligations") or s >= 9:
        return "높음"
    return "중간" if s >= 4 else "낮음"


def checklist(uc: dict, lvl: str):
    d, m, i, c = uc["data"], uc["model"], uc["impact"], uc["controls"]
    items = [
        ("승인 모델 목록에 있는 모델인가", m.get("license_checked", False)),
        ("프롬프트·응답 로그를 남기는가", c.get("prompt_logging", False)),
        ("로그 보존 기간이 사내 기준 이상인가 (예: 365일)", c.get("log_retention_days", 0) >= 365),
        ("사람이 최종 검토·승인하는가", c.get("human_review", False)),
        ("프롬프트 앞단에서 개인정보를 마스킹하는가", c.get("pii_masking", False) or d["classification"] in ("public", "internal")),
        ("프롬프트 인젝션 시험을 했는가", c.get("prompt_injection_test", False)),
        ("장애·오답 시 되돌리는 절차가 있는가", c.get("rollback_plan", False)),
    ]
    if m["deployment"] != "on_prem" or d.get("leaves_company_network"):
        items.append(("망분리 예외 근거(규제 특례·규정)와 보안대책을 확인했는가", False))
    if i.get("affects_rights_or_obligations"):
        items.append(("AI 기본법 고영향 AI 해당 여부를 검토했는가", False))
        items.append(("자동화된 결정 거부·설명 요구 대응 절차가 있는가 (개인정보보호법 제37조의2)", False))
    if i.get("customer_facing"):
        items.append(("고객에게 생성형 AI 사용 사실을 미리 알리는가 (AI 기본법 투명성)", False))
    if lvl == "높음":
        items.append(("AI 위원회(의사결정기구) 심의를 받았는가", False))
    return items


def main(path: str):
    uc = yaml.safe_load(open(path, encoding="utf-8"))
    s, reasons = score(uc)
    lvl = level(s, uc)
    print(f"# AI 도입 심의 자료 — {uc['name']} ({uc['id']})\n")
    print(f"- 작성일: {date.today().isoformat()} · 담당: {uc['owner']}")
    print(f"- 목적: {uc['purpose']}")
    print(f"- 위험 점수: **{s}점 → {lvl}**\n")
    print("## 점수 근거")
    for r in reasons:
        print(f"- {r}")
    print("\n## 심의 체크리스트")
    for text, ok in checklist(uc, lvl):
        print(f"- [{'x' if ok else ' '}] {text}")
    print(f"\n## 규제 확인 일자\n- 근거 규정은 {REG_CHECKED} 기준으로 확인했습니다. 심의 전에 금융위원회·금융감독원·"
          "개인정보보호위원회·국가법령정보센터에서 최신 내용을 다시 확인하세요.")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "usecase.yaml")
