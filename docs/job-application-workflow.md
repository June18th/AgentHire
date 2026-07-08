# Job Application Workflow

This document describes the current job-application workflow foundation for autumn recruitment and personal delivery tracking.

## Goal

JobClaw should manage a user's job-search loop, not only recommend job posts:

```text
discover -> evaluate -> prepare -> submit -> follow up -> interview -> offer/reject -> review
```

The first implemented slice is the action signal layer. It turns existing application records into actionable work items without adding new database columns.

## Existing Domain

Backend domain:

```text
backend/src/main/java/com/git/hui/jobclaw/application/
```

Important objects:

- `JobApplicationEntity`: one user's application record.
- `JobApplicationStatusEnum`: state machine for application progress.
- `JobApplicationEventEntity`: written tests, interviews, follow-ups, and other timeline events.
- `JobApplicationStatusLogEntity`: status transition history.
- `JobApplicationService`: lifecycle operations.
- `JobApplicationController`: `/api/user/applications/**`.

## Action Signals

Every `JobApplicationVo` now includes runtime-computed action fields:

| Field | Meaning |
|---|---|
| `deadlineAt` | Parsed deadline timestamp when a date can be recognized. |
| `daysUntilDeadline` | Days from today to the deadline. |
| `deadlineRisk` | `NONE`, `UNKNOWN`, `EXPIRED`, `DUE_TODAY`, `DUE_SOON`, `THIS_WEEK`, `NORMAL`. |
| `followUpOverdue` | Whether `nextFollowUpAt` is due for a non-terminal application. |
| `actionPriority` | `A`, `B`, `C`, or `NONE`. |
| `suggestedNextAction` | Human-readable next step for the user or agent. |
| `actionReason` | Explanation for why the action was selected. |
| `nextKeyEvent` | Nearest future written-test, interview, HR, or Offer event with preparation guidance. |

The rules live in:

```text
backend/src/main/java/com/git/hui/jobclaw/application/service/JobApplicationActionAdvisor.java
```

These signals are intentionally deterministic. They are suitable for UI badges, IM reminders, and agent prompts.

Current signal rules:

- A priority: overdue follow-up, deadline today, or deadline within 3 days.
- B priority: expired deadline, deadline within 7 days, or submitted for 7+ days without a follow-up plan.
- C priority: active early-stage records that should still be reviewed.
- Terminal records (`REJECTED`, `GAVE_UP`, `EXPIRED`, `CLOSED`) do not produce action items.

Deadline parsing supports normalized dates such as `2026-07-10`, Chinese date text such as `2026年07月10日 23:59 截止`, and month/day-only text such as `7月10日 23:59 截止`. Month/day-only text is inferred against the current year, with a small rollover guard for dates that clearly belong to the next year.

Suggested next actions are Chinese, user-facing instructions. They should remain deterministic and should not call an LLM.

## Default Follow-Up Plan

The workflow now creates a default follow-up plan when the user advances the application:

- When a new application is created directly as `SUBMITTED`, `nextFollowUpAt` defaults to `submittedAt + 7 days` if the user did not set it.
- When an existing application changes to `SUBMITTED`, `nextFollowUpAt` defaults to `submittedAt + 7 days` if it is still empty.
- When an application advances to `WRITTEN_TEST`, `INTERVIEW_1`, `INTERVIEW_2`, or `HR_INTERVIEW`, `nextFollowUpAt` defaults to `now + 3 days` if there is no future reminder.
- When an application advances to `OFFER`, `nextFollowUpAt` defaults to `now + 2 days` if there is no future reminder.
- When the user adds a `WRITTEN_TEST`, `INTERVIEW`, or `HR` event, `nextFollowUpAt` defaults to `eventTime + 1 day` if there is no future reminder.
- When the user adds an `OFFER` event, `nextFollowUpAt` defaults to `eventTime + 2 days` if there is no future reminder.
- When an event implies a legal status transition, the application status is advanced automatically, such as `SUBMITTED + INTERVIEW -> INTERVIEW_1` or `HR_INTERVIEW + OFFER -> OFFER`.
- When the user completes a follow-up without choosing a new reminder date, `nextFollowUpAt` defaults to `now + 7 days`.
- Explicit user-provided `nextFollowUpAt` always wins and is not overwritten.

This keeps the personal job-search loop moving without requiring the user to manually schedule every reminder. It also prevents submitted applications from silently disappearing after the user clicks "已跟进".

## Action Items API

Return the user's most actionable, non-terminal applications:

```http
GET /api/user/applications/action-items?limit=20
```

Sorting order:

```text
actionPriority A -> B -> C
earlier deadline first
earlier follow-up first
higher manual priority first
recently updated first
```

This endpoint is the backend hook for:

- "today's job-search plan"
- overdue follow-up reminders
- deadline-risk cards
- weekly application review

## Briefing API

Return a compact, structured brief for IM reminders, chat agents, and dashboard entry points:

```http
GET /api/user/applications/brief?limit=5
```

The response includes:

- counts for total, active, action items, A/B/C priorities, overdue follow-ups, deadline risks, stale submitted records, interview, and offer records
- `staleSubmitted`, counting submitted applications older than 7 days with no follow-up plan
- `todayEvents` and `next7DayEvents`, counting written-test, interview, HR, and Offer events for briefing and reminders
- `upcomingEvents`, the first few written-test, interview, HR, and Offer events from today through the next 7 days, enriched with application company, position, current status, event urgency, hours-until-event, and deterministic preparation guidance
- a deterministic summary sentence
- `topActions`, using the same action-item ordering rules

This endpoint is intentionally not LLM-generated. Agents should use it as factual context and then decide whether to summarize, rewrite, or ask the user for confirmation.

## IM Brief Command

The same deterministic brief is available from IM/chat system commands:

```text
/brief
/today
```

The command formats `JobApplicationService.brief(userId, 5)` directly. It returns the user's action count, A-priority count, overdue follow-ups, today's events, next-7-day events, top actions, and upcoming important written-test/interview/HR/Offer events.

This command does not call an LLM, so it is suitable for stable daily reminders and can later be reused by proactive scheduled notifications.

## Current Frontend Integration

The action signal layer is now surfaced in the main personal job-search pages:

| Page | Integration |
|---|---|
| `/` | Shows a compact "today's action" entry point next to the job list, including A-priority, overdue follow-up, due-today, due-soon, stale-submitted, and today-event metrics. |
| `/applications` | Shows action priority cards, quiet-submission review, stage board, row hints, detail reasons, status-aware event templates, the next key event in application detail, today's event preparation hints, and CSV export fields. |
| `/calendar` | Adds an action-priority side panel next to deadline, follow-up, and event dates; uses parsed `deadlineAt` before raw deadline text; supports completing follow-ups inline and exporting the current month/week schedule as CSV. |
| `/materials` | Shows material-related application actions for resume and portfolio preparation, can copy a per-application material kit with the primary resume, material links, snippets, and next-step advice, and supports JSON backup/restore for local material data. |
| Global nav | Shows a live action count beside "我的求职" and refreshes after application changes. |

CSV exports from `/applications` include the deterministic action fields so weekly review can happen outside the app when needed.

When the user clicks "已跟进" in `/applications` or `/calendar`, the backend writes a `FOLLOW_UP` event and returns a refreshed `nextFollowUpAt` so the user can see the next reminder that was set.

The `/calendar` CSV export is intentionally lightweight. It is meant for weekly planning, backup, and import into external tools, not as a replacement for the in-app timeline.

The `/materials` page currently stores resume versions, material links, snippets, and checklist state in browser local storage. Users should use the JSON backup/restore controls before changing browsers, clearing browser data, or reinstalling the frontend. The copied material kit is a practical bridge for outreach, email, and manual application forms until a persisted material-application relation is added.

## Next Practical Slice

The next feature can connect these same action signals to IM reminders and agent prompts so the system can proactively tell the user what to handle next.

Use `actionPriority`, `deadlineRisk`, and `suggestedNextAction` as deterministic facts. Do not ask the LLM to invent application status, deadline, or follow-up data; use the LLM only to explain, summarize, or rewrite based on existing application and profile data.

Suggested next backend/frontend slices:

- IM daily briefing: reuse the `/brief` command formatter and send only when `actionCount > 0`.
- Persisted material linkage: allow resume versions or portfolio links to be associated with a specific application instead of only local browser state.
- Proactive reminders: schedule daily IM reminders based on `/brief`, quiet-submission review, due-soon deadlines, and upcoming key events.
- Review analytics: add weekly summaries for conversion rate, stale submitted records, interview outcomes, and offer decisions.
