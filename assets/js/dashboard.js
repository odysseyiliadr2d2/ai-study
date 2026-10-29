(async function () {
  const AX = window.AX;
  const { esc, getJSON, topbar, footer, pad, auth, rpc, kakaoBtn, bindKakao } = AX;
  const main = document.getElementById("main");
  let cur;
  try { cur = await getJSON("data/curriculum.json"); }
  catch (e) { topbar("dashboard", ""); main.innerHTML = `<div class="empty">데이터를 불러오지 못했습니다.</div>`; return; }
  topbar("dashboard", cur.version);
  footer(cur.version, cur.updated);
  const S = cur.sessions, N = S.length;
  const pc = (part) => `var(--p${part})`;

  main.innerHTML = `
    <section class="hero dhead">
      <div><div class="eyebrow">DASHBOARD</div><h1 class="display">스터디 대시보드</h1>
      <p class="lead">승인된 멤버의 세션별 개념·이론, 실습, 퀴즈 기록입니다.</p></div>
      <button class="btn ghost" type="button" id="reload">새로고침</button>
    </section>
    <div id="dash"><p class="notice">불러오는 중…</p></div>`;
  const el = document.getElementById("dash");
  document.getElementById("reload").addEventListener("click", load);
  await AX.ready;

  async function load() {
    if (auth.offline) { el.innerHTML = `<p class="notice">로그인 서버에 연결할 수 없습니다. 내 진도는 <a href="me.html">내 진도</a>에서 볼 수 있습니다.</p>`; return; }
    if (!auth.session) {
      el.innerHTML = `<div class="notice">대시보드는 승인된 스터디 멤버만 볼 수 있습니다.<div class="login-box">${kakaoBtn()}</div></div>`;
      bindKakao(el); return;
    }
    if (!auth.user) {
      const me = auth.me;
      el.innerHTML = `<p class="notice">${me && me.status === "pending" ? "가입 신청이 접수됐습니다. 운영자가 승인하면 대시보드를 볼 수 있습니다."
        : me && me.status === "rejected" ? "가입 신청이 승인되지 않았습니다. 운영자에게 문의하세요."
        : '아직 스터디 가입 신청 전입니다. <a href="me.html">내 진도</a>에서 신청하세요.'}</p>`;
      return;
    }
    el.innerHTML = `<p class="notice">불러오는 중…</p>`;
    let rows;
    try { rows = await rpc("study_dashboard"); }
    catch (e) { el.innerHTML = `<p class="notice">대시보드를 불러오지 못했습니다. ${esc(e.message)} 잠시 후 새로고침하세요.</p>`; return; }

    const members = new Map();
    for (const r of rows || []) {
      if (!members.has(r.member_id)) members.set(r.member_id, { id: r.member_id, name: r.name, org: r.org, ss: {}, last: r.joined_at });
      const m = members.get(r.member_id);
      if (r.week) { m.ss[r.week] = r; if (r.updated_at > m.last) m.last = r.updated_at; }
    }
    const list = [...members.values()];
    if (!list.length) { el.innerHTML = `<p class="notice">아직 승인된 멤버가 없습니다.</p>`; return; }
    const st = (m, id) => { const r = m.ss[id]; if (!r) return "none"; if (r.notes_done && r.lab_done) return "full"; if (r.notes_done || r.lab_done || r.quiz_score != null) return "half"; return "none"; };
    list.forEach((m) => { m.done = S.filter((s) => st(m, s.id) === "full").length; });
    list.sort((a, b) => b.done - a.done || a.name.localeCompare(b.name, "ko"));
    const avg = list.reduce((a, m) => a + m.done, 0) / list.length;
    let qs = 0, qt = 0;
    list.forEach((m) => Object.values(m.ss).forEach((r) => { if (r.quiz_total) { qs += r.quiz_score; qt += r.quiz_total; } }));
    const lastAct = list.map((m) => m.last).filter(Boolean).sort().pop();
    const fmt = (d) => { if (!d) return "–"; const x = new Date(d); return `${x.getMonth() + 1}월 ${x.getDate()}일`; };
    const per = S.map((s) => ({ s, n: list.filter((m) => st(m, s.id) === "full").length, any: list.filter((m) => st(m, s.id) !== "none").length }));

    el.innerHTML = `
      <div class="kpis">
        <div><b>${list.length}명</b><span>승인 멤버</span></div>
        <div><b>${avg.toFixed(1)}<small>/${N}</small></b><span>1인 평균 완료 세션</span></div>
        <div><b>${qt ? Math.round(qs / qt * 100) + "%" : "–"}</b><span>퀴즈 평균 정답률 (최고 점수 기준)</span></div>
        <div><b>${fmt(lastAct)}</b><span>마지막 활동</span></div>
      </div>
      <section class="dpanel">
        <h2>세션별 완료 인원</h2>
        <p class="small muted">개념·이론과 실습을 모두 마친 멤버 수. 막대에 올리면 진행 중인 인원도 보입니다.</p>
        <div class="chartwrap">
          <div class="bars" role="list">
            ${[0, 0.5, 1].map((f) => `<div class="gl" style="bottom:${f * 100}%"><span>${Math.round(list.length * f)}</span></div>`).join("")}
            ${per.map(({ s, n, any }) => `<div class="bar" role="listitem" tabindex="0" style="--c:${pc(s.part)}"
              data-tip="${pad(s.id)}. ${esc(s.title)}&#10;완료 ${n}명 · 진행 중 포함 ${any}명 / ${list.length}명" aria-label="세션 ${s.id} 완료 ${n}명">
              <i style="height:${n / list.length * 100}%"></i></div>`).join("")}
          </div>
          <div class="xlab">${S.map((s) => `<span>${s.id}</span>`).join("")}</div>
        </div>
        <div class="legend">${cur.parts.map((p) => `<span><i class="sq" style="--c:${pc(p.id)}"></i>${p.id}. ${esc(p.title)}</span>`).join("")}</div>
      </section>
      <section class="dpanel">
        <h2>멤버별 진행 현황</h2>
        <p class="small muted">점 아래 숫자는 퀴즈 최고 점수입니다. 표는 가로로 스크롤됩니다.</p>
        <div class="grid-scroll">
          <table class="matrix">
            <thead>
              <tr><th></th>${cur.parts.map((p) => `<th colspan="${S.filter((s) => s.part === p.id).length}" class="ph" style="--c:${pc(p.id)}">${p.id}. ${esc(p.title)}</th>`).join("")}<th></th></tr>
              <tr><th class="name">멤버</th>${S.map((s) => `<th scope="col">${s.id}</th>`).join("")}<th>완료</th></tr>
            </thead>
            <tbody>
              ${list.map((m) => `<tr class="${auth.user && auth.user.id === m.id ? "me" : ""}"><th class="name" scope="row">${esc(m.name)}${m.org ? `<small>${esc(m.org)}</small>` : ""}</th>
                ${S.map((s) => { const r = m.ss[s.id], k = st(m, s.id);
                  const tip = `${m.name} · ${pad(s.id)}. ${s.title}\n개념·이론 ${r && r.notes_done ? "완료" : "미완료"} · 실습 ${r && r.lab_done ? "완료" : "미완료"}\n퀴즈 ${r && r.quiz_total ? r.quiz_score + "/" + r.quiz_total : "미응시"}`;
                  return `<td><span class="cell" style="--c:${pc(s.part)}" tabindex="0" data-tip="${esc(tip)}"><i class="dot ${k}"></i><em>${r && r.quiz_total ? r.quiz_score : ""}</em></span></td>`; }).join("")}
                <td class="pct">${m.done}/${N}</td></tr>`).join("")}
            </tbody>
          </table>
        </div>
        <div class="legend"><span><i class="dot"></i>시작 전</span><span><i class="dot half"></i>진행 중</span><span><i class="dot full"></i>개념·이론·실습 완료</span></div>
      </section>`;
  }

  // 말풍선 (data-tip)
  const tip = document.createElement("div"); tip.className = "tip"; tip.hidden = true; document.body.append(tip);
  const showTip = (t) => {
    const r = t.getBoundingClientRect();
    tip.textContent = t.dataset.tip; tip.hidden = false;
    const w = tip.offsetWidth;
    tip.style.left = Math.max(8, Math.min(innerWidth - w - 8, r.left + r.width / 2 - w / 2)) + "px";
    tip.style.top = (r.top + scrollY - tip.offsetHeight - 8) + "px";
  };
  ["mouseover", "focusin"].forEach((ev) => el.addEventListener(ev, (e) => { const t = e.target.closest("[data-tip]"); if (t) showTip(t); }));
  ["mouseout", "focusout"].forEach((ev) => el.addEventListener(ev, (e) => { if (e.target.closest("[data-tip]")) tip.hidden = true; }));

  load();
})();
