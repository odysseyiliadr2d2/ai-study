/* AI Tech 스터디 학습 사이트 — 정적 SPA (GitHub Pages) */
"use strict";

const SB = { url: "https://keqatughczjqhnubkydy.supabase.co", key: "sb_publishable_DlZV7muab9bvGUiUNWh3gA_RTX9Y6MW" };
const GLOSSARY_URL = "https://odysseyiliadr2d2.github.io/ai-glossary/#";

const STAGES = [
  { n: 1, name: "기초", title: "AI가 어떻게 동작하는가", out: "업무용 프롬프트 템플릿 5종" },
  { n: 2, name: "활용", title: "개발자로서 AI 쓰기", out: "사내 문서 Q&A 미니 RAG" },
  { n: 3, name: "심화", title: "에이전트와 업무 자동화", out: "업무 자동화 에이전트 프로토타입" },
  { n: 4, name: "전문·사업화", title: "AX를 실제로 하려면", out: "팀별 AX 제안서" }
];
const WEEKS = (window.WEEKS || []).slice().sort((a, b) => a.week - b.week);
const byWeek = new Map(WEEKS.map(w => [w.week, w]));
const G = window.GLOSSARY || {};
const MEDIA = window.MEDIA || {};

const $ = (s, el = document) => el.querySelector(s);
const esc = s => String(s ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const cvar = n => `var(--s${n})`;

/* ---------- 저장소 ---------- */
const store = {
  get(k, d) { try { const v = localStorage.getItem(k); return v ? JSON.parse(v) : d; } catch (e) { return d; } },
  set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) {} },
  del(k) { try { localStorage.removeItem(k); } catch (e) {} }
};
store.del("aistudy.user");                            // 예전 PIN 방식 기록 정리
let prog = store.get("aistudy.prog", {});            // { [week]: {notes, lab, score, total} }
const saveProg = () => store.set("aistudy.prog", prog);

/* ---------- 로그인 (Supabase Auth + 카카오) ---------- */
const sb = window.supabase.createClient(SB.url, SB.key, {
  auth: { flowType: "pkce", detectSessionInUrl: true, persistSession: true, autoRefreshToken: true }
});
let session = null;   // 카카오 로그인 세션
let me = null;        // study_members 본인 행 { id, name, org, status, is_admin }
let user = null;      // 승인된 멤버일 때만 me와 같음 (진도 저장·대시보드 조건)

function kakaoName() {
  const m = (session && session.user && session.user.user_metadata) || {};
  return (m.name || m.full_name || m.nickname || m.user_name || m.preferred_username || "").slice(0, 20);
}
async function loadMe() {
  me = null; user = null;
  if (!session) return;
  const { data, error } = await sb.from("study_members").select("id,name,org,status,is_admin").maybeSingle();
  if (!error && data) me = data;
  user = me && me.status === "approved" ? me : null;
}
async function loginKakao() {
  store.set("aistudy.after", location.hash || "#me");
  const { error } = await sb.auth.signInWithOAuth({
    provider: "kakao",
    options: { redirectTo: location.origin + location.pathname }
  });
  if (error) toast("카카오 로그인을 시작하지 못했습니다: " + error.message);
}
async function logout() {
  await sb.auth.signOut();
  session = null; me = null; user = null;
  toast("로그아웃했습니다. 이 브라우저의 진도 기록은 남아 있습니다.");
  renderNav(); render();
}

async function rpc(fn, body = {}) {
  const { data, error } = await sb.rpc(fn, body);
  if (error) throw new Error(error.message || "요청 실패");
  return data;
}

async function pushWeek(week, patch) {
  if (!user) return;
  try {
    await rpc("study_save", {
      p_week: week, p_notes: patch.notes ?? null, p_lab: patch.lab ?? null,
      p_quiz_score: patch.score ?? null, p_quiz_total: patch.total ?? null
    });
    toast("대시보드에 저장했습니다");
  } catch (e) { toast("서버 저장 실패: " + e.message + " (이 브라우저에는 저장됨)"); }
}

/* 승인된 뒤: 이 브라우저 기록을 올리고, 서버 기록을 내려받아 합침 */
async function syncMine() {
  if (!user) return;
  for (const [w, p] of Object.entries(prog)) {
    if (!(p.notes || p.lab || p.score != null)) continue;
    await rpc("study_save", { p_week: +w, p_notes: p.notes ? true : null, p_lab: p.lab ? true : null,
      p_quiz_score: p.score ?? null, p_quiz_total: p.total ?? null }).catch(() => {});
  }
  const { data } = await sb.from("study_progress").select("week,notes_done,lab_done,quiz_score,quiz_total");
  for (const r of data || []) {
    const p = prog[r.week] || {};
    const score = Math.max(p.score ?? -1, r.quiz_score ?? -1);
    prog[r.week] = { notes: !!(p.notes || r.notes_done), lab: !!(p.lab || r.lab_done), total: r.quiz_total ?? p.total };
    if (score >= 0) prog[r.week].score = score;
  }
  saveProg();
}
function syncHint() {
  if (user) return "";
  if (me && me.status === "pending") return " · 운영자 승인 후 대시보드에 반영됩니다";
  return ' · <a href="#me">카카오로 로그인</a>하면 대시보드에 반영됩니다';
}

/* ---------- 진도 상태 ---------- */
function status(w) {
  const p = prog[w] || {};
  if (p.notes && p.lab) return "full";
  if (p.notes || p.lab || p.score != null) return "half";
  return "none";
}
const doneCount = () => WEEKS.filter(w => status(w.week) === "full").length;

/* ---------- 본문 렌더러 ---------- */
function inline(s) {
  let h = esc(s);
  h = h.replace(/`([^`]+)`/g, "<code>$1</code>");
  h = h.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
  h = h.replace(/\[\[([a-z0-9-]+)\|([^\]]+)\]\]/g, (m, id, label) => {
    const g = G[id];
    return `<a class="term" href="${GLOSSARY_URL}${id}" target="_blank" rel="noopener" title="${esc(g ? g.easy : "")}">${label}</a>`;
  });
  h = h.replace(/\[([^\]]+)\]\((https?:[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>');
  return h;
}
function md(src) {
  const codes = [];
  src = src.replace(/```(\w*)\n([\s\S]*?)```/g, (m, lang, code) => { codes.push(code.replace(/\n$/, "")); return `\u0000${codes.length - 1}\u0000`; });
  const out = [];
  for (const block of src.split(/\n\s*\n/)) {
    const lines = block.split("\n").filter(l => l.trim() !== "");
    let i = 0;
    while (i < lines.length) {
      const l = lines[i];
      const code = l.match(/^\u0000(\d+)\u0000$/);
      if (code) { out.push(`<pre><button class="copy" type="button">복사</button><code>${esc(codes[+code[1]])}</code></pre>`); i++; continue; }
      const take = re => { const g = []; while (i < lines.length && re.test(lines[i])) g.push(lines[i++]); return g; };
      if (/^- /.test(l)) { out.push("<ul>" + take(/^- /).map(x => `<li>${inline(x.slice(2))}</li>`).join("") + "</ul>"); continue; }
      if (/^\d+\. /.test(l)) { out.push("<ol>" + take(/^\d+\. /).map(x => `<li>${inline(x.replace(/^\d+\. /, ""))}</li>`).join("") + "</ol>"); continue; }
      if (/^> /.test(l)) { out.push("<blockquote>" + take(/^> /).map(x => inline(x.slice(2))).join(" ") + "</blockquote>"); continue; }
      if (/^\|/.test(l)) {
        const rows = take(/^\|/).filter(r => !/^\|[\s:-]+\|/.test(r)).map(r => r.replace(/^\||\|$/g, "").split("|").map(c => c.trim()));
        out.push("<table><thead><tr>" + rows[0].map(c => `<th>${inline(c)}</th>`).join("") + "</tr></thead><tbody>" +
          rows.slice(1).map(r => "<tr>" + r.map(c => `<td>${inline(c)}</td>`).join("") + "</tr>").join("") + "</tbody></table>");
        continue;
      }
      const para = take(/^(?!- |\d+\. |> |\||\u0000)/);
      if (para.length) out.push(`<p>${inline(para.join(" "))}</p>`); else i++;
    }
  }
  return out.join("\n");
}

/* ---------- 노선도 ---------- */
function wrapTitle(t, max = 9) {
  const parts = t.split(/(?<=[ ·])/);
  const lines = [""];
  for (const p of parts) {
    if ((lines[lines.length - 1] + p).trim().length > max && lines[lines.length - 1]) lines.push(p.trimStart());
    else lines[lines.length - 1] += p;
  }
  return lines.map(s => s.trim()).slice(0, 2);
}
function routeSVG() {
  const W = 1120, x0 = 72, step = 134, y1 = 66, y2 = 252;
  const pos = w => w <= 8 ? [x0 + (w - 1) * step, y1] : [x0 + (16 - w) * step, y2];
  const stageOf = w => byWeek.get(w)?.stage || Math.ceil(w / 4);
  let lines = "", stations = "", labels = "";
  for (let w = 1; w < 16; w++) {
    const [ax, ay] = pos(w), [bx, by] = pos(w + 1);
    const c = cvar(stageOf(w + 1));
    if (w === 8) {
      const r = (y2 - y1) / 2;
      lines += `<path class="run" d="M${ax} ${ay} H${ax + 10} A${r} ${r} 0 0 1 ${ax + 10} ${by} H${bx}" fill="none" stroke="${c}" stroke-width="10" stroke-linecap="round"/>`;
    } else {
      lines += `<path class="run" d="M${ax} ${ay} L${bx} ${by}" fill="none" stroke="${c}" stroke-width="10" stroke-linecap="round"/>`;
    }
  }
  for (const s of STAGES) {
    const first = WEEKS.find(w => w.stage === s.n);
    if (!first) continue;
    const [x, y] = pos(first.week);
    const anchor = first.week <= 8 ? "start" : "end";
    labels += `<text class="lbl" x="${x - (anchor === "start" ? 14 : -14)}" y="${y - 34}" text-anchor="${anchor}" fill="${cvar(s.n)}">${s.n}단계 ${esc(s.name)}</text>`;
  }
  for (const wk of WEEKS) {
    const [x, y] = pos(wk.week), c = cvar(wk.stage), st = status(wk.week);
    const fill = st === "full" ? c : "var(--surface)";
    const inner = st === "half" ? `<circle cx="${x}" cy="${y}" r="5.5" fill="${c}"/>` : "";
    const tl = wrapTitle(wk.title);
    const stLabel = { full: "완료", half: "진행 중", none: "시작 전" }[st];
    stations += `<g class="st" tabindex="0" role="link" data-go="w${wk.week}" aria-label="${wk.week}주 ${esc(wk.title)}, ${stLabel}">
      <circle cx="${x}" cy="${y}" r="26" fill="transparent"/>
      <circle class="ring" cx="${x}" cy="${y}" r="13" fill="${fill}" stroke="${c}" stroke-width="5"/>${inner}
      <text class="wk" x="${x}" y="${y + 38}" text-anchor="middle">${wk.week}주</text>
      ${tl.map((t, i) => `<text class="nm" x="${x}" y="${y + 58 + i * 19}" text-anchor="middle">${esc(t)}</text>`).join("")}
    </g>`;
  }
  return `<svg viewBox="0 0 ${W} 345" role="img" aria-label="16주 학습 노선도">${lines}${labels}${stations}</svg>`;
}
function routeList() {
  let h = "";
  for (const s of STAGES) {
    const ws = WEEKS.filter(w => w.stage === s.n);
    h += `<li style="--c:${cvar(s.n)}" class="stage-li"><div class="stage-h">${s.n}단계 ${esc(s.name)}</div></li>`;
    h += ws.map(w => `<li style="--c:${cvar(s.n)}"><a href="#w${w.week}"><small>${w.week}주</small>${esc(w.title)}</a><span class="dot ${status(w.week)}"></span></li>`).join("");
  }
  return `<ol class="vmap">${h}</ol>`;
}

/* ---------- 화면: 홈 ---------- */
function viewHome() {
  const done = doneCount();
  return `
  <div class="wrap">
    <div class="intro">
      <div>
        <h1>16주 AI 노선도</h1>
        <p>개념부터 에이전트, 폐쇄망 LLM, AX 사업화까지 4단계 16주 과정입니다. 역을 누르면 그 주의 강의 노트, 실습, 영상, 퀴즈로 이동합니다.</p>
      </div>
      <div class="mine"><b>${done}/16</b>${user ? `${esc(user.name)} 님 완료 주차` : "이 브라우저의 완료 주차"}</div>
    </div>
    <div class="map">
      ${routeSVG()}
      ${routeList()}
      <div class="map-legend">
        <span><i class="dot"></i>시작 전</span>
        <span><i class="dot half"></i>진행 중 (노트·실습·퀴즈 중 일부)</span>
        <span><i class="dot full"></i>완료 (강의 노트와 실습 모두)</span>
      </div>
    </div>
    <div class="stages">
      ${STAGES.map(s => `
      <section class="stage" style="--c:${cvar(s.n)}">
        <h2>${s.n}단계 · ${esc(s.name)}</h2>
        <p class="goal">${esc(s.title)}</p>
        <ol>${WEEKS.filter(w => w.stage === s.n).map(w => `<li><a href="#w${w.week}"><small>${w.week}주</small><span>${esc(w.title)}</span></a></li>`).join("")}</ol>
        <p class="out">단계 산출물 <b>${esc(s.out)}</b></p>
      </section>`).join("")}
    </div>
    <div class="howto">
      <div><h3>매주 진행</h3>발표 40분, 실습 60분, 토론 20분. 시작 10분은 AI 테크 데일리 뉴스레터에서 고른 뉴스 3개를 나눕니다.</div>
      <div><h3>진도 기록</h3><a href="#me">내 진도</a>에서 카카오로 로그인하고 운영자 승인을 받으면 강의 노트·실습·퀴즈 기록이 <a href="#dashboard">대시보드</a>에 모입니다.</div>
      <div><h3>용어가 막히면</h3>본문의 점선 밑줄 용어를 누르면 <a href="${GLOSSARY_URL.slice(0, -1)}" target="_blank" rel="noopener">실무 AI 용어집</a>의 해당 문서가 열립니다.</div>
    </div>
  </div>`;
}

/* ---------- 화면: 주차 ---------- */
function viewWeek(wk) {
  const c = cvar(wk.stage), stage = STAGES[wk.stage - 1], p = prog[wk.week] || {};
  const siblings = WEEKS.filter(w => w.stage === wk.stage);
  const prev = byWeek.get(wk.week - 1), next = byWeek.get(wk.week + 1);
  const media = MEDIA[wk.week] || [];
  const kind = m => m.type === "video" ? ["v", "영상"] : m.type === "course" ? ["", "강좌"] : ["", "글"];
  const domain = u => { try { return new URL(u).hostname.replace(/^www\./, ""); } catch (e) { return ""; } };
  return `
  <div class="wrap week" style="--c:${c}">
    <aside class="rail">
      <a class="back" href="#home">← 노선도</a>
      <ol class="mini">${siblings.map(w => `<li class="${w.week === wk.week ? "cur" : ""}"><a href="#w${w.week}" ${w.week === wk.week ? 'aria-current="page"' : ""}>${w.week}주 ${esc(w.title)}</a><span class="dot ${status(w.week)}"></span></li>`).join("")}</ol>
      <nav class="toc" aria-label="이 페이지">
        <a href="#goals" data-anchor>학습 목표</a><a href="#notes" data-anchor>강의 노트</a><a href="#lab" data-anchor>실습 과제</a>
        <a href="#media" data-anchor>영상·자료</a><a href="#terms" data-anchor>관련 용어</a><a href="#quiz" data-anchor>확인 퀴즈</a>
      </nav>
    </aside>
    <article class="article">
      <header class="whead">
        <span class="line"><i></i>${wk.stage}단계 ${esc(stage.name)} · ${wk.week}주차</span>
        <h1 class="page-title">${esc(wk.title)}</h1>
        <p>${esc(wk.summary)}</p>
      </header>
      <div class="track">
        <button class="chk" type="button" data-track="notes" aria-pressed="${!!p.notes}"><span class="box"></span>강의 노트 읽음</button>
        <button class="chk" type="button" data-track="lab" aria-pressed="${!!p.lab}"><span class="box"></span>실습 완료</button>
        <span class="note">${p.score != null ? `퀴즈 최고 ${p.score}/${p.total}` : "퀴즈 미응시"}${syncHint()}</span>
      </div>

      <section class="sec" id="goals"><h2>학습 목표</h2><ul class="goals">${wk.goals.map(g => `<li>${esc(g)}</li>`).join("")}</ul></section>

      <section class="sec note" id="notes"><h2>강의 노트</h2>
        ${wk.sections.map(s => `<h3>${esc(s.h)}</h3>${md(s.body)}`).join("")}
      </section>

      <section class="sec" id="lab"><h2>실습 과제</h2>
        <div class="lab"><h3>${esc(wk.lab.title)}</h3>
          <ol>${wk.lab.steps.map(s => `<li>${inline(s)}</li>`).join("")}</ol>
          <div class="deliv"><b>제출물</b> ${esc(wk.lab.deliverable)}</div>
        </div>
      </section>

      <section class="sec" id="media"><h2>영상·자료</h2>
        ${media.length ? `<ul class="media">${media.map(m => { const [k, kl] = kind(m); return `<li><a href="${esc(m.url)}" target="_blank" rel="noopener">
          <span class="kind ${k}">${kl}</span><span><b>${esc(m.title)}</b><small>${esc(m.channel)} · ${m.lang === "ko" ? "한국어" : "영어"} · ${esc(domain(m.url))}</small></span></a></li>`; }).join("")}</ul>` : `<p class="empty">이 주차의 자료는 준비 중입니다.</p>`}
      </section>

      <section class="sec" id="terms"><h2>관련 용어</h2>
        <div class="terms">${(wk.terms || []).filter(id => G[id]).map(id => `<a href="${GLOSSARY_URL}${id}" target="_blank" rel="noopener" title="${esc(G[id].easy)}">${esc(G[id].ko)}<small>${esc(G[id].en)}</small></a>`).join("")}</div>
        <p class="terms-note">실무 AI 용어집에서 입문·심화 설명과 출처를 볼 수 있습니다.</p>
      </section>

      <section class="sec" id="quiz"><h2>확인 퀴즈</h2>
        <form class="quiz" id="quizForm">
          ${wk.quiz.map((q, i) => `
          <div class="q"><fieldset><legend><small>Q${i + 1}</small>${esc(q.q)}</legend>
            ${q.options.map((o, j) => `<label class="opt"><input type="radio" name="q${i}" value="${j}"><span>${esc(o)}</span></label>`).join("")}
          </fieldset><p class="why" hidden></p></div>`).join("")}
          <div class="qbar"><button class="btn" type="submit">채점하기</button><button class="btn ghost" type="button" id="retry" hidden>다시 풀기</button><span class="score" aria-live="polite"></span></div>
        </form>
      </section>

      <nav class="pager" aria-label="주차 이동">
        ${prev ? `<a href="#w${prev.week}"><small>← ${prev.week}주</small><b>${esc(prev.title)}</b></a>` : "<span></span>"}
        ${next ? `<a href="#w${next.week}"><small>${next.week}주 →</small><b>${esc(next.title)}</b></a>` : `<a href="#dashboard"><small>마지막 주차</small><b>대시보드 보기</b></a>`}
      </nav>
    </article>
  </div>`;
}
function bindWeek(wk) {
  document.querySelectorAll("[data-track]").forEach(b => b.addEventListener("click", () => {
    const k = b.dataset.track, p = prog[wk.week] = prog[wk.week] || {};
    p[k] = !p[k]; saveProg();
    b.setAttribute("aria-pressed", p[k]);
    document.querySelectorAll(".mini li.cur .dot").forEach(d => d.className = "dot " + status(wk.week));
    pushWeek(wk.week, { [k]: p[k] });
  }));
  const form = $("#quizForm");
  form.addEventListener("submit", e => {
    e.preventDefault();
    const answers = wk.quiz.map((q, i) => { const v = form.querySelector(`input[name="q${i}"]:checked`); return v ? +v.value : null; });
    if (answers.includes(null)) { $(".score", form).textContent = `${answers.filter(a => a === null).length}문항이 남았습니다. 모두 고른 뒤 채점하세요.`; return; }
    let score = 0;
    wk.quiz.forEach((q, i) => {
      const box = form.querySelectorAll(".q")[i], ok = answers[i] === q.answer;
      if (ok) score++;
      box.querySelectorAll(".opt").forEach((o, j) => { o.classList.toggle("right", j === q.answer); o.classList.toggle("wrong", j === answers[i] && !ok); });
      box.querySelectorAll("input").forEach(x => x.disabled = true);
      const why = $(".why", box); why.hidden = false;
      why.innerHTML = `<b class="${ok ? "ok" : "no"}">${ok ? "정답" : "오답"}</b> ${esc(q.explain)}`;
    });
    const total = wk.quiz.length, p = prog[wk.week] = prog[wk.week] || {};
    const best = Math.max(score, p.score ?? 0);
    p.score = best; p.total = total; saveProg();
    $(".score", form).textContent = `${score}/${total} 정답${best > score ? ` (최고 ${best}/${total})` : ""}`;
    form.querySelector("[type=submit]").disabled = true; $("#retry").hidden = false;
    $(".track .note").firstChild && ($(".track .note").innerHTML = `퀴즈 최고 ${best}/${total}` + syncHint());
    pushWeek(wk.week, { score, total });
  });
  $("#retry").addEventListener("click", () => {
    form.reset();
    form.querySelectorAll("input").forEach(x => x.disabled = false);
    form.querySelectorAll(".opt").forEach(o => o.classList.remove("right", "wrong"));
    form.querySelectorAll(".why").forEach(w => w.hidden = true);
    form.querySelector("[type=submit]").disabled = false; $("#retry").hidden = true; $(".score", form).textContent = "";
  });
  document.querySelectorAll(".note pre .copy").forEach(b => b.addEventListener("click", async () => {
    try { await navigator.clipboard.writeText(b.nextElementSibling.textContent); b.textContent = "복사됨"; }
    catch (e) { b.textContent = "복사 실패"; }
    setTimeout(() => b.textContent = "복사", 1500);
  }));
}

/* ---------- 화면: 대시보드 ---------- */
function viewDashboard() {
  return `<div class="wrap">
    <div class="dhead"><div><h1>스터디 대시보드</h1><p>승인된 멤버의 주차별 강의 노트·실습·퀴즈 기록입니다.</p></div>
      <button class="btn ghost" type="button" id="reload">새로고침</button></div>
    <div id="dash"><p class="notice">불러오는 중…</p></div>
  </div>`;
}
async function loadDashboard() {
  const el = $("#dash");
  if (!session) { el.innerHTML = `<div class="notice">대시보드는 승인된 스터디 멤버만 볼 수 있습니다.<div style="margin-top:12px">${kakaoBtn()}</div></div>`; bindKakao(el); return; }
  if (!user) { el.innerHTML = `<p class="notice">${me && me.status === "pending" ? "가입 신청이 접수됐습니다. 운영자가 승인하면 대시보드를 볼 수 있습니다." : me && me.status === "rejected" ? "가입 신청이 승인되지 않았습니다. 운영자에게 문의하세요." : '아직 스터디 가입 신청 전입니다. <a href="#me">내 진도</a>에서 신청하세요.'}</p>`; return; }
  let rows;
  try { rows = await rpc("study_dashboard"); }
  catch (e) { el.innerHTML = `<p class="notice">대시보드를 불러오지 못했습니다. ${esc(e.message)} 잠시 후 새로고침하세요.</p>`; return; }
  const members = new Map();
  for (const r of rows || []) {
    if (!members.has(r.member_id)) members.set(r.member_id, { id: r.member_id, name: r.name, org: r.org, weeks: {}, last: r.joined_at });
    const m = members.get(r.member_id);
    if (r.week) { m.weeks[r.week] = r; if (r.updated_at > m.last) m.last = r.updated_at; }
  }
  const list = [...members.values()];
  if (!list.length) { el.innerHTML = `<p class="notice">아직 승인된 멤버가 없습니다.</p>`; return; }
  const st = (m, w) => { const r = m.weeks[w]; if (!r) return "none"; if (r.notes_done && r.lab_done) return "full"; if (r.notes_done || r.lab_done || r.quiz_score != null) return "half"; return "none"; };
  list.forEach(m => { m.done = WEEKS.filter(w => st(m, w.week) === "full").length; });
  list.sort((a, b) => b.done - a.done || a.name.localeCompare(b.name, "ko"));
  const avg = list.reduce((s, m) => s + m.done, 0) / list.length;
  let qs = 0, qt = 0; list.forEach(m => Object.values(m.weeks).forEach(r => { if (r.quiz_total) { qs += r.quiz_score; qt += r.quiz_total; } }));
  const lastAct = list.map(m => m.last).sort().pop();
  const fmt = d => { const x = new Date(d); return `${x.getMonth() + 1}월 ${x.getDate()}일`; };
  const perWeek = WEEKS.map(w => ({ w, n: list.filter(m => st(m, w.week) === "full").length, any: list.filter(m => st(m, w.week) !== "none").length }));

  el.innerHTML = `
    <div class="kpis">
      <div><b>${list.length}명</b><span>등록 멤버</span></div>
      <div><b>${avg.toFixed(1)}주</b><span>1인 평균 완료 주차 (16주 중)</span></div>
      <div><b>${qt ? Math.round(qs / qt * 100) + "%" : "–"}</b><span>퀴즈 평균 정답률 (최고 점수 기준)</span></div>
      <div><b>${fmt(lastAct)}</b><span>마지막 활동</span></div>
    </div>
    <section class="panel">
      <h2>주차별 완료 인원</h2>
      <p class="sub">강의 노트와 실습을 모두 마친 멤버 수. 막대에 올리면 진행 중인 인원도 보입니다.</p>
      <div class="chartwrap">
        <div class="bars" role="list">
          ${[0, 0.5, 1].map(f => `<div class="gl" style="bottom:${f * 100}%"><span>${Math.round(list.length * f)}</span></div>`).join("")}
          ${perWeek.map(({ w, n, any }) => `<div class="bar" role="listitem" tabindex="0" style="--c:${cvar(w.stage)}" data-tip="${w.week}주 ${esc(w.title)}&#10;완료 ${n}명 · 진행 중 포함 ${any}명 / ${list.length}명" aria-label="${w.week}주 완료 ${n}명"><i style="height:${list.length ? n / list.length * 100 : 0}%"></i></div>`).join("")}
        </div>
        <div class="xlab">${WEEKS.map(w => `<span>${w.week}</span>`).join("")}</div>
      </div>
    </section>
    <section class="panel">
      <h2>멤버별 진행 현황</h2>
      <p class="sub">점 아래 숫자는 퀴즈 최고 점수입니다. 표는 가로로 스크롤됩니다.</p>
      <div class="grid-scroll">
        <table class="matrix">
          <thead>
            <tr><th></th>${STAGES.map(s => `<th colspan="4" style="--c:${cvar(s.n)}">${s.n}단계 ${esc(s.name)}</th>`).join("")}<th></th></tr>
            <tr><th class="name">멤버</th>${WEEKS.map(w => `<th scope="col">${w.week}</th>`).join("")}<th>완료</th></tr>
          </thead>
          <tbody>
            ${list.map(m => `<tr class="${user && user.id === m.id ? "me" : ""}"><th class="name" scope="row">${esc(m.name)}${m.org ? `<small>${esc(m.org)}</small>` : ""}</th>
              ${WEEKS.map(w => { const r = m.weeks[w.week], s = st(m, w.week);
                const tip = `${m.name} · ${w.week}주 ${w.title}&#10;강의 노트 ${r && r.notes_done ? "완료" : "미완료"} · 실습 ${r && r.lab_done ? "완료" : "미완료"}&#10;퀴즈 ${r && r.quiz_total ? r.quiz_score + "/" + r.quiz_total : "미응시"}`;
                return `<td><span class="cell" style="--c:${cvar(w.stage)}" tabindex="0" data-tip="${esc(tip)}"><i class="dot ${s}"></i><em>${r && r.quiz_total ? r.quiz_score : ""}</em></span></td>`; }).join("")}
              <td class="pct">${m.done}/16</td></tr>`).join("")}
          </tbody>
        </table>
      </div>
      <div class="legend"><span><i class="dot"></i>시작 전</span><span><i class="dot half"></i>진행 중</span><span><i class="dot full"></i>노트·실습 완료</span></div>
    </section>`;
}

/* ---------- 화면: 내 진도 ---------- */
const KAKAO_SYMBOL = '<svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M12 3.5C6.75 3.5 2.5 6.86 2.5 11c0 2.66 1.76 5 4.41 6.33l-.9 3.28c-.08.29.25.52.5.35l3.93-2.6c.51.06 1.03.09 1.56.09 5.25 0 9.5-3.36 9.5-7.5S17.25 3.5 12 3.5z"/></svg>';
const kakaoBtn = () => `<button class="kakao" type="button" data-kakao>${KAKAO_SYMBOL}카카오 로그인</button>`;
function bindKakao(root = document) { root.querySelectorAll("[data-kakao]").forEach(b => b.addEventListener("click", loginKakao)); }

function viewMe() {
  if (!session) {
    return `<div class="wrap me">
      <h1>내 진도</h1>
      <p>카카오로 로그인하고 스터디 가입을 신청하세요. 운영자가 승인하면 진도가 대시보드에 모입니다. 로그인하지 않아도 이 브라우저에는 진도가 저장됩니다.</p>
      <div style="margin-top:24px">${kakaoBtn()}</div>
      <p class="fine">카카오 계정의 닉네임과 프로필 사진만 받습니다. 카카오톡 친구 목록이나 메시지에는 접근하지 않습니다.</p>
    </div>`;
  }
  if (!me || me.status === "rejected") {
    return `<div class="wrap me">
      <h1>스터디 가입 신청</h1>
      <p>${me ? "이전 신청이 승인되지 않았습니다. 정보를 확인하고 다시 신청할 수 있습니다." : "대시보드에 표시될 이름과 소속을 확인하고 신청하세요. 운영자가 승인하면 진도가 저장됩니다."}</p>
      <form class="form" id="joinForm" style="margin-top:20px">
        <label>이름 <small>대시보드에 표시됩니다</small><input name="name" required maxlength="20" value="${esc(me ? me.name : kakaoName())}" autocomplete="name"></label>
        <label>소속 <small>선택</small><input name="org" maxlength="30" value="${esc(me && me.org ? me.org : "")}" autocomplete="organization"></label>
        <p class="msg" aria-live="polite"></p>
        <div class="qbar"><button class="btn" type="submit">가입 신청</button><button class="btn ghost" type="button" id="logout">로그아웃</button></div>
      </form>
    </div>`;
  }
  if (me.status === "pending") {
    return `<div class="wrap me">
      <h1>승인 대기 중</h1>
      <p>${esc(me.name)} 님의 가입 신청이 접수됐습니다. 운영자가 승인하면 진도가 대시보드에 저장됩니다. 그동안 학습 기록은 이 브라우저에 저장되고, 승인 후 자동으로 옮겨집니다.</p>
      <div class="qbar" style="margin-top:20px"><button class="btn" type="button" id="recheck">승인 여부 다시 확인</button><button class="btn ghost" type="button" id="logout">로그아웃</button></div>
    </div>`;
  }
  return `<div class="wrap me" style="max-width:760px">
    <h1>${esc(me.name)} 님</h1>
    <p>${me.org ? esc(me.org) + " · " : ""}16주 중 ${doneCount()}주를 완료했습니다. 진도는 대시보드에 저장됩니다.</p>
    <div class="qbar" style="margin-top:20px"><a class="btn" href="#home" style="text-decoration:none">노선도로 가기</a><button class="btn ghost" type="button" id="logout">로그아웃</button></div>
    ${me.is_admin ? `<section class="sec" id="admin"><h2>멤버 승인 관리</h2><div id="adminList"><p class="notice">불러오는 중…</p></div></section>` : ""}
  </div>`;
}
function bindMe() {
  bindKakao();
  const lo = $("#logout"); if (lo) lo.addEventListener("click", logout);
  const rc = $("#recheck");
  if (rc) rc.addEventListener("click", async () => {
    await loadMe();
    if (user) { await syncMine(); toast("승인됐습니다. 진도를 대시보드로 옮겼습니다"); }
    else toast("아직 승인 대기 중입니다");
    renderNav(); render();
  });
  const form = $("#joinForm");
  if (form) form.addEventListener("submit", async e => {
    e.preventDefault();
    const msg = $(".msg", form), btn = form.querySelector("[type=submit]");
    btn.disabled = true;
    try {
      await rpc("study_join", { p_name: form.name.value.trim(), p_org: form.org.value.trim() || null });
      await loadMe(); toast("가입 신청을 보냈습니다"); renderNav(); render();
    } catch (err) { msg.className = "msg err"; msg.textContent = err.message; }
    finally { btn.disabled = false; }
  });
  if ($("#adminList")) loadAdmin();
}
async function loadAdmin() {
  const el = $("#adminList");
  let rows;
  try { rows = await rpc("study_admin_members"); }
  catch (e) { el.innerHTML = `<p class="notice">멤버 목록을 불러오지 못했습니다. ${esc(e.message)}</p>`; return; }
  const label = { pending: "승인 대기", approved: "승인됨", rejected: "거절됨" };
  const pending = rows.filter(r => r.status === "pending").length;
  el.innerHTML = `<p class="sub" style="color:var(--muted);margin:0 0 12px">${pending ? `승인 대기 ${pending}명이 있습니다.` : "승인 대기 중인 신청이 없습니다."}</p>
    <table class="admin"><thead><tr><th>이름</th><th>소속</th><th>신청일</th><th>상태</th><th></th></tr></thead><tbody>
    ${rows.map(r => { const d = new Date(r.created_at); const mine = me && r.member_id === me.id;
      return `<tr><td>${esc(r.name)}${r.is_admin ? " <small>(운영자)</small>" : ""}</td><td>${esc(r.org || "")}</td><td>${d.getMonth() + 1}/${d.getDate()}</td>
      <td><span class="badge ${r.status}">${label[r.status]}</span></td>
      <td class="acts">${mine ? "" : r.status === "approved"
        ? `<button class="btn ghost sm" data-set="pending" data-id="${r.member_id}">승인 취소</button>`
        : `<button class="btn sm" data-set="approved" data-id="${r.member_id}">승인</button>${r.status === "pending" ? `<button class="btn ghost sm" data-set="rejected" data-id="${r.member_id}">거절</button>` : ""}`}</td></tr>`; }).join("")}
    </tbody></table>`;
  el.querySelectorAll("[data-set]").forEach(b => b.addEventListener("click", async () => {
    b.disabled = true;
    try { await rpc("study_set_status", { p_member: b.dataset.id, p_status: b.dataset.set }); toast({ approved: "승인했습니다", rejected: "거절했습니다", pending: "승인을 취소했습니다" }[b.dataset.set]); loadAdmin(); }
    catch (e) { toast("처리하지 못했습니다: " + e.message); b.disabled = false; }
  }));
}

/* ---------- 공통 ---------- */
let toastTimer;
function toast(t) {
  let el = $("#toast");
  el.textContent = t; el.hidden = false;
  clearTimeout(toastTimer); toastTimer = setTimeout(() => el.hidden = true, 2600);
}
function renderNav() {
  $("#who").innerHTML = user ? `<b>${esc(user.name)}</b> 님` : me && me.status === "pending" ? "승인 대기" : "";
}
function route() { return decodeURIComponent(location.hash.slice(1)) || "home"; }
function render() {
  const r = route(), main = $("#main");
  let title = "";
  const wm = r.match(/^w(\d{1,2})$/);
  if (wm && byWeek.has(+wm[1])) { const wk = byWeek.get(+wm[1]); main.innerHTML = viewWeek(wk); bindWeek(wk); title = `${wk.week}주 ${wk.title}`; }
  else if (r === "dashboard") { main.innerHTML = viewDashboard(); loadDashboard(); $("#reload").addEventListener("click", loadDashboard); title = "대시보드"; }
  else if (r === "me") { main.innerHTML = viewMe(); bindMe(); title = "내 진도"; }
  else {
    main.innerHTML = viewHome(); title = "";
    main.querySelectorAll(".st").forEach(g => {
      g.addEventListener("click", () => location.hash = g.dataset.go);
      g.addEventListener("keydown", e => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); location.hash = g.dataset.go; } });
    });
  }
  const navKey = wm ? "home" : (["dashboard", "me"].includes(r) ? r : "home");
  document.querySelectorAll(".nav a").forEach(a => a.dataset.nav === navKey ? a.setAttribute("aria-current", "page") : a.removeAttribute("aria-current"));
  document.title = (title ? title + " · " : "") + "AI Tech 스터디";
}

/* 페이지 안 이동(#notes 등)은 해시 라우팅과 분리 */
document.addEventListener("click", e => {
  const a = e.target.closest("a[data-anchor]");
  if (!a) return;
  e.preventDefault();
  const t = document.getElementById(a.getAttribute("href").slice(1));
  if (t) { t.scrollIntoView({ behavior: matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth" }); t.setAttribute("tabindex", "-1"); t.focus({ preventScroll: true }); }
});

/* 대시보드 툴팁 */
const tip = document.createElement("div"); tip.className = "tip"; document.body.appendChild(tip);
const showTip = (el, x, y) => { tip.innerHTML = esc(el.dataset.tip.replace(/&#10;/g, "\n")).replace(/\n/g, "<br>"); tip.style.display = "block"; tip.style.left = Math.min(x + 12, innerWidth - tip.offsetWidth - 8) + "px"; tip.style.top = (y + 14) + "px"; };
document.addEventListener("mousemove", e => { const el = e.target.closest("[data-tip]"); if (el) showTip(el, e.clientX, e.clientY); else tip.style.display = "none"; });
document.addEventListener("focusin", e => { const el = e.target.closest("[data-tip]"); if (el) { const r = el.getBoundingClientRect(); showTip(el, r.left, r.bottom); } else tip.style.display = "none"; });
document.addEventListener("scroll", () => tip.style.display = "none", { passive: true });

addEventListener("hashchange", () => { render(); window.scrollTo(0, 0); });

/* 시작: 카카오 로그인에서 돌아왔으면 세션을 만든 뒤 원래 보던 화면으로 */
(async () => {
  renderNav(); render();
  try {
    const { data } = await sb.auth.getSession();
    session = data.session;
    if (/[?&](code|error)=/.test(location.search)) {
      const err = new URLSearchParams(location.search).get("error_description");
      const back = store.get("aistudy.after", "#me"); store.del("aistudy.after");
      history.replaceState(null, "", location.pathname + back);
      if (err) toast("카카오 로그인 실패: " + err);
    }
    await loadMe();
    if (user) await syncMine();
  } catch (e) { /* 오프라인이면 로컬 진도만 사용 */ }
  renderNav(); render();
  sb.auth.onAuthStateChange((ev, s) => { session = s; if (ev === "SIGNED_OUT") { me = null; user = null; } });
})();
