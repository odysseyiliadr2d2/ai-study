# n8n 의 ① Schedule Trigger(매일 07:30) 를 운영체제 스케줄러로 대신합니다.
# 먼저 서버 시간대가 Asia/Seoul 인지 확인합니다 (n8n 의 GENERIC_TIMEZONE 과 같은 문제).
timedatectl | grep "Time zone"

# Linux · macOS: crontab -e 에 한 줄 추가 (경로는 내 폴더로)
30 7 * * * cd $HOME/labs/s05/python && .venv/bin/python briefing.py >> out/cron.log 2>&1

# Windows: 작업 스케줄러에 매일 07:30 등록
# schtasks /Create /SC DAILY /ST 07:30 /TN "AI 아침 브리핑" /TR "C:\labs\s05\python\.venv\Scripts\python.exe C:\labs\s05\python\briefing.py"

# 다음 날 아침 확인: out/briefing-날짜.md 가 생겼는지, out/cron.log 에 오류가 없는지
