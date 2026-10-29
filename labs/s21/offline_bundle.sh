# ── 인터넷 구간(외부 PC)에서 반입 꾸러미 만들기
mkdir -p bundle/{models,images,wheels}

# 모델 파일 (라이선스 파일 LICENSE 도 함께 받아집니다)
hf download Qwen/Qwen3.5-9B --local-dir bundle/models/Qwen3.5-9B

# 컨테이너 이미지 — 태그는 latest 대신 버전을 고정
docker pull vllm/vllm-openai:<버전>
docker save vllm/vllm-openai:<버전> -o bundle/images/vllm-openai.tar

# 파이썬 휠 — 사내 서버와 같은 OS·파이썬 버전에서 받아야 합니다
pip download -r requirements.txt -d bundle/wheels

# 해시 매니페스트 (예제 코드 manifest.py)
python manifest.py make bundle > bundle/MANIFEST.json

# ── 사내 폐쇄망에서 풀기
python manifest.py verify bundle bundle/MANIFEST.json   # 해시가 하나라도 다르면 중단
docker load -i bundle/images/vllm-openai.tar
pip install --no-index --find-links bundle/wheels -r requirements.txt
export HF_HUB_OFFLINE=1        # 실행 중 외부로 나가려는 시도를 막습니다
