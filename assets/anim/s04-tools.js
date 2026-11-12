// 세션 04 · 회사별 툴체인을 한 장으로
AXAnim.register("s04-tools", (root, U) => U.flow(root, {
 "label": "생성형 AI 도구 지도 애니메이션",
 "nodes": [
  {
   "k": "a",
   "x": 20,
   "y": 20,
   "w": 145,
   "h": 230,
   "t": "Anthropic",
   "chips": [
    {
     "k": "a1",
     "t": "Claude 앱"
    },
    {
     "k": "a2",
     "t": "Claude Code"
    },
    {
     "k": "a3",
     "t": "CLAUDE.md"
    },
    {
     "k": "a4",
     "t": "Claude Agent SDK"
    },
    {
     "k": "a5",
     "t": "Claude 5.5 모델"
    }
   ]
  },
  {
   "k": "o",
   "x": 175,
   "y": 20,
   "w": 145,
   "h": 230,
   "t": "OpenAI",
   "chips": [
    {
     "k": "o1",
     "t": "ChatGPT"
    },
    {
     "k": "o2",
     "t": "Codex CLI"
    },
    {
     "k": "o3",
     "t": "AGENTS.md"
    },
    {
     "k": "o4",
     "t": "Responses · Agents"
    },
    {
     "k": "o5",
     "t": "GPT-5.5"
    }
   ]
  },
  {
   "k": "g",
   "x": 330,
   "y": 20,
   "w": 145,
   "h": 230,
   "t": "Google",
   "chips": [
    {
     "k": "g1",
     "t": "Gemini 앱"
    },
    {
     "k": "g2",
     "t": "Antigravity CLI"
    },
    {
     "k": "g3",
     "t": "AGENTS.md"
    },
    {
     "k": "g4",
     "t": "Gemini API"
    },
    {
     "k": "g5",
     "t": "Gemini 모델"
    }
   ]
  },
  {
   "k": "x",
   "x": 485,
   "y": 20,
   "w": 135,
   "h": 230,
   "t": "오픈소스 축",
   "tone": "green",
   "chips": [
    {
     "k": "x1",
     "t": "Ollama"
    },
    {
     "k": "x2",
     "t": "n8n"
    },
    {
     "k": "x3",
     "t": "MCP"
    },
    {
     "k": "x4",
     "t": "OpenClaw"
    }
   ]
  },
  {
   "k": "bill",
   "x": 20,
   "y": 280,
   "w": 600,
   "h": 105,
   "t": "토큰 과금",
   "at": 6,
   "lines": [
    "입력 토큰 × 입력 단가 + 출력 토큰 × 출력 단가",
    "매번 같은 앞부분(시스템 프롬프트 등)은 프롬프트 캐싱으로 할인",
    "오픈웨이트 모델은 토큰 과금 대신 GPU 서버 · 전력 · 운영 인력 고정비"
   ]
  }
 ],
 "notes": [
  {
   "x": 20,
   "y": 268,
   "t": "위에서부터 앱 → CLI → 규칙 파일 → SDK · API → 모델",
   "cls": "a-sub"
  }
 ],
 "steps": [
  {
   "t": "맨 위는 채팅 앱입니다",
   "d": "Claude 앱 · ChatGPT · Gemini 앱처럼 사람이 직접 대화하는 화면입니다. 업무 자동화는 아래 칸에서 일어납니다.",
   "hl": [
    "a1",
    "o1",
    "g1"
   ]
  },
  {
   "t": "터미널 CLI 코딩 에이전트",
   "d": "Claude Code · Codex CLI · Antigravity CLI(agy)는 터미널에서 코드를 읽고 고치고 명령을 실행합니다. 세션 13 에서 다룹니다.",
   "hl": [
    "a2",
    "o2",
    "g2"
   ]
  },
  {
   "t": "규칙 파일",
   "d": "CLAUDE.md 나 AGENTS.md 에 프로젝트 규칙을 적어 두면 에이전트가 매번 읽습니다. agy 는 AGENTS.md 를 권장하고 GEMINI.md 도 읽습니다.",
   "hl": [
    "a3",
    "o3",
    "g3"
   ]
  },
  {
   "t": "SDK · API",
   "d": "내 프로그램에서 모델을 부르는 길입니다. Claude Agent SDK, OpenAI Responses API 와 Agents SDK, Gemini API 가 여기에 해당합니다.",
   "hl": [
    "a4",
    "o4",
    "g4"
   ]
  },
  {
   "t": "맨 아래는 모델",
   "d": "같은 회사 안에서도 모델은 몇 달마다 바뀝니다. 모델 이름과 종료 일정은 각 회사 문서의 변경 기록에서 확인합니다.",
   "hl": [
    "a5",
    "o5",
    "g5"
   ]
  },
  {
   "t": "옆에는 오픈소스 축",
   "d": "Ollama 는 오픈웨이트 모델을 로컬에서 OpenAI 호환 API 로 띄우고, n8n · MCP · OpenClaw 는 어느 회사 모델과도 붙습니다.",
   "hl": [
    "x1",
    "x2",
    "x3",
    "x4"
   ],
   "hl2": [
    "x"
   ]
  },
  {
   "t": "비용은 토큰으로 셉니다",
   "d": "상용 API 는 입력 · 출력 토큰 수에 단가를 곱해 과금합니다. 같은 앞부분을 반복해 보낸다면 프롬프트 캐싱으로 줄일 수 있습니다.",
   "hl": [
    "bill"
   ]
  }
 ]
}));
