// next.config.ts — Docker 로도 배포할 수 있게 standalone 출력을 켭니다 (Vercel 배포에는 영향 없음)
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",   // .next/standalone 에 server.js 와 필요한 node_modules 만 모음
};

export default nextConfig;
