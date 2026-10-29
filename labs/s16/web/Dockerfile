# Dockerfile — ticket-desk-web (Next.js standalone). 사내 서버에 올릴 때 사용합니다.
# docker build --build-arg NEXT_PUBLIC_SUPABASE_URL=... --build-arg NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=... -t ticket-desk-web .
# 폐쇄망: 기본 이미지는 사내 레지스트리 경로로 바꾸고(BASE_IMAGE), npm 은 .npmrc 로 사내 미러를 봅니다.
ARG BASE_IMAGE=node:24-bookworm-slim

FROM ${BASE_IMAGE} AS deps
WORKDIR /app
COPY package.json package-lock.json .npmrc* ./
RUN npm ci

FROM ${BASE_IMAGE} AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# NEXT_PUBLIC_ 값은 빌드할 때 JS 번들에 박힙니다 → build-arg 로 넘깁니다 (공개돼도 되는 값만)
ARG NEXT_PUBLIC_SUPABASE_URL
ARG NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

FROM ${BASE_IMAGE} AS runtime
WORKDIR /app
ENV NODE_ENV=production PORT=3000 HOSTNAME=0.0.0.0 NEXT_TELEMETRY_DISABLED=1
# standalone 출력에는 static · public 이 빠져 있어 따로 복사합니다
COPY --from=build --chown=node:node /app/.next/standalone ./
COPY --from=build --chown=node:node /app/.next/static ./.next/static
COPY --from=build --chown=node:node /app/public ./public
USER node
EXPOSE 3000
# 비밀값(ANTHROPIC_API_KEY 등)은 이미지에 넣지 않고 실행할 때 -e 또는 Kubernetes Secret 으로
CMD ["node", "server.js"]
