import type { TaskListItem } from "@/lib/api"

export interface TaskDraftResult {
  parsed: boolean
  raw: string
  msg: string
  insertDraftIds: number[]
  updateDraftIds: number[]
  unchangedDraftIds: number[]
  skipDraftIds: number[]
  failedItems: string[]
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null
}

function toDraftIdList(value: unknown) {
  if (!Array.isArray(value)) {
    return []
  }
  return value
    .map((item) => Number(item))
    .filter((item) => Number.isFinite(item) && item > 0)
}

function toFailedItemList(value: unknown) {
  if (!Array.isArray(value)) {
    return []
  }
  return value.map((item) => String(item)).filter(Boolean)
}

export function emptyTaskDraftResult(raw = ""): TaskDraftResult {
  return {
    parsed: false,
    raw,
    msg: "",
    insertDraftIds: [],
    updateDraftIds: [],
    unchangedDraftIds: [],
    skipDraftIds: [],
    failedItems: [],
  }
}

export function parseTaskDraftResult(result?: string | null): TaskDraftResult {
  const raw = result?.trim() || ""
  if (!raw) {
    return emptyTaskDraftResult()
  }

  try {
    const parsed = JSON.parse(raw) as unknown
    if (!isRecord(parsed)) {
      return emptyTaskDraftResult(raw)
    }
    return {
      parsed: true,
      raw,
      msg: typeof parsed.msg === "string" ? parsed.msg : "",
      insertDraftIds: toDraftIdList(parsed.insertDraftIds ?? parsed.insert),
      updateDraftIds: toDraftIdList(parsed.updateDraftIds ?? parsed.update),
      unchangedDraftIds: toDraftIdList(parsed.unchangedDraftIds ?? parsed.unchanged),
      skipDraftIds: toDraftIdList(parsed.skipDraftIds ?? parsed.skip),
      failedItems: toFailedItemList(parsed.failedItems ?? parsed.failed),
    }
  } catch {
    return emptyTaskDraftResult(raw)
  }
}

export function getTaskDraftIds(result: TaskDraftResult) {
  return Array.from(
    new Set([
      ...result.insertDraftIds,
      ...result.updateDraftIds,
      ...result.unchangedDraftIds,
      ...result.skipDraftIds,
    ])
  )
}

export function buildDraftListHref(taskId: number, draftIds: number[], sourceId?: number, runnerType?: string) {
  const params = new URLSearchParams({
    draftIds: draftIds.join(","),
    sourceTaskId: String(taskId),
  })
  if (sourceId) {
    params.set("sourceId", String(sourceId))
  }
  if (runnerType) {
    params.set("runner", runnerType)
  }
  return `/admin/drafts?${params.toString()}`
}

export function formatDateTimeStr(value?: string | number | null) {
  if (value === undefined || value === null || value === "") {
    return "-"
  }

  const normalized = typeof value === "string" && /^\d+$/.test(value) ? Number(value) : value
  const date = new Date(normalized)
  if (Number.isNaN(date.getTime())) {
    return String(value)
  }

  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")} ${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}:${String(date.getSeconds()).padStart(2, "0")}`
}

export function findTaskInList(taskList: TaskListItem[], taskId?: number | null) {
  if (!taskId) {
    return null
  }
  return taskList.find((task) => task.taskId === taskId) ?? null
}
