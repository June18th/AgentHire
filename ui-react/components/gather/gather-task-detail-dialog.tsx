"use client"

import Link from "next/link"
import { DatabaseZap, ExternalLink, GitBranch, Layers3 } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import type { TaskListItem } from "@/lib/api"
import {
  buildGatherTaskQueueHref,
  formatGatherTaskModelDisplay,
  gatherTaskStateClass,
  gatherTaskStateLabels,
  isAgentRunner,
  isImFetchRunner,
  runnerLabels,
  RUNNER_AGENT,
} from "@/lib/admin-workbench"
import {
  buildDraftListHref,
  formatDateTimeStr,
  getTaskDraftIds,
  parseTaskDraftResult,
} from "@/lib/gather-task-utils"
import { cn } from "@/lib/utils"

interface GatherTaskDetailDialogProps {
  task: TaskListItem | null
  open: boolean
  onOpenChange: (open: boolean) => void
  typeLabel?: (type: number) => string
  modelLabel?: (model?: string | null) => string
}

function DetailRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-4 border-t border-surface-border py-3 text-sm first:border-t-0 first:pt-0">
      <span className="shrink-0 text-content-tertiary">{label}</span>
      <div className="min-w-0 text-right font-medium text-content-primary">{children}</div>
    </div>
  )
}

export function GatherTaskDetailDialog({
  task,
  open,
  onOpenChange,
  typeLabel,
  modelLabel,
}: GatherTaskDetailDialogProps) {
  if (!task) {
    return null
  }

  const result = parseTaskDraftResult(task.result)
  const draftIds = getTaskDraftIds(result)
  const hasStructuredResult = draftIds.length > 0 || result.failedItems.length > 0
  const resolvedTypeLabel = typeLabel ? typeLabel(task.type) : `类型 ${task.type}`
  const resolvedModelLabel = formatGatherTaskModelDisplay(task, modelLabel)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] max-w-xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>采集任务 #{task.taskId}</DialogTitle>
          <DialogDescription>
            {runnerLabels[task.runnerType || ""] || task.runnerType || "未知来源"}
            {task.sourceRunIndex ? ` · 第 ${task.sourceRunIndex} 次运行` : ""}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-1">
          <DetailRow label="Gather 任务 ID">
            <span className="font-mono">#{task.taskId}</span>
          </DetailRow>

          {isImFetchRunner(task.runnerType) && (
            <>
              <DetailRow label="IM 业务任务">
                <span className="font-mono">{task.jobFetchBizTaskId || "-"}</span>
              </DetailRow>
              <DetailRow label="用户 ID">
                <span className="font-mono">{task.jobFetchUserId || "-"}</span>
              </DetailRow>
              <DetailRow label="渠道">
                <span>{task.jobFetchChannel || "-"}</span>
              </DetailRow>
            </>
          )}

          <DetailRow label="作业类型">
            <span>{runnerLabels[task.runnerType || ""] || task.runnerType || "-"}</span>
          </DetailRow>
          <DetailRow label="输入类型">
            <span>{resolvedTypeLabel}</span>
          </DetailRow>
          <DetailRow label={isImFetchRunner(task.runnerType) ? "用户/渠道" : "模型"}>
            <span className="max-w-[240px] truncate" title={resolvedModelLabel}>
              {resolvedModelLabel}
            </span>
          </DetailRow>
          <DetailRow label="状态">
            <Badge variant="outline" className={cn("border-slate-200 bg-white", gatherTaskStateClass[task.state])}>
              {gatherTaskStateLabels[task.state] || task.state}
            </Badge>
          </DetailRow>
          <DetailRow label="更新时间">
            <span>{formatDateTimeStr(task.updateTime)}</span>
          </DetailRow>
        </div>

        <div className="rounded-lg border border-surface-border bg-slate-50 p-4">
          <div className="text-sm font-medium text-content-primary">草稿结果</div>
          {!result.raw ? (
            <p className="mt-2 text-sm text-content-muted">未返回</p>
          ) : !result.parsed ? (
            <p className="mt-2 line-clamp-4 text-sm text-content-secondary" title={result.raw}>
              {result.raw}
            </p>
          ) : (
            <div className="mt-3 space-y-2 text-sm">
              <div className="flex flex-wrap gap-2">
                {result.insertDraftIds.length > 0 && (
                  <Badge variant="outline" className="border-emerald-200 bg-emerald-50 text-emerald-700">
                    新增 {result.insertDraftIds.length}
                  </Badge>
                )}
                {result.updateDraftIds.length > 0 && (
                  <Badge variant="outline" className="border-blue-200 bg-blue-50 text-blue-700">
                    更新 {result.updateDraftIds.length}
                  </Badge>
                )}
                {result.unchangedDraftIds.length > 0 && (
                  <Badge variant="outline" className="border-slate-200 bg-slate-50 text-slate-600">
                    无变化 {result.unchangedDraftIds.length}
                  </Badge>
                )}
                {result.skipDraftIds.length > 0 && (
                  <Badge variant="outline" className="border-amber-200 bg-amber-50 text-amber-700">
                    跳过 {result.skipDraftIds.length}
                  </Badge>
                )}
                {result.failedItems.length > 0 && (
                  <Badge variant="outline" className="border-rose-200 bg-rose-50 text-rose-700">
                    失败 {result.failedItems.length}
                  </Badge>
                )}
              </div>
              {!hasStructuredResult && (
                <p className="text-content-secondary">
                  {result.msg && result.msg !== "success" ? result.msg : "未生成草稿"}
                </p>
              )}
            </div>
          )}
        </div>

        <div className="flex flex-wrap gap-2">
          {task.sourceId ? (
            <Button asChild size="sm" variant="outline" className="h-8 gap-1.5">
              <Link href={`/admin/sources/detail?id=${task.sourceId}`}>
                <DatabaseZap className="h-3.5 w-3.5" />
                采集源 #{task.sourceId}
              </Link>
            </Button>
          ) : null}
          {draftIds.length > 0 ? (
            <Button asChild size="sm" variant="outline" className="h-8 gap-1.5">
              <Link href={buildDraftListHref(task.taskId, draftIds, task.sourceId, task.runnerType)}>
                <ExternalLink className="h-3.5 w-3.5" />
                查看草稿
              </Link>
            </Button>
          ) : null}
          {isAgentRunner(task.runnerType) ? (
            <Button asChild size="sm" variant="outline" className="h-8 gap-1.5">
              <Link href="/admin/progress">
                <Layers3 className="h-3.5 w-3.5" />
                Agent 作业链
              </Link>
            </Button>
          ) : null}
          <Button asChild size="sm" variant="outline" className="h-8 gap-1.5">
            <Link
              href={buildGatherTaskQueueHref({
                taskId: task.taskId,
                sourceId: task.sourceId,
                runner: task.runnerType === RUNNER_AGENT ? RUNNER_AGENT : undefined,
              })}
            >
              <GitBranch className="h-3.5 w-3.5" />
              任务队列
            </Link>
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
