(async function () {
  const AX = window.AX;
  const { esc, rich, getJSON, topbar, footer } = AX;
  const main = document.getElementById("main");
  const v = await getJSON("data/version.json").catch(() => ({ current: "", updated: "" }));
  topbar("resources", v.current);
  footer(v.current, v.updated);
  let T;
  try { T = await getJSON("data/timelines.json"); }
  catch (e) { main.innerHTML = `<div class="empty">타임라인을 불러오지 못했습니다.</div>`; return; }
  const KEYS = [["ai", "AI 가이드라인"], ["netsep", "망분리"]];
  let key = location.hash === "#netsep" ? "netsep" : "ai", reg = "전체";

  function render() {
    const t = T[key];
    const ev = (t.events || []).filter((e) => reg === "전체" || e.region === reg);
    const years = [...new Set(ev.map((e) => String(e.date).slice(0, 4)))];
    main.innerHTML = `
      <section class="hero"><div class="eyebrow"><a href="resources.html">자료실</a> · TIMELINE</div>
        <h1 class="display">${esc(t.title)}</h1><p class="lead">${rich(t.intro || "")}</p>
        <div class="langs" role="tablist">${KEYS.map(([k, l]) => `<button class="lang-btn" role="tab" data-k="${k}" aria-selected="${k === key}">${l}</button>`).join("")}</div>
        <p class="small muted">${esc(T.updated)} 기준 · ${t.events.length}건 · 사건마다 출처를 달았습니다.</p></section>
      ${t.now && t.now.length ? `<section class="sec now"><h2>지금 기준 요점</h2><ul>${t.now.map((x) => `<li>${rich(x)}</li>`).join("")}</ul></section>` : ""}
      <div class="filters"><div class="chiprow">${["전체", "국내", "해외"].map((x) => `<button class="filter" data-reg="${x}" aria-pressed="${x === reg}">${x}</button>`).join("")}</div></div>
      <div class="tl">${years.map((y) => `<div class="tl-year"><h2>${y}</h2>${ev.filter((e) => String(e.date).startsWith(y)).map((e) => `
        <article class="tl-item ${esc(e.region === "국내" ? "kr" : "gl")}">
          <div class="tl-date">${esc(e.date)}</div>
          <div class="tl-card">
            <div class="tl-meta"><span class="badge">${esc(e.region)}</span><span class="badge">${esc(e.kind || "")}</span>${e.status ? `<span class="badge st">${esc(e.status)}</span>` : ""}<span class="small muted">${esc(e.org || "")}</span></div>
            <h3>${esc(e.title)}</h3><p>${rich(e.summary || "")}</p>
            ${e.detail && e.detail.length ? `<ul>${e.detail.map((d) => `<li>${rich(d)}</li>`).join("")}</ul>` : ""}
            ${e.impact ? `<p class="impact"><b>실무 영향</b> ${rich(e.impact)}</p>` : ""}
            ${e.sources && e.sources.length ? `<p class="small">${e.sources.map((s) => `<a href="${esc(s.url)}" target="_blank" rel="noopener">${esc(s.label)}</a>`).join(" · ")}</p>` : ""}
          </div></article>`).join("")}</div>`).join("") || `<div class="empty">해당하는 항목이 없습니다.</div>`}</div>
      <p class="small muted">규제·지침은 자주 바뀝니다. 실제 업무 적용 전에는 출처의 원문과 소속 기관 준법·보안 부서의 해석을 확인하세요.</p>`;
    main.querySelectorAll("[data-k]").forEach((b) => b.addEventListener("click", () => { key = b.dataset.k; reg = "전체"; history.replaceState(null, "", "#" + key); render(); }));
    main.querySelectorAll("[data-reg]").forEach((b) => b.addEventListener("click", () => { reg = b.dataset.reg; render(); }));
    document.title = `${t.title} · AX Study`;
  }
  render();
  addEventListener("hashchange", () => { const k = location.hash === "#netsep" ? "netsep" : "ai"; if (k !== key) { key = k; render(); } });
})();
