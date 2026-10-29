// n8n Code 노드 (JavaScript, Run Once for All Items)
// 입력: Merge(Append) 로 합쳐진 Gmail 항목 + RSS 항목
// 출력: LLM 에 넘길 digest 문자열 하나 (항목 1개)
const MAX_BODY = 500;      // 메일 한 통에서 쓸 최대 글자 수
const MAX_MAILS = 30;      // 토큰 비용을 막는 상한

const mails = [];
const feeds = [];

for (const { json: j } of $input.all()) {
  if (j.link && j.title && !j.threadId) {
    // RSS 항목: title · link · contentSnippet
    const snippet = (j.contentSnippet || '').replace(/\s+/g, ' ').slice(0, 300);
    feeds.push(`- ${j.title} (${j.link})\n  ${snippet}`);
  } else {
    // Gmail(Simplify 켬) 항목: From · Subject · snippet
    // 필드 이름은 실행 기록(Executions)에서 한 번 확인하세요
    const from = j.From || j.from || '';
    const subject = j.Subject || j.subject || '(제목 없음)';
    const body = (j.snippet || j.text || '').replace(/\s+/g, ' ').slice(0, MAX_BODY);
    mails.push(`- [${from}] ${subject}\n  ${body}`);
  }
}

const digest = [
  `## 받은 메일 ${mails.length}건`,
  ...mails.slice(0, MAX_MAILS),
  '',
  `## RSS 새 글 ${feeds.length}건`,
  ...feeds,
].join('\n');

return [{
  json: {
    date: $now.toFormat('yyyy-MM-dd'),
    mailCount: mails.length,
    feedCount: feeds.length,
    digest,
  },
}];
