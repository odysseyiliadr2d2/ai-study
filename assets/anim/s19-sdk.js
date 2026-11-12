// 세션 19 · Agent SDK 가 대신 돌리는 루프, 그리고 OpenClaw
AXAnim.register("s19-sdk", (root, U) => U.flow(root, {
 "label": "Agent SDK 애니메이션",
 "nodes": [
  {
   "k": "user",
   "x": 20,
   "y": 20,
   "w": 140,
   "h": 90,
   "t": "사용자",
   "lines": [
    "장애 보고서",
    "써 줘"
   ]
  },
  {
   "k": "main",
   "x": 190,
   "y": 20,
   "w": 220,
   "h": 170,
   "t": "메인 에이전트",
   "lines": [
    {
     "t": "query(prompt, options)",
     "mono": 1
    },
    {
     "t": "max_turns",
     "mono": 1,
     "at": 1
    },
    {
     "t": "max_budget_usd",
     "mono": 1,
     "at": 1
    },
    {
     "t": "보고서 작성 · 재실행 제안",
     "at": 2
    }
   ]
  },
  {
   "k": "sub",
   "x": 440,
   "y": 20,
   "w": 180,
   "h": 170,
   "t": "조사 서브에이전트",
   "at": 2,
   "lines": [
    "읽기 도구만",
    "작은 모델",
    "요약만 돌려줌",
    "작업 ID 는 위임 시 전달"
   ]
  },
  {
   "k": "perm",
   "x": 190,
   "y": 220,
   "w": 220,
   "h": 90,
   "t": "can_use_tool 콜백",
   "tone": "blue",
   "at": 3,
   "lines": [
    "allowed_tools 밖 도구",
    "→ 사람이 승인 · 거절"
   ]
  },
  {
   "k": "res",
   "x": 20,
   "y": 220,
   "w": 140,
   "h": 90,
   "t": "ResultMessage",
   "at": 4,
   "lines": [
    "결과 · 턴 수",
    "시간 · 추정 비용"
   ]
  },
  {
   "k": "claw",
   "x": 440,
   "y": 220,
   "w": 180,
   "h": 165,
   "t": "OpenClaw",
   "tone": "dash",
   "at": 5,
   "lines": [
    "메신저로 지시",
    "정해진 시간에 실행",
    "회사 밖 격리 환경",
    "최소 권한만"
   ]
  }
 ],
 "edges": [
  {
   "from": "user",
   "to": "main"
  },
  {
   "from": "main",
   "to": "sub"
  },
  {
   "from": "main",
   "to": "perm"
  },
  {
   "from": "main",
   "to": "res",
   "off": 50
  }
 ],
 "steps": [
  {
   "t": "루프는 SDK 가 돌립니다",
   "d": "세션 17 에서 직접 짠 도구 호출 루프를 Agent SDK 가 대신 돌립니다. query() 를 돌리며 메시지를 받기만 하면 됩니다.",
   "hl": [
    "user",
    "main"
   ],
   "e": [
    "user-main"
   ]
  },
  {
   "t": "한도를 먼저 정합니다",
   "d": "max_turns 와 max_budget_usd 로 반복 횟수와 비용 상한을 둡니다. setting_sources=[] 로 개인 설정이 섞이지 않게 해 재현성을 지킵니다.",
   "hl": [
    "main"
   ]
  },
  {
   "t": "조사는 서브에이전트에게",
   "d": "읽기 도구만 가진 작은 모델이 로그를 조사하고 요약만 돌려줍니다. 서브에이전트는 부모 대화를 모르므로 작업 ID 를 위임 프롬프트에 넣습니다.",
   "hl": [
    "sub"
   ],
   "e": [
    "main-sub"
   ]
  },
  {
   "t": "허용 목록 밖은 사람에게",
   "d": "allowed_tools 에 없는 도구를 부르려 하면 can_use_tool 콜백으로 넘어가 사람이 승인하거나 거절합니다.",
   "hl2": [
    "perm"
   ],
   "e": [
    "main-perm"
   ]
  },
  {
   "t": "결과와 비용을 받습니다",
   "d": "마지막 ResultMessage 에 결과 텍스트, 턴 수, 소요 시간, 추정 비용이 들어 있어 운영 기록으로 남깁니다.",
   "hl": [
    "res"
   ],
   "e": [
    "main-res"
   ]
  },
  {
   "t": "OpenClaw 는 상시 개인 에이전트",
   "d": "메신저로 지시받고 정해진 시간에 스스로 일하는 런타임입니다. 실행 권한이 넓으므로 회사 밖 격리 환경에서 최소 권한으로만 씁니다.",
   "hl": [
    "claw"
   ]
  }
 ]
}));
