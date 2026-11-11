// 실습 환경 구성 페이지 → 내려받기용 PDF · Markdown (downloads/)
//   python3 -m http.server 8000 &   (저장소 루트)
//   node tools/setup/export.cjs http://localhost:8000
const { chromium } = require("playwright");
const fs = require("fs"), path = require("path"), os = require("os");
const { execFileSync } = require("child_process");
const base = (process.argv[2] || "http://localhost:8000").replace(/\/$/, "");
const root = path.resolve(__dirname, "../..");
const ver = JSON.parse(fs.readFileSync(path.join(root, "data/version.json"), "utf8")).current;
const name = `실습환경-구성-가이드-${ver}`;
const out = path.join(root, "downloads");

(async () => {
  fs.mkdirSync(out, { recursive: true });
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1000, height: 1200 }, colorScheme: "light" });
  await p.goto(base + "/setup.html");
  await p.waitForSelector(".rm-doc");
  // 인쇄본에는 Windows · macOS 를 모두 싣습니다
  await p.addStyleTag({ content: `[data-os],[data-os][hidden]{display:block!important} [data-os]::before{display:block;font-weight:700;font-size:13px;color:#b4501a;margin:10px 0 4px} [data-os="win"]::before{content:"Windows"} [data-os="mac"]::before{content:"macOS"} .os-pick,.rm-dl,.rm-toc,.code .bar button{display:none!important}` });
  await p.pdf({ path: path.join(out, name + ".pdf"), format: "A4", printBackground: true,
    margin: { top: "16mm", bottom: "16mm", left: "14mm", right: "14mm" }, displayHeaderFooter: true, headerTemplate: "<span></span>",
    footerTemplate: `<div style="font-size:8px;width:100%;text-align:center;color:#888">AX Study 실습 환경 구성 가이드 ${ver} · <span class="pageNumber"></span> / <span class="totalPages"></span></div>` });
  const html = await p.evaluate(() => {
    const d = document.querySelector(".rm-doc").cloneNode(true);
    d.querySelectorAll("[data-os]").forEach((x) => { const h = document.createElement("p"); h.innerHTML = `<b>${x.dataset.os === "win" ? "Windows" : "macOS"}</b>`; x.prepend(h); });
    d.querySelectorAll(".callout").forEach((c) => { const q = document.createElement("blockquote"); q.innerHTML = "<p>" + c.innerHTML + "</p>"; c.replaceWith(q); });
    d.querySelectorAll("[data-os]").forEach((x) => x.replaceWith(...x.childNodes));
    d.querySelectorAll(".code").forEach((c) => { const pre = c.querySelector("pre"); const lang = (c.querySelector("code").className.match(/language-(\w+)/) || [])[1] || ""; pre.querySelector("code").className = lang; c.replaceWith(pre); });
    return `<h1>실습 환경 구성</h1><p>${document.querySelector(".lead").textContent}</p>${d.innerHTML}`;
  });
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "setup-"));
  fs.writeFileSync(path.join(tmp, "s.html"), `<!doctype html><meta charset="utf-8">${html}`);
  execFileSync("pandoc", ["s.html", "-f", "html", "-t", "gfm", "--wrap=none", "-o", path.join(out, name + ".md")], { cwd: tmp });
  fs.copyFileSync(path.join(out, name + ".md"), path.join(root, "docs", "SETUP.md"));
  await b.close();
  for (const e of ["pdf", "md"]) console.log(`${name}.${e}`, (fs.statSync(path.join(out, `${name}.${e}`)).size / 1024).toFixed(0) + " KB");
})();
