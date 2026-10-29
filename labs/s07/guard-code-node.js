// Code 노드 (Agent 와 Approval 사이, Run Once for Each Item)
// 에이전트 출력에 대한 마지막 안전장치 — 규칙에 걸리면 needs_human 을 강제로 켭니다
const o = $json.output || {};
const draft = String(o.reply_draft || '');
const reasons = [];

// 1) 약속·보상·가격처럼 담당자만 말할 수 있는 표현
if (/(환불|보상|무상|할인|\d+%|\d+원)/.test(draft)) reasons.push('금액·보상 언급');
// 2) 날짜 약속
if (/(\d{1,2}월\s*\d{1,2}일|까지\s*(완료|조치))/.test(draft)) reasons.push('일정 약속');
// 3) 개인정보 형태가 초안에 섞인 경우
if (/01[016789]-?\d{3,4}-?\d{4}|\d{6}-?[1-4]\d{6}/.test(draft)) reasons.push('개인정보 형태');
// 4) 장애 긴급 건은 항상 사람
if (o.category === '장애·오류' && o.urgency === '높음') reasons.push('긴급 장애');
// 5) 너무 짧거나 비어 있는 초안
if (draft.length < 30) reasons.push('초안 부족');

return {
  json: {
    output: {
      ...o,
      needs_human: Boolean(o.needs_human) || reasons.length > 0,
      guard_reasons: reasons.join(', '),
    },
  },
};
