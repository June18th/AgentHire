# ui-react

JobClaw frontend built with Next.js static export.

V2 defaults to frontend/backend split deployment. In local development, run this app with `pnpm dev` and let it call the backend API. In Docker deployment, the static export is served by the `jobclaw-web` Nginx image.

## Install

```bash
pnpm install
```

## Local Development

```bash
pnpm dev
```

The local frontend dev server uses the same browser-facing port as split Docker:

```text
http://localhost:8088/
```

If `8088` is already occupied by the Docker gateway, use the fallback command:

```bash
pnpm run dev:3000
```

The development API base URL is read from `.env.development`:

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8087
```

## Build

```bash
pnpm build
```

`pnpm build` writes the static export to `.next-build`.

## AI Chat Page

The AI chat page lives at:

```text
app/chat/page.tsx
```

The page stores the current conversation id and recent messages in `localStorage`, loads available agents from the backend, and sends messages through the chat API helpers in `lib/api.ts`.

Assistant messages use a dedicated Markdown renderer:

```text
components/chat/MarkdownMessage.tsx
```

Supported Markdown features:

- headings
- paragraphs and line breaks
- bold text and inline code
- fenced code blocks
- blockquotes
- ordered and unordered lists
- tables
- links

The renderer is intentionally conservative: it does not render raw HTML, and links are only opened for `http`, `https`, and `mailto` URLs.

The chat message list owns its own scroll container. New messages keep the conversation pinned to the bottom only when the user is already near the bottom; otherwise the user's reading position is preserved. Avoid using `scrollIntoView` on message sentinels here because it can scroll the whole page instead of only the conversation panel.

For Docker split deployment, chat UI-only changes require rebuilding `jobclaw-web`:

```bash
docker compose -f docker/compose/compose.dev.yml -f docker/compose/compose.mysql.yml -f docker/compose/compose.frontend.yml build jobclaw-web
docker compose -f docker/compose/compose.dev.yml -f docker/compose/compose.mysql.yml -f docker/compose/compose.frontend.yml up -d jobclaw-web jobclaw-gateway
```

## Applications Page

The user-facing applications page lives at:

```text
app/applications/page.tsx
```

It manages personal job application records. Current behavior includes manual record creation, editing, deletion, status updates, status reopening for terminal records, company/position search, company type filtering, attention filtering, follow-up filtering, pending/overdue follow-up statistics, overdue row highlighting, and a quick complete-follow-up action.

The list view is intentionally table-like:

- Records are capped at 10 items per page.
- Table cells are center-aligned.
- Row and column borders are visible.
- Pagination is rendered below the table.

The page also includes:

- Todo cards for today to submit, today to follow up, today interviews/written tests, and overdue items.
- Summary cards for active applications, submitted-and-later stages, interview stages, and follow-up workload.
- Dashboard cards for status funnel, company type distribution, and Offer conversion.
- CSV export for the current filtered result set, not only the current page.
- Detail view with apply link, deadline, source, attention, next-step suggestion, status history, event records, and review notes.

Attention is stored through the backend `priority` field, but the UI labels it as `关注度`. Do not use `普通 / 重点 / 冲刺` wording in this module.

The homepage and internship job list pages also integrate with applications:

```text
app/page.tsx
app/internship/page.tsx
```

They fetch existing application records by job id and let the user mark a job as `感兴趣 / 准备投递 / 已投递` directly from the list. Existing records show their current application status and a `查看投递` action.

The three date inputs on this page are date-only fields in the UI:

```text
submittedAt       投递时间
deadline          截止时间
nextFollowUpAt    下次跟进时间
```

Related API helpers live in:

```text
lib/job-application-api.ts
```

Important user-facing endpoints used by the frontend:

```text
GET  /api/user/applications/list
GET  /api/user/applications/detail
POST /api/user/applications/save
POST /api/user/applications/status
POST /api/user/applications/reopen
GET  /api/user/applications/by-jobs
GET  /api/user/applications/events
GET  /api/user/applications/events/day
POST /api/user/applications/events/save
POST /api/user/applications/follow-up/complete
POST /api/user/applications/delete
```

## Environment Files

- `.env.development` for local dev
- `.env.test` for test builds
- `.env.production` for production static builds

Use `NEXT_PUBLIC_API_BASE_URL` to control the backend API base URL.

## Legacy Spring Static Copy

The legacy copy-to-Spring commands target:

```text
../backend/src/main/resources/static/
```

Prefer Docker split deployment or local `pnpm dev` during development. Use the legacy copy only when explicitly packaging a single Spring Boot jar:

```bash
pnpm run deploy:legacy-spring-static
```
