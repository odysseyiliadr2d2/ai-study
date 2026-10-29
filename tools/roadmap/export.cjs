// 로드맵 페이지 → 다운로드용 PDF · Word 파일 (downloads/)
// 사용: python3 -m http.server 8000 (저장소 루트) 을 띄운 뒤
//       node tools/roadmap/export.cjs http://localhost:8000
const { chromium } = require("playwright");
const fs = require("fs"), path = require("path"), os = require("os");
const { execFileSync } = require("child_process");
const base = (process.argv[2] || "http://localhost:8000").replace(/\/$/, "");
const root = path.resolve(__dirname, "../..");
const ver = JSON.parse(fs.readFileSync(path.join(root, "data/version.json"), "utf8")).current;
const out = path.join(root, "downloads");
const name = `AI-로드맵-${ver}`;

(async () => {
  fs.mkdirSync(out, { recursive: true });
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1000, height: 1200 }, deviceScaleFactor: 2, colorScheme: "light" });
  await p.goto(base + "/roadmap.html");
  await p.waitForSelector(".rm-fig svg");
  await p.waitForTimeout(500);

  // 1) PDF — 인쇄용 스타일(@media print)로 A4 출력
  await p.pdf({ path: path.join(out, name + ".pdf"), format: "A4", printBackground: true,
    margin: { top: "16mm", bottom: "16mm", left: "14mm", right: "14mm" },
    displayHeaderFooter: true, headerTemplate: "<span></span>",
    footerTemplate: `<div style="font-size:8px;width:100%;text-align:center;color:#888">AI 업무자동화·서비스·에이전트 개발 로드맵 ${ver} · <span class="pageNumber"></span> / <span class="totalPages"></span></div>` });

  // 2) Word — 도식은 PNG로 바꿔 넣고 pandoc 으로 변환
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "rm-"));
  const figs = await p.$$(".rm-fig svg");
  for (let i = 0; i < figs.length; i++) await figs[i].screenshot({ path: path.join(tmp, `fig${i}.png`) });
  const html = await p.evaluate(() => {
    const doc = document.getElementById("doc").cloneNode(true);
    doc.querySelectorAll(".rm-fig").forEach((f, i) => { const s = f.querySelector("svg"); const img = document.createElement("img"); img.src = `fig${i}.png`; img.style.width = "16cm"; s.replaceWith(img); });
    doc.querySelectorAll(".grid-scroll").forEach((w) => w.replaceWith(...w.childNodes));
    const v = document.getElementById("versions").cloneNode(true);
    return `<h1>${document.getElementById("rm-title").textContent}</h1>${doc.innerHTML}<h2>버전 기록</h2>${v.querySelector("#rules").innerHTML}${v.querySelector("#history").innerHTML}`;
  });
  fs.writeFileSync(path.join(tmp, "doc.html"), `<!doctype html><meta charset="utf-8">${html}`);
  execFileSync("pandoc", ["doc.html", "-f", "html", "-t", "docx", "-o", path.join(out, name + ".docx"), "--resource-path", tmp], { cwd: tmp });
  await b.close();
  for (const e of ["pdf", "docx"]) console.log(`${name}.${e}`, (fs.statSync(path.join(out, `${name}.${e}`)).size / 1024).toFixed(0) + " KB");
})();
