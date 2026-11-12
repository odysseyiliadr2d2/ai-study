// 움직이는 도식 공통 엔진
//   AXAnim.register("s09-rag", (root, U) => { ... })   — 장면 모듈이 등록
//   AXAnim.mount(el, "s09-rag")                         — 세션 화면이 호출
(function () {
  const NS = "http://www.w3.org/2000/svg";
  const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;
  const REG = {};

  const U = {
    reduce,
    el(tag, a, p) { const e = document.createElementNS(NS, tag); for (const k in a) e.setAttribute(k, a[k]); if (p) p.appendChild(e); return e; },
    txt(p, x, y, s, cls, extra = {}) { const t = U.el("text", { x, y, class: cls, ...extra }, p); t.textContent = s; return t; },
    // 이 장면이 화면에 보이는지 (탭을 바꾸거나 스크롤로 벗어나면 멈춤)
    watch(root, onChange) {
      if (!("IntersectionObserver" in window)) { onChange(true); return; }
      new IntersectionObserver((es) => es.forEach((e) => onChange(e.isIntersecting)), { threshold: 0.25 }).observe(root);
    },
    // 점 하나가 경로들을 차례로 따라갑니다
    travel(svg, ids) {
      const dot = svg.querySelector(".a-pkt");
      if (!dot) return;
      if (!ids.length || reduce) { dot.setAttribute("cx", -20); return; }
      let i = 0;
      const run = () => {
        const p = svg.querySelector(`[data-k="${ids[i]}"]`); if (!p) return;
        const L = p.getTotalLength(), t0 = performance.now(), dur = 700;
        const tick = (now) => {
          const k = Math.min(1, (now - t0) / dur), e = k < .5 ? 2 * k * k : 1 - Math.pow(-2 * k + 2, 2) / 2;
          const pt = p.getPointAtLength(L * e); dot.setAttribute("cx", pt.x); dot.setAttribute("cy", pt.y);
          if (k < 1) requestAnimationFrame(tick); else if (++i < ids.length) run(); else setTimeout(() => dot.setAttribute("cx", -20), 250);
        };
        requestAnimationFrame(tick);
      };
      run();
    },
    // 상자·화살표형 장면: 데이터(spec)만으로 그립니다
    //   nodes: [{k,x,y,w,h,t, lines:[str|{t,k,at,mono}], chips:[{k,t,at}], cols, bars:[{k,l,v,txt}], at, tone}]
    //   edges: [{from,to,k,off,at,d}]  notes: [{x,y,t,k,at,cls}]
    //   steps: [{t,d, hl:[], hl2:[], e:[], go:[], fade:[], pulse:[], set:{k:text}, bars:{k:[v,txt]}}]
    flow(root, spec) {
      const E = (x) => String(x).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
      const at = (o) => (o && o.at != null ? ` data-at="${o.at}"` : ""), dk = (o) => (o && o.k ? ` data-k="${E(o.k)}"` : "");
      const W = spec.w || 640, H = spec.h || 400, N = {};
      spec.nodes.forEach((n) => (N[n.k] = n));
      const route = (a, b, off = 0) => {
        const [a1, a2, b1, b2] = [a.y, a.y + a.h, b.y, b.y + b.h], [c1, c2, d1, d2] = [a.x, a.x + a.w, b.x, b.x + b.w];
        if (Math.max(a1, b1) < Math.min(a2, b2)) { const y = (Math.max(a1, b1) + Math.min(a2, b2)) / 2 + off;
          return c2 <= d1 ? `M${c2} ${y} H${d1}` : `M${c1} ${y} H${d2}`; }
        if (Math.max(c1, d1) < Math.min(c2, d2)) { const x = (Math.max(c1, d1) + Math.min(c2, d2)) / 2 + off;
          return a2 <= b1 ? `M${x} ${a2} V${b1}` : `M${x} ${a1} V${b2}`; }
        const ay = a.y + a.h / 2 + off, bx = b.x + b.w / 2 + off;
        return `M${c2 <= d1 ? c2 : c1} ${ay} H${bx} V${a2 <= b1 ? b1 : b2}`;
      };
      let m = `<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="${E(spec.label || "")}">`;
      (spec.edges || []).forEach((e) => { e.k = e.k || `${e.from}-${e.to}`;
        m += `<path data-k="${E(e.k)}" class="a-edge"${at(e)} d="${e.d || route(N[e.from], N[e.to], e.off || 0)}"/>`; });
      spec.nodes.forEach((n) => {
        const tone = n.tone ? ` a-t-${n.tone}` : "";
        m += `<g data-k="${E(n.k)}" class="a-box${tone}"${at(n)}><rect x="${n.x}" y="${n.y}" width="${n.w}" height="${n.h}" rx="10"/>`;
        let y = n.y + 24;
        if (n.t) m += `<text class="a-lbl" x="${n.x + 14}" y="${y}">${E(n.t)}</text>`;
        y += 6;
        (n.lines || []).forEach((l) => { const o = typeof l === "string" ? { t: l } : l; y += 17;
          m += `<text class="a-sub${o.mono ? " a-mono" : ""}${o.b ? " a-strong" : ""}" x="${n.x + 14}" y="${y}"${dk(o)}${at(o)}>${E(o.t)}</text>`; });
        if (n.chips) { const cols = n.cols || 1, cw = (n.w - 28 - (cols - 1) * 8) / cols; y += 10;
          n.chips.forEach((c, i) => { const cx = n.x + 14 + (i % cols) * (cw + 8), cy = y + Math.floor(i / cols) * 30;
            m += `<g class="a-chip"${dk(c)}${at(c)} transform="translate(${cx} ${cy})"><rect width="${cw}" height="24" rx="6"/><text x="8" y="16">${E(c.t)}</text></g>`; });
          y += Math.ceil(n.chips.length / cols) * 30 - 6; }
        if (n.bars) { const lw = n.barW || 74, bw = n.w - 28 - lw - 44; y += 8;
          n.bars.forEach((b) => { y += 22;
            m += `<g${dk(b)}${at(b)}><text class="a-sub" x="${n.x + 14}" y="${y}">${E(b.l)}</text>
              <rect class="a-barbg" x="${n.x + 14 + lw}" y="${y - 10}" width="${bw}" height="12" rx="3"/>
              <rect class="a-bar" data-w="${bw}" x="${n.x + 14 + lw}" y="${y - 10}" width="${bw * (b.v || 0)}" height="12" rx="3"/>
              <text class="a-sub a-mono a-barv" x="${n.x + 14 + lw + bw + 6}" y="${y}">${E(b.txt || "")}</text></g>`; }); }
        m += `</g>`;
      });
      (spec.notes || []).forEach((t) => { m += `<text class="${t.cls || "a-sub"}" x="${t.x}" y="${t.y}"${dk(t)}${at(t)}${t.anchor ? ` text-anchor="${t.anchor}"` : ""}>${E(t.t)}</text>`; });
      m += `<circle class="a-pkt" r="6" cx="-20" cy="-20"/></svg>`;
      const init = {}, initBar = {};
      const ctl = U.scene(root, m, spec.steps, (n, svg) => {
        const st = spec.steps[n];
        if (!init.done) { svg.querySelectorAll("text[data-k]").forEach((t) => (init[t.dataset.k] = t.textContent));
          svg.querySelectorAll("g[data-k] .a-bar").forEach((b) => (initBar[b.parentNode.dataset.k] = [+b.getAttribute("width") / +b.dataset.w, b.parentNode.querySelector(".a-barv").textContent]));
          init.done = true; }
        const txt = { ...init }, bars = { ...initBar };
        spec.steps.slice(0, n + 1).forEach((s) => { Object.assign(txt, s.set || {}); Object.assign(bars, s.bars || {}); });
        for (const k in txt) { if (k === "done") continue; const t = svg.querySelector(`text[data-k="${k}"]`); if (t) t.textContent = txt[k]; }
        for (const k in bars) { const g = svg.querySelector(`g[data-k="${k}"]`); if (!g) continue; const b = g.querySelector(".a-bar");
          b.setAttribute("width", (+b.dataset.w * Math.max(0, Math.min(1, bars[k][0]))).toFixed(1)); if (bars[k][1] != null) g.querySelector(".a-barv").textContent = bars[k][1]; }
        svg.querySelectorAll("[data-k]").forEach((e) => { const k = e.dataset.k;
          e.classList.toggle("a-hl", (st.hl || []).includes(k) || (st.e || []).includes(k));
          e.classList.toggle("a-hl2", (st.hl2 || []).includes(k));
          e.classList.toggle("a-fade", (st.fade || []).includes(k));
          e.classList.toggle("a-pulse", (st.pulse || []).includes(k)); });
        U.travel(svg, st.go || st.e || []);
      }, { foot: spec.foot, stepMs: spec.stepMs });
      return ctl;
    },
    cls(root, keys, c, on) { keys.forEach((k) => { const e = root.querySelector(`[data-k="${k}"]`); if (e) e.classList.toggle(c, on); }); },
    // 단계형 장면: 왼쪽 단계 목록과 오른쪽 그림이 함께 넘어갑니다
    scene(root, svgMarkup, steps, draw, opts = {}) {
      const STEP_MS = opts.stepMs || 3600;
      root.innerHTML = `<div class="axa-scene">
        <div class="axa-stagebox"><div class="axa-canvas">${svgMarkup}</div>
          <div class="axa-prog"><i></i></div>
          <div class="axa-ctrl"><button type="button" class="axa-btn axa-primary" data-act="play">재생</button>
            <button type="button" class="axa-btn" data-act="prev">이전</button><button type="button" class="axa-btn" data-act="next">다음</button>
            <span class="axa-count"></span></div></div>
        <ol class="axa-steps">${steps.map((s) => `<li tabindex="0"><b>${s.t}</b></li>`).join("")}</ol>
        <div class="axa-desc" aria-live="polite"><b></b><p></p></div></div>
        ${opts.foot ? `<p class="axa-foot">${opts.foot}</p>` : ""}`;
      const lis = [...root.querySelectorAll(".axa-steps li")], bar = root.querySelector(".axa-prog i"), cnt = root.querySelector(".axa-count");
      const dT = root.querySelector(".axa-desc b"), dD = root.querySelector(".axa-desc p");
      const playBtn = root.querySelector('[data-act="play"]'), svg = root.querySelector("svg");
      let cur = 0, playing = !reduce, visible = false, timer = null, raf = null, start = 0;
      function show(n) {
        cur = (n + steps.length) % steps.length;
        lis.forEach((li, i) => { li.classList.toggle("axa-cur", i === cur); li.classList.toggle("axa-done", i < cur); });
        svg.querySelectorAll("[data-at]").forEach((e) => e.classList.toggle("a-on", cur >= +e.dataset.at));
        cnt.textContent = `${cur + 1} / ${steps.length}`;
        dT.textContent = `${cur + 1}. ${steps[cur].t}`; dD.textContent = steps[cur].d;
        draw(cur, svg, root);
        restart();
      }
      function restart() {
        clearTimeout(timer); cancelAnimationFrame(raf); bar.style.width = "0";
        if (!playing || !visible) return;
        start = performance.now();
        const tick = (now) => { bar.style.width = Math.min(100, (now - start) / STEP_MS * 100) + "%"; if (playing && visible) raf = requestAnimationFrame(tick); };
        raf = requestAnimationFrame(tick);
        timer = setTimeout(() => show(cur + 1), STEP_MS + (cur === steps.length - 1 ? 1500 : 0));
      }
      function setPlay(p) { playing = p; playBtn.textContent = p ? "일시정지" : "재생"; restart(); }
      lis.forEach((li, i) => {
        li.addEventListener("click", () => { setPlay(false); show(i); });
        li.addEventListener("keydown", (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); setPlay(false); show(i); } });
      });
      playBtn.addEventListener("click", () => setPlay(!playing));
      root.querySelector('[data-act="prev"]').addEventListener("click", () => { setPlay(false); show(cur - 1); });
      root.querySelector('[data-act="next"]').addEventListener("click", () => { setPlay(false); show(cur + 1); });
      playBtn.textContent = playing ? "일시정지" : "재생";
      U.watch(root, (v) => { visible = v; if (v) restart(); else { clearTimeout(timer); cancelAnimationFrame(raf); } });
      show(0);
      return { pause: () => setPlay(false), get step() { return cur; } };
    }
  };

  window.AXAnim = {
    register(id, fn) { REG[id] = fn; },
    has(id) { return !!REG[id]; },
    mount(el, id) {
      const fn = REG[id];
      if (!fn) { el.innerHTML = `<div class="axa-loading">도식을 불러오지 못했습니다.</div>`; return; }
      try { fn(el, U); } catch (e) { el.innerHTML = `<div class="axa-loading">도식을 그리지 못했습니다. (${String(e.message || e)})</div>`; }
    }
  };
})();
