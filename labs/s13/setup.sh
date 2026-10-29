# 1) 설치 — 셋 중 팀이 쓰는 것만 설치해도 됩니다
# Claude Code (공식 네이티브 설치, macOS·Linux·WSL)
curl -fsSL https://claude.ai/install.sh | bash
claude --version && claude doctor        # 설치 · 설정 점검

# Codex CLI (오픈소스, npm 또는 Homebrew)
npm install -g @openai/codex
codex --version

# Antigravity CLI (구 Gemini CLI 개인 사용자의 이전 대상)
curl -fsSL https://antigravity.google/cli/install.sh | bash
agy --version
agy plugin import gemini                 # 기존 ~/.gemini 설정이 있으면 가져오기

# 2) 프로젝트에서 시작
cd ticket-desk-api
claude                                   # /init 으로 CLAUDE.md 초안 생성 가능
codex                                    # /init 으로 AGENTS.md 초안 생성 가능
agy

# 3) MCP 연결 예 — 프로젝트 공유 설정(.mcp.json)으로 등록
claude mcp add --transport http --scope project issues https://mcp.example.internal/mcp
claude mcp list                          # Connected 인지 확인 (Added 만으로는 연결 확인 아님)
codex mcp add issues --url https://mcp.example.internal/mcp --bearer-token-env-var ISSUES_TOKEN
