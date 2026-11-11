(async function () {
  const AX = window.AX;
  const { esc, getJSON, topbar, footer } = AX;
  const v = await getJSON("data/version.json").catch(() => ({ current: "", updated: "" }));
  topbar("setup", v.current);
  footer(v.current, v.updated);

  // 내려받기 (tools/setup/export.cjs 로 만든 파일)
  const DL = '<svg viewBox="0 0 16 16" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="M8 2.5v8M4.5 7.5 8 11l3.5-3.5M3 13.5h10"/></svg>';
  const f = (ext) => `downloads/${encodeURIComponent("실습환경-구성-가이드-" + v.current)}.${ext}`;
  document.getElementById("dl").innerHTML =
    `<a class="btn" href="${f("pdf")}" download>${DL}PDF로 받기</a>
     <a class="btn ghost" href="${f("md")}" download>${DL}Markdown으로 받기</a>
     <span class="small muted">${esc(v.current)} 기준 · Windows · macOS 내용 모두 포함</span>`;

  // 운영체제 전환 (처음에는 접속한 기기에 맞춤)
  const guess = /Mac|iPhone|iPad/.test(navigator.platform || navigator.userAgent) ? "mac" : "win";
  function pick(os) {
    document.querySelectorAll("[data-os-btn]").forEach((b) => b.setAttribute("aria-selected", b.dataset.osBtn === os));
    document.querySelectorAll("[data-os]").forEach((d) => (d.hidden = d.dataset.os !== os));
  }
  document.querySelectorAll("[data-os-btn]").forEach((b) => b.addEventListener("click", () => { pick(b.dataset.osBtn); AX.store.set("axstudy.os", b.dataset.osBtn); }));
  pick(AX.store.get("axstudy.os", guess));

  // 복사 버튼
  document.addEventListener("click", async (e) => {
    const btn = e.target.closest("button[data-copy]");
    if (!btn) return;
    const txt = btn.closest(".code").querySelector("code").textContent;
    try { await navigator.clipboard.writeText(txt); btn.textContent = "복사됨"; } catch (er) { btn.textContent = "선택해서 복사"; }
    setTimeout(() => (btn.textContent = "복사"), 1500);
  });
  if (window.Prism) Prism.highlightAll();
})();
