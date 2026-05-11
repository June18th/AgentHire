# AGENTS.md

This file provides guidance to Qoder (qoder.com) when working with code in this repository.

## Project Overview

JobClaw (求职派) is an OpenClaw-style multi-agent practice project for job-search scenarios. It uses IM channels (WeChat/DingDing/FeiShu) as the user-facing entry, routes messages through a shared agent kernel, and composes identity collection, job fetching, job recommendation, task handling, model preferences, providers, and tools as replaceable modules.

The current architecture is not a single "job collection agent". Treat it as a modular agent runtime:
- `channels/` adapt external IM messages into the internal message model.
- `core/` owns the event bus, message router, intent classification, session-agent binding, agent registry, model resolution, memory, and shared agent contracts.
- `agents/` contains business agents such as identity collection, job fetching, and job recommendation.
- `providers/` isolates model-provider integrations.
- `plugins/` exposes tool capabilities such as Playwright browsing and job-library search.
- `app/` assembles the runtime and keeps the web/admin/job-data business domains.

**Tech stack**: Java 21, Spring Boot 4.0.5, Spring AI 2.0.0-M4, Spring Modulith, LangGraph4J, JPA/Hibernate, H2/MySQL, React 19, Next.js 15, TailwindCSS, shadcn/ui

## Commands

### Backend

```bash
./mvnw clean package -DskipTests    # Build all modules
./mvnw spring-boot:run              # Run with dev profile (H2, port 8087)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
./mvnw test -Dtest=ExcelLoadTest    # Run a single test class
./mvnw test                         # Run all tests
```

### Frontend (from ui-react/)

```bash
pnpm install         # Install dependencies
pnpm dev             # Dev server
pnpm build           # Production build (static export)
pnpm run deploy      # Build + copy to app/src/main/resources/static/
```

### Configuration

All runtime config goes in `.env` (copy from `.env.example`). Key variables:
- `ZHIPU_API_KEY` / `SILICON_API_KEY` — AI model API keys
- `JOBCLAW_SERVER_PORT` — defaults to 8087
- `AGENT_AI_PREFERENCE_TEXT` — default text model (format: `provider#ModelName`, e.g. `zhipufree#GLM-4-Flash`)
- `AGENT_AI_PREFERENCE_VISION` — default vision model

Maven profiles: `dev` (default, H2), `test` (MySQL + Liquibase), `prod` (MySQL + Liquibase).

### First-Time Setup

1. Copy `.env.example` to `.env` and fill in `ZHIPU_API_KEY`
2. Avoid polluting seed data — copy the H2 database and point to it:
   ```bash
   cp workspace/datas/jobclaw.mv.db workspace/datas/jobclaw-my.mv.db
   # then set JOBCLAW_DATABASE_NAME=jobclaw-my in .env
   ```
3. For local config overrides, copy `application.yml` to `application-private.yml` in the same directory (git-ignored, auto-imported via `optional:classpath:application-private.yaml`)
4. Run `./mvnw spring-boot:run` — browser opens at `http://localhost:8087`

Workspace directory layout at runtime:
- `workspace/datas/` — H2 database files
- `workspace/users/{userId}/` — user profiles (soul.md, identity.md, info.md)
- `workspace/conversations/{userId}/` — chat session history (YAML)

System commands in IM chat: `/help`, `/agents`, `/current`, `/agent <id>`, `/reset`

Full startup guide: `docs/getting-started.md`

## Architecture

### Maven Module Structure

```
JobClaw/              (parent POM, BOM management)
├── app/              Spring Boot application, assembles all modules
├── core/             Agent/Channel abstractions, routing, event bus, model providers
├── channels/
│   ├── wechat-clawbot/   WeChat ClawBot channel
│   ├── dingding/         DingDing channel
│   └── feishu/           FeiShu channel
├── providers/
│   ├── openai/       OpenAI-compatible model provider
│   ├── zhipu/        ZhiPu (智谱) model provider
│   ├── ali/          Ali (阿里百炼) model provider
│   └── anthropic/    Anthropic model provider
├── plugins/
│   ├── playwright/   Playwright browser automation tool
│   └── job-library/  Job library search tool
└── agents/
    ├── identity-collector-agent/  User identity/soul/info collection
    ├── job-fetch-agent/           Job info fetching, crawling, extraction
    └── job-recommend-agent/       Job recommendation
```

Module dependency: `app` depends on all other modules. `core` is the shared foundation that channels, providers, plugins, and agents depend on. Keep new business agents outside `app` unless they are only part of the web/admin job-data pipeline.

### Message Flow (the core pipeline)

This is the most important architectural concept — understanding it requires reading files across `core/`:

```
IM Message arrives (WeChat/DingDing/FeiShu)
  │
  ▼
AbsChannel.processMessage()          # channels/*/...Channel.java
  → adaptToReceive() → ChannelReceiveMessage
  → reportToAgent()
  │
  ▼
ChannelEventPublisher.publishMessageReceived()    # core/bus/
  → Spring ApplicationEvent → MessageReceivedEvent
  │
  ▼
MsgRouter.onMessageReceived()        # core/router/MsgRouter.java
  Step 1: IIdentityAgent.triggerToCollectIdentity()  — auto identity collection
  Step 2: SystemCommandDispatcher                     — /agent, /reset, /help commands
  Step 3: SessionAgentBinder.needsIntentRecognition() — skip if session already bound
  Step 4: IntentClassifier.classify()                 — keyword + LLM composite
  Step 5: AgentRouter.route()                         — map intent to agent
  Step 6: SessionAgentBinder.bind()                   — persist session-agent mapping
  Step 7: routeToAgent() → BizAgent.process()/stream()
  │
  ▼ (agent response)
ChannelEventPublisher.publishMessageResponse()  → MessageResponseEvent
  │
  ▼
MsgRouter.onMessageResponse()
  → ChannelRegistry.getChannel() → Channel.responseToUser()
```

### Key Abstractions in `core/`

| Abstraction | File | Purpose |
|---|---|---|
| `Channel` | `core/channel/Channel.java` | Interface for IM channels: `reportToAgent()`, `responseToUser()` |
| `AbsChannel` | `core/channel/AbsChannel.java` | Base channel with heartbeat, event publishing, `CommandLineRunner` auto-start |
| `ChannelBinder` | `core/channel/ChannelBinder.java` | Binds channel config to a channel instance |
| `BizAgent` | `core/agent/BizAgent.java` | Interface for business agents: `process()`, `stream()`, `getAgentIntro()` |
| `AbsBizAgent` | `core/agent/impl/AbsBizAgent.java` | Base agent with LLM calling, memory management |
| `AgentRegistry` | `core/router/intent/AgentRegistry.java` | Spring-based agent discovery and registry |
| `AgentRouter` | `core/router/intent/AgentRouter.java` | Routes classified intents to agents |
| `IntentClassifier` | `core/router/intent/IntentClassifier.java` | Classifies user message intent |
| `ModelProviders` | `core/providers/ModelProviders.java` | Resolves `Model` instances from user preferences |
| `LlmCaller` | `core/agent/llm/LlmCaller.java` | LLM call abstraction (sync + stream) |
| `ChannelEventPublisher` | `core/bus/ChannelEventPublisher.java` | Event bus for channel-agent decoupling |

### Model Resolution

Models are resolved per-user through `ModelProviders.getModel(userId, modelType)`:
- User preference format: `provider#ModelName` (e.g. `zhipufree#GLM-4-Flash`)
- Provider configs are in `application.yml` under `agent.ai.providers`
- Each provider module implements `ModelProvider` interface with a specific `apiStyle` key
- Results are cached in `ModelProviders.modelCache`

### Agent Identity System

The `identity-collector-agent` implements a 3-layer user profile:
- **Soul** (`soul/`) — personality, values, career aspirations (extracted via AI)
- **Identity** (`user/`) — skills, education, experience (rule-based + AI collectors)
- **Info** (`info/`) — basic demographic info (extracted from user-agent/chat metadata)

Collection is triggered automatically by `IIdentityAgent.triggerToCollectIdentity()` in `MsgRouter` when a user's profile is incomplete.

### App Module Business Domains

`app/src/main/java/com/git/hui/jobclaw/`:
- `agents/` — LangGraph4J-based job-data workflow chain (TaskClassify, TaskGather, DraftWasher, DraftPublish)
- `gather/` — AI-powered job data collection pipeline (GatherAiAgent, OfferGatherService)
- `oc/` — Job info CRUD, draft management, MCP server endpoints
- `user/` — User management, login (WeChat QR), membership, payments
- `configs/` — Global dictionary, environment config
- `openapi/` — Cross-platform account integration (PaiCoding)
- `constants/` — Enums per domain (oc, user, gather)
- `components/` — Cross-cutting: AsyncUtil, ID generator, .env processor

Each domain follows: `dao/entity/` → `dao/repository/` → `service/` → `convert/` (BO/Entity mapping).

### Frontend (ui-react/)

Static-export Next.js app deployed to Spring Boot's `static/` directory. Uses shadcn/ui (Radix primitives). Key paths:
- `app/` — pages: admin (jobs/drafts/users/coupons/dict), internal, internship, job, user
- `lib/api.ts` — axios-based HTTP client
- `lib/config.ts` — global dictionary cache from backend
- `components/ui/` — shadcn/ui components

## Coding Conventions

### Backend (Java)

- Java 21 features: Records, Pattern Matching, Virtual Threads
- Agent beans: `@Component` with `BizAgent` interface, auto-registered via `AgentRegistry`
- LLM calls: Use `LlmCaller` or `BizAgentLlmCaller`, not raw Spring AI APIs directly
- Memory: `SmartWindowChatMemory` with configurable context window (see `agent.context.window.*` props)
- No interfaces for service classes — direct implementation classes for simplicity
- Lombok is used throughout (`@Data`, `@Slf4j`, `@Builder`)
- Utilities: Hutool (`cn.hutool`) is available

### Frontend (TypeScript/React)

- Function components + Hooks only, no class components
- Props: `interface XxxProps`, no `any` types
- State: `useState<T>` with explicit generics
- Avoid `enum`, use union types + `as const`
- Styling: TailwindCSS with `cn()` (clsx + tailwind-merge)
- No responsive design needed (mobile-first IM bot companion)

## Anchor Comments

- AI-generated code: mark with `AI-GENERATED`
- Use prefixes: `AIDEV-NOTE:`, `AIDEV-TODO:`, `AIDEV-QUESTION:`
- Keep under 50 characters
- Do not delete existing `AIDEV-NOTE` comments
- Update anchors when modifying related code
- Add anchors for: long code (>30 lines), complex logic, important business logic, potential defects

## Working Principles

1. **Think before coding** — State assumptions, surface tradeoffs, ask when uncertain
2. **Simplicity first** — Minimum code that solves the problem, no speculative abstractions
3. **Surgical changes** — Touch only what's necessary, match existing style, clean up only your own orphans
4. **Goal-driven** — Define verifiable success criteria before implementing
