(async function () {
  const AX = window.AX;
  const { esc, getJSON, topbar, footer, SITE } = AX;
  const main = document.getElementById("main");
  const v = await getJSON("data/version.json").catch(() => ({ current: "", updated: "" }));
  topbar("resources", v.current);
  footer(v.current, v.updated);
  const [cases, tl] = await Promise.all([getJSON("data/cases.json").catch(() => null), getJSON("data/timelines.json").catch(() => null)]);
  const n = (x) => (x ? x.length : 0);
  const items = [
    { href: "cases.html", eb: "CASES", t: "AI 도입 사례", d: "국내·해외 은행·증권·보험·캐피탈과 공공 분야의 AI 도입 사례를 기사 형식으로 정리했습니다.", meta: cases ? `${n(cases.articles)}편 · ${esc(cases.updated)} 기준` : "" },
    { href: "guidelines.html#ai", eb: "TIMELINE", t: "금융분야 AI 가이드라인", d: "금융위·금감원 가이드라인, AI 기본법, EU AI Act 등 금융 AI 규제의 흐름을 타임라인으로 봅니다.", meta: tl ? `${n(tl.ai && tl.ai.events)}건 · ${esc(tl.updated)} 기준` : "" },
    { href: "guidelines.html#netsep", eb: "TIMELINE", t: "금융권 망분리 규제", d: "망분리 의무화부터 2024년 개선 로드맵, 생성형 AI 허용과 지금 진행 상황까지 정리했습니다.", meta: tl ? `${n(tl.netsep && tl.netsep.events)}건 · ${esc(tl.updated)} 기준` : "" },
    { href: "setup.html", eb: "SETUP", t: "실습 환경 구성", d: "Python·Java 설치, 가상환경과 Maven Wrapper, API 키, 사내 폐쇄망 반입 방법을 안내합니다.", meta: "PDF · Markdown 내려받기" },
    { href: "roadmap.html", eb: "ROADMAP", t: "로드맵 문서", d: "AI 업무자동화·서비스·에이전트 개발 로드맵 전체와 버전 기록입니다.", meta: `${esc(v.current)} · PDF · Word` },
    { href: SITE.glossaryUrl, eb: "GLOSSARY", t: "실무 AI 용어집", d: "세션에 나오는 AI 용어를 입문·심화 설명과 출처로 찾아봅니다.", meta: "새 창", ext: true }
  ];
  main.innerHTML = `
    <section class="hero"><div class="eyebrow">RESOURCES</div><h1 class="display">자료실</h1>
      <p class="lead">세션 밖에서 함께 보면 좋은 자료입니다. 사례와 규제 타임라인은 출처를 확인한 내용만 싣고, 날짜를 적어 둡니다.</p></section>
    <div class="grid res-grid">${items.map((i) => `
      <a class="card" href="${esc(i.href)}" ${i.ext ? 'target="_blank" rel="noopener"' : ""}>
        <div class="sess">${i.eb}</div><h3><span class="hl">${esc(i.t)}</span></h3><p>${esc(i.d)}</p>
        <div class="small muted">${i.meta}</div></a>`).join("")}</div>`;
})();
