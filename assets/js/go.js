// 이동 전용 페이지(s/NN.html, changelog.html)에서 쓰는 리다이렉트.
// CSP(script-src 'self') 때문에 인라인 스크립트 대신 이 파일을 씁니다.
(function () {
  var s = document.currentScript, to = s && s.getAttribute("data-to");
  if (!to || !/^(\.\.\/)?[\w-]+\.html(\?[\w=&-]*)?(#[\w-]*)?$/.test(to)) return;   // 사이트 안 페이지로만
  location.replace(to + (s.hasAttribute("data-keep-hash") ? location.hash : ""));
})();
