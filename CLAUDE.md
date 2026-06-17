# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

JobClaw (求职派) — an OpenClaw-style multi-agent system for job-search scenarios. IM channels (WeChat/DingDing/FeiShu) feed into a shared agent kernel that routes messages through intent classification to pluggable business agents (identity collection, job fetching, job recommendation). The `app` module assembles everything and hosts the web admin, job data pipeline, payments, and an MCP server.

**Language**: Chinese-first codebase. Comments, commit messages, docs, and UI copy are predominantly in Chinese.

## Commands

### Backend

```bash
./mvnw install -DskipTests                   # First-time build (all modules)
./mvnw spring-boot:run -pl app               # Run dev server (H2, port 8087)
./mvnw clean package -DskipTests             # Package all modules
./mvnw test                                  # Run all tests
./mvnw test -pl app -Dtest=ExcelLoadTest     # Run a single test class
```

### Frontend (from ui-react/)

```bash
pnpm install        # Install deps
pnpm dev            # Dev server at localhost:3000
pnpm build          # Static export to out/
pnpm run deploy     # Build + copy static assets to app/src/main/resources/static/
```

### First-time setup

```bash
cp .env.example .env                                              # Create env config
cp workspace/datas/jobclaw.mv.db workspace/datas/jobclaw-my.mv.db # Copy seed DB
# Edit .env: set JOBCLAW_DATABASE_NAME=jobclaw-my, fill ZHIPU_API_KEY
# macOS: set MCP_SERVERS_CONFIG=classpath:mcp-servers-mac.json
./mvnw install -DskipTests && ./mvnw spring-boot:run -pl app
```

## Architecture

### Module dependency

```
app ──► core (shared foundation)
    ──► channels/{wechat-clawbot, dingding, feishu}  ──► core
    ──► providers/{openai, zhipu, anthropic}          ──► core
    ──► plugins/{playwright, job-library}             ──► core
    ──► agents/{identity-collector, job-fetch, job-recommend} ──► core
```

New business agents go under `agents/`, not inside `app/` — unless they are purely part of the web/admin job-data pipeline.

### Message flow (the central pipeline)

```
IM message → Channel.adaptToReceive() → ChannelReceiveMessage
  → ChannelEventPublisher (Spring ApplicationEvent)
  → MsgRouter.onMessageReceived():
      1. IIdentityAgent.triggerToCollectIdentity()   (auto profile collection)
      2. SystemCommandDispatcher                      (/help, /agent, /reset)
      3. SessionAgentBinder.needsIntentRecognition()
      4. IntentClassifier.classify()                  (keyword + LLM composite)
      5. AgentRouter.route()                          (intent → agent)
      6. SessionAgentBinder.bind()
      7. BizAgent.process()/stream()
  → ChannelEventPublisher.publishMessageResponse()
  → Channel.responseToUser()
```

### Model resolution

Per-user via `ModelProviders.getModel(userId, modelType)`. Preference format: `provider#ModelName` (e.g. `zhipufree#GLM-4-Flash`). Provider configs live in `application.yml` under `agent.ai.providers`. Each provider module implements `ModelProvider` with an `apiStyle` key.

### App module business domains

`app/src/main/java/com/git/hui/jobclaw/`:
- `agents/` — LangGraph4J job-data workflow (TaskClassify → TaskGather → DraftWasher → DraftPublish)
- `gather/` — AI-powered job data collection (GatherAiAgent, OfferGatherService)
- `oc/` — Job info CRUD, drafts, MCP server endpoints
- `user/` — User management, WeChat QR login, membership, payments
- Domain pattern: `dao/entity/` → `dao/repository/` → `service/` → `convert/` (BO/Entity mapping)

### Identity system (3-layer user profile)

- **Soul** (`workspace/users/{id}/soul/`) — personality, career aspirations (AI-extracted)
- **Identity** (`workspace/users/{id}/user/`) — skills, education, experience
- **Info** (`workspace/users/{id}/info/`) — basic demographics

Auto-triggered by `IIdentityAgent.triggerToCollectIdentity()` in `MsgRouter` when a profile is incomplete.

### Runtime data paths

- `workspace/datas/` — H2 database files
- `workspace/users/{userId}/` — user profiles (soul.md, identity.md, info.md)
- `workspace/conversations/{userId}/` — chat session history (YAML)
- `logs/oc.log` — main application log

## Tech stack

- Java 21 (records, pattern matching, virtual threads)
- Spring Boot 4.0.5, Spring AI 2.0.0-M4, Spring Modulith
- LangGraph4J 1.6.0-rc4 (multi-agent workflow in `app/agents/`)
- JPA/Hibernate with H2 (dev) or MySQL + Liquibase (test/prod)
- JobRunr for background task scheduling (dashboard on port 8099)
- React 19 / Next.js 15 (static export) / TailwindCSS / shadcn/ui

## Configuration

All runtime config via `.env` (never committed). Key variables:
- `ZHIPU_API_KEY` / `SILICON_API_KEY` — model API keys
- `JOBCLAW_DATABASE_NAME` — H2 DB filename (default: `jobclaw`)
- `AGENT_AI_PREFERENCE_TEXT` / `AGENT_AI_PREFERENCE_VISION` — default models
- `MCP_SERVERS_CONFIG` — `classpath:mcp-servers.json` (Windows) or `classpath:mcp-servers-mac.json` (macOS/Linux)

Maven profiles: `dev` (default, H2), `test` (MySQL + Liquibase), `prod` (MySQL + Liquibase). Environment-specific configs live under `app/src/main/resources-env/{dev,test,prod}/`.

Local overrides: `application-private.yml` (git-ignored, auto-imported).

## Coding conventions

### Backend (Java)

- Agent registration: `@Component` implementing `BizAgent` → auto-discovered by `AgentRegistry`
- LLM calls: use `LlmCaller` or `BizAgentLlmCaller`, not raw Spring AI client APIs
- Memory: `SmartWindowChatMemory` with configurable context window (`agent.context.window.*`)
- Lombok throughout (`@Data`, `@Slf4j`, `@Builder`)
- Hutool (`cn.hutool`) utilities available
- No separate interfaces for service classes — direct implementation

### Frontend (TypeScript/React)

- Function components + hooks only
- `interface XxxProps` for component props, no `any`
- `useState<T>` with explicit generics
- Union types + `as const` instead of `enum`
- Styling: TailwindCSS with `cn()` (clsx + tailwind-merge)

### Anchor comments

- Mark AI-generated code with `AI-GENERATED`
- Use prefixes: `AIDEV-NOTE:`, `AIDEV-TODO:`, `AIDEV-QUESTION:`
- Do not delete existing `AIDEV-NOTE` comments
