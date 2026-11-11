# 기본 트랙 — Next.js 프로젝트 (Node.js 24 LTS 기준, Next.js 16 최소 요구 20.9)
npx create-next-app@latest ticket-desk-web --yes   # TypeScript · App Router 기본값
cd ticket-desk-web
npm install zod
npm run dev                                        # http://localhost:3000/requests

# API 확인
curl -s -X POST localhost:3000/api/requests -H 'Content-Type: application/json' \
  -d '{"title":"월말 잔고 추출 요청","body":"11월 말 기준 상품별 잔고를 엑셀로 받고 싶습니다"}'
curl -s 'localhost:3000/api/requests?status=RECEIVED'
curl -s -X POST localhost:3000/api/requests -H 'Content-Type: application/json' -d '{"title":"짧"}'   # 400
