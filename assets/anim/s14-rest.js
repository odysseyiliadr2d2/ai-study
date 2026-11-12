// 세션 14 · 등록 버튼 한 번이 지나가는 길
AXAnim.register("s14-rest", (root, U) => U.flow(root, {
 "label": "화면과 REST API 흐름 애니메이션",
 "nodes": [
  {
   "k": "ui",
   "x": 20,
   "y": 20,
   "w": 170,
   "h": 170,
   "t": "화면 (브라우저)",
   "lines": [
    "업무 요청 등록 폼",
    "제목 · 내용 · 분류",
    {
     "t": "① 화면 검증: 필수값",
     "at": 1,
     "b": 1
    },
    {
     "t": "편의용 · 우회 가능",
     "at": 1
    }
   ]
  },
  {
   "k": "ctl",
   "x": 220,
   "y": 20,
   "w": 180,
   "h": 110,
   "t": "Controller",
   "lines": [
    {
     "t": "POST /api/requests",
     "mono": 1
    },
    {
     "t": "② 형식 검증 → 400",
     "at": 2
    }
   ]
  },
  {
   "k": "svc",
   "x": 220,
   "y": 150,
   "w": 180,
   "h": 110,
   "t": "Service",
   "lines": [
    "업무 규칙은 한곳에",
    {
     "t": "③ 중복 · 권한 확인",
     "at": 3
    }
   ]
  },
  {
   "k": "repo",
   "x": 220,
   "y": 280,
   "w": 180,
   "h": 100,
   "t": "Repository",
   "lines": [
    "SQL 또는 JPA"
   ]
  },
  {
   "k": "db",
   "x": 430,
   "y": 280,
   "w": 190,
   "h": 100,
   "t": "DB",
   "lines": [
    {
     "t": "requests 테이블",
     "mono": 0
    }
   ]
  },
  {
   "k": "resp",
   "x": 430,
   "y": 20,
   "w": 190,
   "h": 240,
   "t": "응답",
   "lines": [
    {
     "t": "201 Created",
     "mono": 1,
     "at": 5,
     "b": 1
    },
    {
     "t": "{ \"id\": 1024,",
     "mono": 1,
     "at": 5
    },
    {
     "t": "  \"status\": \"접수\" }",
     "mono": 1,
     "at": 5
    },
    {
     "t": "",
     "at": 5
    },
    {
     "t": "화면은 목록을 다시 그림",
     "at": 5
    },
    {
     "t": "틀리면 400 · 이유 표시",
     "at": 5
    }
   ]
  }
 ],
 "edges": [
  {
   "from": "ui",
   "to": "ctl"
  },
  {
   "from": "ctl",
   "to": "svc"
  },
  {
   "from": "svc",
   "to": "repo"
  },
  {
   "from": "repo",
   "to": "db"
  },
  {
   "from": "ctl",
   "to": "resp"
  }
 ],
 "steps": [
  {
   "t": "화면이 입력을 받습니다",
   "d": "사용자가 폼에 제목과 내용을 쓰고 등록을 누릅니다. 화면은 보여 주고 입력을 받는 일만 합니다.",
   "hl": [
    "ui"
   ]
  },
  {
   "t": "REST API 로 보냅니다",
   "d": "POST /api/requests 로 JSON 을 보냅니다. 화면 검증은 사용자 편의일 뿐 우회될 수 있어 서버에서 다시 검사합니다.",
   "hl": [
    "ui"
   ],
   "e": [
    "ui-ctl"
   ]
  },
  {
   "t": "Controller 가 형식을 검사합니다",
   "d": "필수값 · 길이 · 형식이 틀리면 400 과 이유를 돌려줍니다. Controller 는 요청을 받아 서비스에 넘기는 일만 합니다.",
   "hl": [
    "ctl"
   ]
  },
  {
   "t": "업무 규칙은 Service 에",
   "d": "중복 등록, 권한, 상태 변경 규칙 같은 업무 판단은 Service 한곳에 모읍니다. 화면이 바뀌어도 규칙이 흩어지지 않습니다.",
   "hl": [
    "svc"
   ],
   "e": [
    "ctl-svc"
   ]
  },
  {
   "t": "저장은 Repository 가",
   "d": "DB 접근은 Repository 로 감쌉니다. Python 트랙은 SQL, Java 트랙은 Spring Data JPA 를 씁니다.",
   "hl": [
    "repo",
    "db"
   ],
   "e": [
    "svc-repo",
    "repo-db"
   ]
  },
  {
   "t": "201 과 결과를 돌려줍니다",
   "d": "규격서에 정한 상태 코드와 JSON 을 돌려주면 화면은 목록을 다시 그립니다. 규격을 먼저 써 두면 화면과 서버를 나눠 만들 수 있습니다.",
   "hl": [
    "resp"
   ],
   "e": [
    "ctl-resp"
   ]
  }
 ]
}));
