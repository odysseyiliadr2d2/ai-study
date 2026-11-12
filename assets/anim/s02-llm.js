// 세션 02 · LLM 한 문장 따라가기: 입력 문장 → 토큰화 → 임베딩 → 트랜스포머 → 확률 분포 → 다음 토큰
AXAnim.register("s02-llm", (root, U) => {
  root.innerHTML = `<nav class="axa-map" aria-label="단계"></nav>
    <div class="axa-benchtop"><span class="axa-round" data-k="round">생성 1회차</span><span class="axa-foot" data-k="cachenote" style="margin:0"></span>
      <div class="axa-ctrl"><button type="button" class="axa-btn axa-primary" data-k="play">일시정지</button><button type="button" class="axa-btn" data-k="prev">이전</button>
        <button type="button" class="axa-btn" data-k="next">다음</button><button type="button" class="axa-btn" data-k="restart">처음부터</button></div></div>
    <div class="axa-prog"><i data-k="prog"></i></div>
    <div class="axa-canvas" style="margin-top:10px">
      <svg class="w-svg" data-k="svg" viewBox="0 0 960 470" role="img" aria-label="LLM 처리 과정 애니메이션">
        <text class="w-cap" x="24" y="30">문장</text><text class="w-sent" data-k="sent" x="24" y="66"></text>
        <text class="w-cap" x="24" y="104">토큰 · ID</text><g data-k="toks"></g>
        <text class="w-cap" x="24" y="192">벡터 · 8칸만 표시 (주황 + · 파랑 −)</text><g data-k="vecs"></g><g data-k="pos"></g><g data-k="arcs"></g>
        <text class="w-cap" x="24" y="452" data-k="cap-att"></text><g data-k="panel"></g><g data-k="flyer" class="w-fly"></g>
      </svg></div>
    <label class="axa-temp">temperature <input data-k="temp" type="range" min="0.2" max="1.6" step="0.1" value="1"><output data-k="tout">1.0</output>
      <span class="axa-foot" style="margin:0">확률 분포 단계에서 움직여 보세요. 낮추면 1등에 몰리고, 높이면 고르게 퍼집니다.</span></label>
    <div class="axa-explain"><div><h4 data-k="ex-t"></h4><p data-k="ex-d"></p></div><div class="axa-real"><h5>실제 모델에서는</h5><ul data-k="ex-r"></ul></div></div>
    <p class="axa-foot">토큰 ID, 벡터 값, 어텐션 가중치, 확률은 이해를 돕기 위한 예시 값입니다. 실제 모델의 토크나이저와 수치는 모델마다 다릅니다.</p>`;
  const $ = (k) => root.querySelector(`[data-k="${k}"]`), el = U.el, txt = U.txt, reduce = U.reduce, svg = $("svg");

  // ── 예시 데이터 ──
  const TOK = [
    { w: "배치", id: 48213 }, { w: "가", id: 1130 }, { w: "▁실패", id: 30957 }, { w: "하면", id: 8842 },
    { w: "▁운영팀에", id: 51270 }, { w: "▁알림이", id: 22719 }
  ];
  const ROUNDS = [
    { n: 4, att: [0.41, 0.06, 0.38, 0.15],
      cand: [["▁운영팀에", 2.1], ["▁재실행", 1.55], ["▁알림이", 1.3], ["▁담당자가", 1.05], ["▁로그를", 0.7], ["▁고양이", -2.4]], pick: 0 },
    { n: 5, att: [0.22, 0.03, 0.31, 0.18, 0.26],
      cand: [["▁알림이", 2.4], ["▁문자가", 1.45], ["▁보고합니다", 1.2], ["▁연락이", 0.95], ["▁메일이", 0.6], ["▁점심", -2.2]], pick: 0 }
  ];
  const X0 = 24, DX = 100, W = 90, TY = 116, VY = 204, CH = 16, NV = 8;
  const colX = (i) => X0 + i * DX;
  // 토큰마다 고정된 예시 벡터 (임베딩) 와 문맥 반영 후 벡터
  const rnd = (s) => () => ((s = (s * 9301 + 49297) % 233280) / 233280) * 2 - 1;
  const EMB = TOK.map((t) => { const r = rnd(t.id % 9973 + 11); return Array.from({ length: NV }, () => +(r() * 0.9).toFixed(2)); });
  const CTX = TOK.map((t, i) => { const r = rnd(t.id % 7919 + 5); return EMB[i].map((v) => +Math.max(-1, Math.min(1, v * 0.4 + r() * 0.75)).toFixed(2)); });

  // ── 그림 요소 만들기 ──
  const toks = $("toks"), vecs = $("vecs"), pos = $("pos"), arcs = $("arcs"), panel = $("panel"), flyer = $("flyer");
  const tokG = TOK.map((t, i) => { const g = el("g", { class: "w-tok w-fadein w-hide", transform: `translate(${colX(i)} ${TY})` }, toks);
    el("rect", { width: W, height: 40, rx: 8 }, g); txt(g, W / 2, 25, t.w.replace("▁", "␣"), "w-w", { "text-anchor": "middle" }); txt(g, W / 2, 56, "ID " + t.id, "w-id", { "text-anchor": "middle" }); return g; });
  const vecG = TOK.map((t, i) => { const g = el("g", { class: "w-fadein w-hide", transform: `translate(${colX(i)} ${VY})` }, vecs);
    const cells = Array.from({ length: NV }, (_, j) => { el("rect", { x: 0, y: j * CH, width: W, height: CH - 2, rx: 2, class: "w-cell0" }, g);
      return el("rect", { x: 0, y: j * CH, width: W, height: CH - 2, rx: 2, class: "w-cell", "fill-opacity": 0 }, g); });
    const vt = txt(g, 4, NV * CH + 14, "", "a-sub a-mono"); return { g, cells, vt }; });
  const posG = TOK.map((t, i) => { const g = el("g", { class: "w-fadein w-hide", transform: `translate(${colX(i) + W - 36} ${VY + 2})` }, pos);
    el("rect", { width: 34, height: 18, rx: 9, fill: "var(--a-blue-soft)", stroke: "var(--a-blue)" }, g); txt(g, 17, 13, "+p" + i, "a-sub a-mono", { "text-anchor": "middle" }); return g; });
  function paintVec(i, vals) { vecG[i].cells.forEach((c, j) => { const v = vals[j]; c.setAttribute("fill", v >= 0 ? "var(--a-accent)" : "var(--a-blue)"); c.setAttribute("fill-opacity", Math.min(1, Math.abs(v) * 1.05).toFixed(2)); });
    const f = (v) => (v < 0 ? "−" : "") + Math.abs(v).toFixed(2).replace(/^0/, "");
    vecG[i].vt.textContent = "[" + f(vals[0]) + " " + f(vals[1]) + " …]"; }

  // 오른쪽 보조판
  const PX = 640, PY = 96, PW = 300, PH = 330;
  const frame = el("rect", { x: PX, y: PY, width: PW, height: PH, rx: 12, class: "w-frame" }, panel);
  const ptitle = txt(panel, PX + 16, PY + 26, "", "a-lbl");
  const psub = txt(panel, PX + 16, PY + 46, "", "a-sub");
  // (1) 임베딩 표
  const gTable = el("g", { class: "w-fadein w-hide" }, panel);
  const rowHl = el("rect", { x: PX + 12, y: PY + 62, width: PW - 24, height: 22, rx: 5, class: "w-rowhl" }, gTable);
  const tableRows = [1129, 1130, 1131, 8842, 22719, 30957, 48213, 51270, 51271].map((id, k) => { const y = PY + 78 + k * 26;
    txt(gTable, PX + 22, y, String(id).padStart(6, " "), "a-sub a-mono"); const r = rnd(id + 3);
    for (let j = 0; j < 10; j++) { const v = r(); el("rect", { x: PX + 84 + j * 19, y: y - 11, width: 16, height: 14, rx: 2, fill: v >= 0 ? "var(--a-accent)" : "var(--a-blue)", "fill-opacity": Math.abs(v) * .8 }, gTable); }
    return { id, y }; });
  txt(gTable, PX + 22, PY + PH - 14, "어휘 약 10만 행 × 차원 수천 열 중 일부", "a-sub");
  // (2) 층 쌓기
  const gLayers = el("g", { class: "w-fadein w-hide" }, panel);
  const LAY = [1, 2, 3, 16, 31, 32];
  const layerFills = LAY.map((n, k) => { const y = PY + 70 + k * 40;
    el("rect", { x: PX + 16, y, width: PW - 32, height: 30, rx: 6, class: "w-layer" }, gLayers);
    const f = el("rect", { x: PX + 16, y, width: 0, height: 30, rx: 6, class: "w-layerfill" }, gLayers);
    txt(gLayers, PX + 28, y + 20, k === 3 ? "⋮  층 4 ~ 30" : `층 ${n} · 어텐션 → 피드포워드`, "a-sub", { "font-weight": 700, fill: "var(--a-ink)" }); return f; });
  const layerCount = txt(gLayers, PX + 16, PY + PH - 14, "", "a-sub a-mono");
  // (3) 확률 분포
  const gProb = el("g", { class: "w-fadein w-hide" }, panel);
  const probRows = Array.from({ length: 6 }, (_, k) => { const y = PY + 72 + k * 40;
    const w = txt(gProb, PX + 16, y + 14, "", "w-pw"); el("rect", { x: PX + 120, y: y, width: 150, height: 18, rx: 4, class: "w-probbg" }, gProb);
    const b = el("rect", { x: PX + 120, y: y, width: 0, height: 18, rx: 4, class: "w-prob" }, gProb); const v = txt(gProb, PX + 120, y + 32, "", "w-pv"); return { w, b, v }; });
  const probNote = txt(gProb, PX + 16, PY + PH - 14, "", "a-sub");
  // 마지막 벡터 → 보조판 흐름선
  const flowLine = el("path", { class: "w-line w-fadein w-hide", d: "" }, svg);

  // ── 화면 상태 ──
  const STEPS = [];
  ROUNDS.forEach((_, r) => [1, 2, 3, 4, 5, 6].forEach((s) => { if (!(r === 1 && s === 1)) STEPS.push({ r, s }); }));
  STEPS.push({ r: 1, s: 7 }); // 끝
  const NAMES = ["입력 문장", "토큰화", "임베딩", "트랜스포머", "확률 분포", "다음 토큰"];
  const map = root.querySelector(".axa-map");
  map.innerHTML = NAMES.map((n, i) => `<button type="button" data-s="${i + 1}"><small>0${i + 1}</small><b>${n}</b></button>`).join("");
  let idx = 0, playing = !reduce, epoch = 0, timer = null, raf = null, T = 1, sampled = null;
  const wait = (ms, ep) => new Promise((res, rej) => setTimeout(() => (ep === epoch ? res() : rej("x")), reduce ? 0 : ms));
  const probs = (cand) => { const e = cand.map(([, l]) => Math.exp(l / T)), s = e.reduce((a, b) => a + b, 0); return e.map((x) => x / s); };

  const TEXT = {
    1: ["입력 문장", "사용자가 쓴 글이 그대로 모델에 들어가는 것은 아닙니다. 먼저 글자를 모델이 아는 조각으로 바꿔야 합니다.",
        ["실제로는 시스템 프롬프트, 대화 기록, 질문이 이어 붙은 긴 글이 한꺼번에 들어갑니다", "모델이 한 번에 볼 수 있는 길이를 컨텍스트 창이라고 합니다 (수십만 토큰)"]],
    2: ["토큰화", "문장을 자주 쓰이는 글자 조각(토큰)으로 자르고, 각 조각을 어휘 사전의 번호(ID)로 바꿉니다. ␣는 앞에 띄어쓰기가 있다는 표시입니다.",
        ["BPE 같은 방식으로 만든 어휘 사전은 보통 10만~25만 개입니다", "한국어는 영어보다 같은 뜻에 토큰이 더 많이 드는 편이라 비용·길이 계산에 영향을 줍니다", "요금과 컨텍스트 한도는 글자 수가 아니라 토큰 수로 셉니다"]],
    3: ["임베딩", "토큰 ID 로 임베딩 표의 한 줄을 꺼내면 그 토큰의 벡터가 됩니다. 여기에 '몇 번째 자리인지' 정보(+p)를 더해 순서를 알 수 있게 합니다.",
        ["벡터는 수천 차원입니다. 여기서는 8칸만 색으로 보여 줍니다 (주황 +, 파랑 −)", "임베딩 표의 값은 학습으로 정해지고, 추론할 때는 바뀌지 않습니다", "세션 08 의 문장 임베딩은 이 벡터들을 문장 하나로 모은 것입니다"]],
    4: ["트랜스포머", "각 토큰이 앞쪽 토큰들을 얼마나 참고할지 어텐션 가중치를 계산하고, 그 비율로 정보를 섞습니다. 이 과정을 수십 층 반복하면 벡터에 문맥이 담깁니다. 아래 선 굵기가 마지막 토큰이 참고한 비율입니다.",
        ["모든 토큰이 동시에 이 계산을 하지만, 뒤쪽 토큰은 볼 수 없습니다 (앞만 보기)", "층마다 여러 개의 어텐션 헤드가 서로 다른 관계(주어-서술어, 조건-결과 등)를 봅니다", "큰 모델은 층이 수십~100여 개, GPU 메모리와 속도를 가장 많이 쓰는 부분입니다"]],
    5: ["확률 분포", "마지막 토큰의 최종 벡터를 어휘 전체 크기의 점수(로짓)로 바꾸고, 소프트맥스로 합이 100%인 확률로 만듭니다. 아래 temperature 를 움직이면 분포 모양이 바뀝니다.",
        ["후보는 어휘 사전 전체(10만 개 이상)입니다. 여기서는 상위 6개만 보여 줍니다", "temperature 를 받지 않는 모델 API 도 있습니다. 그때는 지시문과 예시로 출력 모양을 고정합니다"]],
    6: ["다음 토큰", "확률에 따라 토큰 하나를 뽑아 문장 뒤에 붙입니다. 그리고 늘어난 문장으로 처음 단계부터 다시 예측합니다. 답이 끝날 때까지 이 바퀴를 수백~수천 번 돕니다.",
        ["가장 높은 것만 고르면(greedy) 답이 늘 같아지지만 단조로워집니다", "끝을 뜻하는 특수 토큰이 뽑히거나 최대 토큰 수에 닿으면 멈춥니다", "출력 토큰이 많을수록 응답 시간과 비용이 늘어납니다"]],
    7: ["이렇게 한 토큰씩 이어집니다", "두 바퀴를 돌아 '배치가 실패하면 운영팀에 알림이'까지 만들었습니다. 실제 답변은 이 바퀴를 끝 토큰이 나올 때까지 반복한 결과입니다.",
        ["두 번째 바퀴부터는 앞 토큰의 계산 결과를 KV 캐시에 저장해 두고 새 토큰만 계산합니다", "그래서 첫 토큰이 나오기까지가 가장 오래 걸리고, 그다음은 빠르게 이어집니다"]]
  };

  function setText(s) { const t = TEXT[s]; $("ex-t").textContent = t[0]; $("ex-d").textContent = t[1]; const ul = $("ex-r"); ul.textContent = ""; t[2].forEach((x) => { const li = document.createElement("li"); li.textContent = x; ul.appendChild(li); }); }
  function panelMode(m, title, sub) { [gTable, gLayers, gProb].forEach((g) => g.classList.add("w-hide")); frame.style.opacity = m ? 1 : 0;
    ptitle.textContent = title || ""; psub.textContent = sub || ""; if (m) m.classList.remove("w-hide"); }
  function drawProbs(rd, pickIdx) {
    const p = probs(rd.cand);
    probRows.forEach((row, k) => { row.w.textContent = rd.cand[k][0].replace("▁", "␣"); row.b.setAttribute("width", (p[k] * 150).toFixed(1));
      row.v.textContent = `로짓 ${rd.cand[k][1].toFixed(2)} → ${(p[k] * 100).toFixed(1)}%`;
      const pk = pickIdx === k; row.b.classList.toggle("w-pick", pk); row.w.classList.toggle("w-pick", pk); });
  }
  function drawArcs(rd, on) {
    arcs.innerHTML = ""; if (!on) { $("cap-att").textContent = ""; return; }
    const q = rd.n - 1, qx = colX(q) + W / 2, y0 = VY + NV * CH + 22;
    $("cap-att").textContent = `어텐션: '${TOK[q].w.replace("▁", "␣")}'(마지막 토큰)이 앞 토큰을 참고한 비율`;
    rd.att.forEach((a, k) => { const kx = colX(k) + W / 2, depth = 26 + Math.abs(q - k) * 16;
      const d = k === q ? `M${qx - 14} ${y0} C ${qx - 30} ${y0 + 40}, ${qx + 30} ${y0 + 40}, ${qx + 14} ${y0}` : `M${qx} ${y0} Q ${(qx + kx) / 2} ${y0 + depth * 2} ${kx} ${y0}`;
      const p = el("path", { d, class: "w-arc", "stroke-width": (1.5 + a * 16).toFixed(1), opacity: .35 + a }, arcs);
      const L = p.getTotalLength(); p.style.strokeDasharray = L; p.style.strokeDashoffset = reduce ? 0 : L;
      requestAnimationFrame(() => requestAnimationFrame(() => (p.style.strokeDashoffset = 0)));
      const lx = k === q ? qx + 26 : (qx + kx) / 2 - 12, ly = k === q ? y0 + 34 : y0 + depth + 4;
      txt(arcs, lx, ly, a.toFixed(2), "a-sub a-mono", { "font-weight": 700, fill: "var(--a-ink)" }); });
  }

  // 단계 k 가 시작할 때의 모습을 즉시 맞춥니다 (이전 단계 결과는 완성된 상태)
  function base(st) {
    const { r, s } = st, rd = ROUNDS[Math.min(r, 1)];
    const nPrev = r === 0 ? 0 : ROUNDS[0].n;               // 2회차에서는 앞 토큰 4개가 이미 계산돼 있음 (KV 캐시)
    const nNow = s === 7 ? 6 : rd.n;
    let sent = TOK.slice(0, s === 7 ? 6 : (s >= 2 || r === 1 ? nNow : 0)).map((t) => t.w).join("").replace(/▁/g, " ");
    if (r === 0 && s === 1) sent = "";
    $("sent").textContent = sent;
    tokG.forEach((g, i) => { const vis = s === 7 ? i < 6 : (r === 0 ? s >= 3 && i < nNow : i < nPrev || (s >= 3 && i < nNow));
      g.classList.toggle("w-hide", !(vis || (s === 2 && i < (r === 0 ? 0 : nPrev)))); g.classList.remove("a-hl", "w-q"); });
    vecG.forEach((v, i) => { const done = s === 7 ? i < 5 : (r === 1 && i < nPrev) || (s >= 4 && i < nNow);
      const vis = done || (s >= 4 && i < nNow); v.g.classList.toggle("w-hide", !vis); paintVec(i, done && (s >= 5 || r === 1 || s === 7) ? CTX[i] : EMB[i]); });
    if (s === 7) { vecG[5].g.classList.add("w-hide"); }
    posG.forEach((p) => p.classList.add("w-hide"));
    drawArcs(rd, s >= 5 && s !== 7);
    if (s >= 5 && s !== 7) tokG[rd.n - 1].classList.add("w-q");
    flowLine.classList.toggle("w-hide", !(s >= 6 && s !== 7));
    panelMode(null);
    flyer.innerHTML = ""; flyer.style.transform = ""; flyer.style.opacity = 1;
    $("round").textContent = s === 7 ? "생성 완료 (2회)" : `생성 ${r + 1}회차`; $("round").classList.toggle("r2", r === 1);
    $("cachenote").textContent = r === 1 && s !== 7 ? "앞 토큰 4개는 KV 캐시에서 재사용 · 새 토큰만 계산" : "";
    [...map.children].forEach((b, i) => { b.classList.toggle("axa-cur", i + 1 === s); b.classList.toggle("axa-done", s === 7 || i + 1 < s); });
    setText(s);
  }

  // 단계별 애니메이션
  const ANIM = {
    1: async (st, ep) => { const full = "배치가 실패하면"; for (let i = 1; i <= full.length; i++) { $("sent").textContent = full.slice(0, i); await wait(110, ep); } },
    2: async (st, ep) => {
      const rd = ROUNDS[st.r], from = st.r === 0 ? 0 : ROUNDS[0].n;
      panelMode(null);
      for (let i = from; i < rd.n; i++) { tokG[i].classList.remove("w-hide"); tokG[i].classList.add("a-hl"); await wait(420, ep); tokG[i].classList.remove("a-hl"); }
    },
    3: async (st, ep) => {
      const rd = ROUNDS[st.r], from = st.r === 0 ? 0 : ROUNDS[0].n;
      panelMode(gTable, "임베딩 표에서 한 줄 꺼내기", "토큰 ID = 표의 행 번호");
      for (let i = from; i < rd.n; i++) {
        tokG[i].classList.add("a-hl"); const row = tableRows.find((x) => x.id === TOK[i].id); rowHl.setAttribute("y", row.y - 15);
        await wait(520, ep); vecG[i].g.classList.remove("w-hide"); paintVec(i, EMB[i]); await wait(380, ep); tokG[i].classList.remove("a-hl");
      }
      await wait(300, ep); for (let i = from; i < rd.n; i++) posG[i].classList.remove("w-hide");
    },
    4: async (st, ep) => {
      const rd = ROUNDS[st.r], q = rd.n - 1, from = st.r === 0 ? 0 : ROUNDS[0].n;
      for (let i = 0; i < rd.n; i++) { vecG[i].g.classList.remove("w-hide"); if (i >= from) paintVec(i, EMB[i]); }
      panelMode(gLayers, "트랜스포머 층을 차례로 통과", st.r === 1 ? "앞 토큰 결과는 캐시 · 새 토큰 열만 계산" : "모든 토큰 열이 함께 계산됩니다");
      tokG[q].classList.add("w-q"); drawArcs(rd, true);
      const steps = 24;
      for (let k = 1; k <= steps; k++) {
        const f = k / steps; layerFills.forEach((lf, li) => lf.setAttribute("width", Math.max(0, Math.min(1, f * LAY.length - li)) * (PW - 32)));
        layerCount.textContent = `통과한 층 ${Math.round(f * 32)} / 32`;
        for (let i = from; i < rd.n; i++) paintVec(i, EMB[i].map((v, j) => +(v + (CTX[i][j] - v) * f).toFixed(2)));
        await wait(70, ep);
      }
    },
    5: async (st, ep) => {
      const rd = ROUNDS[st.r], q = rd.n - 1, sx = colX(q) + W, sy = VY + NV * CH / 2;
      flowLine.setAttribute("d", `M${sx + 4} ${sy} C ${sx + 60} ${sy}, ${PX - 50} ${PY + 120}, ${PX - 6} ${PY + 120}`); flowLine.classList.remove("w-hide");
      panelMode(gProb, "다음 토큰 후보와 확률", "최종 벡터 × 어휘 행렬 → 로짓 → 소프트맥스");
      probRows.forEach((row) => { row.b.setAttribute("width", 0); row.w.textContent = ""; row.v.textContent = ""; });
      await wait(400, ep); drawProbs(rd, -1); probNote.textContent = "상위 6개만 표시 · 나머지 수만 개는 0%에 가까움";
    },
    6: async (st, ep) => {
      const rd = ROUNDS[st.r]; panelMode(gProb, "확률대로 하나 뽑기", "주사위를 굴리듯 확률 비율로 선택"); drawProbs(rd, -1);
      const p = probs(rd.cand);
      const pick = sampled !== null ? sampled : rd.pick;
      for (let k = 0; k < 10; k++) { drawProbs(rd, k % 4); await wait(90 + k * 18, ep); }
      drawProbs(rd, pick);
      const word = rd.cand[pick][0];
      probNote.textContent = `'${word.replace("▁", "␣")}' 선택 (확률 ${(p[pick] * 100).toFixed(1)}%)`;
      await wait(450, ep);
      // 고른 토큰이 다음 칸으로 날아갑니다
      flyer.innerHTML = ""; const g = el("g", { class: "w-tok a-hl" }, flyer); el("rect", { width: W, height: 40, rx: 8 }, g);
      txt(g, W / 2, 25, word.replace("▁", "␣"), "w-w", { "text-anchor": "middle" });
      const y0 = PY + 72 + pick * 40 - 12; flyer.style.transform = `translate(${PX + 16}px, ${y0}px)`;
      await wait(60, ep); flyer.style.transform = `translate(${colX(rd.n)}px, ${TY}px)`;
      await wait(950, ep);
      if (pick === rd.pick) { tokG[rd.n].classList.remove("w-hide"); flyer.innerHTML = ""; }
      $("sent").textContent = TOK.slice(0, rd.n).map((t) => t.w).join("").replace(/▁/g, " ") + word.replace("▁", " ");
      if (pick !== rd.pick) probNote.textContent += " · 예시는 1등 토큰으로 이어갑니다";
    },
    7: async (st, ep) => { $("sent").textContent = "배치가 실패하면 운영팀에 알림이 …"; }
  };

  let visible = false;
  async function go(i, auto) {
    clearTimeout(timer); cancelAnimationFrame(raf); epoch++; const ep = epoch;
    idx = (i + STEPS.length) % STEPS.length; if (!auto) sampled = null;
    const st = STEPS[idx]; base(st); $("prog").style.width = (idx / (STEPS.length - 1) * 100) + "%";
    try { await ANIM[st.s](st, ep); } catch (e) { return; }
    if (ep !== epoch) return;
    schedule();
  }
  // 화면에 보일 때만 다음 단계로 넘어갑니다
  function schedule() { clearTimeout(timer); timer = null; if (!playing || !visible) return;
    timer = setTimeout(() => go(idx + 1, true), reduce ? 0 : (STEPS[idx].s === 7 ? 5200 : 2600)); }
  function setPlay(p) { playing = p; $("play").textContent = p ? "일시정지" : "재생"; if (p) go(idx + 1 >= STEPS.length ? 0 : idx, true); else clearTimeout(timer); }
  $("play").addEventListener("click", () => setPlay(!playing));
  $("prev").addEventListener("click", () => { setPlay(false); go(idx - 1); });
  $("next").addEventListener("click", () => { setPlay(false); go(idx + 1); });
  $("restart").addEventListener("click", () => { playing = true; $("play").textContent = "일시정지"; go(0, true); });
  map.addEventListener("click", (e) => { const b = e.target.closest("button[data-s]"); if (!b) return; setPlay(false);
    const s = +b.dataset.s, r = STEPS[idx].r === 1 && s >= 2 ? 1 : 0; go(STEPS.findIndex((x) => x.r === r && x.s === s)); });
  $("temp").addEventListener("input", (e) => { T = +e.target.value; $("tout").textContent = T.toFixed(1); const st = STEPS[idx];
    if (st.s === 5 || st.s === 6) { setPlay(false); drawProbs(ROUNDS[st.r], st.s === 6 ? (sampled ?? ROUNDS[st.r].pick) : -1); } });
  if (!playing) $("play").textContent = "재생";
  let started = false;
  U.watch(root, (v) => { visible = v;
    if (v && !started) { started = true; go(0, true); }
    else if (v) { if (!timer) schedule(); }
    else { clearTimeout(timer); timer = null; } });
});
