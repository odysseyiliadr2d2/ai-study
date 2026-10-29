npm i -g vercel
vercel login
vercel link                        # .vercel/project.json 생성 (orgId, projectId) — 이 폴더는 커밋하지 않음

# 앱 환경변수: 운영(Production)과 미리보기(Preview)에 각각 (값은 프롬프트로 입력)
vercel env add NEXT_PUBLIC_SUPABASE_URL production
vercel env add NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY production
vercel env add ANTHROPIC_API_KEY production
vercel env add N8N_WEBHOOK_URL production
vercel env add N8N_WEBHOOK_SECRET production
vercel env add APP_BASE_URL production

# 파이프라인 비밀: GitHub 저장소 Secrets (gh CLI)
gh secret set VERCEL_TOKEN                      # vercel.com 계정 설정의 Tokens 에서 발급
gh secret set VERCEL_ORG_ID     --body "$(jq -r .orgId .vercel/project.json)"
gh secret set VERCEL_PROJECT_ID --body "$(jq -r .projectId .vercel/project.json)"
gh variable set PROD_URL --body "https://ticket-desk-web.vercel.app"   # 실제 운영 도메인으로
