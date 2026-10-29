(async function () {
  const AX = window.AX;
  const { esc, rich, getJSON, topbar, footer, pad, SITE } = AX;
  const main = document.getElementById("main");
  const id = parseInt(new URLSearchParams(location.search).get("id") || "1", 10);

  let cur, s;
  try {
    cur = await getJSON("data/curriculum.json");
    s = await getJSON(`data/sessions/s${pad(id)}.json`);
  } catch (e) {
    topbar("", cur ? cur.version : "");
    main.innerHTML = `<div class="empty">세션 ${id}을 불러오지 못했습니다. (${esc(e.message)})<br>
      파일을 직접 열었다면 <code>python3 -m http.server</code>로 띄워 주세요. <a href="index.html">목록으로</a></div>`;
    return;
  }
  // 퀴즈·영상·용어는 없어도 본문은 보이도록 따로 불러옵니다
  const opt = (p) => getJSON(p).catch(() => ({}));
  const [QUIZ, MEDIA, TERMS, GLOSS] = await Promise.all(
    ["data/quiz.json", "data/media.json", "data/terms.json", "data/glossary.json"].map(opt));
  const quiz = QUIZ[id] || [], media = MEDIA[id] || [], terms = (TERMS[id] || []).filter((t) => GLOSS[t]);

  topbar("", cur.version);
  footer(cur.version, cur.updated);
  const part = cur.parts.find((p) => p.id === s.part) || {};
  document.title = `${pad(s.id)}. ${s.title} · AX Study`;

  const P = (arr) => (arr || []).map((t) => `<p>${rich(t)}</p>`).join("");
  const UL = (arr, cls) => arr && arr.length ? `<ul${cls ? ` class="${cls}"` : ""}>${arr.map((t) => `<li>${rich(t)}</li>`).join("")}</ul>` : "";
  const OL = (arr) => arr && arr.length ? `<ol class="flow">${arr.map((t) => `<li>${rich(t)}</li>`).join("")}</ol>` : "";
  const fig = (d) => d && d.src ? `<figure class="diagram"><a href="${esc(d.src)}" target="_blank" rel="noopener" title="새 창에서 크게 보기">
      <img src="${esc(d.src)}" alt="${esc(d.caption || "")}"></a>
      ${d.caption ? `<figcaption>${esc(d.caption)}</figcaption>` : ""}</figure>` : "";
  let codeN = 0;
  const code = (c) => {
    if (!c || !c.src) return "";
    const lang = (c.lang || "text").toLowerCase();
    const n = codeN++;
    return `<div class="code"><div class="bar"><span>${esc(c.file || lang)}</span>
      <button type="button" data-copy="${n}">복사</button></div>
      <pre><code class="language-${esc(lang)}" id="code-${n}">${esc(c.src)}</code></pre></div>`;
  };

  // ── 1. 개념 ──
  const c = s.concept || {};
  const concept = `
    <div class="eyebrow">01 · 개념</div><h2>무엇인가</h2>
    ${c.lead ? `<div class="callout">${rich(c.lead)}</div>` : ""}
    ${P(c.body)}
    ${fig(c.diagram)}
    ${c.terms && c.terms.length ? `<h3>핵심 용어</h3><div class="terms">${c.terms.map((t) =>
      `<div class="term"><b>${esc(t.term)}</b><span>${rich(t.desc)}</span></div>`).join("")}</div>` : ""}`;

  // ── 2. 이론 ──
  const t = s.theory || {};
  const theory = `
    <div class="eyebrow">02 · 이론</div><h2>어떻게 동작하나</h2>
    ${t.lead ? `<div class="callout">${rich(t.lead)}</div>` : ""}
    ${fig(t.diagram)}
    ${(t.sections || []).map((x) => `<h3>${esc(x.title)}</h3>${P(x.body)}${UL(x.bullets)}${code(x.code)}`).join("")}`;

  // ── 3. 실습 코드 ──
  const l = s.lab || {};
  const lab = `
    <div class="eyebrow">03 · 실습 코드</div><h2>${esc(l.title || "직접 만들어 보기")}</h2>
    ${l.lead ? `<p>${rich(l.lead)}</p>` : ""}
    ${l.env && l.env.length ? `<div class="env">${l.env.map((e) => `<span class="tag">${esc(e)}</span>`).join("")}</div>` : ""}
    ${(l.steps || []).map((st, i) => `<div class="step">
        <div class="step-title"><span class="no">${i + 1}</span><h3 style="margin:0">${esc(st.title)}</h3></div>
        ${P(Array.isArray(st.body) ? st.body : [st.body].filter(Boolean))}${code(st.code)}</div>`).join("")}
    ${l.check && l.check.length ? `<h3>확인 포인트</h3>${UL(l.check)}` : ""}
    <p class="small muted">실습 파일은 저장소의 <code>labs/s${pad(s.id)}/</code> 폴더에도 있습니다.</p>`;

  // ── 4. 실무 예제 ──
  const x = s.example || {};
  const example = `
    <div class="eyebrow">04 · 실무 예제</div><h2>${esc(x.title || "현업에서는 이렇게")}</h2>
    ${x.context ? `<div class="callout">${rich(x.context)}</div>` : ""}
    ${x.flow && x.flow.length ? `<h3>진행 순서</h3>${OL(x.flow)}` : ""}
    ${fig(x.diagram)}
    ${code(x.code)}
    ${(x.tips && x.tips.length) || (x.pitfalls && x.pitfalls.length) ? `<div class="cols">
      ${x.tips && x.tips.length ? `<div class="box good"><h4>실무 팁</h4>${UL(x.tips)}</div>` : ""}
      ${x.pitfalls && x.pitfalls.length ? `<div class="box bad"><h4>자주 하는 실수</h4>${UL(x.pitfalls)}</div>` : ""}</div>` : ""}
    ${s.refs && s.refs.length ? `<h3>더 읽을거리</h3><ul class="refs">${s.refs.map((r) =>
      `<li><a href="${esc(r.url)}" target="_blank" rel="noopener">${esc(r.label)}</a></li>`).join("")}</ul>` : ""}`;

  // ── 5. 퀴즈·자료 ──
  const kind = (m) => m.type === "video" ? ["v", "영상"] : m.type === "course" ? ["c", "강좌"] : ["a", "글"];
  const domain = (u) => { try { return new URL(u).hostname.replace(/^www\./, ""); } catch (e) { return ""; } };
  const extra = `
    <div class="eyebrow">05 · 퀴즈·자료</div><h2>확인 퀴즈</h2>
    ${quiz.length ? `<form class="quiz" id="quizForm">
      ${quiz.map((q, i) => `<div class="q"><fieldset><legend><small>Q${i + 1}</small>${esc(q.q)}</legend>
        ${q.options.map((o, j) => `<label class="opt"><input type="radio" name="q${i}" value="${j}"><span>${esc(o)}</span></label>`).join("")}
        </fieldset><p class="why" hidden></p></div>`).join("")}
      <div class="qbar"><button class="btn" type="submit">채점하기</button><button class="btn ghost" type="button" id="retry" hidden>다시 풀기</button><span class="score" aria-live="polite"></span></div>
    </form>` : `<p class="muted">이 세션의 퀴즈는 준비 중입니다.</p>`}
    <h3>영상·자료</h3>
    ${media.length ? `<ul class="media">${media.map((m) => { const [k, kl] = kind(m); return `<li><a href="${esc(m.url)}" target="_blank" rel="noopener">
      <span class="kind ${k}">${kl}</span><span><b>${esc(m.title)}</b><small>${esc(m.channel || "")} · ${m.lang === "ko" ? "한국어" : "영어"} · ${esc(domain(m.url))}</small></span></a></li>`; }).join("")}</ul>`
      : `<p class="muted">추가 자료는 '실무 예제' 탭의 더 읽을거리를 보세요.</p>`}
    ${terms.length ? `<h3>관련 용어</h3><div class="gterms">${terms.map((tid) =>
      `<a href="${esc(SITE.glossaryUrl)}#${esc(tid)}" target="_blank" rel="noopener" title="${esc(GLOSS[tid].easy)}">${esc(GLOSS[tid].ko)}<small>${esc(GLOSS[tid].en)}</small></a>`).join("")}</div>
      <p class="small muted">실무 AI 용어집에서 입문·심화 설명과 출처를 볼 수 있습니다.</p>` : ""}`;

  const panels = [
    { key: "concept", label: "개념", html: concept },
    { key: "theory", label: "이론", html: theory },
    { key: "lab", label: "실습 코드", html: lab },
    { key: "example", label: "실무 예제", html: example },
    { key: "quiz", label: "퀴즈·자료", html: extra }
  ];

  const prev = cur.sessions.find((q) => q.id === s.id - 1);
  const next = cur.sessions.find((q) => q.id === s.id + 1);

  main.innerHTML = `
    <div class="crumb"><a href="index.html">커리큘럼</a> / <a href="index.html#part-${part.id}">PART ${part.id} ${esc(part.title || "")}</a></div>
    <header class="s-head">
      <div class="eyebrow">SESSION ${pad(s.id)} · ${esc(part.subtitle || "")}</div>
      <h1 class="display">${esc(s.title)}</h1>
      <p class="lead muted" style="margin:0">${esc(s.summary)}</p>
      <div class="s-meta">
        <span class="tag level">${esc(s.level)}</span>
        ${s.duration ? `<span class="tag">${esc(s.duration)}</span>` : ""}
        ${(s.tags || []).map((g) => `<span class="tag">${esc(g)}</span>`).join("")}
        <button class="share" type="button" id="share" title="카카오톡·메신저에 붙여 넣으면 이 세션 미리보기가 보입니다">
          <svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="M6.5 9.5 9.5 6.5M7 4.5l1.3-1.3a2.5 2.5 0 0 1 3.5 3.5L10.5 8M9 11.5l-1.3 1.3a2.5 2.5 0 0 1-3.5-3.5L5.5 8"/></svg>공유 링크 복사</button>
      </div>
    </header>
    <section class="goals">
      <div><h3>이번 세션 목표</h3>${UL(s.goals)}</div>
      ${s.output ? `<div class="output"><b>산출물</b>${rich(s.output)}</div>` : ""}
    </section>
    <div class="track" id="track"></div>
    <div class="tabs"><div class="row" role="tablist" aria-label="세션 구성">
      ${panels.map((p, i) => `<button class="tab" role="tab" id="tab-${p.key}" aria-controls="panel-${p.key}"
        aria-selected="${i === 0}" tabindex="${i === 0 ? 0 : -1}"><span class="n">0${i + 1}</span>${p.label}</button>`).join("")}
    </div></div>
    ${panels.map((p, i) => `<section class="panel" role="tabpanel" id="panel-${p.key}" aria-labelledby="tab-${p.key}" ${i ? "hidden" : ""}>${p.html}</section>`).join("")}
    <nav class="pager" aria-label="이전·다음 세션">
      ${prev ? `<a href="session.html?id=${prev.id}"><span class="small muted">이전</span><br><b>${pad(prev.id)}. ${esc(prev.title)}</b></a>` : "<span></span>"}
      ${next ? `<a class="next" href="session.html?id=${next.id}"><span class="small muted">다음</span><br><b>${pad(next.id)}. ${esc(next.title)}</b></a>`
        : `<a class="next" href="dashboard.html"><span class="small muted">마지막 세션</span><br><b>대시보드 보기</b></a>`}
    </nav>`;

  // ── 진도 표시 (개념·이론 / 실습 / 퀴즈) ──
  function renderTrack() {
    const p = AX.prog()[s.id] || {};
    document.getElementById("track").innerHTML = `
      <button class="chk" type="button" data-track="notes" aria-pressed="${!!p.notes}"><span class="cb"></span>개념·이론 학습 완료</button>
      <button class="chk" type="button" data-track="lab" aria-pressed="${!!p.lab}"><span class="cb"></span>실습 완료</button>
      <span class="note">${p.score != null ? `퀴즈 최고 ${p.score}/${p.total}` : "퀴즈 미응시"} · ${AX.syncHint()}</span>`;
    document.querySelectorAll("[data-track]").forEach((b) => b.addEventListener("click", () => {
      const k = b.dataset.track;
      AX.mark(s.id, { [k]: !(AX.prog()[s.id] || {})[k] });
    }));
  }
  renderTrack();

  // 공유 링크: s/NN.html 은 세션별 미리보기(제목·요약·이미지)를 담고 이 화면으로 이동합니다
  document.getElementById("share").addEventListener("click", async () => {
    const url = new URL(`s/${pad(s.id)}.html`, location.href).href;
    const text = `${pad(s.id)}. ${s.title} · AX Study`;
    if (navigator.share && matchMedia("(pointer: coarse)").matches) {
      try { await navigator.share({ title: text, text: s.summary, url }); return; } catch (e) { if (e.name === "AbortError") return; }
    }
    try { await navigator.clipboard.writeText(url); AX.toast("링크를 복사했습니다. 카카오톡에 붙여 넣으면 미리보기가 나옵니다"); }
    catch (e) { AX.toast("복사가 막혀 있습니다. 이 주소를 직접 복사하세요: " + url); }
  });
  AX.onChange(renderTrack);

  // ── 탭 전환 (#lab 처럼 주소로 바로 열기) ──
  const tabs = [...main.querySelectorAll(".tab")];
  function show(key, focus) {
    tabs.forEach((b) => {
      const on = b.id === "tab-" + key;
      b.setAttribute("aria-selected", on); b.tabIndex = on ? 0 : -1;
      document.getElementById(b.getAttribute("aria-controls")).hidden = !on;
      if (on && focus) b.focus();
    });
    history.replaceState(null, "", "#" + key);
  }
  tabs.forEach((b, i) => {
    b.addEventListener("click", () => show(b.id.slice(4)));
    b.addEventListener("keydown", (e) => {
      if (e.key !== "ArrowRight" && e.key !== "ArrowLeft") return;
      const j = (i + (e.key === "ArrowRight" ? 1 : -1) + tabs.length) % tabs.length;
      show(tabs[j].id.slice(4), true);
    });
  });
  const h = location.hash.slice(1);
  if (panels.some((p) => p.key === h)) show(h);

  // ── 복사 버튼 ──
  main.addEventListener("click", async (e) => {
    const btn = e.target.closest("button[data-copy]");
    if (!btn) return;
    const txt = document.getElementById("code-" + btn.dataset.copy).textContent;
    try { await navigator.clipboard.writeText(txt); btn.textContent = "복사됨"; }
    catch (err) { btn.textContent = "선택해서 복사"; }
    setTimeout(() => (btn.textContent = "복사"), 1500);
  });

  // ── 퀴즈 ──
  const form = document.getElementById("quizForm");
  if (form) {
    form.addEventListener("submit", (e) => {
      e.preventDefault();
      const answers = quiz.map((q, i) => { const v = form.querySelector(`input[name="q${i}"]:checked`); return v ? +v.value : null; });
      const left = answers.filter((a) => a === null).length;
      if (left) { form.querySelector(".score").textContent = `${left}문항이 남았습니다. 모두 고른 뒤 채점하세요.`; return; }
      let score = 0;
      quiz.forEach((q, i) => {
        const box = form.querySelectorAll(".q")[i], ok = answers[i] === q.answer;
        if (ok) score++;
        box.querySelectorAll(".opt").forEach((o, j) => { o.classList.toggle("right", j === q.answer); o.classList.toggle("wrong", j === answers[i] && !ok); });
        box.querySelectorAll("input").forEach((x) => (x.disabled = true));
        const why = box.querySelector(".why"); why.hidden = false;
        why.innerHTML = `<b class="${ok ? "ok" : "no"}">${ok ? "정답" : "오답"}</b> ${esc(q.explain)}`;
      });
      const best = Math.max(score, (AX.prog()[s.id] || {}).score ?? 0);
      form.querySelector(".score").textContent = `${score}/${quiz.length} 정답${best > score ? ` (최고 ${best}/${quiz.length})` : ""}`;
      form.querySelector("[type=submit]").disabled = true; document.getElementById("retry").hidden = false;
      AX.mark(s.id, { score, total: quiz.length });
    });
    document.getElementById("retry").addEventListener("click", () => {
      form.reset();
      form.querySelectorAll("input").forEach((x) => (x.disabled = false));
      form.querySelectorAll(".opt").forEach((o) => o.classList.remove("right", "wrong"));
      form.querySelectorAll(".why").forEach((w) => (w.hidden = true));
      form.querySelector("[type=submit]").disabled = false; document.getElementById("retry").hidden = true;
      form.querySelector(".score").textContent = "";
    });
  }

  if (window.Prism) Prism.highlightAll();
})();
