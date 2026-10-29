(async function () {
  const AX = window.AX;
  const { esc, getJSON, topbar, footer, pad, auth, rpc, toast, kakaoBtn, bindKakao } = AX;
  const main = document.getElementById("main");
  let cur;
  try { cur = await getJSON("data/curriculum.json"); }
  catch (e) { topbar("me", ""); main.innerHTML = `<div class="empty">데이터를 불러오지 못했습니다.</div>`; return; }
  topbar("me", cur.version);
  footer(cur.version, cur.updated);
  main.className = "wrap narrow";
  await AX.ready;

  // 세션별 내 진도 (로그인과 상관없이 항상 보임)
  function myGrid() {
    const total = cur.sessions.length;
    const full = cur.sessions.filter((s) => AX.status(s.id) === "full").length;
    const half = cur.sessions.filter((s) => AX.status(s.id) === "half").length;
    const label = { full: "완료", half: "진행 중", none: "시작 전" };
    return `<section class="sec">
      <h2>세션별 진도 <small class="muted">${full}/${total} 완료 · 진행 중 ${half}</small></h2>
      <div class="progress big" aria-hidden="true"><i style="width:${full / total * 100}%"></i></div>
      ${cur.parts.map((p) => `<div class="mypart"><h4>PART ${p.id} · ${esc(p.title)}</h4><div class="mycells">
        ${cur.sessions.filter((s) => s.part === p.id).map((s) => {
          const st = AX.status(s.id), pr = AX.prog()[s.id] || {};
          return `<a class="mycell ${st}" href="session.html?id=${s.id}" title="${esc(`${pad(s.id)}. ${s.title} · ${label[st]}`)}">
            <i class="dot ${st}"></i><span><b>${pad(s.id)}</b> ${esc(s.title)}</span>
            <small>${[pr.notes && "개념✓", pr.lab && "실습✓", pr.score != null && `퀴즈 ${pr.score}/${pr.total}`].filter(Boolean).join(" · ")}</small></a>`;
        }).join("")}</div></div>`).join("")}
      <div class="legend"><span><i class="dot"></i>시작 전</span><span><i class="dot half"></i>진행 중</span><span><i class="dot full"></i>개념·이론·실습 완료</span></div>
    </section>`;
  }

  function view() {
    const me = auth.me;
    let head;
    if (auth.offline) {
      head = `<h1 class="display">내 진도</h1>
        <p class="notice">로그인 서버에 연결할 수 없어 이 브라우저에 저장된 진도만 보여 줍니다.</p>`;
    } else if (!auth.session) {
      head = `<h1 class="display">내 진도</h1>
        <p class="lead">카카오로 로그인하고 스터디 가입을 신청하세요. 운영자가 승인하면 진도가 대시보드에 모입니다. 로그인하지 않아도 이 브라우저에는 진도가 저장됩니다.</p>
        <div class="login-box">${kakaoBtn()}</div>
        <p class="small muted">카카오 계정의 닉네임과 프로필 사진만 받습니다. 카카오톡 친구 목록이나 메시지에는 접근하지 않습니다.</p>`;
    } else if (!me || me.status === "rejected") {
      head = `<h1 class="display">스터디 가입 신청</h1>
        <p class="lead">${me ? "이전 신청이 승인되지 않았습니다. 정보를 확인하고 다시 신청할 수 있습니다." : "대시보드에 표시될 이름과 소속을 확인하고 신청하세요. 운영자가 승인하면 진도가 저장됩니다."}</p>
        <form class="form" id="joinForm">
          <label>이름 <small>대시보드에 표시됩니다</small><input name="name" required maxlength="20" value="${esc(me ? me.name : AX.kakaoName())}" autocomplete="name"></label>
          <label>소속 <small>선택</small><input name="org" maxlength="30" value="${esc(me && me.org ? me.org : "")}" autocomplete="organization"></label>
          <p class="msg" aria-live="polite"></p>
          <div class="qbar"><button class="btn" type="submit">가입 신청</button><button class="btn ghost" type="button" data-logout>로그아웃</button></div>
        </form>`;
    } else if (me.status === "pending") {
      head = `<h1 class="display">승인 대기 중</h1>
        <p class="lead">${esc(me.name)} 님의 가입 신청이 접수됐습니다. 운영자가 승인하면 진도가 대시보드에 저장됩니다. 그동안 학습 기록은 이 브라우저에 저장되고, 승인 후 자동으로 옮겨집니다.</p>
        <div class="qbar"><button class="btn" type="button" id="recheck">승인 여부 다시 확인</button><button class="btn ghost" type="button" data-logout>로그아웃</button></div>`;
    } else {
      head = `<h1 class="display">${esc(me.name)} 님</h1>
        <p class="lead">${me.org ? esc(me.org) + " · " : ""}진도는 대시보드에 함께 저장됩니다.</p>
        <div class="qbar"><a class="btn" href="index.html">커리큘럼으로</a><a class="btn ghost" href="dashboard.html">대시보드</a><button class="btn ghost" type="button" data-logout>로그아웃</button></div>`;
    }
    main.innerHTML = `<section class="hero"><div class="eyebrow">MY PROGRESS</div>${head}</section>
      ${myGrid()}
      ${me && me.status === "approved" && me.is_admin ? `<section class="sec" id="admin"><h2>멤버 승인 관리</h2><div id="adminList"><p class="notice">불러오는 중…</p></div></section>` : ""}`;
    bind();
  }

  function bind() {
    bindKakao(main);
    main.querySelectorAll("[data-logout]").forEach((b) => b.addEventListener("click", AX.logout));
    const rc = document.getElementById("recheck");
    if (rc) rc.addEventListener("click", async () => {
      rc.disabled = true;
      await AX.loadMe();
      if (auth.user) { await AX.syncMine(); toast("승인됐습니다. 진도를 대시보드로 옮겼습니다"); }
      else toast("아직 승인 대기 중입니다");
      AX.renderWho(); view();
    });
    const form = document.getElementById("joinForm");
    if (form) form.addEventListener("submit", async (e) => {
      e.preventDefault();
      const msg = form.querySelector(".msg"), btn = form.querySelector("[type=submit]");
      btn.disabled = true;
      try {
        await rpc("study_join", { p_name: form.name.value.trim(), p_org: form.org.value.trim() || null });
        await AX.loadMe(); toast("가입 신청을 보냈습니다"); AX.renderWho(); view();
      } catch (err) { msg.className = "msg err"; msg.textContent = err.message; btn.disabled = false; }
    });
    if (document.getElementById("adminList")) loadAdmin();
  }

  async function loadAdmin() {
    const el = document.getElementById("adminList");
    let rows;
    try { rows = await rpc("study_admin_members"); }
    catch (e) { el.innerHTML = `<p class="notice">멤버 목록을 불러오지 못했습니다. ${esc(e.message)}</p>`; return; }
    const label = { pending: "승인 대기", approved: "승인됨", rejected: "거절됨" };
    const pending = rows.filter((r) => r.status === "pending").length;
    el.innerHTML = `<p class="small muted">${pending ? `승인 대기 ${pending}명이 있습니다.` : "승인 대기 중인 신청이 없습니다."}</p>
      <div class="grid-scroll"><table class="admin"><thead><tr><th>이름</th><th>소속</th><th>신청일</th><th>상태</th><th></th></tr></thead><tbody>
      ${rows.map((r) => { const d = new Date(r.created_at); const mine = auth.me && r.member_id === auth.me.id;
        return `<tr><td>${esc(r.name)}${r.is_admin ? " <small>(운영자)</small>" : ""}</td><td>${esc(r.org || "")}</td><td>${d.getMonth() + 1}/${d.getDate()}</td>
        <td><span class="badge ${esc(r.status)}">${label[r.status] || esc(r.status)}</span></td>
        <td class="acts">${mine ? "" : r.status === "approved"
          ? `<button class="btn ghost sm" data-set="pending" data-id="${esc(r.member_id)}">승인 취소</button>`
          : `<button class="btn sm" data-set="approved" data-id="${esc(r.member_id)}">승인</button>${r.status === "pending" ? `<button class="btn ghost sm" data-set="rejected" data-id="${esc(r.member_id)}">거절</button>` : ""}`}</td></tr>`; }).join("")}
      </tbody></table></div>`;
    el.querySelectorAll("[data-set]").forEach((b) => b.addEventListener("click", async () => {
      b.disabled = true;
      try {
        await rpc("study_set_status", { p_member: b.dataset.id, p_status: b.dataset.set });
        toast({ approved: "승인했습니다", rejected: "거절했습니다", pending: "승인을 취소했습니다" }[b.dataset.set]);
        loadAdmin();
      } catch (e) { toast("처리하지 못했습니다: " + e.message); b.disabled = false; }
    }));
  }

  view();
  AX.onChange(view);
})();
