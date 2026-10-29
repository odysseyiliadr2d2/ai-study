// 로드맵 문서(HTML 내보내기) + 도식 JSX → 사이트용 data/roadmap.html
import fs from "node:fs";
import { transformSync } from "esbuild";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
const [docHtml, jsxDir, out] = process.argv.slice(2);
let html = fs.readFileSync(docHtml, "utf8");
const vars = new Set();
html = html.replace(/<figure data-embed="node\/([\w-]+)">(<figcaption>[\s\S]*?<\/figcaption>)?<\/figure>/g, (m, id, cap) => {
  const src = fs.readFileSync(`${jsxDir}/${id}.jsx`, "utf8");
  const js = transformSync(src, { loader: "jsx", jsx: "transform", format: "cjs" }).code;
  const mod = { exports: {} };
  new Function("module", "exports", "React", js)(mod, mod.exports, React);
  let svg = renderToStaticMarkup(React.createElement(mod.exports.default));
  const k = id.slice(0, 8);
  svg = svg.replace(/id="([\w-]+)"/g, `id="$1-${k}"`).replace(/url\(#([\w-]+)\)/g, `url(#$1-${k})`);
  for (const v of svg.matchAll(/var\((--[\w-]+)\)/g)) vars.add(v[1]);
  return `<figure class="rm-fig">${svg}${cap || ""}</figure>`;
});
html = html.replace(/<h1[\s\S]*?<\/h1>\s*/, "")                       // 제목은 페이지 머리에서
           .replace(/<p><time data-atom[\s\S]*?<\/p>\s*/, "")          // 날짜·작성자 줄
           .replace(/<h2 id="버전-이력">[\s\S]*$/, "")              // 버전 이력은 사이트 데이터로 아래에 따로
           .replace("맨 아래 &#39;버전 이력&#39;에", "맨 아래 &#39;버전 기록&#39;에");
html = html.replace(/<a href="(https?:[^"]+)"/g, '<a href="$1" target="_blank" rel="noopener"');
fs.writeFileSync(out, html);
console.log("figures ok, css vars:", [...vars].sort().join(" "));
