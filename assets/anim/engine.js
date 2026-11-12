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
