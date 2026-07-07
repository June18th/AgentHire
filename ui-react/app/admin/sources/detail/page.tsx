"use client"

import Link from "next/link"
import { useSearchParams } from "next/navigation"
import { useCallback, useEffect, useMemo, useState } from "react"
import {
  ArrowLeft,
  Archive,
  Clock3,
  DatabaseZap,
  ExternalLink,
  FileText,
  Loader2,
  RefreshCcw,
} from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import {
  fetchDraftList,
  fetchGatherSourceList,
  fetchTaskList,
  reRunGatherSource,
  updateGatherSourceStatus,
  type GatherSourceItem,
  type TaskListItem,
} from "@/lib/api"
import { cn } from "@/lib/utils"
import { useToast } from "@/hooks/use-toast"

const TASK_PAGE_SIZE = 8

const typeLabels: Record<number, string> = {
  0: "Agent",
  1: "HTML",
  2: "文本",
  3: "链接",
  4: "Excel",
  5: "CSV",
  6: "图片",
}

const ownerLabels: Record<string, string> = {
  admin: "Admin 投料",
  agent: "Agent 作业",
  system: "System",
}

const runnerLabels: Record<string, string> = {
  draft_only: "生成草稿",
  agent: "Agent 作业",
}

const statusLabels: Record<string, string> = {
  active: "正常",
  paused: "暂停",
  archived: "归档",
  invalid: "失效",
}

const statusClassNames: Record<string, string> = {
  active: "border-emerald-200 bg-emerald-50 text-emerald-700",
  paused: "border-amber-200 bg-amber-50 text-amber-700",
  archived: "border-slate-200 bg-slate-50 text-slate-600",
  invalid: "border-rose-200 bg-rose-50 text-rose-700",
}

const taskStateClass: Record<number, string> = {
  0: "border-slate-200 bg-slate-50 text-slate-600",
  1: "border-blue-200 bg-blue-50 text-blue-700",
  2: "border-emerald-200 bg-emerald-50 text-emerald-700",
  3: "border-rose-200 bg-rose-50 text-rose-700",
}

const taskStateLabels: Record<number, string> = {
  0: "待处理",
  1: "处理中",
  2: "已完成",
  3: "失败",
}

interface ParsedResultSummary {
  parsed: boolean
  insert: number
  update: number
  unchanged: number
  skip: number
  failed: number
}

interface TaskDraftResult {
  parsed: boolean
  raw: string
  insertDraftIds: number[]
  updateDraftIds: number[]
  unchangedDraftIds: number[]
  skipDraftIds: number[]
  failedItems: string[]
}

function formatDateTime(value?: number | string | null) {
  if (value === undefined || value === null || value === "") {
    return "-"
  }
  const normalized = typeof value === "string" && /^\d+$/.test(value) ? Number(value) : value
  const date = new Date(normalized)
  if (Number.isNaN(date.getTime())) {
    return String(value)
  }
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")} ${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`
}

function parseResultSummary(summary?: string | null): ParsedResultSummary {
  if (!summary) {
    return { parsed: false, insert: 0, update: 0, unchanged: 0, skip: 0, failed: 0 }
  }

  try {
    const parsed = JSON.parse(summary) as Record<string, unknown>
    const count = (key: string) => {
      const value = parsed[key]
      return Array.isArray(value) ? value.length : 0
    }
    return {
      parsed: true,
      insert: count("insertDraftIds"),
      update: count("updateDraftIds"),
      unchanged: count("unchangedDraftIds"),
      skip: count("skipDraftIds"),
      failed: count("failedItems"),
    }
  } catch {
    return { parsed: false, insert: 0, update: 0, unchanged: 0, skip: 0, failed: 0 }
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value)
}

function toDraftIdList(value: unknown) {
  const source = Array.isArray(value) ? value : typeof value === "string" ? value.split(",") : []
  return Array.from(
    new Set(
      source
        .map((item) => Number(String(item).trim()))
        .filter((id) => Number.isInteger(id) && id > 0)
    )
  )
}

function toFailedItemList(value: unknown) {
  const source = Array.isArray(value) ? value : typeof value === "string" ? value.split(",") : []
  return source
    .map((item) => {
      if (typeof item === "string" || typeof item === "number") {
        return String(item).trim()
      }
      if (isRecord(item)) {
        const title = item.title ?? item.position ?? item.name ?? item.reason ?? item.msg
        return title === undefined || title === null ? JSON.stringify(item) : String(title).trim()
      }
      return ""
    })
    .filter(Boolean)
}

function emptyTaskDraftResult(raw = ""): TaskDraftResult {
  return {
    parsed: false,
    raw,
    insertDraftIds: [],
    updateDraftIds: [],
    unchangedDraftIds: [],
    skipDraftIds: [],
    failedItems: [],
  }
}

function parseTaskDraftResult(result?: string | null): TaskDraftResult {
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

function getTaskDraftIds(result: TaskDraftResult) {
  return Array.from(new Set([
    ...result.insertDraftIds,
    ...result.updateDraftIds,
    ...result.unchangedDraftIds,
    ...result.skipDraftIds,
  ]))
}

function buildDraftListHref(taskId: number, draftIds: number[], sourceId?: number) {
  const params = new URLSearchParams({
    draftIds: draftIds.join(","),
    sourceTaskId: String(taskId),
  })
  if (sourceId) {
    params.set("sourceId", String(sourceId))
  }
  return `/admin/drafts?${params.toString()}`
}

function buildSourceDraftHref(sourceId: number) {
  const params = new URLSearchParams({ sourceId: String(sourceId) })
  return `/admin/drafts?${params.toString()}`
}

function buildPendingSourceDraftHref(sourceId: number) {
  const params = new URLSearchParams({
    sourceId: String(sourceId),
    toProcess: "1",
  })
  return `/admin/drafts?${params.toString()}`
}

function ResultSummary({ source }: { source: GatherSourceItem }) {
  const summary = parseResultSummary(source.lastResultSummary)

  if (!source.lastResultSummary) {
    return <span className="text-sm text-content-muted">暂无结果</span>
  }

  if (!summary.parsed) {
    return (
      <div className="rounded-lg border border-surface-border bg-slate-50 p-3 text-sm text-content-secondary">
        {source.lastResultSummary}
      </div>
    )
  }

  const items = [
    { label: "新增", count: summary.insert, className: "border-emerald-200 bg-emerald-50 text-emerald-700" },
    { label: "更新", count: summary.update, className: "border-blue-200 bg-blue-50 text-blue-700" },
    { label: "无变化", count: summary.unchanged, className: "border-slate-200 bg-slate-50 text-slate-600" },
    { label: "跳过", count: summary.skip, className: "border-amber-200 bg-amber-50 text-amber-700" },
    { label: "失败", count: summary.failed, className: "border-rose-200 bg-rose-50 text-rose-700" },
  ]

  return (
    <div className="grid gap-3 sm:grid-cols-5">
      {items.map((item) => (
        <div key={item.label} className={cn("rounded-lg border px-3 py-2", item.className)}>
          <div className="text-xs">{item.label}</div>
          <div className="mt-1 text-xl font-semibold">{item.count}</div>
        </div>
      ))}
    </div>
  )
}

function StatCard({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="rounded-lg border border-surface-border bg-slate-50 px-4 py-3">
      <div className="text-xs text-content-tertiary">{label}</div>
      <div className="mt-1 truncate text-sm font-medium text-content-primary" title={String(value)}>
        {value || "-"}
      </div>
    </div>
  )
}

function TaskResultBadges({ task }: { task: TaskListItem }) {
  const result = parseTaskDraftResult(task.result)
  const draftIds = getTaskDraftIds(result)

  if (!result.raw) {
    return <span className="text-sm text-content-muted">未返回</span>
  }

  if (!result.parsed) {
    return (
      <span className="line-clamp-2 text-sm text-content-secondary" title={result.raw}>
        {result.raw}
      </span>
    )
  }

  const items = [
    { label: "新增", count: result.insertDraftIds.length, className: "border-emerald-200 bg-emerald-50 text-emerald-700" },
    { label: "更新", count: result.updateDraftIds.length, className: "border-blue-200 bg-blue-50 text-blue-700" },
    { label: "无变化", count: result.unchangedDraftIds.length, className: "border-slate-200 bg-slate-50 text-slate-600" },
    { label: "跳过", count: result.skipDraftIds.length, className: "border-amber-200 bg-amber-50 text-amber-700" },
    { label: "失败", count: result.failedItems.length, className: "border-rose-200 bg-rose-50 text-rose-700" },
  ]

  return (
    <div className="min-w-[280px] space-y-2">
      <div className="flex flex-wrap gap-1.5">
        {items.map((item) => (
          <Badge key={item.label} variant="outline" className={cn("h-6 rounded-md px-2", item.className)}>
            {item.label} {item.count}
          </Badge>
        ))}
      </div>
      {draftIds.length > 0 && (
        <Button asChild size="sm" variant="outline" className="h-8 gap-1.5 border-slate-200 bg-white text-slate-700">
          <Link href={buildDraftListHref(task.taskId, draftIds, task.sourceId)}>
            <ExternalLink className="h-3.5 w-3.5" />
            查看草稿
          </Link>
        </Button>
      )}
    </div>
  )
}

export default function AdminSourceDetailPage() {
  const searchParams = useSearchParams()
  const sourceId = Number(searchParams.get("id") || 0)
  const { toast } = useToast()
  const [source, setSource] = useState<GatherSourceItem | null>(null)
  const [tasks, setTasks] = useState<TaskListItem[]>([])
  const [taskTotal, setTaskTotal] = useState(0)
  const [draftTotal, setDraftTotal] = useState(0)
  const [pendingDraftTotal, setPendingDraftTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [reRunning, setReRunning] = useState(false)
  const [statusUpdating, setStatusUpdating] = useState(false)

  const totalPages = useMemo(() => Math.max(1, Math.ceil(taskTotal / TASK_PAGE_SIZE)), [taskTotal])
  const canReRun = source?.status === "active"

  const loadDetail = useCallback(async () => {
    if (!sourceId) {
      setError("缺少来源 ID")
      return
    }

    setLoading(true)
    setError(null)
    try {
      const [sourceRes, taskRes, draftRes, pendingDraftRes] = await Promise.all([
        fetchGatherSourceList({ id: sourceId, page: 1, size: 1 }),
        fetchTaskList({ sourceId, page, size: TASK_PAGE_SIZE }),
        fetchDraftList({ sourceId, page: 1, size: 1 }),
        fetchDraftList({ sourceId, toProcess: "1", page: 1, size: 1 }),
      ])
      setSource(sourceRes.list?.[0] || null)
      setTasks(taskRes.list || [])
      setTaskTotal(taskRes.total || 0)
      setDraftTotal(draftRes.total || 0)
      setPendingDraftTotal(pendingDraftRes.total || 0)
      if (!sourceRes.list?.[0]) {
        setError(`未找到来源 #${sourceId}`)
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : "加载来源详情失败"
      setError(message)
      toast({ title: "加载失败", description: message, variant: "destructive" })
    } finally {
      setLoading(false)
    }
  }, [page, sourceId, toast])

  useEffect(() => {
    loadDetail()
  }, [loadDetail])

  const handleReRun = async () => {
    if (!source) {
      return
    }
    setReRunning(true)
    try {
      const taskId = await reRunGatherSource(source.id)
      toast({ title: "已重新采集", description: `任务 #${taskId} 已加入采集队列` })
      setPage(1)
      await loadDetail()
    } catch (err) {
      toast({
        title: "重新采集失败",
        description: err instanceof Error ? err.message : "请稍后重试",
        variant: "destructive",
      })
    } finally {
      setReRunning(false)
    }
  }

  const handleStatusUpdate = async (status: string) => {
    if (!source) {
      return
    }
    setStatusUpdating(true)
    try {
      const updatedSource = await updateGatherSourceStatus(source.id, status)
      setSource(updatedSource)
      toast({ title: "状态已更新", description: `来源 #${source.id} 已标记为${statusLabels[status] || status}` })
      await loadDetail()
    } catch (err) {
      toast({
        title: "状态更新失败",
        description: err instanceof Error ? err.message : "请稍后重试",
        variant: "destructive",
      })
    } finally {
      setStatusUpdating(false)
    }
  }

  return (
    <div className="mx-auto flex w-full max-w-[1440px] flex-col gap-5 px-6 py-5">
      <section className="rounded-lg border border-surface-border bg-white px-5 py-4 shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex min-w-0 items-center gap-3">
            <Button asChild variant="outline" size="icon" className="h-10 w-10 shrink-0">
              <Link href="/admin/sources">
                <ArrowLeft className="h-4 w-4" />
              </Link>
            </Button>
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md border border-blue-100 bg-blue-50 text-blue-600">
              <DatabaseZap className="h-5 w-5" />
            </div>
            <div className="min-w-0">
              <h1 className="line-clamp-1 text-xl font-semibold leading-7 text-content-primary">
                来源详情 {source ? `#${source.id}` : sourceId ? `#${sourceId}` : ""}
              </h1>
              <p className="text-xs text-content-tertiary">来源资产、最近采集结果与关联任务回看</p>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Button asChild variant="outline" className="h-9 gap-2">
              <Link href={buildSourceDraftHref(sourceId || 0)}>
                <FileText className="h-4 w-4" />
                查看草稿
              </Link>
            </Button>
            <Button asChild variant="outline" className="h-9 gap-2">
              <Link href={`/admin/entry?tab=tasks&sourceId=${sourceId || ""}`}>
                <ExternalLink className="h-4 w-4" />
                查看任务
              </Link>
            </Button>
            <Button className="h-9 gap-2" onClick={handleReRun} disabled={!source || !canReRun || reRunning}>
              {reRunning ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCcw className="h-4 w-4" />}
              重新采集
            </Button>
            {source?.status === "active" ? (
              <Button variant="outline" className="h-9" onClick={() => handleStatusUpdate("paused")} disabled={statusUpdating}>
                暂停
              </Button>
            ) : source?.status === "paused" ? (
              <Button variant="outline" className="h-9" onClick={() => handleStatusUpdate("active")} disabled={statusUpdating}>
                恢复
              </Button>
            ) : null}
            {source && source.status !== "archived" && (
              <Button variant="outline" className="h-9" onClick={() => handleStatusUpdate("archived")} disabled={statusUpdating}>
                归档
              </Button>
            )}
          </div>
        </div>
      </section>

      {source && !canReRun && (
        <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
          当前来源状态为「{statusLabels[source.status] || source.status}」，暂不可重新采集；恢复为「正常」后可继续触发任务。
        </div>
      )}

      {error && (
        <div className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
          {error}
        </div>
      )}

      <section className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_360px]">
        <div className="rounded-lg border border-surface-border bg-white p-5 shadow-sm">
          <div className="mb-4 flex items-center gap-2">
            <FileText className="h-4 w-4 text-content-muted" />
            <h2 className="text-base font-semibold text-content-primary">来源内容</h2>
          </div>
          {loading && !source ? (
            <div className="flex h-40 items-center justify-center text-sm text-content-tertiary">
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              加载来源中
            </div>
          ) : source ? (
            <div className="space-y-4">
              <div>
                <div className="mb-2 flex flex-wrap items-center gap-2">
                  <Badge variant="outline" className="border-blue-200 bg-blue-50 text-blue-700">
                    {typeLabels[source.type] || `类型 ${source.type}`}
                  </Badge>
                  <Badge variant="outline" className="border-slate-200 bg-slate-50 text-slate-700">
                    v{source.version}
                  </Badge>
                  <Badge
                    variant="outline"
                    className={cn("h-6 rounded-md px-2", statusClassNames[source.status] || "border-slate-200 bg-slate-50 text-slate-600")}
                  >
                    {statusLabels[source.status] || source.status || "-"}
                  </Badge>
                </div>
                <h3 className="text-lg font-semibold text-content-primary">{source.title || "未命名来源"}</h3>
              </div>
              <div className="max-h-[360px] overflow-auto whitespace-pre-wrap break-all rounded-lg border border-surface-border bg-slate-50 p-4 text-sm leading-6 text-content-secondary">
                {source.content || "暂无内容"}
              </div>
            </div>
          ) : (
            <div className="flex h-40 flex-col items-center justify-center text-content-tertiary">
              <Archive className="mb-2 h-7 w-7" />
              <span className="text-sm">暂无来源信息</span>
            </div>
          )}
        </div>

        <aside className="space-y-5">
          <section className="rounded-lg border border-surface-border bg-white p-5 shadow-sm">
            <div className="mb-4 flex items-center gap-2">
              <Clock3 className="h-4 w-4 text-content-muted" />
              <h2 className="text-base font-semibold text-content-primary">来源元信息</h2>
            </div>
            <div className="grid gap-3">
              <StatCard label="归属视角" value={source ? ownerLabels[source.ownerType] || source.ownerType : "-"} />
              <StatCard label="作业类型" value={source ? runnerLabels[source.runnerType] || source.runnerType : "-"} />
              <StatCard label="最近模型" value={source?.lastModel || "-"} />
              <StatCard label="最近任务" value={source?.lastTaskId ? `#${source.lastTaskId}` : "-"} />
              <StatCard label="关联草稿" value={draftTotal} />
              <StatCard label="待处理草稿" value={pendingDraftTotal} />
              <StatCard label="最近运行" value={formatDateTime(source?.lastRunTime)} />
              <StatCard label="更新时间" value={formatDateTime(source?.updateTime)} />
            </div>
          </section>

          <section className="rounded-lg border border-surface-border bg-white p-5 shadow-sm">
            <h2 className="mb-4 text-base font-semibold text-content-primary">最近结果</h2>
            {source ? <ResultSummary source={source} /> : <span className="text-sm text-content-muted">暂无结果</span>}
          </section>
        </aside>
      </section>

      <section className="rounded-lg border border-surface-border bg-white shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-surface-border px-5 py-4">
          <div>
            <h2 className="text-base font-semibold text-content-primary">关联任务</h2>
            <p className="mt-1 text-sm text-content-tertiary">
              共 {taskTotal} 条任务，当前第 {page} / {totalPages} 页
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Button asChild variant="outline" className="h-9 gap-2">
              <Link href={buildSourceDraftHref(sourceId || 0)}>
                <FileText className="h-4 w-4" />
                全部草稿
              </Link>
            </Button>
            <Button asChild variant="outline" className="h-9 gap-2">
              <Link href={buildPendingSourceDraftHref(sourceId || 0)}>
                <FileText className="h-4 w-4" />
                待处理草稿
              </Link>
            </Button>
            <Button variant="outline" className="h-9 gap-2" onClick={loadDetail} disabled={loading}>
              {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCcw className="h-4 w-4" />}
              刷新
            </Button>
          </div>
        </div>

        <div className="p-5">
          <div className="overflow-hidden rounded-lg border border-surface-border">
            <Table>
              <TableHeader>
                <TableRow className="bg-slate-50">
                  <TableHead className="w-24">任务 ID</TableHead>
                  <TableHead className="w-28">类型</TableHead>
                  <TableHead className="w-40">作业</TableHead>
                  <TableHead className="w-32">状态</TableHead>
                  <TableHead>模型</TableHead>
                  <TableHead className="min-w-[300px]">草稿结果</TableHead>
                  <TableHead className="w-44">更新时间</TableHead>
                  <TableHead className="w-24 text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {loading && tasks.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={8} className="h-28 text-center text-content-tertiary">
                      <Loader2 className="mx-auto mb-2 h-4 w-4 animate-spin" />
                      加载任务中
                    </TableCell>
                  </TableRow>
                ) : tasks.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={8} className="h-28 text-center text-content-tertiary">
                      暂无关联任务
                    </TableCell>
                  </TableRow>
                ) : (
                  tasks.map((task) => (
                    <TableRow key={task.taskId}>
                      <TableCell className="font-medium text-content-primary">#{task.taskId}</TableCell>
                      <TableCell>
                        <Badge variant="outline" className="border-blue-200 bg-blue-50 text-blue-700">
                          {typeLabels[task.type] || `类型 ${task.type}`}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-sm text-content-secondary">
                        {runnerLabels[task.runnerType || ""] || task.runnerType || "-"}
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline" className={cn("border-slate-200 bg-white", taskStateClass[task.state])}>
                          {taskStateLabels[task.state] || task.state}
                        </Badge>
                      </TableCell>
                      <TableCell className="max-w-[280px] truncate text-sm text-content-secondary" title={task.model}>
                        {task.model || "-"}
                      </TableCell>
                      <TableCell>
                        <TaskResultBadges task={task} />
                      </TableCell>
                      <TableCell className="text-sm text-content-tertiary">{formatDateTime(task.updateTime)}</TableCell>
                      <TableCell className="text-right">
                        <Button asChild size="sm" variant="outline" className="h-8 gap-1.5">
                          <Link href={`/admin/entry?tab=tasks&sourceId=${sourceId}`}>
                            <ExternalLink className="h-3.5 w-3.5" />
                            队列
                          </Link>
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </div>

          <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
            <div className="text-sm text-content-tertiary">
              详情页聚合来源与任务历史，便于回看一次投料后的采集链路。
            </div>
            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                disabled={page <= 1 || loading}
                onClick={() => setPage((current) => Math.max(1, current - 1))}
              >
                上一页
              </Button>
              <Button
                variant="outline"
                size="sm"
                disabled={page >= totalPages || loading}
                onClick={() => setPage((current) => current + 1)}
              >
                下一页
              </Button>
            </div>
          </div>
        </div>
      </section>
    </div>
  )
}
