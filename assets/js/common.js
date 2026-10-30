/* 공통: 설정, 테마, 상단 바, 데이터 로드, 서식 렌더, 로그인(카카오 + 운영자 승인), 진도 저장 */
(function () {
  const SITE = Object.assign({
    glossaryUrl: "https://odysseyiliadr2d2.github.io/ai-glossary/",
    repoUrl: "",
    supabaseUrl: "",
    supabaseKey: ""
  }, window.SITE_CONFIG || {});

  const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const pad = (n) => String(n).padStart(2, "0");
  const store = {
    get(k, d) { try { const v = localStorage.getItem(k); return v ? JSON.parse(v) : d; } catch (e) { return d; } },
    set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) {} },
    del(k) { try { localStorage.removeItem(k); } catch (e) {} }
  };

  // ── 테마 ──────────────────────────────────
  function setTheme(t) {
    if (t) document.documentElement.setAttribute("data-theme", t);
    else document.documentElement.removeAttribute("data-theme");
    t ? store.set("axstudy.theme", t) : store.del("axstudy.theme");
  }
  const savedTheme = store.get("axstudy.theme", null);
  if (savedTheme) document.documentElement.setAttribute("data-theme", savedTheme);

  // ── 데이터 ────────────────────────────────
  async function getJSON(path) {
    const res = await fetch(path, { cache: "no-cache" });
    if (!res.ok) throw new Error(path + " (" + res.status + ")");
    return res.json();
  }

  // ── 서식: 허용한 태그만 살리고 나머지는 글자로 ──
  function rich(s) {
    let out = esc(s);
    out = out.replace(/&amp;(lt|gt|amp|quot);/g, "&$1;");   // 미리 써 둔 엔티티는 한 번만
    out = out.replace(/&lt;(\/?)(b|strong|em|code|br|sub|sup)&gt;/g, "<$1$2>");
    out = out.replace(/&lt;a href=&quot;(https?:\/\/[^&"]+|\.{0,2}\/[^&"]*|[\w-]+\.html[^&"]*)&quot;&gt;(.*?)&lt;\/a&gt;/g,
      (m, href, txt) => `<a href="${href}" ${href.startsWith("http") ? 'target="_blank" rel="noopener"' : ""}>${txt}</a>`);
    return out;
  }

  // ── 진도: { [세션]: {notes, lab, score, total} } ──
  //   notes = 개념·이론 학습 완료, lab = 실습 완료. 둘 다면 '완료'
  let prog = store.get("axstudy.prog", null);
  if (!prog) {                                   // 이전 형식(완료 세션 번호 목록) 옮기기
    prog = {};
    for (const id of store.get("axstudy.done", [])) prog[id] = { notes: true, lab: true };
    store.set("axstudy.prog", prog); store.del("axstudy.done");
  }
  const saveProg = () => store.set("axstudy.prog", prog);
  function status(id) {
    const p = prog[id] || {};
    if (p.notes && p.lab) return "full";
    if (p.notes || p.lab || p.score != null) return "half";
    return "none";
  }
  const listeners = [];
  const emit = () => listeners.forEach((f) => { try { f(); } catch (e) {} });

  // ── 로그인 (Supabase Auth · 카카오) ─────────
  const auth = { sb: null, session: null, me: null, user: null, ready: false, offline: false };
  if (window.supabase && SITE.supabaseUrl && SITE.supabaseKey) {
    try {
      auth.sb = window.supabase.createClient(SITE.supabaseUrl, SITE.supabaseKey, {
        auth: { flowType: "pkce", detectSessionInUrl: true, persistSession: true, autoRefreshToken: true }
      });
    } catch (e) { auth.sb = null; }
  }
  const kakaoName = () => {
    const m = (auth.session && auth.session.user && auth.session.user.user_metadata) || {};
    return (m.name || m.full_name || m.nickname || m.user_name || m.preferred_username || "").slice(0, 20);
  };
  async function loadMe() {
    auth.me = null; auth.user = null;
    if (!auth.session) return;
    const { data, error } = await auth.sb.from("study_members").select("id,name,org,status,is_admin").maybeSingle();
    if (!error && data) auth.me = data;
    auth.user = auth.me && auth.me.status === "approved" ? auth.me : null;
  }
  async function rpc(fn, body = {}) {
    if (!auth.sb) throw new Error("로그인 서버에 연결할 수 없습니다");
    const { data, error } = await auth.sb.rpc(fn, body);
    if (error) throw new Error(error.message || "요청 실패");
    return data;
  }
  async function loginKakao() {
    if (!auth.sb) { toast("로그인 서버에 연결할 수 없습니다. 진도는 이 브라우저에만 저장됩니다."); return; }
    // 카카오에서 돌아올 주소는 사이트 첫 화면 하나로 고정하고, 원래 보던 화면은 따로 기억합니다
    // 카카오 로그인이 서버에서 꺼져 있으면 오류 화면(JSON)으로 넘어가지 않고 안내만 합니다
    try {
      const r = await fetch(SITE.supabaseUrl + "/auth/v1/settings", { headers: { apikey: SITE.supabaseKey } });
      const st = r.ok ? await r.json() : null;
      if (st && st.external && !st.external.kakao) { toast("카카오 로그인이 아직 준비 중입니다. 운영자에게 문의하세요. 진도는 이 브라우저에 저장됩니다."); return; }
    } catch (e) { /* 확인 실패 시 그대로 진행 */ }
    const page = location.pathname.split("/").pop() || "index.html";
    store.set("axstudy.after", page + location.search + location.hash);
    const { error } = await auth.sb.auth.signInWithOAuth({
      provider: "kakao",
      options: { redirectTo: new URL("./", location.href).href }
    });
    if (error) toast("카카오 로그인을 시작하지 못했습니다: " + error.message);
  }
  async function logout() {
    if (auth.sb) await auth.sb.auth.signOut();
    auth.session = null; auth.me = null; auth.user = null;
    toast("로그아웃했습니다. 이 브라우저의 진도 기록은 남아 있습니다.");
    renderWho(); emit();
  }
  async function syncMine() {
    if (!auth.user) return;
    for (const [id, p] of Object.entries(prog)) {
      if (!(p.notes || p.lab || p.score != null)) continue;
      await rpc("study_save", { p_week: +id, p_notes: p.notes ? true : null, p_lab: p.lab ? true : null,
        p_quiz_score: p.score ?? null, p_quiz_total: p.total ?? null }).catch(() => {});
    }
    const { data } = await auth.sb.from("study_progress").select("week,notes_done,lab_done,quiz_score,quiz_total");
    for (const r of data || []) {
      const p = prog[r.week] || {};
      const score = Math.max(p.score ?? -1, r.quiz_score ?? -1);
      prog[r.week] = { notes: !!(p.notes || r.notes_done), lab: !!(p.lab || r.lab_done), total: r.quiz_total ?? p.total };
      if (score >= 0) prog[r.week].score = score;
    }
    saveProg();
  }
  async function mark(id, patch) {
    const p = prog[id] = Object.assign(prog[id] || {}, patch);
    if (patch.score != null) p.score = Math.max(patch.score, p.score ?? 0);
    saveProg(); emit();
    if (!auth.user) return;
    try {
      await rpc("study_save", { p_week: id, p_notes: patch.notes ?? null, p_lab: patch.lab ?? null,
        p_quiz_score: patch.score ?? null, p_quiz_total: patch.total ?? null });
      toast("대시보드에 저장했습니다");
    } catch (e) { toast("서버 저장 실패: " + e.message + " (이 브라우저에는 저장됨)"); }
  }
  function syncHint() {
    if (auth.user) return "대시보드에 함께 저장됩니다.";
    if (auth.me && auth.me.status === "pending") return "운영자 승인 후 대시보드에 반영됩니다.";
    return '진도는 이 브라우저에 저장됩니다. <a href="me.html">카카오로 로그인</a>하면 대시보드에 모입니다.';
  }

  const ready = (async () => {
    if (!auth.sb) { auth.offline = true; auth.ready = true; return; }
    try {
      const { data } = await auth.sb.auth.getSession();
      auth.session = data.session;
      if (/[?&](code|error)=/.test(location.search)) {      // 카카오에서 돌아온 직후
        const err = new URLSearchParams(location.search).get("error_description");
        const back = store.get("axstudy.after", "me.html"); store.del("axstudy.after");
        if (err) store.set("axstudy.toast", "카카오 로그인 실패: " + err);
        location.replace(back || "me.html");
        return;
      }
      await loadMe();
      if (auth.user) await syncMine();
      auth.sb.auth.onAuthStateChange((ev, s) => { auth.session = s; if (ev === "SIGNED_OUT") { auth.me = null; auth.user = null; } });
    } catch (e) { auth.offline = true; }
    auth.ready = true;
    renderWho(); emit();
  })();

  // ── 알림 ──────────────────────────────────
  let toastTimer;
  function toast(t) {
    let el = document.getElementById("toast");
    if (!el) { el = document.createElement("div"); el.id = "toast"; el.className = "toast"; el.setAttribute("role", "status"); document.body.append(el); }
    el.textContent = t; el.hidden = false;
    clearTimeout(toastTimer); toastTimer = setTimeout(() => (el.hidden = true), 2800);
  }
  const pending = store.get("axstudy.toast", null);
  if (pending) { store.del("axstudy.toast"); setTimeout(() => toast(pending), 300); }

  // ── 상단 바 ───────────────────────────────
  function renderWho() {
    const el = document.getElementById("who");
    if (!el) return;
    // 내용 수정 안내는 운영자에게만 보입니다
    const admin = !!(auth.user && auth.user.is_admin);
    const note = document.getElementById("admin-note");
    if (note) note.innerHTML = admin ? ` · 내용 수정은 <code>data/</code> 폴더의 JSON을 고친 뒤 커밋합니다.` : "";
    el.innerHTML = auth.user ? `<a href="me.html"><b>${esc(auth.user.name)}</b> 님</a>`
      : auth.me && auth.me.status === "pending" ? `<a href="me.html">승인 대기</a>`
      : auth.offline ? "" : `<a href="me.html" class="login">로그인</a>`;
  }
  function topbar(active, version) {
    const el = document.createElement("header");
    el.className = "topbar";
    const a = (href, key, label, cls = "") => `<a href="${href}" class="${cls} ${active === key ? "active" : ""}" ${active === key ? 'aria-current="page"' : ""}>${label}</a>`;
    el.innerHTML = `
      <div class="wrap">
        <a class="wordmark" href="index.html">AX <span>Study</span></a>
        <span class="chip-version" title="로드맵 문서 버전">${esc(version || "")}</span>
        <nav class="nav" aria-label="주 메뉴">
          ${a("index.html", "home", "커리큘럼")}
          ${a("dashboard.html", "dashboard", "대시보드")}
          ${a("me.html", "me", "내 진도")}
          ${a("roadmap.html", "roadmap", "로드맵")}
          <a href="${esc(SITE.glossaryUrl)}" target="_blank" rel="noopener">용어집</a>
          <span id="who" class="who"></span>
          <button class="theme-btn" type="button" aria-label="화면 테마 바꾸기">테마</button>
        </nav>
      </div>`;
    document.body.prepend(el);
    el.querySelector(".theme-btn").addEventListener("click", () => {
      const cur = document.documentElement.getAttribute("data-theme");
      const dark = cur ? cur === "dark" : matchMedia("(prefers-color-scheme: dark)").matches;
      setTheme(dark ? "light" : "dark");
    });
    renderWho();
  }

  function footer(version, updated) {
    const f = document.createElement("footer");
    f.innerHTML = `<div class="wrap">AX 비즈니스 전환 스터디 · 로드맵 ${esc(version)} (${esc(updated)}) ·
      <a href="roadmap.html#versions">버전 기록</a><span id="admin-note"></span></div>`;
    document.body.append(f);
    renderWho();
  }

  const KAKAO_SYMBOL = '<svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M12 3.5C6.75 3.5 2.5 6.86 2.5 11c0 2.66 1.76 5 4.41 6.33l-.9 3.28c-.08.29.25.52.5.35l3.93-2.6c.51.06 1.03.09 1.56.09 5.25 0 9.5-3.36 9.5-7.5S17.25 3.5 12 3.5z"/></svg>';
  const kakaoBtn = () => `<button class="kakao" type="button" data-kakao>${KAKAO_SYMBOL}카카오 로그인</button>`;
  function bindKakao(root = document) { root.querySelectorAll("[data-kakao]").forEach((b) => b.addEventListener("click", loginKakao)); }

  const ICONS = {
    concept: '<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="8" cy="7" r="4.5"/><path d="M6 13.5h4M6.5 11.5h3"/></svg>',
    theory: '<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="2" y="2.5" width="12" height="11" rx="1.5"/><path d="M5 6h6M5 9h4"/></svg>',
    lab: '<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M5.5 4.5 2.5 8l3 3.5M10.5 4.5l3 3.5-3 3.5"/></svg>',
    example: '<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="2" y="4" width="12" height="9" rx="1.5"/><path d="M5.5 4V2.5h5V4"/></svg>',
    quiz: '<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="8" cy="8" r="6"/><path d="M6.3 6.2a1.8 1.8 0 1 1 2.5 1.7c-.5.2-.8.6-.8 1.1v.4M8 11.3v.2"/></svg>'
  };

  window.AX = {
    SITE, esc, rich, getJSON, pad, ICONS, store, toast, topbar, footer,
    auth, ready, rpc, loadMe, syncMine, loginKakao, logout, kakaoName, kakaoBtn, bindKakao,
    prog: () => prog, status, mark, syncHint, onChange: (f) => listeners.push(f), renderWho
  };
})();
