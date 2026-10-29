// 정보계 아침 브리핑 — 배치 결과 메일 집계 (Code 노드, Run Once for All Items)
// 가정: 배치 서버가 보내는 메일 제목 형식
//   [BATCH][OK]   DW_DAILY_LOAD  02:14~02:51
//   [BATCH][FAIL] ODS_CUST_SNAP  03:05  ORA-01652
//   [BATCH][LATE] STG_ACCT_HIST  예정 04:00 → 05:12
const RE = /\[BATCH\]\[(OK|FAIL|LATE)\]\s+(\S+)\s*(.*)/;

const counts = { OK: 0, FAIL: 0, LATE: 0 };
const issues = [];
const unknown = [];

for (const { json: j } of $input.all()) {
  const subject = j.Subject || j.subject || '';
  const m = subject.match(RE);
  if (!m) { unknown.push(subject); continue; }
  const [, status, job, rest] = m;
  counts[status] += 1;
  if (status !== 'OK') {
    issues.push({
      status, job, detail: rest.trim(),
      body: (j.snippet || '').replace(/\s+/g, ' ').slice(0, 400),
    });
  }
}

return [{
  json: {
    date: $now.toFormat('yyyy-MM-dd'),
    okCount: counts.OK,
    failCount: counts.FAIL,
    lateCount: counts.LATE,
    issues,                       // LLM 에는 실패·지연 건만 넘깁니다
    unknownSubjects: unknown,     // 형식이 다른 메일은 따로 표시
    oneLine: `정상 ${counts.OK} · 실패 ${counts.FAIL} · 지연 ${counts.LATE}`,
  },
}];
