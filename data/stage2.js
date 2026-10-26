/* 2단계 · 활용: 개발자로서 AI 쓰기 (5–8주) */
window.WEEKS = window.WEEKS || [];
window.WEEKS.push(
{
  week: 5, stage: 2,
  title: "LLM API 실습",
  summary: "코드에서 LLM을 호출하고, 시스템 프롬프트와 구조화된 출력으로 결과를 프로그램이 쓸 수 있게 만듭니다.",
  goals: [
    "Python으로 LLM API를 호출하고 응답을 처리할 수 있다",
    "시스템 프롬프트와 사용자 프롬프트의 역할을 구분해 쓸 수 있다",
    "JSON 등 구조화된 출력을 받아 검증할 수 있다"
  ],
  sections: [
    { h: "왜 API인가", body: `채팅 화면은 사람이 쓰기 좋지만, 업무 시스템에 붙이려면 [[api|API]]가 필요합니다. API로 부르면 수천 건의 문서를 일괄 처리하고, 결과를 DB에 넣고, 배치 스케줄에 태울 수 있습니다.

이 실습에서는 **OpenAI 호환 API** 형식을 씁니다. 여러 상용 서비스와 13주차에 다룰 vLLM·Ollama 같은 사내 서빙 엔진이 같은 형식을 지원하기 때문에, 지금 짠 코드를 나중에 폐쇄망 서버로 옮길 때 주소만 바꾸면 됩니다.` },
    { h: "첫 호출", body: `\`\`\`python
# pip install openai
import os
from openai import OpenAI

client = OpenAI(
    base_url=os.environ["LLM_BASE_URL"],   # 상용 API 또는 사내 서버 주소
    api_key=os.environ["LLM_API_KEY"],
)

resp = client.chat.completions.create(
    model=os.environ["LLM_MODEL"],
    messages=[
        {"role": "system", "content": "당신은 금융 DW 운영 담당자다. 한국어로 간결하게 답한다."},
        {"role": "user", "content": "ORA-01555 오류의 원인과 조치를 3줄로 알려줘."},
    ],
    temperature=0.2,
)
print(resp.choices[0].message.content)
print(resp.usage)   # 입력·출력 토큰 수 → 비용 계산에 사용
\`\`\`

- [[system-prompt|시스템 프롬프트]]: 모델의 역할과 규칙. 대화 내내 유지됨
- [[user-prompt|사용자 프롬프트]]: 이번 요청의 내용
- [[temperature|온도]]: 낮을수록 일관된 답, 높을수록 다양한 답. 업무 자동화는 0~0.3 권장

> API 키는 코드에 직접 쓰지 말고 환경변수나 비밀 저장소로 관리하세요.` },
    { h: "구조화된 출력", body: `프로그램이 결과를 받아 쓰려면 자유로운 문장보다 정해진 구조가 필요합니다. [[structured-output|구조화된 출력]]은 모델이 지정한 JSON 스키마에 맞춰 답하게 하는 기능입니다.

\`\`\`python
import json

schema = {
  "type": "object",
  "properties": {
    "category": {"type": "string", "enum": ["장애", "문의", "개선요청"]},
    "urgency":  {"type": "integer", "minimum": 1, "maximum": 5},
    "summary":  {"type": "string"}
  },
  "required": ["category", "urgency", "summary"]
}

resp = client.chat.completions.create(
    model=os.environ["LLM_MODEL"],
    messages=[{"role": "user", "content": f"다음 요청을 분류하라:\\n<ticket>{ticket_text}</ticket>"}],
    response_format={"type": "json_schema",
                     "json_schema": {"name": "ticket", "schema": schema}},
)
data = json.loads(resp.choices[0].message.content)
\`\`\`

지원하지 않는 모델이라면 프롬프트에 스키마를 적고, 받은 결과를 \`json.loads\`와 스키마 검증으로 확인한 뒤 실패하면 다시 요청하는 방식([[retry-fallback|재시도와 폴백]])을 씁니다.` },
    { h: "스트리밍과 운영 고려사항", body: `[[streaming-response|스트리밍]]은 답변을 다 만들 때까지 기다리지 않고 토큰이 나오는 대로 받는 방식입니다. 사람이 보는 화면에서는 체감 속도가 크게 좋아집니다. 첫 토큰이 나오기까지의 시간을 [[ttft|첫 토큰 지연(TTFT)]]이라고 합니다.

운영에서 챙길 것:
- [[rate-limit|레이트 리밋]]: 분당 요청·토큰 한도. 일괄 처리 시 대기 후 재시도 필요
- 타임아웃과 재시도: 네트워크 오류에 대비
- 로그: 입력·출력·토큰 수·지연 시간 기록. 단, 고객정보는 [[pii-masking|마스킹]] 후 저장` }
  ],
  lab: {
    title: "업무 요청 자동 분류기",
    steps: [
      "가상의 업무 요청(장애·문의·개선요청) 20건을 만든다",
      "위 코드로 각 요청을 JSON으로 분류하고 결과를 CSV로 저장한다",
      "사람이 붙인 정답과 비교해 정확도를 계산하고, 틀린 사례를 보고 프롬프트를 개선한다"
    ],
    deliverable: "분류 스크립트, 결과 CSV, 정확도와 개선 전후 비교"
  },
  quiz: [
    { q: "대화 내내 모델의 역할과 규칙을 유지시키는 메시지는?", options: ["사용자 메시지", "시스템 메시지", "어시스턴트 메시지", "도구 메시지"], answer: 1, explain: "시스템 프롬프트가 모델의 역할과 규칙을 정합니다." },
    { q: "업무 자동화에서 일관된 답을 원할 때 온도 설정은?", options: ["1.5 이상", "1.0 고정", "0~0.3 정도로 낮게", "온도와 무관하다"], answer: 2, explain: "온도가 낮을수록 출력이 일관됩니다." },
    { q: "OpenAI 호환 API 형식으로 코드를 짜는 장점은?", options: ["무조건 무료다", "사내 서빙 엔진으로 옮길 때 주소만 바꾸면 된다", "토큰 수가 줄어든다", "환각이 사라진다"], answer: 1, explain: "vLLM·Ollama 등도 같은 형식을 지원해 이식이 쉽습니다." },
    { q: "응답 JSON이 스키마에 맞지 않을 때 적절한 처리는?", options: ["그대로 DB에 넣는다", "검증 후 실패하면 재요청한다", "온도를 2로 올린다", "프로그램을 종료한다"], answer: 1, explain: "검증과 재시도는 LLM 출력을 시스템에 넣을 때의 기본입니다." },
    { q: "첫 토큰이 나오기까지 걸리는 시간을 뜻하는 용어는?", options: ["처리량", "TTFT", "레이트 리밋", "컨텍스트 윈도우"], answer: 1, explain: "Time To First Token, 즉 첫 토큰 지연입니다." }
  ],
  terms: ["api", "system-prompt", "temperature", "structured-output", "streaming-response", "ttft", "rate-limit", "pii-masking"]
},
{
  week: 6, stage: 2,
  title: "임베딩과 벡터DB",
  summary: "문장을 벡터로 바꿔 의미가 비슷한 문서를 찾는 원리를 익히고, 벡터DB와 청킹의 기초를 다룹니다.",
  goals: [
    "임베딩과 코사인 유사도로 의미 검색이 가능한 이유를 설명할 수 있다",
    "벡터DB의 역할과 주요 선택지를 비교할 수 있다",
    "문서를 적절한 크기로 나누는 청킹 전략을 세울 수 있다"
  ],
  sections: [
    { h: "키워드 검색의 한계", body: `"대출 금리 인하 요구권"을 찾는데 문서에는 "금리인하요구권 행사 절차"라고 적혀 있다면, 단순 키워드 검색은 놓칠 수 있습니다. 사용자가 "이자 좀 깎을 수 있나요?"라고 물으면 더 어렵습니다.

[[semantic-search|의미 검색]]은 단어가 아니라 뜻으로 찾습니다. 질문과 문서를 모두 [[embedding|임베딩]] 벡터로 바꾸고, 벡터끼리 가까운 것을 찾는 방식입니다.` },
    { h: "임베딩과 유사도", body: `[[embedding-model|임베딩 모델]]은 문장 하나를 수백~수천 차원의 숫자 배열로 바꿉니다. 두 벡터가 얼마나 같은 방향을 가리키는지를 [[cosine-similarity|코사인 유사도]]로 계산하며, 1에 가까울수록 의미가 비슷합니다.

\`\`\`python
import numpy as np

def embed(texts):
    r = client.embeddings.create(model=os.environ["EMBED_MODEL"], input=texts)
    return np.array([d.embedding for d in r.data])

docs = ["금리인하요구권 행사 절차", "체크카드 해외 결제 한도", "예금자보호 한도 안내"]
D = embed(docs)
q = embed(["이자 좀 깎을 수 있나요?"])[0]

sims = D @ q / (np.linalg.norm(D, axis=1) * np.linalg.norm(q))
print(sorted(zip(sims, docs), reverse=True)[0])   # 금리인하요구권이 1위
\`\`\`

> 한국어 문서라면 한국어 성능이 검증된 임베딩 모델을 고르는 것이 검색 품질에 가장 큰 영향을 줍니다.` },
    { h: "벡터DB", body: `문서가 수십만 건이 되면 질문마다 모든 벡터와 비교할 수 없습니다. [[vector-db|벡터 데이터베이스]]는 [[ann|근사 최근접 이웃 검색]] 알고리즘(대표적으로 [[hnsw|HNSW]])으로 빠르게 비슷한 벡터를 찾아 줍니다.

- **기존 DB 확장**: PostgreSQL의 pgvector, Oracle Database 23ai의 AI Vector Search 등. 기존 운영 체계를 그대로 쓸 수 있음
- **전용 벡터DB**: Qdrant, Milvus, Weaviate, Chroma 등. 대규모·고성능에 유리
- **[[metadata-filtering|메타데이터 필터링]]**: "2025년 이후 개정된 여신 규정 중에서만" 처럼 조건을 함께 거는 기능이 실무에서 필수

> Oracle 기반 DW 환경이라면 별도 벡터DB를 들이기 전에 사용 중인 DB 버전의 벡터 기능부터 확인해 보세요.` },
    { h: "청킹: 얼마나 잘라 넣을까", body: `긴 문서를 통째로 하나의 벡터로 만들면 세부 내용이 흐려집니다. 그래서 문서를 적당한 크기로 나누는데, 이것이 [[chunking|청킹]]입니다.

- 너무 작으면: 맥락이 끊겨 "제3항에 따라"가 무엇인지 모름
- 너무 크면: 관련 없는 내용이 섞여 검색 정확도가 떨어짐
- 시작점: 300~800토큰, 앞뒤 10~20% [[chunk-overlap|오버랩]]
- 더 좋은 방법: 규정은 조·항 단위, 매뉴얼은 제목(섹션) 단위처럼 문서 구조를 따라 자르기

각 청크에는 출처 문서명, 조항 번호, 개정일 같은 메타데이터를 꼭 붙이세요. 답변에 출처를 다는 데 쓰입니다.` }
  ],
  lab: {
    title: "사내 문서 의미 검색 만들기",
    steps: [
      "공개 가능한 문서 3~5개(약관, 업무 매뉴얼 등)를 준비하고 섹션 단위로 청킹한다",
      "임베딩을 만들어 Chroma 또는 pgvector에 저장한다",
      "질문 10개로 키워드 검색과 의미 검색의 상위 3건을 비교한다"
    ],
    deliverable: "검색 스크립트와 키워드 vs 의미 검색 비교표"
  },
  quiz: [
    { q: "두 임베딩 벡터의 의미 유사도를 계산할 때 주로 쓰는 것은?", options: ["유클리드 거리만", "코사인 유사도", "해시 값 비교", "문자열 길이"], answer: 1, explain: "방향의 유사성을 보는 코사인 유사도를 가장 많이 씁니다." },
    { q: "HNSW는 무엇을 위한 알고리즘인가?", options: ["토큰화", "근사 최근접 이웃 검색", "파인튜닝", "암호화"], answer: 1, explain: "대규모 벡터에서 빠르게 가까운 벡터를 찾는 ANN 알고리즘입니다." },
    { q: "청크를 너무 작게 자를 때의 문제는?", options: ["비용이 무조건 늘어난다", "맥락이 끊겨 의미 파악이 어려워진다", "벡터 차원이 줄어든다", "검색이 불가능해진다"], answer: 1, explain: "앞뒤 맥락이 사라져 내용을 이해하기 어려워집니다." },
    { q: "'2025년 이후 개정된 규정 중에서만 검색'을 가능하게 하는 기능은?", options: ["메타데이터 필터링", "온도 조절", "스트리밍", "양자화"], answer: 0, explain: "청크에 붙인 메타데이터로 조건 필터링을 합니다." },
    { q: "규정 문서에 가장 적합한 청킹 기준은?", options: ["무조건 100자 단위", "조·항 등 문서 구조 단위", "문서 전체를 하나로", "무작위 분할"], answer: 1, explain: "문서 구조를 따라 자르면 의미 단위가 보존됩니다." }
  ],
  terms: ["embedding-model", "cosine-similarity", "semantic-search", "vector-db", "hnsw", "chunking", "chunk-overlap", "metadata-filtering"]
},
{
  week: 7, stage: 2,
  title: "RAG 기초",
  summary: "검색과 생성을 결합해 사내 문서를 근거로 답하는 RAG 파이프라인을 처음부터 끝까지 구현합니다.",
  goals: [
    "RAG의 전체 흐름(색인 → 검색 → 프롬프트 조립 → 생성)을 설명할 수 있다",
    "검색 결과를 근거로 출처를 달아 답하게 만들 수 있다",
    "RAG가 실패하는 대표 지점을 진단할 수 있다"
  ],
  sections: [
    { h: "RAG란", body: `[[rag|검색 증강 생성(RAG)]]은 질문이 들어오면 먼저 관련 문서를 찾아서([[retrieval|검색]]), 그 문서를 프롬프트에 넣고, 그 안에서만 답하게 하는 방식입니다. 모델이 모르는 사내 지식을 답하게 하면서 [[hallucination|환각]]을 줄이는 가장 실용적인 방법입니다.

모델을 다시 학습시키는 [[fine-tuning|파인튜닝]]과 달리, 문서만 갱신하면 최신 내용이 바로 반영되고 답변마다 출처를 보여 줄 수 있습니다. 금융권 업무에 잘 맞는 이유입니다.` },
    { h: "파이프라인 네 단계", body: `1. **색인(오프라인)**: 문서 파싱 → 청킹 → 임베딩 → 벡터DB 저장 (6주차)
2. **검색**: 질문을 임베딩해 상위 k개 청크를 찾음 ([[top-k-retrieval|top-k]])
3. **프롬프트 조립**: 찾은 청크를 출처와 함께 프롬프트에 넣음
4. **생성**: 모델이 근거 안에서 답하고 출처를 표시

\`\`\`python
def answer(question, k=4):
    hits = vector_search(question, k=k)          # [(text, meta), ...]
    context = "\\n\\n".join(
        f'<doc id="{i+1}" src="{m["file"]} {m["section"]}">\\n{t}\\n</doc>'
        for i, (t, m) in enumerate(hits))
    prompt = f"""아래 문서만 근거로 질문에 답하라.
문서에 없는 내용이면 "제공된 문서에서 찾을 수 없습니다"라고 답하라.
문장 끝에 근거 문서 번호를 [1]처럼 표시하라.

{context}

<question>{question}</question>"""
    r = client.chat.completions.create(model=os.environ["LLM_MODEL"],
        messages=[{"role": "user", "content": prompt}], temperature=0)
    return r.choices[0].message.content, hits
\`\`\`` },
    { h: "출처 인용과 근거성", body: `RAG의 가치는 "왜 그렇게 답했는지"를 보여 줄 수 있다는 데 있습니다. 답변에 근거 문서 번호를 달고, 화면에서 원문을 바로 열어 볼 수 있게 하세요. 이렇게 근거에 기반해 답하는 성질을 [[groundedness|근거성]]이라고 하며, 12주차 평가의 핵심 지표가 됩니다.

> "문서에서 찾을 수 없습니다"라고 답하는 것은 실패가 아니라 정상 동작입니다. 모르는 것을 지어내는 것보다 훨씬 안전합니다.` },
    { h: "RAG가 틀리는 지점", body: `답이 틀렸을 때는 어느 단계 문제인지부터 나눠 보세요.
- **검색 실패**: 정답이 담긴 청크가 상위 k개에 없음 → 청킹·임베딩 모델·검색 방식 개선 (12주차 [[hybrid-search|하이브리드 검색]], [[reranking|재순위화]])
- **파싱 실패**: PDF의 표가 깨져서 들어감 → [[document-parsing|문서 파싱]] 개선
- **생성 실패**: 근거는 있는데 잘못 해석함 → 프롬프트 개선, 더 좋은 모델
- **질문 문제**: 질문이 모호함 → [[query-rewriting|쿼리 재작성]]

검색 결과(hits)를 항상 로그로 남겨 두면 원인을 금방 찾을 수 있습니다.` }
  ],
  lab: {
    title: "사내 문서 Q&A 미니 RAG (2단계 팀 산출물)",
    steps: [
      "6주차에 만든 검색에 위 answer() 함수를 붙여 RAG를 완성한다",
      "업무 매뉴얼이나 ETL 설계서(공개 가능한 범위)를 대상으로 질문 20개와 정답을 만든다",
      "답변 정확도와 출처 정확도를 따로 채점하고, 틀린 사례를 검색·파싱·생성 실패로 분류한다"
    ],
    deliverable: "동작하는 RAG 데모, 평가표, 실패 원인 분석 (팀 발표)"
  },
  quiz: [
    { q: "RAG의 올바른 처리 순서는?", options: ["생성 → 검색 → 색인", "색인 → 검색 → 프롬프트 조립 → 생성", "검색 → 파인튜닝 → 생성", "생성 → 색인 → 검색"], answer: 1, explain: "문서를 미리 색인하고, 질문 시 검색한 결과로 프롬프트를 만들어 생성합니다." },
    { q: "파인튜닝 대비 RAG의 장점이 아닌 것은?", options: ["문서 갱신이 바로 반영된다", "출처를 보여 줄 수 있다", "모델의 말투와 형식을 근본적으로 바꾼다", "재학습 비용이 들지 않는다"], answer: 2, explain: "말투·형식 자체를 바꾸는 것은 파인튜닝이 더 잘하는 일입니다." },
    { q: "정답 문서가 상위 k개 검색 결과에 없다면 어떤 실패인가?", options: ["생성 실패", "검색 실패", "토큰 초과", "온도 문제"], answer: 1, explain: "근거 자체를 못 찾은 검색 단계의 실패입니다." },
    { q: "문서에 없는 질문을 받았을 때 바람직한 RAG 동작은?", options: ["일반 지식으로 그럴듯하게 답한다", "찾을 수 없다고 답한다", "랜덤 문서를 인용한다", "오류를 낸다"], answer: 1, explain: "근거가 없으면 없다고 답하는 것이 안전한 동작입니다." },
    { q: "답변이 제공된 근거에 얼마나 기반하는지를 나타내는 지표는?", options: ["처리량", "근거성(groundedness)", "TTFT", "파라미터 수"], answer: 1, explain: "근거성은 답변이 근거 문서에 충실한 정도입니다." }
  ],
  terms: ["rag", "retrieval", "top-k-retrieval", "grounding", "groundedness", "document-parsing", "query-rewriting", "knowledge-base"]
},
{
  week: 8, stage: 2,
  title: "AI 코딩 도구",
  summary: "AI 코딩 어시스턴트와 에이전트형 코딩 도구로 코드 작성, 리뷰, 테스트 생성을 실무에 적용합니다.",
  goals: [
    "자동완성형·대화형·에이전트형 코딩 도구의 차이를 이해한다",
    "AI에게 코드를 맡길 때 맥락과 검증 기준을 주는 법을 익힌다",
    "AI가 만든 코드를 안전하게 리뷰하고 테스트하는 습관을 만든다"
  ],
  sections: [
    { h: "세 가지 형태", body: `- **자동완성형**: 편집기에서 다음 줄을 제안. 손이 빨라지는 수준
- **대화형**: 채팅으로 코드 설명·수정 요청. 함수 단위 작업에 적합
- **[[coding-agent|코딩 에이전트]]**: 저장소 전체를 읽고, 파일을 수정하고, 테스트를 돌리고, 결과를 보고 다시 고치는 것까지 스스로 진행. 여러 파일에 걸친 작업을 맡길 수 있음

에이전트형 도구가 빠르게 발전하면서 개발자의 역할이 "직접 작성"에서 "지시하고 검토하는 사람"으로 옮겨 가고 있습니다.` },
    { h: "잘 맡기는 법", body: `코딩 도구도 결국 프롬프트입니다. 3주차 원칙이 그대로 적용됩니다.
- **맥락**: 테이블 정의, 기존 코드 규칙, DB 버전(Oracle 19c 등)을 알려 주기
- **완료 기준**: "기존 테스트가 모두 통과하고, 새 테스트 3개 추가"처럼 검증 가능한 조건
- **범위**: 한 번에 하나의 기능. 큰 작업은 계획을 먼저 세우게 하고 단계별로 진행
- **규칙 파일**: 프로젝트 규칙을 [[agents-md|에이전트 지시 파일]](AGENTS.md, CLAUDE.md 등)에 적어 두면 매번 설명하지 않아도 됨

\`\`\`text
목표: STG_CUST 적재 프로시저에 선분이력(유효시작일/종료일) 처리 추가
맥락: Oracle 19c, PL/SQL. 기존 적재 규칙은 docs/etl_rules.md 참고
제약: 기존 컬럼명 변경 금지, MERGE 문 사용
완료 기준: 변경 전후 샘플 데이터로 이력 3건이 맞게 생기는지 검증 SQL 포함
먼저 계획만 보여 주고, 내가 승인하면 작성하라.
\`\`\`` },
    { h: "바이브 코딩과 그 경계", body: `[[vibe-coding|바이브 코딩]]은 코드를 자세히 읽지 않고 AI에 말로 지시해 결과물을 만드는 방식입니다. 개인 도구나 시제품을 빠르게 만들 때는 매우 강력합니다.

하지만 금융 시스템의 운영 코드에서는 위험합니다. AI가 만든 코드도 사람이 만든 코드와 같은 기준으로 리뷰해야 합니다.
- SQL: 실행 계획, 인덱스 사용, 대량 데이터에서의 성능 확인
- 보안: 하드코딩된 비밀번호, SQL 인젝션, 과도한 권한
- 정확성: 경계값과 NULL 처리, 날짜·시간대
- 라이선스: 외부 코드를 그대로 가져오지 않았는지` },
    { h: "테스트 생성과 리뷰 보조", body: `AI가 특히 잘하는 일은 테스트와 리뷰입니다. "이 함수의 경계값 테스트를 만들어 줘", "이 PL/SQL에서 성능 문제가 될 부분을 찾아 줘"처럼 쓰면 사람이 놓치는 부분을 보완해 줍니다.

> 폐쇄망 환경이라면 외부 코딩 도구에 사내 코드를 넣으면 안 되는 경우가 많습니다. 13주차의 사내 LLM 서빙과 연결하면 같은 경험을 내부에서 구현할 수 있습니다.` }
  ],
  lab: {
    title: "AI와 짝 프로그래밍",
    steps: [
      "공개 가능한 작은 과제를 고른다(예: CSV를 읽어 월별 집계하는 Python, 간단한 PL/SQL 프로시저)",
      "에이전트형 또는 대화형 도구에 계획 → 구현 → 테스트 순서로 지시해 완성한다",
      "결과 코드를 위 리뷰 체크리스트로 점검하고, AI가 놓친 문제를 기록한다"
    ],
    deliverable: "완성 코드, 사용한 프롬프트 기록, 리뷰에서 발견한 문제 목록"
  },
  quiz: [
    { q: "저장소를 읽고 여러 파일을 수정하며 테스트까지 돌리는 도구 유형은?", options: ["자동완성형", "코딩 에이전트", "검색 엔진", "임베딩 모델"], answer: 1, explain: "스스로 계획·수정·검증을 반복하는 것이 코딩 에이전트입니다." },
    { q: "AI에게 코드 작업을 맡길 때 가장 중요한 요소는?", options: ["최대한 짧은 지시", "검증 가능한 완료 기준", "높은 온도 설정", "영어로만 지시"], answer: 1, explain: "완료 기준이 있어야 AI도 스스로 검증하고 사람도 확인할 수 있습니다." },
    { q: "프로젝트 규칙을 매번 설명하지 않도록 저장소에 두는 파일은?", options: ["README만", "에이전트 지시 파일(AGENTS.md 등)", ".gitignore", "requirements.txt"], answer: 1, explain: "에이전트 지시 파일에 규칙을 적으면 도구가 자동으로 참고합니다." },
    { q: "금융 운영 코드에서 바이브 코딩이 위험한 이유는?", options: ["속도가 느려서", "검토 없이 성능·보안 문제가 들어갈 수 있어서", "AI가 SQL을 못 써서", "비용이 너무 비싸서"], answer: 1, explain: "읽지 않은 코드는 검증되지 않은 코드입니다." },
    { q: "AI가 특히 효과적으로 보조하는 작업은?", options: ["운영 배포 승인", "테스트 생성과 코드 리뷰", "보안 정책 결정", "예산 승인"], answer: 1, explain: "테스트 케이스 생성과 리뷰 관점 제시는 AI가 잘하는 영역입니다." }
  ],
  terms: ["coding-agent", "vibe-coding", "agents-md", "copilot", "regression-testing"]
}
);
