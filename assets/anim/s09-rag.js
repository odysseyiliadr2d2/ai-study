// 세션 09 · RAG: 질문 하나가 근거 있는 답이 되기까지
AXAnim.register("s09-rag", (root, U) => {
  const svg = `<svg viewBox="0 0 640 400" role="img" aria-label="RAG 처리 흐름 애니메이션">
    <path data-k="e1" class="a-edge" d="M150 70 H200"/><path data-k="e2" class="a-edge" d="M330 70 H370"/>
    <path data-k="e3" class="a-edge" d="M500 190 V230"/><path data-k="e4" class="a-edge" d="M370 310 H330"/><path data-k="e5" class="a-edge" d="M200 310 H150"/>
    <g data-k="q" class="a-box"><rect x="20" y="30" width="130" height="80" rx="10"/><text class="a-lbl" x="34" y="58">질문</text><text class="a-sub" x="34" y="78">야간 배치 재실행</text><text class="a-sub" x="34" y="94">승인 절차가 뭐야?</text></g>
    <g data-k="emb" class="a-box"><rect x="200" y="30" width="130" height="80" rx="10"/><text class="a-lbl" x="214" y="58">임베딩</text><text class="a-sub" x="214" y="78">문장 → 숫자 벡터</text>
      <text class="a-sub a-mono" x="214" y="96" data-at="1">[0.12, −0.48, …]</text></g>
    <g data-k="db" class="a-box"><rect x="370" y="20" width="250" height="170" rx="10"/><text class="a-lbl" x="384" y="46">벡터DB · 사내 문서 조각</text>
      <g class="a-chip" data-k="c1" transform="translate(384 60)"><rect width="92" height="30" rx="6"/><text x="10" y="19">운영규정 §4.2</text></g>
      <g class="a-chip" data-k="c2" transform="translate(514 60)"><rect width="92" height="30" rx="6"/><text x="10" y="19">휴가 신청 안내</text></g>
      <g class="a-chip" data-k="c3" transform="translate(384 98)"><rect width="92" height="30" rx="6"/><text x="10" y="19">배치 장애 매뉴얼</text></g>
      <g class="a-chip" data-k="c4" transform="translate(514 98)"><rect width="92" height="30" rx="6"/><text x="10" y="19">보안 서약서</text></g>
      <g class="a-chip" data-k="c5" transform="translate(384 136)"><rect width="92" height="30" rx="6"/><text x="10" y="19">변경관리 지침</text></g>
      <g class="a-chip" data-k="c6" transform="translate(514 136)"><rect width="92" height="30" rx="6"/><text x="10" y="19">구내식당 메뉴</text></g>
      <g data-at="2"><text class="a-sub a-mono" x="482" y="80">.91</text><text class="a-sub a-mono" x="482" y="118">.87</text><text class="a-sub a-mono" x="482" y="156">.82</text>
        <text class="a-sub a-mono" x="610" y="80">.21</text><text class="a-sub a-mono" x="610" y="118">.18</text><text class="a-sub a-mono" x="610" y="156">.05</text></g></g>
    <g data-k="p" class="a-box"><rect x="370" y="230" width="250" height="150" rx="10"/><text class="a-lbl" x="384" y="256">프롬프트 조립</text>
      <text class="a-sub" x="384" y="278" data-at="3">지시: 근거에 있는 내용만 답하고 출처를 다세요</text>
      <g data-at="3" class="a-chip a-hl" transform="translate(384 290)"><rect width="74" height="26" rx="6"/><text x="8" y="17">[1] §4.2</text></g>
      <g data-at="3" class="a-chip a-hl" transform="translate(464 290)"><rect width="74" height="26" rx="6"/><text x="8" y="17">[2] 매뉴얼</text></g>
      <g data-at="3" class="a-chip a-hl" transform="translate(544 290)"><rect width="66" height="26" rx="6"/><text x="8" y="17">[3] 지침</text></g>
      <text class="a-sub" x="384" y="346" data-at="3">질문: 야간 배치 재실행 승인 절차가 뭐야?</text></g>
    <g data-k="llm" class="a-box"><rect x="200" y="270" width="130" height="80" rx="10"/><text class="a-lbl" x="214" y="298">LLM</text><text class="a-sub" x="214" y="318">근거를 읽고</text><text class="a-sub" x="214" y="334">답을 씁니다</text></g>
    <g data-k="a" class="a-box"><rect x="20" y="230" width="130" height="150" rx="10"/><text class="a-lbl" x="34" y="256">답변</text>
      <g data-at="5"><text class="a-sub" x="34" y="280">① 장애 원인 확인</text><text class="a-sub" x="34" y="298">② 운영팀장 승인 [1]</text><text class="a-sub" x="34" y="316">③ 변경관리 등록 [3]</text><text class="a-sub" x="34" y="334">④ 재실행 후 건수</text><text class="a-sub" x="34" y="350">   대사 [2]</text></g></g>
    <circle class="a-pkt" r="6" cx="-20" cy="-20"/></svg>`;
  U.scene(root, svg, [
    { t: "질문이 들어옵니다", d: "직원이 메신저에 '야간 배치 재실행 승인 절차가 뭐야?'라고 묻습니다. LLM 은 우리 회사 규정을 모르므로 바로 답하게 하지 않습니다." },
    { t: "질문을 벡터로 바꿉니다", d: "임베딩 모델이 문장을 수백 개의 숫자로 바꿉니다. 뜻이 비슷한 문장은 비슷한 숫자 묶음이 됩니다." },
    { t: "가까운 문서 조각을 찾습니다", d: "벡터DB 가 질문 벡터와 각 조각의 유사도를 계산하고, 점수가 높은 3개(운영규정, 장애 매뉴얼, 변경관리 지침)만 고릅니다." },
    { t: "프롬프트에 근거를 붙입니다", d: "고른 조각에 번호를 달아 지시문, 질문과 함께 한 프롬프트로 묶습니다. '근거에 있는 내용만, 출처를 달아서'가 핵심 지시입니다." },
    { t: "LLM 이 근거를 읽고 씁니다", d: "모델은 프롬프트 안의 근거를 읽고 답을 만듭니다. 근거에 없는 내용은 '찾지 못했다'고 답하게 합니다." },
    { t: "출처가 달린 답을 돌려줍니다", d: "답의 문장마다 [1], [3] 같은 출처 번호가 붙어 사람이 원문을 바로 확인할 수 있습니다." }
  ], (n, s) => {
    ["q", "emb", "db", "p", "llm", "a"].forEach((b, i) => U.cls(s, [b], "a-hl", i === n));
    ["e1", "e2", "e3", "e4", "e5"].forEach((e, i) => U.cls(s, [e], "a-hl", i < n));
    U.cls(s, ["c1", "c3", "c5"], "a-hl", n >= 2);
    U.cls(s, ["c2", "c4", "c6"], "a-fade", n >= 2);
    U.cls(s, ["llm"], "a-pulse", n === 4);
    U.travel(s, n > 0 ? ["e" + n] : []);
  }, { foot: "문서 이름과 유사도 점수는 예시입니다." });
});
