// 카카오톡·슬랙 등 링크 미리보기(Open Graph) 생성
//  - assets/og/*.png : 1200×630 미리보기 이미지 (사이트, 로드맵, 대시보드, 세션 24개)
//  - s/NN.html       : 세션별 공유용 주소. 미리보기 태그를 담고 session.html?id=NN 으로 바로 이동
//  - *.html          : <!-- og:start --> ~ <!-- og:end --> 사이 태그를 다시 씀
// 사용: node tools/og/build.cjs   (글꼴은 assets/vendor/pretendard 를 씀)
const { chromium } = require("playwright");
const fs = require("fs"), path = require("path");
const root = path.resolve(__dirname, "../..");
const SITE = "https://odysseyiliadr2d2.github.io/ai-study/";
const cur = JSON.parse(fs.readFileSync(path.join(root, "data/curriculum.json"), "utf8"));
const ver = JSON.parse(fs.readFileSync(path.join(root, "data/version.json"), "utf8")).current;
const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
const pad = (n) => String(n).padStart(2, "0");

function card({ eyebrow, title, sub, chips = [], foot }) {
  return `<!doctype html><html><head><meta charset="utf-8">
<link rel="stylesheet" href="file://${root}/assets/vendor/pretendard/pretendard.css">
<style>
*{box-sizing:border-box;margin:0}
body{width:1200px;height:630px;background:#1f1f1f;color:#fff;font-family:Pretendard,sans-serif;padding:64px 72px;position:relative;overflow:hidden}
.bar{position:absolute;left:0;top:0;bottom:0;width:14px;background:#f26b1d}
.glow{position:absolute;right:-160px;top:-160px;width:520px;height:520px;border-radius:50%;background:radial-gradient(circle,#f26b1d55,#f26b1d00 70%)}
.wm{font-size:30px;font-weight:700;letter-spacing:-.01em}.wm span{color:#f26b1d}
.eb{margin-top:54px;font-size:26px;font-weight:700;color:#ff9a57;letter-spacing:.04em}
h1{margin-top:14px;word-break:keep-all;font-size:${title.length > 16 ? 62 : 74}px;line-height:1.18;font-weight:800;letter-spacing:-.02em;max-width:1000px}
h1 em{font-style:normal;background:linear-gradient(transparent 58%,#f26b1d88 58%,#f26b1d88 94%,transparent 94%);padding:0 4px}
p{margin-top:22px;word-break:keep-all;font-size:30px;line-height:1.45;color:#cfcac4;max-width:1000px}
.chips{position:absolute;left:72px;bottom:60px;display:flex;gap:12px}
.chips span{font-size:22px;font-weight:700;padding:8px 18px;border-radius:10px;background:#2f2f2f;color:#e8e4df}
.chips span.o{background:#f26b1d;color:#1f1f1f}
.foot{position:absolute;right:72px;bottom:68px;font-size:22px;color:#9c9791;font-weight:600}
</style></head><body><div class="bar"></div><div class="glow"></div>
<div class="wm">AX <span>Study</span></div>
<div class="eb">${esc(eyebrow)}</div><h1><em>${esc(title)}</em></h1><p>${esc(sub)}</p>
<div class="chips">${chips.map((c, i) => `<span class="${i === 0 ? "o" : ""}">${esc(c)}</span>`).join("")}</div>
<div class="foot">${esc(foot || "")}</div></body></html>`;
}

function tags({ title, desc, url, image }) {
  return `<!-- og:start (tools/og/build.cjs 가 다시 씀) -->
  <meta property="og:type" content="website">
  <meta property="og:site_name" content="AX Study">
  <meta property="og:locale" content="ko_KR">
  <meta property="og:title" content="${esc(title)}">
  <meta property="og:description" content="${esc(desc)}">
  <meta property="og:url" content="${esc(url)}">
  <meta property="og:image" content="${esc(image)}">
  <meta property="og:image:width" content="1200">
  <meta property="og:image:height" content="630">
  <meta property="og:image:alt" content="${esc(title)}">
  <meta name="twitter:card" content="summary_large_image">
  <!-- og:end -->`;
}
function inject(file, t) {
  const p = path.join(root, file); let h = fs.readFileSync(p, "utf8");
  h = h.replace(/\s*<!-- og:start[\s\S]*?<!-- og:end -->/, "");
  h = h.replace(/(<meta name="viewport"[^>]*>)/, `$1\n  ${t}`);
  fs.writeFileSync(p, h);
}

(async () => {
  const b = await chromium.launch();
  const page = await b.newPage({ viewport: { width: 1200, height: 630 } });
  const outDir = path.join(root, "assets/og"); fs.mkdirSync(outDir, { recursive: true });
  const shot = async (name, html) => {
    const f = path.join(outDir, "_tmp.html"); fs.writeFileSync(f, html);
    await page.goto("file://" + f); await page.evaluate(() => document.fonts.ready);
    await page.screenshot({ path: path.join(outDir, name + ".png") }); fs.unlinkSync(f);
  };
  const N = cur.sessions.length;
  const pages = [
    { file: "index.html", img: "site", title: "AX Study · AI 풀스택 스터디", desc: `AI 업무자동화부터 서비스·에이전트 개발까지 ${N}세션. 세션마다 개념·이론·실습 코드·실무 예제·퀴즈.`,
      card: { eyebrow: "AX 비즈니스 전환 스터디", title: `업무자동화부터 에이전트까지, ${N}세션`, sub: "개념 · 이론(도식) · 실습 코드 · 실무 예제 · 퀴즈를 카드로 봅니다", chips: ["6파트", `${N}세션`, "n8n · RAG · MCP · Agent"], foot: ver } },
    { file: "roadmap.html", img: "roadmap", title: "로드맵 · AX Study", desc: "AI 업무자동화·서비스·에이전트 개발 로드맵 문서와 버전 기록. PDF·Word로 내려받을 수 있습니다.",
      card: { eyebrow: "ROADMAP", title: "AI 업무자동화·서비스·에이전트 개발 로드맵", sub: "6단계 흐름 · 도구 지도 · 마일스톤 · 버전 기록", chips: ["PDF", "Word", "도식 9개"], foot: ver } },
    { file: "dashboard.html", img: "dashboard", title: "대시보드 · AX Study", desc: "스터디 멤버의 세션별 개념·이론, 실습, 퀴즈 진행 현황.",
      card: { eyebrow: "DASHBOARD", title: "스터디 대시보드", sub: "멤버별 · 세션별 진행 현황을 한눈에", chips: ["카카오 로그인", "운영자 승인"], foot: ver } },
    { file: "me.html", img: "site", title: "내 진도 · AX Study", desc: "카카오로 로그인하고 스터디 가입을 신청하면 진도가 대시보드에 모입니다." },
    { file: "session.html", img: "site", title: "세션 · AX Study", desc: `AX Study ${N}세션 — 개념·이론·실습 코드·실무 예제·퀴즈.` }
  ];
  for (const pg of pages) {
    if (pg.card) await shot(pg.img, card(pg.card));
    inject(pg.file, tags({ title: pg.title, desc: pg.desc, url: SITE + (pg.file === "index.html" ? "" : pg.file), image: `${SITE}assets/og/${pg.img}.png?v=${ver}` }));
  }

  // 세션별 공유 주소 s/NN.html
  fs.mkdirSync(path.join(root, "s"), { recursive: true });
  for (const s of cur.sessions) {
    const part = cur.parts.find((p) => p.id === s.part);
    await shot(`s${pad(s.id)}`, card({ eyebrow: `SESSION ${pad(s.id)} · PART ${part.id} ${part.title}`, title: s.title, sub: s.summary, chips: [s.level, ...s.tags].slice(0, 4), foot: "개념 · 이론 · 실습 · 예제 · 퀴즈" }));
    const target = `../session.html?id=${s.id}`;
    fs.writeFileSync(path.join(root, "s", `${pad(s.id)}.html`), `<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <link rel="icon" href="../assets/icon/favicon.svg" type="image/svg+xml">
  <link rel="icon" href="../assets/icon/favicon-32.png" sizes="32x32" type="image/png">
  ${tags({ title: `${pad(s.id)}. ${s.title} · AX Study`, desc: s.summary, url: `${SITE}s/${pad(s.id)}.html`, image: `${SITE}assets/og/s${pad(s.id)}.png?v=${ver}` })}
  <title>${esc(`${pad(s.id)}. ${s.title} · AX Study`)}</title>
  <meta http-equiv="refresh" content="0; url=${target}">
  <script>location.replace("${target}" + location.hash);</script>
</head>
<body><p><a href="${target}">${esc(`${pad(s.id)}. ${s.title}`)} 세션으로 이동</a></p></body>
</html>
`);
  }
  await b.close();
  console.log("미리보기 이미지", fs.readdirSync(outDir).length, "개, 세션 공유 주소", N, "개");
})();
