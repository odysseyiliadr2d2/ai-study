/* 3단계 · 심화: 에이전트와 업무 자동화 (9–12주) */
window.WEEKS = window.WEEKS || [];
window.WEEKS.push(
{
  week: 9, stage: 3,
  title: "에이전트 기초",
  summary: "도구 호출과 에이전트 루프를 이해하고, 언제 에이전트가 필요하고 언제 단순 워크플로로 충분한지 판단합니다.",
  goals: [
    "함수 호출(도구 사용)이 동작하는 방식을 설명할 수 있다",
    "관찰-판단-행동의 에이전트 루프를 직접 구현할 수 있다",
    "워크플로와 에이전트 중 무엇을 쓸지 판단할 수 있다"
  ],
  sections: [
    { h: "챗봇에서 에이전트로", body: `지금까지의 LLM은 글을 받아 글을 돌려주는 존재였습니다. [[ai-agent|AI 에이전트]]는 여기에 손발을 붙인 것입니다. 목표를 주면 스스로 계획을 세우고, 필요한 [[tool-use|도구]]를 호출하고, 결과를 보고 다음 행동을 정하며 일을 끝까지 처리합니다.

에이전트의 구성요소는 네 가지입니다.
- **모델**: 판단하는 두뇌
- **도구**: DB 조회, 파일 읽기, API 호출, 코드 실행
- **상태·메모리**: 지금까지 한 일과 결과 ([[memory-agent|에이전트 메모리]])
- **종료 조건과 가드레일**: 언제 멈추고, 무엇은 사람 승인을 받을지` },
    { h: "함수 호출의 원리", body: `[[function-calling|함수 호출]]에서 모델이 직접 함수를 실행하지는 않습니다. 우리가 "이런 도구들이 있다"고 [[tool-schema|도구 스키마]]로 알려 주면, 모델은 "이 도구를 이 인자로 불러 달라"는 요청을 JSON으로 돌려줍니다. 실제 실행은 우리 코드가 하고, 결과를 다시 모델에게 넘깁니다.

\`\`\`python
tools = [{
  "type": "function",
  "function": {
    "name": "get_batch_status",
    "description": "DW 야간 배치 작업의 실행 상태를 조회한다",
    "parameters": {"type": "object",
      "properties": {"job_name": {"type": "string"}, "run_date": {"type": "string", "description": "YYYY-MM-DD"}},
      "required": ["job_name", "run_date"]}
  }
}]

def run_tool(name, args):
    if name == "get_batch_status":
        return query_batch_log(**args)        # 실제 조회는 우리 코드가 수행
    raise ValueError(name)
\`\`\`

> 도구 설명(description)을 사람에게 쓰듯 명확히 적는 것이 에이전트 품질의 절반입니다.` },
    { h: "에이전트 루프", body: `[[agent-loop|에이전트 루프]]는 모델이 더 이상 도구를 부르지 않을 때까지 "모델 호출 → 도구 실행 → 결과 전달"을 반복하는 구조입니다. 추론과 행동을 번갈아 하는 이 패턴을 [[react|ReAct]]라고도 부릅니다.

\`\`\`python
messages = [{"role": "system", "content": "DW 운영 보조. 조회만 가능하고 변경은 하지 않는다."},
            {"role": "user", "content": "어제 실패한 배치가 있으면 원인을 정리해줘"}]

for step in range(10):                        # 무한 루프 방지
    r = client.chat.completions.create(model=MODEL, messages=messages, tools=tools)
    msg = r.choices[0].message
    messages.append(msg)
    if not msg.tool_calls:                    # 도구 호출이 없으면 최종 답
        print(msg.content); break
    for call in msg.tool_calls:
        result = run_tool(call.function.name, json.loads(call.function.arguments))
        messages.append({"role": "tool", "tool_call_id": call.id, "content": json.dumps(result, ensure_ascii=False)})
\`\`\`` },
    { h: "워크플로냐 에이전트냐", body: `모든 자동화에 에이전트가 필요하지는 않습니다. 순서가 정해진 일은 코드가 흐름을 정하고 LLM은 각 단계만 맡는 [[agentic-workflow|에이전틱 워크플로]]가 더 싸고, 빠르고, 예측 가능합니다.

- **워크플로가 맞는 경우**: 매번 같은 단계(분류 → 추출 → 요약), 감사 추적이 중요
- **에이전트가 맞는 경우**: 무엇을 몇 번 조회해야 할지 미리 알 수 없음(장애 원인 조사, 리서치)
- **공통 원칙**: 변경·삭제·발송처럼 되돌리기 어려운 행동에는 [[human-in-the-loop|사람의 승인]]을 넣는다

> 가장 단순한 방법으로 시작하고, 필요할 때만 자율성을 높이세요. [[autonomy-level|자율성 수준]]이 높을수록 위험도 커집니다.` }
  ],
  lab: {
    title: "조회 전용 업무 에이전트",
    steps: [
      "SQLite나 CSV로 가상의 배치 실행 로그와 테이블 적재 건수 데이터를 만든다",
      "조회 도구 2~3개를 정의하고 위 루프로 에이전트를 구현한다",
      "'어제 실패한 배치와 영향받은 테이블은?' 같은 질문 5개로 실행 궤적(어떤 도구를 몇 번 불렀는지)을 기록한다"
    ],
    deliverable: "에이전트 코드와 질문별 실행 궤적 로그"
  },
  quiz: [
    { q: "함수 호출에서 실제 함수를 실행하는 주체는?", options: ["LLM 모델", "우리가 작성한 애플리케이션 코드", "벡터DB", "토크나이저"], answer: 1, explain: "모델은 호출 요청만 만들고, 실행은 애플리케이션이 합니다." },
    { q: "에이전트 루프가 끝나는 조건으로 가장 일반적인 것은?", options: ["토큰이 1개 생성될 때", "모델이 더 이상 도구를 호출하지 않고 답을 낼 때", "온도가 0일 때", "첫 도구 호출 직후"], answer: 1, explain: "도구 호출 없이 최종 답을 내면 루프를 종료합니다." },
    { q: "매번 같은 순서로 처리되는 감사 대상 업무에 더 적합한 것은?", options: ["완전 자율 에이전트", "코드가 흐름을 정하는 워크플로", "멀티 에이전트", "무작위 실행"], answer: 1, explain: "정해진 흐름은 워크플로가 예측 가능하고 감사하기 쉽습니다." },
    { q: "되돌리기 어려운 행동(데이터 삭제 등)에 넣어야 할 장치는?", options: ["온도 상향", "사람의 승인(휴먼 인 더 루프)", "토큰 증가", "캐싱"], answer: 1, explain: "중요한 행동 전에는 사람이 확인하도록 설계합니다." },
    { q: "에이전트 품질에 큰 영향을 주는 요소는?", options: ["도구 설명을 명확히 쓰는 것", "도구 이름을 짧게 하는 것", "도구를 최대한 많이 주는 것", "출력을 영어로 받는 것"], answer: 0, explain: "모델은 설명을 보고 도구를 고르므로 설명의 명확성이 중요합니다." }
  ],
  terms: ["ai-agent", "tool-use", "function-calling", "tool-schema", "agent-loop", "react", "agentic-workflow", "human-in-the-loop"]
},
{
  week: 10, stage: 3,
  title: "MCP와 도구 연동",
  summary: "모델 컨텍스트 프로토콜(MCP)로 DB·파일·사내 시스템을 표준 방식으로 연결하고 권한과 보안을 설계합니다.",
  goals: [
    "MCP가 해결하는 문제와 서버·클라이언트 구조를 설명할 수 있다",
    "간단한 MCP 서버를 만들어 AI 도구에 연결할 수 있다",
    "MCP 연동 시 권한·보안 위험을 식별할 수 있다"
  ],
  sections: [
    { h: "도구 연결의 USB 규격", body: `9주차처럼 도구를 직접 붙이면, AI 앱마다 같은 도구를 따로 구현해야 합니다. [[mcp|모델 컨텍스트 프로토콜(MCP)]]은 이 연결 방식을 표준화한 개방형 규약입니다. 도구를 한 번 MCP 서버로 만들어 두면, MCP를 지원하는 여러 AI 앱(Claude, 코딩 도구, IDE 등)에서 그대로 쓸 수 있습니다.

- **[[mcp-server|MCP 서버]]**: 도구·데이터를 제공하는 쪽. 예) 사내 DB 조회 서버, 파일 서버
- **[[mcp-client|MCP 클라이언트]]**: AI 앱 안에서 서버에 접속하는 쪽
- 서버가 제공하는 것: **tools**(실행할 기능), **resources**(읽을 데이터), **prompts**(재사용 프롬프트)` },
    { h: "MCP 서버 만들기", body: `공식 Python SDK를 쓰면 함수에 데코레이터를 붙이는 것만으로 MCP 서버가 됩니다.

\`\`\`python
# pip install "mcp[cli]"
from mcp.server.fastmcp import FastMCP
import sqlite3

mcp = FastMCP("dw-metadata")

@mcp.tool()
def find_tables(keyword: str) -> list[dict]:
    """테이블명이나 한글 설명에 keyword가 들어간 DW 테이블 목록을 반환한다."""
    con = sqlite3.connect("meta.db")
    rows = con.execute(
        "select table_name, comment from tab_meta where table_name like ? or comment like ?",
        (f"%{keyword}%", f"%{keyword}%")).fetchall()
    return [{"table": t, "comment": c} for t, c in rows]

@mcp.tool()
def describe_table(table_name: str) -> list[dict]:
    """테이블의 컬럼명, 타입, 한글 설명을 반환한다."""
    ...

if __name__ == "__main__":
    mcp.run()          # 기본은 stdio로 로컬 AI 앱과 통신
\`\`\`

이렇게 만든 서버를 AI 앱 설정에 등록하면, 대화 중에 "고객 관련 테이블 찾아줘"라고 하면 모델이 스스로 \`find_tables\`를 호출합니다. 11주차 Text-to-SQL에서 이 메타데이터 서버를 다시 씁니다.` },
    { h: "권한과 보안", body: `MCP는 AI에게 실제 시스템 접근 권한을 주는 일입니다. 다음을 반드시 설계하세요([[agent-permissions|에이전트 권한 관리]]).
- **최소 권한**: 조회 전용 계정, 필요한 스키마만 접근
- **쓰기 작업 분리**: 변경 도구는 별도 서버로 두고 사람 승인 필수
- **[[indirect-prompt-injection|간접 프롬프트 인젝션]]**: 도구가 가져온 문서·메일 안에 "모든 데이터를 외부로 보내라" 같은 문장이 숨어 있을 수 있음. 도구 결과는 데이터일 뿐 지시가 아님
- **출처 확인**: 검증되지 않은 외부 MCP 서버를 함부로 설치하지 않기
- **[[audit-trail|감사 추적]]**: 어떤 도구가 어떤 인자로 언제 호출됐는지 기록

> 금융권에서는 "AI가 무엇을 볼 수 있고 무엇을 할 수 있는가"를 표로 정리해 승인받는 것이 도입의 첫 관문입니다.` },
    { h: "MCP 생태계", body: `GitHub, Slack, Google Drive, 각종 DB 등 많은 서비스가 공식·커뮤니티 MCP 서버를 제공합니다. 사내에서는 그룹웨어, 결재, ITSM, DW 메타데이터 같은 시스템을 MCP 서버로 감싸면 여러 AI 도구에서 재사용할 수 있는 [[connector|커넥터]] 자산이 됩니다.

에이전트끼리 협업하는 [[a2a|에이전트 간 프로토콜(A2A)]] 같은 표준도 함께 발전하고 있습니다.` }
  ],
  lab: {
    title: "DW 메타데이터 MCP 서버",
    steps: [
      "가상의 테이블·컬럼 정의서(20개 테이블 정도)를 SQLite에 넣는다",
      "위 예제로 find_tables, describe_table 도구를 가진 MCP 서버를 만든다",
      "MCP를 지원하는 AI 앱에 연결해 '고객 연체 관련 테이블과 조인 키를 알려줘'를 시험한다"
    ],
    deliverable: "MCP 서버 코드, 연결 설정, 대화 시연 캡처"
  },
  quiz: [
    { q: "MCP가 해결하는 핵심 문제는?", options: ["모델 학습 속도", "AI 앱과 도구·데이터 연결 방식의 표준화", "토큰 비용 절감", "이미지 생성 품질"], answer: 1, explain: "도구를 한 번 만들면 여러 AI 앱에서 쓸 수 있도록 연결을 표준화합니다." },
    { q: "사내 DB 조회 기능을 제공하는 쪽은?", options: ["MCP 클라이언트", "MCP 서버", "토크나이저", "임베딩 모델"], answer: 1, explain: "도구와 데이터를 제공하는 쪽이 MCP 서버입니다." },
    { q: "도구가 가져온 문서 속 숨은 지시로 AI를 조종하는 공격은?", options: ["간접 프롬프트 인젝션", "데이터 포이즈닝", "모델 탈취", "DDoS"], answer: 0, explain: "외부 데이터에 심어진 지시로 AI 행동을 바꾸려는 공격입니다." },
    { q: "MCP 서버의 DB 계정으로 가장 적절한 것은?", options: ["DBA 권한 계정", "필요한 스키마만 조회 가능한 계정", "모든 개발자 공용 계정", "비밀번호 없는 계정"], answer: 1, explain: "최소 권한 원칙에 따라 조회 전용 계정을 씁니다." },
    { q: "MCP 서버가 제공할 수 있는 세 가지 요소가 아닌 것은?", options: ["tools", "resources", "prompts", "gpus"], answer: 3, explain: "MCP 서버는 도구, 리소스, 프롬프트를 제공합니다." }
  ],
  terms: ["mcp", "mcp-server", "mcp-client", "connector", "agent-permissions", "indirect-prompt-injection", "audit-trail", "a2a"]
},
{
  week: 11, stage: 3,
  title: "Text-to-SQL",
  summary: "DW 메타데이터와 테이블 정의서를 활용해 자연어 질문을 SQL로 바꾸고, 실무 수준의 정확도를 확보하는 방법을 다룹니다.",
  goals: [
    "Text-to-SQL이 실무에서 어려운 이유를 설명할 수 있다",
    "스키마 정보·업무 용어·예시 SQL로 정확도를 높일 수 있다",
    "생성된 SQL을 안전하게 검증·실행하는 구조를 설계할 수 있다"
  ],
  sections: [
    { h: "왜 어려운가", body: `[[text-to-sql|Text-to-SQL]]은 "지난달 신규 고객 중 대출 보유 비율은?" 같은 질문을 SQL로 바꿔 실행하는 기술입니다. 데모는 쉽지만 실제 DW에서는 어렵습니다.

- 테이블이 수백~수천 개이고 이름이 \`TB_CUST_M\`, \`ADW_LN_BAL_D\` 처럼 약어
- "신규 고객"의 정의가 부서마다 다름(최초 개설일? 첫 거래일?)
- 선분이력 테이블은 기준일 조건(유효시작일 ≤ 기준일 < 유효종료일)을 빠뜨리면 중복 집계
- 대량 테이블에 조건 없는 쿼리가 나가면 운영에 영향

> 모델의 SQL 실력보다 "우리 데이터를 얼마나 잘 설명해 주느냐"가 정확도를 결정합니다.` },
    { h: "정확도를 높이는 네 가지 재료", body: `1. **스키마 정보**: 질문과 관련된 테이블·컬럼 정의만 골라 넣기. 전체를 넣으면 컨텍스트가 넘치고 헷갈림. 10주차 메타데이터 MCP 서버나 6주차 의미 검색으로 관련 테이블을 먼저 찾음
2. **업무 용어 사전**: "신규 고객 = 최초 계좌 개설일이 해당 월인 고객" 같은 정의. [[semantic-layer|시맨틱 레이어]]의 역할
3. **예시 SQL(퓨샷)**: 검증된 질문-SQL 쌍을 비슷한 것끼리 검색해 함께 제공
4. **작성 규칙**: Oracle 문법, 선분이력 기준일 조건 필수, 날짜 함수 규칙 등

\`\`\`text
<schema>
ADW_CUST_H (고객 이력, 선분이력)
  CUST_NO 고객번호, CUST_GRD 고객등급, VLD_STRT_DT 유효시작일, VLD_END_DT 유효종료일
ADW_ACCT_M (계좌 마스터)
  ACCT_NO, CUST_NO, PRD_CD 상품코드, OPEN_DT 개설일
</schema>
<glossary>신규 고객: 해당 월에 최초 계좌를 개설한 고객</glossary>
<rules>
- Oracle 19c 문법
- 이력 테이블은 :base_dt BETWEEN VLD_STRT_DT AND VLD_END_DT - 1 조건 필수
- SELECT만 작성. 결과는 최대 1000행
</rules>
<question>2026년 9월 신규 고객 수를 고객등급별로</question>
\`\`\`` },
    { h: "검증과 안전한 실행", body: `생성된 SQL을 바로 운영 DB에서 실행하면 안 됩니다.
- **문법·정책 검사**: SELECT 외 문장 차단, 허용된 스키마만, 행 수 제한
- **실행 계획 확인**: 풀스캔·카티션 곱 감지 시 실행 중단
- **조회 전용 계정과 분석용 복제본**: 운영 영향 차단
- **자기 수정**: 오류 메시지를 모델에게 돌려줘 SQL을 고치게 하는 루프(최대 2~3회)
- **결과 설명**: SQL과 함께 "어떤 조건으로 무엇을 셌는지"를 자연어로 보여 줘 사용자가 확인

> 숫자를 보고하는 업무라면, AI가 만든 SQL은 반드시 사람이 확인한 뒤 공식 수치로 쓰세요.` },
    { h: "평가 방법", body: `질문 50~100개와 정답 SQL을 만들어 [[golden-dataset|골든 데이터셋]]으로 씁니다. SQL 문장이 같은지가 아니라 **실행 결과가 같은지**로 채점하는 것이 일반적입니다. 테이블 선택 오류, 조인 오류, 조건 누락, 집계 오류로 실패 유형을 나누면 어떤 재료를 보강해야 할지 보입니다.

[[metadata-management|메타데이터 관리]]와 [[data-catalog|데이터 카탈로그]]가 잘 되어 있는 조직일수록 Text-to-SQL 정확도가 높습니다. 결국 AI 도입은 데이터 관리 수준을 그대로 드러냅니다.` }
  ],
  lab: {
    title: "DW 자연어 질의 시제품",
    steps: [
      "가상 DW 스키마(이력 테이블 포함 5~8개)와 샘플 데이터를 SQLite 또는 Oracle XE에 만든다",
      "업무 용어 사전 10개, 예시 SQL 10개를 작성하고 위 프롬프트 구조로 Text-to-SQL을 구현한다",
      "질문 20개로 실행 결과 기준 정확도를 측정하고, 재료(스키마·용어·예시)를 하나씩 뺐을 때의 정확도 변화를 비교한다"
    ],
    deliverable: "Text-to-SQL 코드, 정확도 비교표, 실패 유형 분석"
  },
  quiz: [
    { q: "실무 Text-to-SQL 정확도를 가장 크게 좌우하는 것은?", options: ["모델의 SQL 문법 지식", "우리 데이터와 업무 용어를 얼마나 잘 설명해 주는가", "온도 설정", "출력 언어"], answer: 1, explain: "스키마·용어·예시 같은 맥락이 정확도를 결정합니다." },
    { q: "선분이력 테이블 조회에서 기준일 조건을 빠뜨리면?", options: ["결과가 빈다", "여러 이력 행이 함께 잡혀 중복 집계된다", "문법 오류가 난다", "성능이 좋아진다"], answer: 1, explain: "유효기간 조건 없이 조회하면 같은 대상의 여러 이력이 함께 집계됩니다." },
    { q: "Text-to-SQL 정답 채점 방식으로 일반적인 것은?", options: ["SQL 문자열이 완전히 같은지", "실행 결과가 같은지", "SQL 길이", "사용된 함수 개수"], answer: 1, explain: "같은 결과를 내는 SQL은 여러 형태가 가능하므로 실행 결과로 비교합니다." },
    { q: "생성된 SQL의 안전장치로 적절하지 않은 것은?", options: ["SELECT만 허용", "행 수 제한", "운영 DB에 DBA 계정으로 즉시 실행", "실행 계획 확인"], answer: 2, explain: "운영 DB에 고권한으로 바로 실행하는 것은 가장 위험한 방식입니다." },
    { q: "'신규 고객' 같은 업무 용어의 정의를 일관되게 관리하는 계층은?", options: ["시맨틱 레이어", "토크나이저", "벡터 인덱스", "KV 캐시"], answer: 0, explain: "시맨틱 레이어는 업무 용어와 데이터의 정의를 연결합니다." }
  ],
  terms: ["text-to-sql", "semantic-layer", "metadata-management", "data-catalog", "golden-dataset", "structured-data"]
},
{
  week: 12, stage: 3,
  title: "고급 RAG와 평가",
  summary: "하이브리드 검색과 재순위화로 RAG 품질을 끌어올리고, 골든 데이터셋과 LLM 심판으로 품질을 숫자로 관리합니다.",
  goals: [
    "하이브리드 검색과 재순위화의 효과를 설명하고 적용할 수 있다",
    "RAG 품질을 검색 지표와 생성 지표로 나눠 측정할 수 있다",
    "가드레일로 위험한 입력·출력을 걸러 낼 수 있다"
  ],
  sections: [
    { h: "하이브리드 검색", body: `의미 검색은 표현이 달라도 찾아내지만, 상품코드·조항번호·오류코드처럼 정확히 일치해야 하는 단어에는 약합니다. 키워드 검색([[bm25|BM25]])은 그 반대입니다.

[[hybrid-search|하이브리드 검색]]은 두 결과를 합칩니다. 가장 흔한 방법은 각 검색의 순위를 점수로 바꿔 더하는 RRF(Reciprocal Rank Fusion)입니다.

\`\`\`python
def rrf(rank_lists, k=60):
    score = {}
    for ranks in rank_lists:                 # [[doc_id, ...], [doc_id, ...]]
        for r, doc in enumerate(ranks):
            score[doc] = score.get(doc, 0) + 1 / (k + r + 1)
    return sorted(score, key=score.get, reverse=True)

final = rrf([bm25_search(q, 20), vector_search(q, 20)])[:10]
\`\`\`` },
    { h: "재순위화와 맥락 보강", body: `[[reranking|재순위화]]는 1차 검색으로 넉넉히(20~50개) 가져온 뒤, 질문과 각 문서를 함께 읽는 정밀 모델(리랭커)로 다시 점수를 매겨 상위 몇 개만 남기는 방식입니다. 비용이 조금 늘지만 정확도 향상 폭이 큽니다.

그 밖의 개선 기법:
- [[contextual-retrieval|맥락 검색]]: 청크마다 "이 청크는 OO 규정 제3장의 일부"처럼 문서 맥락 요약을 붙여 색인
- [[query-rewriting|쿼리 재작성]]: 모호한 질문을 검색하기 좋은 형태로 바꾸거나 여러 개로 나눔
- [[agentic-rag|에이전틱 RAG]]: 에이전트가 검색 결과를 보고 부족하면 다시 검색
- [[graphrag|그래프RAG]]: 문서 간 관계를 그래프로 만들어 여러 문서에 걸친 질문에 대응` },
    { h: "품질을 숫자로 관리하기", body: `"좋아진 것 같다"로는 운영할 수 없습니다. [[eval|평가]]는 두 갈래로 나눕니다.

- **검색 지표**: 정답 문서가 상위 k개에 들어왔는가(Recall@k), 몇 위였는가(MRR)
- **생성 지표**: 답이 맞는가(정확성), 근거에 충실한가([[groundedness|근거성]]), 질문에 답했는가(관련성)

생성 지표는 사람이 채점하면 정확하지만 느립니다. 그래서 채점 기준을 준 다른 LLM이 점수를 매기는 [[llm-as-judge|LLM 심판 평가]]를 함께 씁니다. 처음에는 사람 채점과 LLM 채점을 비교해 심판을 신뢰할 수 있는지 확인하세요.

> 프롬프트나 모델을 바꿀 때마다 같은 [[golden-dataset|골든 데이터셋]]으로 다시 돌리는 것이 [[regression-testing|회귀 테스트]]입니다. 점수가 떨어지면 배포하지 않습니다.` },
    { h: "가드레일", body: `[[guardrail|가드레일]]은 입력과 출력 양쪽에 두는 안전장치입니다.
- **입력**: 주민번호·계좌번호 등 [[pii-masking|개인정보 마스킹]], [[prompt-injection|프롬프트 인젝션]] 탐지, 업무 범위 밖 질문 차단
- **출력**: 근거 없는 수치 차단, 금지 표현(투자 권유 등) 필터, 출처 필수 확인
- **운영**: 차단 사례 로그를 모아 정기 점검` }
  ],
  lab: {
    title: "RAG 2.0: 개선과 측정",
    steps: [
      "7주차 팀 RAG에 BM25를 추가해 하이브리드 검색과 RRF를 구현한다",
      "골든 데이터셋 30문항으로 기존 대비 Recall@5와 정답률을 비교한다",
      "LLM 심판 프롬프트를 작성해 근거성을 채점하고, 사람 채점 10건과 일치율을 확인한다"
    ],
    deliverable: "개선 전후 지표 비교표와 LLM 심판 프롬프트"
  },
  quiz: [
    { q: "오류코드 'ORA-01555'처럼 정확한 문자열 검색에 강한 방식은?", options: ["의미 검색", "BM25 키워드 검색", "이미지 검색", "음성 검색"], answer: 1, explain: "정확히 일치해야 하는 단어는 키워드 검색이 강합니다." },
    { q: "재순위화(reranking)의 방식으로 맞는 것은?", options: ["검색 없이 바로 생성", "넉넉히 검색한 후 정밀 모델로 다시 순위를 매긴다", "문서를 삭제한다", "임베딩 차원을 줄인다"], answer: 1, explain: "1차 후보를 정밀 모델로 재평가해 상위만 남깁니다." },
    { q: "정답 문서가 상위 k개 안에 포함된 비율을 나타내는 지표는?", options: ["Recall@k", "TTFT", "F1", "처리량"], answer: 0, explain: "Recall@k는 검색 단계의 핵심 지표입니다." },
    { q: "LLM 심판 평가를 처음 도입할 때 해야 할 일은?", options: ["바로 사람 평가를 없앤다", "사람 채점과 비교해 신뢰도를 확인한다", "온도를 최대로 한다", "심판 모델을 매번 바꾼다"], answer: 1, explain: "심판의 채점이 사람과 얼마나 일치하는지 먼저 확인해야 합니다." },
    { q: "프롬프트 변경 후 같은 데이터셋으로 성능 저하를 확인하는 것은?", options: ["레드티밍", "회귀 테스트", "양자화", "증류"], answer: 1, explain: "변경 전후를 같은 기준으로 비교하는 회귀 테스트입니다." }
  ],
  terms: ["hybrid-search", "bm25", "reranking", "contextual-retrieval", "agentic-rag", "eval", "llm-as-judge", "guardrail"]
}
);
