"use client"

import Link from "next/link"
import { useCallback, useEffect, useMemo, useState } from "react"
import {
  Archive,
  DatabaseZap,
  ExternalLink,
  Loader2,
  PlayCircle,
  RefreshCcw,
  Search,
  ShieldCheck,
} from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import {
  fetchDraftList,
  fetchGatherSourceList,
  reRunGatherSource,
  updateGatherSourceStatus,
  type GatherSourceItem,
  type GatherSourceListQuery,
} from "@/lib/api"
import { cn } from "@/lib/utils"
import { useToast } from "@/hooks/use-toast"

const PAGE_SIZE = 10
const ALL_VALUE = "-1"

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
  admin: "Admin",
  agent: "Agent",
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

function normalizeFilterValue(value: string) {
  return value === ALL_VALUE ? undefined : value
}

interface ParsedResultSummary {
  parsed: boolean
  insert: number
  update: number
  unchanged: number
  skip: number
  failed: number
}

interface SourceSummary {
  total: number
  admin: number
  agent: number
  pendingDrafts: number
}

function formatDateTime(value?: number | string | null) {
  if (value === undefined || value === null || value === "") {
    return "-"
  }
  const date = new Date(value)
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

function ResultSummaryBadges({ source }: { source: GatherSourceItem }) {
  const summary = parseResultSummary(source.lastResultSummary)

  if (!source.lastResultSummary) {
    return <span className="text-sm text-content-muted">暂无结果</span>
  }

  if (!summary.parsed) {
    return (
      <span className="line-clamp-2 text-sm text-content-secondary" title={source.lastResultSummary}>
        {source.lastResultSummary}
      </span>
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
    <div className="flex min-w-[260px] flex-wrap gap-1.5">
      {items.map((item) => (
        <Badge key={item.label} variant="outline" className={cn("h-6 rounded-md px-2", item.className)}>
          {item.label} {item.count}
        </Badge>
      ))}
    </div>
  )
}

function SummaryCard({
  label,
  value,
  description,
  icon: Icon,
  onClick,
  href,
}: {
  label: string
  value: number
  description: string
  icon: typeof DatabaseZap
  onClick?: () => void
  href?: string
}) {
  const content = (
    <div className="flex h-full items-center justify-between gap-4 rounded-lg border border-surface-border bg-white px-4 py-3 text-left shadow-sm transition hover:border-blue-200 hover:bg-blue-50/40">
      <div className="min-w-0">
        <div className="text-xs text-content-tertiary">{label}</div>
        <div className="mt-1 text-2xl font-semibold text-content-primary">{value}</div>
        <div className="mt-1 truncate text-xs text-content-muted">{description}</div>
      </div>
      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md border border-blue-100 bg-blue-50 text-blue-600">
        <Icon className="h-5 w-5" />
      </div>
    </div>
  )

  if (href) {
    return (
      <Link href={href} className="block">
        {content}
      </Link>
    )
  }

  return (
    <button type="button" className="block w-full" onClick={onClick}>
      {content}
    </button>
  )
}

export default function AdminSourcesPage() {
  const { toast } = useToast()
  const [keyword, setKeyword] = useState("")
  const [query, setQuery] = useState<GatherSourceListQuery>({ page: 1, size: PAGE_SIZE })
  const [summary, setSummary] = useState<SourceSummary>({ total: 0, admin: 0, agent: 0, pendingDrafts: 0 })
  const [sources, setSources] = useState<GatherSourceItem[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [reRunId, setReRunId] = useState<number | null>(null)
  const [statusUpdatingId, setStatusUpdatingId] = useState<number | null>(null)

  const totalPages = useMemo(() => Math.max(1, Math.ceil(total / (query.size || PAGE_SIZE))), [query.size, total])

  const loadSummary = useCallback(async () => {
    try {
      const [allSources, adminSources, agentSources, pendingDrafts] = await Promise.all([
        fetchGatherSourceList({ page: 1, size: 1 }),
        fetchGatherSourceList({ page: 1, size: 1, ownerType: "admin" }),
        fetchGatherSourceList({ page: 1, size: 1, ownerType: "agent" }),
        fetchDraftList({ page: 1, size: 1, toProcess: "1" }),
      ])
      setSummary({
        total: allSources.total || 0,
        admin: adminSources.total || 0,
        agent: agentSources.total || 0,
        pendingDrafts: pendingDrafts.total || 0,
      })
    } catch (err) {
      toast({
        title: "总览加载失败",
        description: err instanceof Error ? err.message : "请稍后重试",
        variant: "destructive",
      })
    }
  }, [toast])

  const loadSources = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await fetchGatherSourceList(query)
      setSources(res.list || [])
      setTotal(res.total || 0)
    } catch (err) {
      const message = err instanceof Error ? err.message : "获取采集源列表失败"
      setError(message)
      toast({ title: "加载失败", description: message, variant: "destructive" })
    } finally {
      setLoading(false)
    }
  }, [query, toast])

  useEffect(() => {
    loadSources()
  }, [loadSources])

  useEffect(() => {
    loadSummary()
  }, [loadSummary])

  const handleSearch = () => {
    setQuery((current) => ({ ...current, page: 1, keyword: keyword.trim() || undefined }))
  }

  const updateFilter = (key: "ownerType" | "runnerType" | "status", value: string) => {
    setQuery((current) => ({ ...current, page: 1, [key]: normalizeFilterValue(value) }))
  }

  const applySummaryFilter = (next: GatherSourceListQuery) => {
    setKeyword("")
    setQuery({ page: 1, size: PAGE_SIZE, ...next })
  }

  const handleReRun = async (source: GatherSourceItem) => {
    setReRunId(source.id)
    try {
      const taskId = await reRunGatherSource(source.id)
      toast({ title: "已重新采集", description: `任务 #${taskId} 已加入采集队列` })
      await loadSources()
    } catch (err) {
      toast({
        title: "重新采集失败",
        description: err instanceof Error ? err.message : "请稍后重试",
        variant: "destructive",
      })
    } finally {
      setReRunId(null)
    }
  }

  const handleStatusUpdate = async (source: GatherSourceItem, status: string) => {
    setStatusUpdatingId(source.id)
    try {
      await updateGatherSourceStatus(source.id, status)
      toast({ title: "状态已更新", description: `来源 #${source.id} 已标记为${statusLabels[status] || status}` })
      await Promise.all([loadSources(), loadSummary()])
    } catch (err) {
      toast({
        title: "状态更新失败",
        description: err instanceof Error ? err.message : "请稍后重试",
        variant: "destructive",
      })
    } finally {
      setStatusUpdatingId(null)
    }
  }

  return (
    <div className="mx-auto flex w-full max-w-[1440px] flex-col gap-5 px-6 py-5">
      <section className="rounded-lg border border-surface-border bg-white px-5 py-4 shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex min-w-0 items-center gap-3">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md border border-blue-100 bg-blue-50 text-blue-600">
              <DatabaseZap className="h-5 w-5" />
            </div>
            <div className="min-w-0">
              <h1 className="text-xl font-semibold leading-7 text-content-primary">采集源管理</h1>
              <p className="text-xs text-content-tertiary">长期来源资产、最近任务结果、重新采集入口</p>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant="outline" className="h-8 gap-1.5 border-emerald-200 bg-emerald-50 px-3 text-emerald-700">
              <ShieldCheck className="h-3.5 w-3.5" />
              正常来源
            </Badge>
            <Button asChild className="h-9 gap-2">
              <Link href="/admin/entry">
                <PlayCircle className="h-4 w-4" />
                新建投料
              </Link>
            </Button>
          </div>
        </div>
      </section>

      <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <SummaryCard
          label="全部来源"
          value={summary.total}
          description="长期采集来源资产"
          icon={DatabaseZap}
          onClick={() => applySummaryFilter({})}
        />
        <SummaryCard
          label="Admin 投料"
          value={summary.admin}
          description="人工投料进入草稿链路"
          icon={PlayCircle}
          onClick={() => applySummaryFilter({ ownerType: "admin" })}
        />
        <SummaryCard
          label="Agent 作业"
          value={summary.agent}
          description="Agent 侧生成或维护的来源"
          icon={ShieldCheck}
          onClick={() => applySummaryFilter({ ownerType: "agent" })}
        />
        <SummaryCard
          label="待处理草稿"
          value={summary.pendingDrafts}
          description="进入草稿审核队列"
          icon={Archive}
          href="/admin/drafts?toProcess=1"
        />
      </section>

      <section className="rounded-lg border border-surface-border bg-white shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-surface-border px-5 py-4">
          <div>
            <h2 className="text-base font-semibold text-content-primary">来源列表</h2>
            <p className="mt-1 text-sm text-content-tertiary">共 {total} 个来源，当前第 {query.page || 1} / {totalPages} 页</p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Select value={query.ownerType || ALL_VALUE} onValueChange={(value) => updateFilter("ownerType", value)}>
              <SelectTrigger className="h-10 w-36 bg-white">
                <SelectValue placeholder="全部归属" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL_VALUE}>全部归属</SelectItem>
                <SelectItem value="admin">Admin 投料</SelectItem>
                <SelectItem value="agent">Agent 作业</SelectItem>
                <SelectItem value="system">System</SelectItem>
              </SelectContent>
            </Select>
            <Select value={query.runnerType || ALL_VALUE} onValueChange={(value) => updateFilter("runnerType", value)}>
              <SelectTrigger className="h-10 w-36 bg-white">
                <SelectValue placeholder="全部作业" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL_VALUE}>全部作业</SelectItem>
                <SelectItem value="draft_only">生成草稿</SelectItem>
                <SelectItem value="agent">Agent 作业</SelectItem>
              </SelectContent>
            </Select>
            <Select value={query.status || ALL_VALUE} onValueChange={(value) => updateFilter("status", value)}>
              <SelectTrigger className="h-10 w-32 bg-white">
                <SelectValue placeholder="全部状态" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL_VALUE}>全部状态</SelectItem>
                {Object.entries(statusLabels).map(([value, label]) => (
                  <SelectItem key={value} value={value}>
                    {label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <div className="relative">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-content-muted" />
              <Input
                value={keyword}
                onChange={(event) => setKeyword(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter") handleSearch()
                }}
                placeholder="搜索来源标题或内容"
                className="h-10 w-64 bg-white pl-9"
              />
            </div>
            <Button variant="outline" className="h-10 gap-2" onClick={handleSearch}>
              <Search className="h-4 w-4" />
              搜索
            </Button>
            <Button variant="outline" className="h-10 gap-2" onClick={loadSources} disabled={loading}>
              {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCcw className="h-4 w-4" />}
              刷新
            </Button>
          </div>
        </div>

        <div className="p-5">
          {error && (
            <div className="mb-4 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
              {error}
            </div>
          )}

          <div className="overflow-hidden rounded-lg border border-surface-border">
            <Table>
              <TableHeader>
                <TableRow className="bg-slate-50">
                  <TableHead className="w-[80px]">ID</TableHead>
                  <TableHead>来源</TableHead>
                  <TableHead className="w-[120px]">类型</TableHead>
                  <TableHead className="w-[180px]">归属</TableHead>
                  <TableHead className="w-[310px]">最近结果</TableHead>
                  <TableHead className="w-[150px]">更新时间</TableHead>
                  <TableHead className="w-[170px] text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {loading && sources.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={7} className="h-32 text-center text-content-tertiary">
                      <Loader2 className="mx-auto mb-2 h-5 w-5 animate-spin" />
                      正在加载采集源
                    </TableCell>
                  </TableRow>
                ) : sources.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={7} className="h-40 text-center">
                      <Archive className="mx-auto mb-3 h-8 w-8 text-content-muted" />
                      <div className="text-sm font-medium text-content-secondary">暂无采集源</div>
                      <div className="mt-1 text-xs text-content-tertiary">从采集工作台提交一次投料后，会自动生成来源资产。</div>
                    </TableCell>
                  </TableRow>
                ) : (
                  sources.map((source) => (
                    <TableRow key={source.id}>
                      <TableCell className="font-medium text-content-secondary">#{source.id}</TableCell>
                      <TableCell>
                        <div className="max-w-[420px]">
                          <div className="line-clamp-1 font-medium text-content-primary" title={source.title}>
                            {source.title || "未命名来源"}
                          </div>
                          <div className="mt-1 line-clamp-2 break-all text-xs text-content-tertiary" title={source.content}>
                            {source.content || "-"}
                          </div>
                        </div>
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline" className="border-blue-200 bg-blue-50 text-blue-700">
                          {typeLabels[source.type] || `类型 ${source.type}`}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <div className="flex flex-col gap-1">
                          <span className="text-sm text-content-secondary">{ownerLabels[source.ownerType] || source.ownerType || "-"}</span>
                          <span className="text-xs text-content-tertiary">{runnerLabels[source.runnerType] || source.runnerType || "-"}</span>
                          <Badge variant="outline" className={cn("h-6 w-fit rounded-md px-2", statusClassNames[source.status] || "border-slate-200 bg-slate-50 text-slate-600")}>
                            {statusLabels[source.status] || source.status || "-"}
                          </Badge>
                          {source.lastModel && (
                            <span className="max-w-[160px] truncate text-xs text-content-muted" title={source.lastModel}>
                              {source.lastModel}
                            </span>
                          )}
                        </div>
                      </TableCell>
                      <TableCell>
                        <ResultSummaryBadges source={source} />
                      </TableCell>
                      <TableCell className="text-sm text-content-tertiary">
                        {formatDateTime(source.updateTime)}
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex flex-wrap justify-end gap-2">
                          <Button asChild size="sm" variant="outline" className="h-8 gap-1.5">
                            <Link href={`/admin/sources/detail?id=${source.id}`}>
                              <ExternalLink className="h-3.5 w-3.5" />
                              详情
                            </Link>
                          </Button>
                          {source.lastTaskId && (
                            <Button asChild size="sm" variant="outline" className="h-8 gap-1.5">
                              <Link href={`/admin/entry?tab=tasks&sourceId=${source.id}`}>
                                <ExternalLink className="h-3.5 w-3.5" />
                                任务
                              </Link>
                            </Button>
                          )}
                          <Button
                            size="sm"
                            variant="outline"
                            className="h-8 gap-1.5"
                            onClick={() => handleReRun(source)}
                            disabled={source.status !== "active" || reRunId === source.id}
                          >
                            {reRunId === source.id ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <RefreshCcw className="h-3.5 w-3.5" />}
                            重新采集
                          </Button>
                          {source.status === "active" ? (
                            <Button
                              size="sm"
                              variant="outline"
                              className="h-8"
                              onClick={() => handleStatusUpdate(source, "paused")}
                              disabled={statusUpdatingId === source.id}
                            >
                              暂停
                            </Button>
                          ) : source.status === "paused" ? (
                            <Button
                              size="sm"
                              variant="outline"
                              className="h-8"
                              onClick={() => handleStatusUpdate(source, "active")}
                              disabled={statusUpdatingId === source.id}
                            >
                              恢复
                            </Button>
                          ) : null}
                          {source.status !== "archived" && (
                            <Button
                              size="sm"
                              variant="outline"
                              className="h-8"
                              onClick={() => handleStatusUpdate(source, "archived")}
                              disabled={statusUpdatingId === source.id}
                            >
                              归档
                            </Button>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </div>

          <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
            <div className="text-sm text-content-tertiary">
              采集源会自动聚合同一类型、同一规范化内容的重复投料。
            </div>
            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                disabled={(query.page || 1) <= 1 || loading}
                onClick={() => setQuery((current) => ({ ...current, page: Math.max(1, (current.page || 1) - 1) }))}
              >
                上一页
              </Button>
              <Button
                variant="outline"
                size="sm"
                disabled={(query.page || 1) >= totalPages || loading}
                onClick={() => setQuery((current) => ({ ...current, page: (current.page || 1) + 1 }))}
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
