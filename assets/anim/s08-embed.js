// 세션 08 · 임베딩: 뜻이 비슷한 문장은 가까운 점이 됩니다
AXAnim.register("s08-embed", (root, U) => {
  const groups = [
    { name: "배치 · 운영", color: "var(--a-accent)", soft: "var(--a-soft)", cx: 190, cy: 150, items: [["배치 재실행 절차", 150, 150], ["야간 배치 실패 대응", 95, 128], ["작업 스케줄 변경", 110, 182], ["ETL 지연 알림", 175, 205], ["배치 로그 보는 법", 215, 112]] },
    { name: "보안 · 인증", color: "var(--a-blue)", soft: "var(--a-blue-soft)", cx: 470, cy: 120, items: [["비밀번호 변경", 420, 95], ["OTP 재발급", 500, 128], ["망분리 예외 신청", 410, 160], ["계정 잠금 해제", 470, 62]] },
    { name: "복지 · 총무", color: "var(--a-green)", soft: "var(--a-green-soft)", cx: 400, cy: 300, items: [["휴가 신청", 340, 280], ["구내식당 메뉴", 440, 318], ["주차 등록", 470, 275], ["경조사 지원", 360, 345]] }
  ];
  let ready = false;
  const ctl = U.scene(root, `<svg viewBox="0 0 640 400" role="img" aria-label="임베딩 공간 산점도 애니메이션"></svg>`, [
    { t: "문장 하나가 점 하나가 됩니다", d: "사내 FAQ 문장 13개를 임베딩하면 각각 좌표를 가진 점이 됩니다. 비슷한 숫자를 가진 문장은 가까이 찍힙니다." },
    { t: "뜻이 비슷한 것끼리 모입니다", d: "누가 분류해 주지 않아도 배치·운영, 보안·인증, 복지·총무 문장이 저절로 무리를 이룹니다. 단어가 달라도 뜻이 가까우면 같이 모입니다." },
    { t: "질문도 같은 공간에 찍힙니다", d: "'배치 다시 돌리려면?'처럼 FAQ 와 표현이 달라도, 같은 모델로 임베딩하면 배치·운영 무리 근처에 떨어집니다." },
    { t: "가까운 순서로 k개를 고릅니다", d: "질문 점에서 가장 가까운 점 3개(k=3)를 찾습니다. 벡터DB 의 인덱스(HNSW 등)는 이 계산을 수백만 개에서도 빠르게 해 줍니다." },
    { t: "가까움은 코사인 유사도로 잽니다", d: "두 벡터가 가리키는 방향이 얼마나 같은지를 값으로 봅니다. 기준값(예: 0.75)보다 낮으면 '근거 없음'으로 처리합니다." }
  ], (n) => { if (ready) draw(n); }, { foot: "실제 임베딩은 384~1,024차원입니다. 여기서는 2차원으로 줄여 그렸고, 좌표와 유사도는 예시입니다." });

  const svg = root.querySelector("svg"), el = U.el;
  const pts = [];
  groups.forEach((g, gi) => g.items.forEach(([t, x, y]) => pts.push({ x, y, t, g: gi })));
  const q = { x: 240, y: 180, t: "배치 다시 돌리려면?" };
  pts.forEach((p) => { p.d = Math.hypot(p.x - q.x, p.y - q.y); });
  const near = [...pts].sort((a, b) => a.d - b.d).slice(0, 3), sims = [0.89, 0.84, 0.79];
  for (let x = 40; x <= 600; x += 80) el("line", { x1: x, y1: 20, x2: x, y2: 380, stroke: "var(--a-line)", "stroke-width": 1 }, svg);
  for (let y = 20; y <= 380; y += 60) el("line", { x1: 40, y1: y, x2: 600, y2: y, stroke: "var(--a-line)", "stroke-width": 1 }, svg);
  groups.forEach((g) => { el("ellipse", { cx: g.cx, cy: g.cy, rx: 112, ry: 82, fill: g.soft, stroke: g.color, "stroke-width": 1.2, "stroke-dasharray": "4 4", "data-at": 1 }, svg);
    const lb = U.txt(svg, g.cx - 100, g.cy - 88, g.name, "a-lbl", { "data-at": 1 }); lb.style.fill = g.color; });
  const lines = near.map(() => el("line", { x1: q.x, y1: q.y, x2: q.x, y2: q.y, stroke: "var(--a-accent)", "stroke-width": 2 }, svg));
  const ring = el("circle", { cx: q.x, cy: q.y, r: 0, fill: "none", stroke: "var(--a-accent)", "stroke-width": 1.2, "stroke-dasharray": "3 4" }, svg);
  const dots = pts.map((p) => { const g = el("g", {}, svg); el("circle", { cx: p.x, cy: p.y, r: 6, fill: groups[p.g].color }, g); U.txt(g, p.x + 9, p.y + 4, p.t, "a-sub"); g.style.transition = "opacity .4s"; return g; });
  const qg = el("g", { "data-at": 2 }, svg);
  el("circle", { cx: q.x, cy: q.y, r: 8, fill: "var(--a-ink)" }, qg); el("circle", { cx: q.x, cy: q.y, r: 13, fill: "none", stroke: "var(--a-ink)", "stroke-width": 1.5 }, qg);
  el("line", { x1: q.x, y1: q.y + 13, x2: 120, y2: 262, stroke: "var(--a-ink)", "stroke-width": 1, "stroke-dasharray": "2 3" }, qg);
  U.txt(qg, 52, 280, "질문: " + q.t, "a-lbl");
  near.forEach((p, i) => { const g = el("g", { "data-at": 3 }, svg); el("circle", { cx: p.x, cy: p.y - 16, r: 9, fill: "var(--a-accent)" }, g);
    U.txt(g, p.x - 3.5, p.y - 12, String(i + 1), "", { fill: "#fff", "font-size": 11, "font-weight": 700 });
    U.txt(svg, p.x + 9, p.y + 20, "cos " + sims[i], "a-sub a-mono", { "data-at": 4 }); });
  function draw(n) {
    svg.querySelectorAll("[data-at]").forEach((e) => e.classList.toggle("a-on", n >= +e.dataset.at));
    dots.forEach((d, i) => { d.style.opacity = n >= 3 ? (near.includes(pts[i]) ? 1 : .25) : 1; });
    lines.forEach((ln, i) => { const on = n >= 3; ln.style.transition = U.reduce ? "none" : "all .7s cubic-bezier(.2,.7,.2,1)";
      ln.setAttribute("x2", on ? near[i].x : q.x); ln.setAttribute("y2", on ? near[i].y : q.y); });
    ring.style.transition = U.reduce ? "none" : "r .8s"; ring.setAttribute("r", n >= 3 ? near[2].d + 8 : 0);
  }
  ready = true; draw(ctl.step);
});
