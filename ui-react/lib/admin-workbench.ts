export const RUNNER_DRAFT_ONLY = "draft_only"
export const RUNNER_AGENT = "agent"
export const RUNNER_IM_FETCH = "im_fetch"

export const runnerLabels: Record<string, string> = {
  [RUNNER_DRAFT_ONLY]: "Admin 投料",
  [RUNNER_AGENT]: "Agent 作业",
  [RUNNER_IM_FETCH]: "IM 抓取",
}

export interface GatherTaskModelDisplayInput {
  runnerType?: string | null
  model?: string | null
  jobFetchUserId?: string | null
  jobFetchChannel?: string | null
}

export function getWorkbenchScopeLabel(runner?: string | null) {
  if (runner === RUNNER_AGENT) {
    return "Agent 作业"
  }
  if (runner === RUNNER_DRAFT_ONLY) {
    return "Admin 投料"
  }
  if (runner === RUNNER_IM_FETCH) {
    return "IM 抓取"
  }
  return "全部作业"
}

export function canAdminReRunGatherTask(runnerType?: string | null) {
  return !runnerType || runnerType === RUNNER_DRAFT_ONLY
}

export function isAgentRunner(runnerType?: string | null) {
  return runnerType === RUNNER_AGENT
}

export function formatGatherTaskModelDisplay(
  task: GatherTaskModelDisplayInput,
  formatModelLabel?: (model?: string | null) => string
) {
  if (task.runnerType === RUNNER_IM_FETCH) {
    const userId = task.jobFetchUserId?.trim() || "-"
    const channel = task.jobFetchChannel?.trim() || "-"
    return `${userId} / ${channel}`
  }
  if (formatModelLabel) {
    return formatModelLabel(task.model)
  }
  return task.model?.trim() || "-"
}

export function isImFetchRunner(runnerType?: string | null) {
  return runnerType === RUNNER_IM_FETCH
}

export const gatherTaskStateLabels: Record<number, string> = {
  0: "待处理",
  1: "处理中",
  2: "已完成",
  3: "失败",
}

export const gatherTaskStateClass: Record<number, string> = {
  0: "border-slate-200 bg-slate-50 text-slate-600",
  1: "border-blue-200 bg-blue-50 text-blue-700",
  2: "border-emerald-200 bg-emerald-50 text-emerald-700",
  3: "border-rose-200 bg-rose-50 text-rose-700",
}

export function buildGatherTaskQueueHref(options?: {
  taskId?: number
  sourceId?: number
  runner?: string
}) {
  const params = new URLSearchParams({ tab: "tasks" })
  if (options?.taskId) {
    params.set("taskId", String(options.taskId))
  }
  if (options?.sourceId) {
    params.set("sourceId", String(options.sourceId))
  }
  if (options?.runner) {
    params.set("runner", options.runner)
  }
  return `/admin/entry?${params.toString()}`
}
