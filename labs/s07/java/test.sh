# 테스트 체크 (n8n 실습과 같은 다섯 가지 상황)
RUN="./mvnw -q compile exec:java -Dexec.mainClass=study.s07.InquiryApp"
rm -f processed.txt log.csv errors.log && rm -rf outbox

# 1) 정상 문의 · 2) FAQ 에 없는 문의 · 3) 승인 거절
$RUN                      # m001 은 y, m002 는 y, m003 · m004 는 n 을 입력

# 4) 무응답: 입력을 주지 않으면(EOF) 모두 '수동 처리' 로 기록되고 outbox 는 그대로
rm -f processed.txt && $RUN < /dev/null

# 5) 자격증명 오류: 키를 일부러 틀리게 → 메일마다 '오류' 기록, errors.log 확인
rm -f processed.txt && ANTHROPIC_API_KEY=sk-ant-wrong $RUN < /dev/null
cat errors.log
