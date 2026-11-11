(async function () {
  const AX = window.AX;
  const { esc, rich, getJSON, topbar, footer } = AX;
  const main = document.getElementById("main");
  const v = await getJSON("data/version.json").catch(() => ({ current: "", updated: "" }));
  topbar("resources", v.current);
  footer(v.current, v.updated);
  let data;
  try { data = await getJSON("data/cases.json"); }
  catch (e) { main.innerHTML = `<div class="empty">사례를 불러오지 못했습니다.</div>`; return; }
  const A = data.articles || [];
  const SECTORS = ["전체", "은행", "증권", "보험", "캐피탈", "공공"];
  const REGIONS = ["전체", "국내", "해외"];
  let sec = "전체", reg = "전체", q = "";

  function list() {
    document.title = "AI 도입 사례 · AX Study";
    main.innerHTML = `
      <section class="hero"><div class="eyebrow"><a href="resources.html">자료실</a> · CASES</div>
        <h1 class="display">AI 도입 사례</h1><p class="lead">${rich(data.intro || "")}</p>
        <p class="small muted">${esc(data.updated)} 기준 · ${A.length}편 · 각 기사 아래에 출처가 있습니다.</p></section>
      <div class="filters"><div class="chiprow" id="sec">${SECTORS.map((x) => `<button class="filter" data-sec="${x}" aria-pressed="${x === sec}">${x}</button>`).join("")}</div>
        <div class="chiprow" id="reg">${REGIONS.map((x) => `<button class="filter" data-reg="${x}" aria-pressed="${x === reg}">${x}</button>`).join("")}</div>
        <input class="search" id="q" type="search" placeholder="기관·기술 검색 (예: 상담, 신한, 에이전트)" value="${esc(q)}" aria-label="사례 검색"></div>
      <div class="grid" id="cards"></div>`;
    main.querySelectorAll("[data-sec]").forEach((b) => b.addEventListener("click", () => { sec = b.dataset.sec; list(); }));
    main.querySelectorAll("[data-reg]").forEach((b) => b.addEventListener("click", () => { reg = b.dataset.reg; list(); }));
    const qi = document.getElementById("q");
    qi.addEventListener("input", () => { q = qi.value; cards(); });
    cards();
  }
  function cards() {
    const k = q.trim().toLowerCase();
    const rows = A.filter((a) => (sec === "전체" || a.sector === sec) && (reg === "전체" || a.region === reg)
      && (!k || [a.org, a.title, a.summary, ...(a.tags || [])].join(" ").toLowerCase().includes(k)))
      .sort((x, y) => String(y.date).localeCompare(String(x.date)));
    document.getElementById("cards").innerHTML = rows.length ? rows.map((a) => `
      <a class="card case" href="cases.html?id=${encodeURIComponent(a.id)}">
        <div class="sess">${esc(a.sector)} · ${esc(a.region)} · ${esc(a.date)}</div>
        <h3><span class="hl">${esc(a.title)}</span></h3>
        <p><b>${esc(a.org)}</b> — ${rich(a.summary)}</p>
        <div class="tags">${(a.tags || []).map((t) => `<span class="tag">${esc(t)}</span>`).join("")}</div></a>`).join("")
      : `<div class="empty">조건에 맞는 사례가 없습니다.</div>`;
  }
  function article(a) {
    document.title = `${a.title} · AX Study`;
    const same = A.filter((x) => x.sector === a.sector && x.id !== a.id).slice(0, 3);
    main.innerHTML = `
      <div class="crumb"><a href="resources.html">자료실</a> / <a href="cases.html">AI 도입 사례</a> / ${esc(a.sector)}</div>
      <article class="panel rm-doc case-article">
        <div class="eyebrow">${esc(a.sector)} · ${esc(a.region)} · ${esc(a.date)}</div>
        <h1 class="display">${esc(a.title)}</h1>
        <p class="lead"><b>${esc(a.org)}</b></p>
        <div class="tags">${(a.tags || []).map((t) => `<span class="tag">${esc(t)}</span>`).join("")}</div>
        <div class="callout">${rich(a.summary)}</div>
        ${(a.body || []).map((p) => `<p>${rich(p)}</p>`).join("")}
        ${a.points && a.points.length ? `<h2>우리 업무에 주는 시사점</h2><ul>${a.points.map((p) => `<li>${rich(p)}</li>`).join("")}</ul>` : ""}
        <h2>출처</h2><ul class="refs">${(a.sources || []).map((s) => `<li><a href="${esc(s.url)}" target="_blank" rel="noopener">${esc(s.label)}</a></li>`).join("")}</ul>
        <p class="small muted">기사 내용은 출처의 공개 정보를 요약한 것이며 ${esc(data.updated)} 기준입니다. 수치는 각 기관 발표 기준입니다.</p>
      </article>
      ${same.length ? `<h2 class="rm-h">같은 분야 사례</h2><div class="grid">${same.map((x) => `<a class="card case" href="cases.html?id=${encodeURIComponent(x.id)}"><div class="sess">${esc(x.region)} · ${esc(x.date)}</div><h3><span class="hl">${esc(x.title)}</span></h3><p><b>${esc(x.org)}</b></p></a>`).join("")}</div>` : ""}
      <p><a class="btn ghost" href="cases.html">목록으로</a></p>`;
    scrollTo(0, 0);
  }
  const id = new URLSearchParams(location.search).get("id");
  const a = id && A.find((x) => x.id === id);
  a ? article(a) : list();
})();
