# 운영 명령 모음 — 배포 확인 · 로그 · 롤백 (두 트랙)

# ── Java 트랙 (Kubernetes)
kubectl -n ticketdesk rollout status deployment/ticket-desk-api      # 배포 진행 확인
kubectl -n ticketdesk rollout history deployment/ticket-desk-api     # change-cause 에 커밋 SHA
kubectl -n ticketdesk get pods -l app=ticket-desk-api -o wide
kubectl -n ticketdesk logs -l app=ticket-desk-api --tail=200          # 최근 로그
kubectl -n ticketdesk logs deploy/ticket-desk-api | grep "traceId=8f2c"   # 세션 14의 traceId 로 추적
kubectl -n ticketdesk rollout undo deployment/ticket-desk-api        # 직전 버전으로 롤백
kubectl -n ticketdesk rollout undo deployment/ticket-desk-api --to-revision=3

# 헬스체크 직접 확인 (클러스터 안에서)
kubectl -n ticketdesk run curl --rm -it --image=curlimages/curl --restart=Never -- \
  curl -s http://ticket-desk-api/actuator/health/readiness

# ── 기본 트랙 (Vercel)
vercel rollback                    # 직전 운영 배포로 (Hobby 플랜은 직전 하나만)
vercel rollback status
vercel promote <deployment-url>    # 롤백 취소 · 특정 배포를 운영으로
curl -s https://ticket-desk-web.vercel.app/api/health

# ── 로컬에서 운영과 같은 이미지로 확인
docker build -t ticket-desk-api:local . && \
docker run --rm -p 8080:8080 --env-file .env.local ticket-desk-api:local
