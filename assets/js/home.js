(async function () {
  const AX = window.AX;
  const { esc, getJSON, topbar, footer, pad, ICONS } = AX;
  let cur;
  try {
    cur = await getJSON("data/curriculum.json");
  } catch (e) {
    document.getElementById("parts").innerHTML =
      `<div class="empty">데이터를 불러오지 못했습니다. 파일을 직접 열었다면 <code>python3 -m http.server</code>로 띄워 주세요.</div>`;
    topbar("home", ""); return;
  }
  topbar("home", cur.version);
  footer(cur.version, cur.updated);

  const total = cur.sessions.length;
  document.getElementById("st-total").firstChild.textContent = total;
  function stats() {
    const n = cur.sessions.filter((s) => AX.status(s.id) === "full").length;
    document.getElementById("st-done").textContent = n;
    document.getElementById("st-of").textContent = " / " + total;
    document.getElementById("st-bar").style.width = (n / total * 100) + "%";
  }
  stats();
  document.getElementById("st-ver").textContent = cur.version;
  document.getElementById("st-upd").textContent = cur.updated + " 기준";

  // 필터
  const filters = document.getElementById("filters");
  const state = { part: 0, q: "" };
  const btns = [{ id: 0, title: "전체" }, ...cur.parts].map((p) => {
    const b = document.createElement("button");
    b.className = "filter"; b.type = "button";
    b.textContent = p.id ? `${p.id}. ${p.title}` : p.title;
    b.setAttribute("aria-pressed", p.id === 0 ? "true" : "false");
    b.addEventListener("click", () => {
      state.part = p.id;
      btns.forEach((x) => x.setAttribute("aria-pressed", "false"));
      b.setAttribute("aria-pressed", "true");
      render();
    });
    filters.append(b);
    return b;
  });
  const search = document.createElement("input");
  search.className = "search"; search.type = "search"; search.placeholder = "세션·태그 검색 (예: RAG, Java)";
  search.setAttribute("aria-label", "세션 검색");
  search.addEventListener("input", () => { state.q = search.value.trim().toLowerCase(); render(); });
  filters.append(search);

  // 이전(16주) 사이트 주소 #w3 · #dashboard · #me 를 새 화면으로 연결
  const OLD = {1:1,2:2,3:3,4:4,5:6,6:8,7:9,8:13,9:17,10:18,11:11,12:10,13:21,14:22,15:23,16:24};
  const ow = location.hash.match(/^#w(\d{1,2})$/);
  if (ow && OLD[ow[1]]) { location.replace(`session.html?id=${OLD[ow[1]]}`); return; }
  if (location.hash === "#dashboard") { location.replace("dashboard.html"); return; }
  if (location.hash === "#me") { location.replace("me.html"); return; }

  // 주소에 #part-3 처럼 오면 그 파트만
  const m = location.hash.match(/part-(\d)/);
  if (m) { const b = btns[+m[1]]; if (b) b.click(); }

  function card(s) {
    const st = AX.status(s.id);
    const tags = [`<span class="tag level">${esc(s.level)}</span>`, ...s.tags.map((t) => `<span class="tag">${esc(t)}</span>`)].join("");
    return `<a class="card" href="session.html?id=${s.id}">
      <div class="top"><span class="sess">SESSION ${pad(s.id)}</span>${st === "full" ? '<span class="done-mark">완료</span>' : st === "half" ? '<span class="done-mark half">진행 중</span>' : ""}</div>
      <h3>${esc(s.title)}</h3>
      <p>${esc(s.summary)}</p>
      <div class="tags">${tags}</div>
      <div class="four" aria-label="구성">
        <span>${ICONS.concept}개념</span><span>${ICONS.theory}이론</span><span>${ICONS.lab}실습</span><span>${ICONS.example}예제</span><span>${ICONS.quiz}퀴즈</span>
      </div></a>`;
  }

  function render() {
    const q = state.q;
    const html = cur.parts
      .filter((p) => !state.part || p.id === state.part)
      .map((p) => {
        const list = cur.sessions.filter((s) => s.part === p.id).filter((s) =>
          !q || (s.title + " " + s.summary + " " + s.tags.join(" ")).toLowerCase().includes(q));
        if (!list.length) return "";
        return `<section class="part" id="part-${p.id}">
          <div class="part-head">
            <span class="num">PART ${p.id}</span><h2>${esc(p.title)}</h2>
            <span class="muted">${esc(p.subtitle)}</span>
            <span class="out">산출물 <b>${esc(p.output)}</b></span>
            <p>${esc(p.goal)}</p>
          </div>
          <div class="grid">${list.map(card).join("")}</div></section>`;
      }).join("");
    document.getElementById("parts").innerHTML = html || `<div class="empty">찾는 세션이 없습니다.</div>`;
  }
  render();
  AX.onChange(() => { stats(); render(); });
})();
