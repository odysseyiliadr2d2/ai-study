(async function () {
  const { esc, getJSON, topbar, footer } = window.AX;
  const v = await getJSON("data/version.json");
  topbar("roadmap", v.current);
  footer(v.current, v.updated);

  // 내려받기 (tools/roadmap/export.cjs 로 만든 downloads/ 파일)
  const fname = (ext) => `downloads/${encodeURIComponent(`AI-로드맵-${v.current}`)}.${ext}`;
  const DL = '<svg viewBox="0 0 16 16" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="M8 2.5v8M4.5 7.5 8 11l3.5-3.5M3 13.5h10"/></svg>';
  document.getElementById("dl").innerHTML =
    `<a class="btn" href="${fname("pdf")}" download>${DL}PDF로 받기</a>
     <a class="btn ghost" href="${fname("docx")}" download>${DL}Word로 받기</a>
     <span class="small muted">${esc(v.current)} · ${esc(v.updated)} 기준 · 도식 포함</span>`;

  // 로드맵 문서 본문 (data/roadmap.html — tools/roadmap 으로 문서에서 생성)
  const doc = document.getElementById("doc");
  try {
    const r = await fetch("data/roadmap.html?v=" + encodeURIComponent(v.current + v.updated));
    if (!r.ok) throw new Error(r.status);
    doc.innerHTML = await r.text();
    const hs = [...doc.querySelectorAll("h2")];
    hs.forEach((h, i) => { if (!h.id) h.id = "sec-" + i; });
    document.getElementById("toc").innerHTML = hs.map((h) => `<a href="#${esc(h.id)}">${esc(h.textContent)}</a>`).join("")
      + `<a href="#versions">버전 기록</a>`;
    doc.querySelectorAll(".rm-fig > svg").forEach((svg) => { const w = document.createElement("div"); w.className = "rm-scroll"; svg.before(w); w.append(svg); });
    doc.querySelectorAll("table").forEach((t) => { const w = document.createElement("div"); w.className = "grid-scroll"; t.before(w); w.append(t); });
  } catch (e) {
    doc.innerHTML = `<div class="empty">로드맵 문서를 불러오지 못했습니다. 파일을 직접 열었다면 <code>python3 -m http.server</code>로 띄워 주세요.</div>`;
  }

  // 버전 기록
  document.getElementById("rules").innerHTML =
    `<div class="eyebrow">버전 규칙</div><h2>현재 버전 ${esc(v.current)} · ${esc(v.updated)}</h2>
     <p class="muted">기준 문서: ${esc(v.roadmap_doc)}</p>
     <ul>${v.rules.map((r) => `<li>${esc(r)}</li>`).join("")}</ul>`;
  document.getElementById("history").innerHTML = v.history.map((h) =>
    `<div class="rel ${h.version === v.current ? "current" : ""}">
       <div class="eyebrow">${esc(h.date)}</div><h2>${esc(h.version)}</h2>
       <ul>${h.changes.map((c) => `<li>${esc(c)}</li>`).join("")}</ul></div>`).join("");
  if (location.hash) { const t = document.getElementById(decodeURIComponent(location.hash.slice(1))); if (t) t.scrollIntoView(); }
})();
