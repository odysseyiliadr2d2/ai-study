// 세션 17 · 에이전트 루프: 도구를 부르고, 결과를 보고, 다시 판단합니다
AXAnim.register("s17-agent", (root, U) => {
  const svg = `<svg viewBox="0 0 640 400" role="img" aria-label="에이전트 도구 호출 루프 애니메이션">
    <path data-k="e1" class="a-edge" d="M140 70 H230"/><path data-k="e2" class="a-edge" d="M370 70 H440"/><path data-k="e2b" class="a-edge" d="M440 100 H370"/>
    <path data-k="e3" class="a-edge" d="M520 120 V170"/><path data-k="e3b" class="a-edge" d="M540 170 V120"/><path data-k="e5" class="a-edge" d="M230 100 H140 V290"/>
    <g data-k="u" class="a-box"><rect x="20" y="30" width="120" height="90" rx="10"/><text class="a-lbl" x="34" y="58">사용자</text><text class="a-sub" x="34" y="80">어젯밤 배치</text><text class="a-sub" x="34" y="96">왜 실패했어?</text></g>
    <g data-k="m" class="a-box"><rect x="230" y="20" width="140" height="110" rx="10"/><text class="a-lbl" x="246" y="48">모델 (Claude)</text><text class="a-sub" x="246" y="70">다음에 할 일을 정함</text>
      <text class="a-sub a-mono" data-k="turn" x="246" y="112">반복 0 / 8</text></g>
    <g data-k="x" class="a-box"><rect x="440" y="30" width="180" height="90" rx="10"/><text class="a-lbl" x="456" y="58">도구 실행기 (내 코드)</text><text class="a-sub a-mono" data-k="call" x="456" y="82">대기</text><text class="a-sub" data-k="res" x="456" y="102"></text></g>
    <g data-k="tools" class="a-box"><rect x="440" y="170" width="180" height="130" rx="10"/><text class="a-lbl" x="456" y="196">도구</text>
      <g class="a-chip" data-k="t1" transform="translate(456 208)"><rect width="148" height="24" rx="6"/><text x="8" y="16">get_batch_log · 읽기</text></g>
      <g class="a-chip" data-k="t2" transform="translate(456 238)"><rect width="148" height="24" rx="6"/><text x="8" y="16">get_row_count · 읽기</text></g>
      <g class="a-chip" data-k="t3" transform="translate(456 268)"><rect width="148" height="24" rx="6"/><text x="8" y="16">request_rerun · 쓰기</text></g></g>
    <g data-k="g" class="a-box a-gate" data-at="4"><rect x="230" y="180" width="170" height="84" rx="10"/><text class="a-lbl" x="246" y="208">사람 승인</text><text class="a-sub" x="246" y="228">쓰기 도구는 실행 전</text><text class="a-sub" x="246" y="246">승인 대기 · 거절 가능</text></g>
    <g data-k="a" class="a-box"><rect x="20" y="290" width="140" height="96" rx="10"/><text class="a-lbl" x="34" y="314">최종 답변</text>
      <g data-at="5"><text class="a-sub" x="34" y="334">① 현상 ② 원인</text><text class="a-sub" x="34" y="352">③ 조치안</text><text class="a-sub" x="34" y="370">④ 재실행 승인 필요</text></g></g>
    <circle class="a-pkt" r="6" cx="-20" cy="-20"/></svg>`;
  const plan = [
    { box: ["u", "m"], path: ["e1"], turn: 1, call: "대기", res: "" },
    { box: ["m", "x"], tool: "t1", path: ["e2", "e3"], turn: 1, call: "tool_use: get_batch_log", res: "" },
    { box: ["x", "m"], tool: "t1", path: ["e3b", "e2b"], turn: 2, call: "tool_result", res: "ORA-01652 임시 공간 부족" },
    { box: ["m", "x"], tool: "t2", path: ["e2", "e3", "e3b", "e2b"], turn: 3, call: "tool_use: get_row_count", res: "적재 0건 (평소 12만 건)" },
    { box: ["m", "g"], tool: "t3", path: ["e2"], turn: 4, call: "tool_use: request_rerun", res: "승인 대기 중", gate: true },
    { box: ["m", "a"], path: ["e5"], turn: 4, call: "stop_reason: end_turn", res: "" }
  ];
  U.scene(root, svg, [
    { t: "질문을 받습니다", d: "운영자가 '어젯밤 배치 왜 실패했어?'라고 묻습니다. 모델에게는 질문과 함께 쓸 수 있는 도구 목록이 전달됩니다." },
    { t: "모델이 도구를 고릅니다", d: "모델은 직접 서버에 접속하지 않습니다. 'get_batch_log 를 이 인자로 불러 달라'는 요청(tool_use)만 돌려주고, 실제 실행은 우리 코드가 합니다." },
    { t: "결과를 보고 다시 판단합니다", d: "실행 결과(tool_result)를 대화에 붙여 다시 보냅니다. 로그에서 임시 공간 부족 오류를 확인한 모델이 다음 행동을 정합니다." },
    { t: "필요하면 또 부릅니다", d: "적재 건수를 확인해 영향 범위를 봅니다. 도구 호출과 판단을 반복하는 것이 에이전트 루프이고, 반복 횟수에는 상한(8회)을 둡니다." },
    { t: "쓰기 도구 앞에서는 멈춥니다", d: "재실행처럼 시스템을 바꾸는 도구는 실행 전에 사람 승인을 받습니다. 거절하면 모델은 지금까지 확인한 내용으로 보고서를 씁니다." },
    { t: "더 부를 도구가 없으면 답합니다", d: "모델이 end_turn 으로 끝내면 루프가 멈춥니다. 답은 현상, 원인(근거 로그·수치), 조치안, 재실행 필요 여부 순서로 정리됩니다." }
  ], (n, s) => {
    const p = plan[n], q = (k) => s.querySelector(`[data-k="${k}"]`);
    ["u", "m", "x", "tools", "a"].forEach((b) => U.cls(s, [b], "a-hl", p.box.includes(b)));
    U.cls(s, ["g"], "a-hl2", !!p.gate);
    ["t1", "t2", "t3"].forEach((t) => U.cls(s, [t], "a-hl", p.tool === t));
    s.querySelectorAll(".a-edge").forEach((e) => e.classList.toggle("a-hl", p.path.includes(e.dataset.k)));
    q("call").textContent = p.call; q("res").textContent = p.res; q("turn").textContent = `반복 ${p.turn} / 8`;
    U.cls(s, ["m"], "a-pulse", n === 2 || n === 3);
    U.travel(s, p.path);
  }, { foot: "도구 이름과 결과 값은 세션 17 실습의 운영 에이전트를 줄여 옮긴 예시입니다." });
});
