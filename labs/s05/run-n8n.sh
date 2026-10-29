# 1) 데이터 볼륨 만들기 (워크플로·자격증명이 여기에 저장됩니다)
docker volume create n8n_data

# 2) n8n 실행 — docs.n8n.io 'Install with Docker' 의 명령에 서울 시간대만 넣었습니다
docker run -it --rm \
  --name n8n \
  -p 5678:5678 \
  -e GENERIC_TIMEZONE="Asia/Seoul" \
  -e TZ="Asia/Seoul" \
  -e N8N_ENFORCE_SETTINGS_FILE_PERMISSIONS=true \
  -v n8n_data:/home/node/.n8n \
  n8nio/n8n

# 3) 브라우저에서 http://localhost:5678 접속 → 소유자 계정 만들기
# 업데이트: docker pull n8nio/n8n 후 컨테이너를 다시 실행
