"use client"

import { useEffect, useMemo, useState } from "react"
import Link from "next/link"
import { useRouter, useSearchParams } from "next/navigation"
import { AlertTriangle, CalendarClock, CheckCircle2, Download, ExternalLink, FilePlus2, Pencil, RefreshCw, RotateCcw, Search, Star, Trash2 } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Textarea } from "@/components/ui/textarea"
import { AuthGuard } from "@/components/auth/AuthGuard"
import { useLoginUser } from "@/hooks/useLoginUser"
import { useToast } from "@/hooks/use-toast"
import {
  changeJobApplicationStatus,
  completeJobApplicationFollowUp,
  deleteJobApplication,
  fetchJobApplicationActionItems,
  fetchJobApplicationDetail,
  fetchJobApplicationEventsByDay,
  fetchJobApplicationReview,
  fetchJobApplications,
  reopenJobApplication,
  saveJobApplication,
  saveJobApplicationEvent,
  type JobApplicationActionScope,
  type JobApplicationEvent,
  type JobApplicationFollowUpScope,
  type JobApplicationItem,
  type JobApplicationReview,
  type JobApplicationStatus,
} from "@/lib/job-application-api"

const PAGE_SIZE = 10
const SUMMARY_SIZE = 1000
const statCardClass = "rounded-lg border border-surface-border bg-white p-4 shadow-sm"
const statLabelClass = "text-sm font-medium text-content-tertiary"
const statValueClass = "mt-2 text-2xl font-semibold text-content-primary"
const tableHeadClass = "border-r border-surface-border bg-blue-50 px-4 py-3 text-center font-semibold text-blue-700 last:border-r-0"
const tableCellClass = "border-r border-surface-border px-4 py-4 text-center align-middle last:border-r-0"
const ACTIVE_PROCESS_STATUS = ["SUBMITTED", "WRITTEN_TEST", "INTERVIEW_1", "INTERVIEW_2", "HR_INTERVIEW", "OFFER", "ACCEPTED"]
const INTERVIEW_PROCESS_STATUS = ["WRITTEN_TEST", "INTERVIEW_1", "INTERVIEW_2", "HR_INTERVIEW"]
const FOLLOW_UP_REQUIRED_PROCESS_STATUS = ["WRITTEN_TEST", "INTERVIEW_1", "INTERVIEW_2", "HR_INTERVIEW", "OFFER"]
const STALE_SUBMITTED_MS = 7 * 24 * 60 * 60 * 1000

const ATTENTION_OPTIONS = [
  { value: "0", label: "未标记" },
  { value: "1", label: "1 星" },
  { value: "2", label: "2 星" },
  { value: "3", label: "3 星" },
]

function MiniList({ items, emptyText }: { items: JobApplicationItem[]; emptyText: string }) {
  if (items.length === 0) {
    return <div className="text-xs text-content-tertiary">{emptyText}</div>
  }

  return (
    <div className="mt-2 grid gap-1.5">
      {items.slice(0, 3).map((item) => (
        <div key={item.id} className="truncate text-xs text-content-secondary" title={`${item.companyName} / ${item.position}`}>
          {item.companyName} / {item.position}
        </div>
      ))}
      {items.length > 3 ? <div className="text-xs text-content-tertiary">还有 {items.length - 3} 条</div> : null}
    </div>
  )
}

const STATUS_OPTIONS: Array<{ value: JobApplicationStatus; label: string }> = [
  { value: "INTERESTED", label: "感兴趣" },
  { value: "PREPARING", label: "准备投递" },
  { value: "SUBMITTED", label: "已投递" },
  { value: "WRITTEN_TEST", label: "笔试" },
  { value: "INTERVIEW_1", label: "一面" },
  { value: "INTERVIEW_2", label: "二面" },
  { value: "HR_INTERVIEW", label: "HR 面" },
  { value: "OFFER", label: "Offer" },
  { value: "ACCEPTED", label: "已接受" },
  { value: "REJECTED", label: "已拒绝" },
  { value: "GAVE_UP", label: "已放弃" },
  { value: "EXPIRED", label: "已过期" },
  { value: "CLOSED", label: "已结束" },
]

const EVENT_TYPE_OPTIONS = [
  { value: "FOLLOW_UP", label: "跟进" },
  { value: "WRITTEN_TEST", label: "笔试" },
  { value: "INTERVIEW", label: "面试" },
  { value: "HR", label: "HR 沟通" },
  { value: "OFFER", label: "Offer" },
  { value: "OTHER", label: "其他" },
]

interface EventTemplate {
  eventType: string
  label: string
  title: string
  note: string
  defaultHour: number
  defaultLeadDays: number
}

const EVENT_TEMPLATES: EventTemplate[] = [
  { eventType: "WRITTEN_TEST", label: "笔试", title: "笔试安排", note: "记录笔试时间、平台、题型、准备要点和完成后的复盘。", defaultHour: 19, defaultLeadDays: 1 },
  { eventType: "INTERVIEW", label: "面试", title: "面试安排", note: "记录面试轮次、面试官、考察重点、准备材料和下一步计划。", defaultHour: 10, defaultLeadDays: 2 },
  { eventType: "HR", label: "HR", title: "HR 沟通", note: "记录薪资范围、到岗时间、意向城市、沟通结论和待确认问题。", defaultHour: 15, defaultLeadDays: 2 },
  { eventType: "OFFER", label: "Offer", title: "Offer 沟通", note: "记录薪资结构、截止回复时间、对比项和最终决策依据。", defaultHour: 18, defaultLeadDays: 1 },
]

// AI-GENERATED AIDEV-NOTE: stage event nudges
const RECOMMENDED_EVENT_TYPES_BY_STATUS: Partial<Record<JobApplicationStatus, string[]>> = {
  SUBMITTED: ["WRITTEN_TEST", "INTERVIEW"],
  WRITTEN_TEST: ["INTERVIEW"],
  INTERVIEW_1: ["INTERVIEW", "HR", "OFFER"],
  INTERVIEW_2: ["HR", "OFFER"],
  HR_INTERVIEW: ["OFFER", "HR"],
  OFFER: ["OFFER", "HR"],
}

const COMPANY_TYPE_OPTIONS = [
  { value: "央国企", label: "央国企" },
  { value: "外企", label: "外企" },
  { value: "民企", label: "民企" },
  { value: "事业单位", label: "事业单位" },
  { value: "银行", label: "银行" },
  { value: "学校", label: "学校" },
  { value: "央企", label: "央企" },
  { value: "互联网", label: "互联网" },
  { value: "其他", label: "其他" },
]

const FOLLOW_UP_SCOPE_OPTIONS: Array<{ value: JobApplicationFollowUpScope; label: string }> = [
  { value: "PENDING", label: "待跟进" },
  { value: "OVERDUE", label: "已到期" },
]

const ACTION_SCOPE_OPTIONS: Array<{ value: "all" | JobApplicationActionScope; label: string }> = [
  { value: "all", label: "全部行动" },
  { value: "A", label: "A 级优先" },
  { value: "OVERDUE_FOLLOW_UP", label: "逾期跟进" },
  { value: "DUE_TODAY", label: "今日截止" },
  { value: "DUE_SOON", label: "临近截止" },
  { value: "THIS_WEEK", label: "本周截止" },
  { value: "EXPIRED_DEADLINE", label: "已过截止" },
  { value: "UNKNOWN_DEADLINE", label: "截止未知" },
  { value: "MISSING_APPLY_URL", label: "链接缺失" },
  { value: "STALE_SUBMITTED", label: "静默投递" },
  { value: "PROCESS_NEEDS_FOLLOW_UP", label: "流程待跟进" },
]

function normalizeActionScope(value: string | null): "all" | JobApplicationActionScope {
  return ACTION_SCOPE_OPTIONS.some((item) => item.value === value) ? (value as "all" | JobApplicationActionScope) : "all"
}

const NEXT_STATUS: Partial<Record<JobApplicationStatus, JobApplicationStatus[]>> = {
  INTERESTED: ["PREPARING", "SUBMITTED", "GAVE_UP", "EXPIRED", "CLOSED"],
  PREPARING: ["SUBMITTED", "GAVE_UP", "EXPIRED", "CLOSED"],
  SUBMITTED: ["WRITTEN_TEST", "INTERVIEW_1", "REJECTED", "GAVE_UP", "EXPIRED", "CLOSED"],
  WRITTEN_TEST: ["INTERVIEW_1", "REJECTED", "GAVE_UP", "EXPIRED", "CLOSED"],
  INTERVIEW_1: ["INTERVIEW_2", "HR_INTERVIEW", "OFFER", "REJECTED", "GAVE_UP", "CLOSED"],
  INTERVIEW_2: ["HR_INTERVIEW", "OFFER", "REJECTED", "GAVE_UP", "CLOSED"],
  HR_INTERVIEW: ["OFFER", "REJECTED", "GAVE_UP", "CLOSED"],
  OFFER: ["ACCEPTED", "REJECTED", "GAVE_UP", "CLOSED"],
}

const BOARD_COLUMNS: Array<{ key: string; label: string; hint: string; statuses: JobApplicationStatus[] }> = [
  { key: "pool", label: "机会池", hint: "感兴趣 / 准备投递", statuses: ["INTERESTED", "PREPARING"] },
  { key: "submitted", label: "已投递", hint: "等待反馈", statuses: ["SUBMITTED"] },
  { key: "interview", label: "笔面试", hint: "笔试 / 面试 / HR", statuses: ["WRITTEN_TEST", "INTERVIEW_1", "INTERVIEW_2", "HR_INTERVIEW"] },
  { key: "offer", label: "Offer", hint: "Offer / 已接受", statuses: ["OFFER", "ACCEPTED"] },
  { key: "closed", label: "已结束", hint: "拒绝 / 放弃 / 过期 / 关闭", statuses: ["REJECTED", "GAVE_UP", "EXPIRED", "CLOSED"] },
]

function statusLabel(status?: string) {
  return STATUS_OPTIONS.find((item) => item.value === status)?.label || status || "-"
}

function eventTypeLabel(type?: string) {
  return EVENT_TYPE_OPTIONS.find((item) => item.value === type)?.label || type || "-"
}

function eventSubject(event: JobApplicationEvent) {
  if (event.companyName || event.position) {
    return [event.companyName, event.position].filter(Boolean).join(" / ")
  }
  return event.eventTitle
}

function eventUrgencyLabel(urgency?: string) {
  if (urgency === "TODAY") return "今天"
  if (urgency === "TOMORROW") return "明天"
  if (urgency === "THIS_WEEK") return "本周"
  if (urgency === "PAST") return "待复盘"
  if (urgency === "LATER") return "后续"
  return ""
}

function nextKeyEvent(events?: JobApplicationEvent[]) {
  if (!events?.length) return undefined
  const importantTypes = new Set(["WRITTEN_TEST", "INTERVIEW", "HR", "OFFER"])
  const now = Date.now()
  return events
    .filter((event) => importantTypes.has(event.eventType) && (event.eventTime || 0) >= now)
    .sort((a, b) => (a.eventTime || 0) - (b.eventTime || 0))[0]
}

function companyTypeLabel(value?: string) {
  return COMPANY_TYPE_OPTIONS.find((item) => item.value === value)?.label || value || "-"
}

function attentionLabel(value?: number) {
  const numeric = Number(value || 0)
  if (numeric <= 0) return "未标记"
  return `${numeric} 星`
}

function attentionStars(value?: number) {
  const numeric = Math.max(0, Math.min(3, Number(value || 0)))
  return "★".repeat(numeric) || "-"
}

function percentValue(count: number, base: number) {
  if (base <= 0) return 0
  return Math.round((count / base) * 100)
}

function formatDate(value?: number | string) {
  if (!value) return "-"
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return "-"
  return date.toLocaleString("zh-CN", { hour12: false })
}

function formatDateOnly(value?: number | string) {
  if (!value) return ""
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ""
  const offsetMs = date.getTimezoneOffset() * 60_000
  return new Date(date.getTime() - offsetMs).toISOString().slice(0, 10)
}

function toDateTimeInput(date: Date) {
  const offsetMs = date.getTimezoneOffset() * 60_000
  return new Date(date.getTime() - offsetMs).toISOString().slice(0, 16)
}

function nextDefaultEventTime(template: EventTemplate) {
  const date = new Date()
  date.setDate(date.getDate() + template.defaultLeadDays)
  date.setHours(template.defaultHour, 0, 0, 0)
  return toDateTimeInput(date)
}

function toDateInput(value?: number | string) {
  if (!value) return ""
  if (typeof value !== "string") {
    return formatDateOnly(value)
  }
  const normalized = value.trim().replace(" ", "T")
  if (/^\d{4}-\d{2}-\d{2}$/.test(normalized)) {
    return normalized
  }
  if (/^\d{4}-\d{2}-\d{2}T/.test(normalized)) {
    return normalized.slice(0, 10)
  }
  return formatDateOnly(normalized)
}

function toDateStartTime(value?: string) {
  return value ? new Date(`${value}T00:00:00`).getTime() : undefined
}

function suggestedEventTitle(template: EventTemplate, detail?: JobApplicationItem | null) {
  if (!detail) return template.title
  if (template.eventType !== "INTERVIEW") return template.title
  if (detail.currentStatus === "INTERVIEW_1") return "二面安排"
  if (detail.currentStatus === "INTERVIEW_2") return "终面安排"
  if (detail.currentStatus === "WRITTEN_TEST") return "一面安排"
  return "一面安排"
}

function eventTemplatesForStatus(status?: JobApplicationStatus) {
  const recommendedTypes = status ? RECOMMENDED_EVENT_TYPES_BY_STATUS[status] || [] : []
  const recommended = recommendedTypes
    .map((type) => EVENT_TEMPLATES.find((template) => template.eventType === type))
    .filter((template): template is EventTemplate => Boolean(template))
  const recommendedSet = new Set(recommended.map((template) => template.eventType))
  return [...recommended, ...EVENT_TEMPLATES.filter((template) => !recommendedSet.has(template.eventType))]
}

function escapeCsvCell(value: unknown) {
  const text = value == null ? "" : String(value)
  return `"${text.replace(/"/g, '""')}"`
}

function statusBadgeVariant(record: JobApplicationItem) {
  if (record.terminal) return "outline"
  if (["OFFER", "ACCEPTED"].includes(record.currentStatus)) return "default"
  return "secondary"
}

function isActionableRecord(record: JobApplicationItem) {
  return !record.terminal
}

function isFollowUpPending(record: JobApplicationItem) {
  return isActionableRecord(record) && Boolean(record.nextFollowUpAt)
}

function isFollowUpOverdue(record: JobApplicationItem, now = Date.now()) {
  return isFollowUpPending(record) && Number(record.nextFollowUpAt) <= now
}

function isStaleSubmittedRecord(record: JobApplicationItem, now = Date.now()) {
  return (
    isActionableRecord(record) &&
    record.currentStatus === "SUBMITTED" &&
    !record.nextFollowUpAt &&
    Boolean(record.submittedAt) &&
    Number(record.submittedAt) <= now - STALE_SUBMITTED_MS
  )
}

function isProcessNeedsFollowUpRecord(record: JobApplicationItem) {
  return FOLLOW_UP_REQUIRED_PROCESS_STATUS.includes(record.currentStatus) && !record.nextFollowUpAt
}

function isMissingApplyUrlRecord(record: JobApplicationItem) {
  return !record.terminal && ["INTERESTED", "PREPARING", "SUBMITTED"].includes(record.currentStatus) && !record.applyUrl?.trim()
}

function staleSubmittedDays(record: JobApplicationItem, now = Date.now()) {
  if (!record.submittedAt) return 0
  return Math.max(0, Math.floor((now - Number(record.submittedAt)) / 86_400_000))
}

function recordFollowUpOverdue(record: JobApplicationItem) {
  return record.followUpOverdue ?? isFollowUpOverdue(record)
}

function followUpHint(value?: number, now = Date.now()) {
  if (!value) return ""
  const diffDays = Math.ceil((value - now) / 86_400_000)
  if (diffDays < 0) return `已到期 ${Math.abs(diffDays)} 天`
  if (diffDays === 0) return "今天到期"
  return `${diffDays} 天后跟进`
}

function displayDate(value?: number | string) {
  return formatDateOnly(value) || "-"
}

function dateOnlyToTime(value?: string, endOfDay = false) {
  if (!value) return undefined
  const normalized = value.trim()
  if (!normalized) return undefined
  const date = new Date(`${normalized.slice(0, 10)}T${endOfDay ? "23:59:59" : "00:00:00"}`)
  return Number.isNaN(date.getTime()) ? undefined : date.getTime()
}

function hasActionPriority(record: JobApplicationItem) {
  return Boolean(record.actionPriority && record.actionPriority !== "NONE")
}

function actionPriorityLabel(priority?: string) {
  if (priority === "A") return "A"
  if (priority === "B") return "B"
  if (priority === "C") return "C"
  return "-"
}

function actionPriorityRank(priority?: string) {
  if (priority === "A") return 0
  if (priority === "B") return 1
  if (priority === "C") return 2
  return 3
}

function actionPriorityClass(priority?: string) {
  if (priority === "A") return "border-red-200 bg-red-50 text-red-700"
  if (priority === "B") return "border-amber-200 bg-amber-50 text-amber-700"
  if (priority === "C") return "border-blue-200 bg-blue-50 text-blue-700"
  return "border-surface-border bg-surface-muted text-content-tertiary"
}

function deadlineRiskLabel(risk?: string) {
  const labels: Record<string, string> = {
    EXPIRED: "已过截止",
    DUE_TODAY: "今天截止",
    DUE_SOON: "临近截止",
    THIS_WEEK: "本周截止",
    NORMAL: "截止正常",
    UNKNOWN: "截止未知",
    NONE: "无截止",
  }
  return risk ? labels[risk] || risk : ""
}

function nextStepSuggestion(record: JobApplicationItem) {
  const suggested = record.suggestedNextAction?.trim()
  if (suggested) return suggested
  if (record.terminal) return "如仍需继续，可重新打开并回到准备投递状态。"
  if (recordFollowUpOverdue(record)) return "跟进时间已到期，建议先完成跟进并记录结果。"
  if (record.nextFollowUpAt) return `下一步：${followUpHint(record.nextFollowUpAt)}。`
  if (record.currentStatus === "INTERESTED") return "建议确认岗位匹配度，补齐投递链接和截止时间。"
  if (record.currentStatus === "PREPARING") return "建议整理简历版本、内推信息，并设置下次跟进时间。"
  if (INTERVIEW_PROCESS_STATUS.includes(record.currentStatus)) return "建议记录笔面试安排、题目和复盘备注。"
  if (record.currentStatus === "OFFER") return "建议补充 Offer 沟通结果、薪资信息或最终选择备注。"
  return "建议保持状态更新，并记录下一步跟进计划。"
}

function startOfWeekTime(now = Date.now()) {
  const date = new Date(now)
  const day = date.getDay() || 7
  date.setDate(date.getDate() - day + 1)
  date.setHours(0, 0, 0, 0)
  return date.getTime()
}

function weeklyReviewHint(stats: {
  created: number
  submittedAndLater: number
  interviews: number
  offers: number
  overdue: number
  stale: number
  processNeedsFollowUp: number
  expiredDeadline: number
  unknownDeadline: number
  missingApplyUrl: number
}) {
  if (stats.overdue > 0) return `本周还有 ${stats.overdue} 条跟进已到期，建议先清空逾期队列。`
  if (stats.stale > 0) return `有 ${stats.stale} 条静默投递需要复盘，建议检查邮箱、短信和官网状态。`
  if (stats.processNeedsFollowUp > 0) return `有 ${stats.processNeedsFollowUp} 条流程已推进但没有下一次跟进，建议补齐复盘和提醒。`
  if (stats.expiredDeadline > 0) return `有 ${stats.expiredDeadline} 个活跃岗位已过截止时间，建议确认是否仍开放或关闭失效目标。`
  if (stats.unknownDeadline > 0) return `有 ${stats.unknownDeadline} 个活跃岗位截止时间未知，建议先补齐日期再安排投递节奏。`
  if (stats.missingApplyUrl > 0) return `有 ${stats.missingApplyUrl} 条活跃投递缺少投递链接，建议补齐官网、网申或内推入口。`
  if (stats.interviews > 0) return `本周已有 ${stats.interviews} 条笔面试推进，建议补充复盘和下一轮安排。`
  if (stats.submittedAndLater > 0) return `本周已有 ${stats.submittedAndLater} 条进入正式流程，继续补齐跟进提醒。`
  if (stats.created > 0) return `本周新增 ${stats.created} 条投递记录，建议优先完成材料匹配和截止日期确认。`
  return "本周还没有新的投递推进，可以先从岗位库筛选下一批目标。"
}

export default function ApplicationsPage() {
  const { toast } = useToast()
  const { userInfo } = useLoginUser()
  const router = useRouter()
  const searchParams = useSearchParams()
  const [records, setRecords] = useState<JobApplicationItem[]>([])
  const [summaryRecords, setSummaryRecords] = useState<JobApplicationItem[]>([])
  const [actionItems, setActionItems] = useState<JobApplicationItem[]>([])
  const [serverWeeklyReview, setServerWeeklyReview] = useState<JobApplicationReview | null>(null)
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const [companyName, setCompanyName] = useState("")
  const [position, setPosition] = useState("")
  const [status, setStatus] = useState<"all" | JobApplicationStatus>("all")
  const [companyType, setCompanyType] = useState("all")
  const [followUpScope, setFollowUpScope] = useState<"all" | JobApplicationFollowUpScope>("all")
  const [attention, setAttention] = useState("all")
  const [actionScope, setActionScope] = useState<"all" | JobApplicationActionScope>("all")
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [eventSaving, setEventSaving] = useState(false)
  const [completingFollowUpId, setCompletingFollowUpId] = useState<number | null>(null)
  const [detail, setDetail] = useState<JobApplicationItem | null>(null)
  const [todayEvents, setTodayEvents] = useState<JobApplicationEvent[]>([])
  const [editingRecord, setEditingRecord] = useState<JobApplicationItem | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [form, setForm] = useState({
    companyName: "",
    position: "",
    applyUrl: "",
    submittedAt: "",
    deadline: "",
    companyType: "",
    priority: "0",
    nextFollowUpAt: "",
    remark: "",
  })
  const [eventForm, setEventForm] = useState({
    eventType: "FOLLOW_UP",
    eventTitle: "",
    eventTime: "",
    eventResult: "",
    note: "",
  })

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))
  const actionScopeLabel = ACTION_SCOPE_OPTIONS.find((item) => item.value === actionScope)?.label || "全部行动"
  const followUpSummary = useMemo(() => {
    const pendingRecords = summaryRecords.filter(isFollowUpPending)
    return {
      pending: pendingRecords.length,
      overdue: pendingRecords.filter(recordFollowUpOverdue).length,
    }
  }, [summaryRecords])

  const activeCount = useMemo(() => summaryRecords.filter((record) => !record.terminal).length, [summaryRecords])
  const submittedAndLaterCount = useMemo(() => summaryRecords.filter((record) => ACTIVE_PROCESS_STATUS.includes(record.currentStatus)).length, [summaryRecords])
  const interviewCount = useMemo(() => summaryRecords.filter((record) => INTERVIEW_PROCESS_STATUS.includes(record.currentStatus)).length, [summaryRecords])
  const staleSubmittedRecords = useMemo(() => summaryRecords.filter(isStaleSubmittedRecord), [summaryRecords])
  const processNeedsFollowUpRecords = useMemo(() => summaryRecords.filter(isProcessNeedsFollowUpRecord), [summaryRecords])
  const todayTodo = useMemo(() => {
    const today = formatDateOnly(Date.now())
    const toSubmit = summaryRecords.filter(
      (record) => !record.terminal && ["INTERESTED", "PREPARING"].includes(record.currentStatus) && formatDateOnly(record.deadline) === today
    )
    const toFollowUp = summaryRecords.filter((record) => !record.terminal && formatDateOnly(record.nextFollowUpAt) === today)
    return {
      toSubmit,
      toFollowUp,
      dueSoon: summaryRecords.filter((record) => !record.terminal && record.deadlineRisk === "DUE_SOON"),
      thisWeek: summaryRecords.filter((record) => !record.terminal && record.deadlineRisk === "THIS_WEEK"),
      expiredDeadline: summaryRecords.filter((record) => !record.terminal && record.deadlineRisk === "EXPIRED"),
      unknownDeadline: summaryRecords.filter((record) => !record.terminal && record.deadlineRisk === "UNKNOWN"),
      missingApplyUrl: summaryRecords.filter(isMissingApplyUrlRecord),
      overdue: summaryRecords.filter(recordFollowUpOverdue),
    }
  }, [summaryRecords])
  const statusStats = useMemo(
    () =>
      STATUS_OPTIONS.map((item) => ({
        ...item,
        count: summaryRecords.filter((record) => record.currentStatus === item.value).length,
      })).filter((item) => item.count > 0),
    [summaryRecords]
  )
  const boardColumns = useMemo(
    () =>
      BOARD_COLUMNS.map((column) => {
        const recordsInColumn = summaryRecords
          .filter((record) => column.statuses.includes(record.currentStatus))
          .sort((a, b) => {
            const priorityDiff = actionPriorityRank(a.actionPriority) - actionPriorityRank(b.actionPriority)
            if (priorityDiff !== 0) return priorityDiff
            const overdueDiff = Number(recordFollowUpOverdue(b)) - Number(recordFollowUpOverdue(a))
            if (overdueDiff !== 0) return overdueDiff
            const aDeadline = a.deadlineAt || dateOnlyToTime(a.deadline) || Number.MAX_SAFE_INTEGER
            const bDeadline = b.deadlineAt || dateOnlyToTime(b.deadline) || Number.MAX_SAFE_INTEGER
            if (aDeadline !== bDeadline) return aDeadline - bDeadline
            const aFollowUp = a.nextFollowUpAt || Number.MAX_SAFE_INTEGER
            const bFollowUp = b.nextFollowUpAt || Number.MAX_SAFE_INTEGER
            if (aFollowUp !== bFollowUp) return aFollowUp - bFollowUp
            return (b.updateTime || 0) - (a.updateTime || 0)
          })
        return {
          ...column,
          total: recordsInColumn.length,
          records: recordsInColumn.slice(0, 5),
        }
      }),
    [summaryRecords]
  )
  const companyStats = useMemo(() => {
    const counts = new Map<string, number>()
    summaryRecords.forEach((record) => {
      const key = record.companyType || "未分类"
      counts.set(key, (counts.get(key) || 0) + 1)
    })
    return Array.from(counts.entries())
      .map(([label, count]) => ({ label, count }))
      .sort((a, b) => b.count - a.count)
      .slice(0, 5)
  }, [summaryRecords])
  const localWeeklyReview = useMemo(() => {
    const weekStart = startOfWeekTime()
    const stats = {
      created: summaryRecords.filter((record) => (record.createTime || 0) >= weekStart).length,
      submittedAndLater: summaryRecords.filter((record) => ACTIVE_PROCESS_STATUS.includes(record.currentStatus) && (record.updateTime || 0) >= weekStart).length,
      interviews: summaryRecords.filter((record) => INTERVIEW_PROCESS_STATUS.includes(record.currentStatus) && (record.updateTime || 0) >= weekStart).length,
      offers: summaryRecords.filter((record) => ["OFFER", "ACCEPTED"].includes(record.currentStatus) && (record.updateTime || 0) >= weekStart).length,
      overdue: summaryRecords.filter(recordFollowUpOverdue).length,
      stale: staleSubmittedRecords.length,
      processNeedsFollowUp: summaryRecords.filter(isProcessNeedsFollowUpRecord).length,
      expiredDeadline: summaryRecords.filter((record) => !record.terminal && record.deadlineRisk === "EXPIRED").length,
      unknownDeadline: summaryRecords.filter((record) => !record.terminal && record.deadlineRisk === "UNKNOWN").length,
      missingApplyUrl: summaryRecords.filter(isMissingApplyUrlRecord).length,
    }
    return {
      ...stats,
      hint: weeklyReviewHint(stats),
    }
  }, [summaryRecords, staleSubmittedRecords])
  const weeklyReview = useMemo(() => {
    if (!serverWeeklyReview) return localWeeklyReview
    const stats = {
      created: serverWeeklyReview.createdThisWeek || 0,
      submittedAndLater: serverWeeklyReview.submittedAndLaterThisWeek || 0,
      interviews: serverWeeklyReview.interviewThisWeek || 0,
      offers: serverWeeklyReview.offerThisWeek || 0,
      overdue: serverWeeklyReview.overdueFollowUps || 0,
      stale: serverWeeklyReview.staleSubmitted || 0,
      processNeedsFollowUp: serverWeeklyReview.processNeedsFollowUp || 0,
      expiredDeadline: serverWeeklyReview.expiredDeadline || 0,
      unknownDeadline: serverWeeklyReview.unknownDeadline || 0,
      missingApplyUrl: serverWeeklyReview.missingApplyUrl || 0,
    }
    return {
      ...stats,
      hint: serverWeeklyReview.summary || weeklyReviewHint(stats),
    }
  }, [localWeeklyReview, serverWeeklyReview])
  const conversionBase = Math.max(1, submittedAndLaterCount)
  const offerCount = useMemo(() => summaryRecords.filter((record) => ["OFFER", "ACCEPTED"].includes(record.currentStatus)).length, [summaryRecords])
  const hasActiveFilters = companyName.trim() !== "" || position.trim() !== "" || status !== "all" || companyType !== "all" || followUpScope !== "all" || attention !== "all"
  const currentPageStart = total === 0 ? 0 : (page - 1) * PAGE_SIZE + 1
  const currentPageEnd = Math.min(page * PAGE_SIZE, total)

  const buildListQuery = (pageNumber: number, pageSize: number) => ({
    page: pageNumber,
    size: pageSize,
    companyName: companyName.trim() || undefined,
    position: position.trim() || undefined,
    currentStatus: status === "all" ? undefined : status,
    companyType: companyType === "all" ? undefined : companyType,
    followUpScope: followUpScope === "all" ? undefined : followUpScope,
    priority: attention === "all" ? undefined : Number(attention),
  })

  const loadRecords = async () => {
    if (!userInfo) return
    setLoading(true)
    try {
      const res = await fetchJobApplications(buildListQuery(page, PAGE_SIZE))
      setRecords((res.list || []).slice(0, PAGE_SIZE))
      setTotal(res.total || 0)
    } catch (error) {
      toast({
        title: "投递记录加载失败",
        description: error instanceof Error ? error.message : "请稍后重试",
        variant: "destructive",
      })
    } finally {
      setLoading(false)
    }
  }

  const loadSummaryRecords = async () => {
    if (!userInfo) return
    try {
      const res = await fetchJobApplications(buildListQuery(1, SUMMARY_SIZE))
      setSummaryRecords(res.list || [])
    } catch {
      setSummaryRecords([])
    }
  }

  const loadWeeklyReview = async () => {
    if (!userInfo) return
    try {
      setServerWeeklyReview(await fetchJobApplicationReview())
    } catch {
      setServerWeeklyReview(null)
    }
  }

  const loadTodayEvents = async () => {
    if (!userInfo) return
    const today = formatDateOnly(Date.now())
    const start = dateOnlyToTime(today) || new Date().setHours(0, 0, 0, 0)
    const end = dateOnlyToTime(today, true) || new Date().setHours(23, 59, 59, 999)
    try {
      setTodayEvents(await fetchJobApplicationEventsByDay(start, end))
    } catch {
      setTodayEvents([])
    }
  }

  const loadActionItems = async () => {
    if (!userInfo) return
    try {
      setActionItems(await fetchJobApplicationActionItems(20, actionScope === "all" ? undefined : actionScope))
    } catch {
      setActionItems([])
    }
  }

  useEffect(() => {
    if (!userInfo) return
    const applicationId = Number(searchParams.get("applicationId"))
    if (!applicationId || Number.isNaN(applicationId)) return

    openDetail(applicationId)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userInfo, searchParams])

  useEffect(() => {
    setActionScope(normalizeActionScope(searchParams.get("actionScope")))
  }, [searchParams])

  useEffect(() => {
    loadRecords()
    loadSummaryRecords()
    loadWeeklyReview()
    loadTodayEvents()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userInfo, page, status, companyType, followUpScope, attention])

  useEffect(() => {
    loadActionItems()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userInfo, actionScope])

  const handleSearch = () => {
    setPage(1)
    if (page === 1) {
      loadRecords()
      loadSummaryRecords()
      loadWeeklyReview()
    }
  }

  const handleRefresh = () => {
    loadRecords()
    loadSummaryRecords()
    loadWeeklyReview()
    loadTodayEvents()
    loadActionItems()
  }

  const handleResetFilters = async () => {
    setCompanyName("")
    setPosition("")
    setStatus("all")
    setCompanyType("all")
    setFollowUpScope("all")
    setAttention("all")
    setPage(1)
    if (!userInfo) return

    setLoading(true)
    try {
      const [listRes, summaryRes] = await Promise.all([
        fetchJobApplications({ page: 1, size: PAGE_SIZE }),
        fetchJobApplications({ page: 1, size: SUMMARY_SIZE }),
      ])
      setRecords((listRes.list || []).slice(0, PAGE_SIZE))
      setTotal(listRes.total || 0)
      setSummaryRecords(summaryRes.list || [])
      loadWeeklyReview()
      loadActionItems()
    } catch (error) {
      toast({
        title: "筛选重置失败",
        description: error instanceof Error ? error.message : "请稍后重试",
        variant: "destructive",
      })
    } finally {
      setLoading(false)
    }
  }

  const handleStatusFilterChange = (value: "all" | JobApplicationStatus) => {
    setStatus(value)
    setPage(1)
  }

  const handleCompanyTypeFilterChange = (value: string) => {
    setCompanyType(value)
    setPage(1)
  }

  const handleFollowUpScopeChange = (value: "all" | JobApplicationFollowUpScope) => {
    setFollowUpScope(value)
    setPage(1)
  }

  const handleAttentionFilterChange = (value: string) => {
    setAttention(value)
    setPage(1)
  }

  const handleActionScopeChange = (value: "all" | JobApplicationActionScope) => {
    setActionScope(value)
    const params = new URLSearchParams(searchParams.toString())
    params.delete("applicationId")
    if (value === "all") {
      params.delete("actionScope")
    } else {
      params.set("actionScope", value)
    }
    const query = params.toString()
    router.push(query ? `/applications?${query}` : "/applications")
  }

  const resetApplicationForm = () => {
    setEditingRecord(null)
    setForm({
      companyName: "",
      position: "",
      applyUrl: "",
      submittedAt: "",
      deadline: "",
      companyType: "",
      priority: "0",
      nextFollowUpAt: "",
      remark: "",
    })
  }

  const openCreateForm = () => {
    resetApplicationForm()
    setFormOpen(true)
  }

  const openEditForm = (record: JobApplicationItem) => {
    setEditingRecord(record)
    setForm({
      companyName: record.companyName || "",
      position: record.position || "",
      applyUrl: record.applyUrl || "",
      submittedAt: toDateInput(record.submittedAt),
      deadline: toDateInput(record.deadline),
      companyType: record.companyType || "",
      priority: String(record.priority || 0),
      nextFollowUpAt: toDateInput(record.nextFollowUpAt),
      remark: record.remark || "",
    })
    setFormOpen(true)
  }

  const resetEventForm = () => {
    setEventForm({
      eventType: "FOLLOW_UP",
      eventTitle: "",
      eventTime: "",
      eventResult: "",
      note: "",
    })
  }

  const applyEventTemplate = (template: EventTemplate) => {
    setEventForm((value) => ({
      ...value,
      eventType: template.eventType,
      eventTitle: suggestedEventTitle(template, detail),
      eventTime: value.eventTime || nextDefaultEventTime(template),
      eventResult: "",
      note: template.note,
    }))
  }

  const handleSave = async () => {
    if (!form.companyName.trim() || !form.position.trim()) {
      toast({ title: "信息不完整", description: "公司名称和岗位名称必填", variant: "destructive" })
      return
    }

    setSaving(true)
    try {
      await saveJobApplication({
        id: editingRecord?.id,
        companyName: form.companyName.trim(),
        position: form.position.trim(),
        applyUrl: form.applyUrl.trim(),
        companyType: form.companyType,
        priority: Number(form.priority || 0),
        deadline: form.deadline,
        submittedAt: toDateStartTime(form.submittedAt),
        nextFollowUpAt: toDateStartTime(form.nextFollowUpAt),
        remark: form.remark.trim(),
        source: "manual",
      })
      setFormOpen(false)
      resetApplicationForm()
      toast({ title: editingRecord ? "投递记录已更新" : "投递记录已保存" })
      loadRecords()
      loadSummaryRecords()
      loadWeeklyReview()
      loadActionItems()
    } catch (error) {
      toast({
        title: "保存失败",
        description: error instanceof Error ? error.message : "请稍后重试",
        variant: "destructive",
      })
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async (record: JobApplicationItem) => {
    if (!window.confirm(`确认删除 ${record.companyName} / ${record.position} 这条投递记录吗？`)) {
      return
    }
    try {
      await deleteJobApplication(record.id)
      setRecords((current) => current.filter((item) => item.id !== record.id))
      setSummaryRecords((current) => current.filter((item) => item.id !== record.id))
      setActionItems((current) => current.filter((item) => item.id !== record.id))
      setTotal((value) => Math.max(0, value - 1))
      if (detail?.id === record.id) {
        setDetail(null)
      }
      loadWeeklyReview()
      toast({ title: "投递记录已删除" })
    } catch (error) {
      toast({
        title: "删除失败",
        description: error instanceof Error ? error.message : "请稍后重试",
        variant: "destructive",
      })
    }
  }

  const handleExportCsv = async () => {
    if (total === 0) {
      toast({ title: "暂无可导出的投递记录" })
      return
    }
    const header = [
      "公司",
      "岗位",
      "状态",
      "行动优先级",
      "下一步建议",
      "行动原因",
      "截止风险",
      "距截止天数",
      "跟进已到期",
      "公司类型",
      "关注度",
      "投递时间",
      "截止时间",
      "下次跟进",
      "投递链接",
      "备注",
      "更新时间",
    ]
    let exportRecords = summaryRecords
    if (summaryRecords.length < total) {
      try {
        const res = await fetchJobApplications(buildListQuery(1, Math.max(total, SUMMARY_SIZE)))
        exportRecords = res.list || []
      } catch (error) {
        toast({
          title: "导出失败",
          description: error instanceof Error ? error.message : "请稍后重试",
          variant: "destructive",
        })
        return
      }
    }
    const rows = exportRecords.map((record) => [
      record.companyName,
      record.position,
      record.currentStatusDesc || statusLabel(record.currentStatus),
      actionPriorityLabel(record.actionPriority),
      nextStepSuggestion(record),
      record.actionReason || "",
      deadlineRiskLabel(record.deadlineRisk),
      record.daysUntilDeadline ?? "",
      recordFollowUpOverdue(record) ? "是" : "否",
      companyTypeLabel(record.companyType),
      attentionLabel(record.priority),
      displayDate(record.submittedAt),
      record.deadline || "",
      displayDate(record.nextFollowUpAt),
      record.applyUrl || "",
      record.remark || "",
      formatDate(record.updateTime),
    ])
    const csv = [header, ...rows].map((row) => row.map(escapeCsvCell).join(",")).join("\n")
    const blob = new Blob([`\uFEFF${csv}`], { type: "text/csv;charset=utf-8" })
    const url = URL.createObjectURL(blob)
    const link = document.createElement("a")
    link.href = url
    link.download = `jobclaw-applications-${new Date().toISOString().slice(0, 10)}.csv`
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  }

  const handleSaveEvent = async () => {
    if (!detail) return
    if (!eventForm.eventTitle.trim()) {
      toast({ title: "事件标题必填", description: "例如：一面安排、笔试完成、HR 电话沟通。", variant: "destructive" })
      return
    }

    setEventSaving(true)
    try {
      const event = await saveJobApplicationEvent({
        applicationId: detail.id,
        eventType: eventForm.eventType,
        eventTitle: eventForm.eventTitle.trim(),
        eventTime: eventForm.eventTime ? new Date(eventForm.eventTime).getTime() : undefined,
        eventResult: eventForm.eventResult.trim(),
        note: eventForm.note.trim(),
      })
      setDetail((current) =>
        current
          ? {
              ...current,
              events: [...(current.events || []), event].sort((a, b) => (a.eventTime || 0) - (b.eventTime || 0)),
            }
          : current
      )
      setDetail(await fetchJobApplicationDetail(detail.id))
      resetEventForm()
      loadTodayEvents()
      loadRecords()
      loadSummaryRecords()
      loadWeeklyReview()
      loadActionItems()
      toast({ title: "投递事件已保存" })
    } catch (error) {
      toast({
        title: "保存事件失败",
        description: error instanceof Error ? error.message : "请稍后重试",
        variant: "destructive",
      })
    } finally {
      setEventSaving(false)
    }
  }

  const handleChangeStatus = async (record: JobApplicationItem, targetStatus: JobApplicationStatus) => {
    try {
      const updated = await changeJobApplicationStatus(record.id, targetStatus)
      setRecords((current) => current.map((item) => (item.id === record.id ? updated : item)))
      setSummaryRecords((current) => current.map((item) => (item.id === record.id ? updated : item)))
      if (detail?.id === record.id) {
        const latest = await fetchJobApplicationDetail(record.id)
        setDetail(latest)
      }
      const nextFollowUpText = updated.nextFollowUpAt ? `；下次跟进：${displayDate(updated.nextFollowUpAt)}` : ""
      toast({ title: "状态已更新", description: `${record.companyName} / ${statusLabel(targetStatus)}${nextFollowUpText}` })
      loadWeeklyReview()
      loadActionItems()
    } catch (error) {
      toast({
        title: "状态更新失败",
        description: error instanceof Error ? error.message : "请稍后重试",
        variant: "destructive",
      })
    }
  }

  const handleReopen = async (record: JobApplicationItem) => {
    try {
      const updated = await reopenJobApplication(record.id, "PREPARING", "用户主动重新打开")
      setRecords((current) => current.map((item) => (item.id === record.id ? updated : item)))
      setSummaryRecords((current) => current.map((item) => (item.id === record.id ? updated : item)))
      if (detail?.id === record.id) {
        setDetail(await fetchJobApplicationDetail(record.id))
      }
      toast({ title: "已重新打开", description: `${record.companyName} / ${record.position}` })
      loadRecords()
      loadSummaryRecords()
      loadWeeklyReview()
      loadActionItems()
    } catch (error) {
      toast({
        title: "重新打开失败",
        description: error instanceof Error ? error.message : "请稍后重试",
        variant: "destructive",
      })
    }
  }

  const handleCompleteFollowUp = async (record: JobApplicationItem, note = "从我的投递列表完成跟进") => {
    setCompletingFollowUpId(record.id)
    try {
      const updated = await completeJobApplicationFollowUp({
        id: record.id,
        note,
      })
      setRecords((current) => current.map((item) => (item.id === record.id ? updated : item)))
      setSummaryRecords((current) => current.map((item) => (item.id === record.id ? updated : item)))
      if (detail?.id === record.id) {
        setDetail(await fetchJobApplicationDetail(record.id))
      }
      const nextFollowUpText = updated.nextFollowUpAt ? `下次跟进：${displayDate(updated.nextFollowUpAt)}` : "未设置下次跟进"
      toast({ title: "已记录本次跟进", description: `${record.companyName} / ${record.position}；${nextFollowUpText}` })
      loadRecords()
      loadSummaryRecords()
      loadWeeklyReview()
      loadActionItems()
    } catch (error) {
      toast({
        title: "完成跟进失败",
        description: error instanceof Error ? error.message : "请稍后重试",
        variant: "destructive",
      })
    } finally {
      setCompletingFollowUpId(null)
    }
  }

  const openDetail = async (id: number) => {
    try {
      setDetail(await fetchJobApplicationDetail(id))
      resetEventForm()
    } catch (error) {
      toast({
        title: "详情加载失败",
        description: error instanceof Error ? error.message : "请稍后重试",
        variant: "destructive",
      })
    }
  }

  const sameCompanyJobHref = (companyName: string, internship = false) =>
    `${internship ? "/internship" : "/"}?companyName=${encodeURIComponent(companyName)}`
  const detailNextEvent = detail?.nextKeyEvent || nextKeyEvent(detail?.events)
  const detailEventTemplates = useMemo(() => eventTemplatesForStatus(detail?.currentStatus), [detail?.currentStatus])
  const detailRecommendedEventTypes = useMemo(
    () => new Set(detail?.currentStatus ? RECOMMENDED_EVENT_TYPES_BY_STATUS[detail.currentStatus] || [] : []),
    [detail?.currentStatus],
  )

  if (!userInfo) {
    return (
      <AuthGuard title="请先登录" description="登录后可以记录秋招投递进度、面试安排和跟进提醒。">
        <div />
      </AuthGuard>
    )
  }

  return (
    <div className="min-h-screen bg-surface-muted">
      <div className="mx-auto flex max-w-[1440px] flex-col gap-4 px-6 py-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h1 className="text-xl font-semibold text-content-primary">我的投递</h1>
            <p className="mt-1 text-sm text-content-tertiary">记录岗位投递状态、面试进度和下一次跟进时间。</p>
          </div>
          <div className="flex items-center gap-2">
            <Button variant="outline" className="h-9 gap-2" onClick={handleRefresh} disabled={loading}>
              <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`} />
              刷新
            </Button>
            <Button variant="outline" className="h-9 gap-2" onClick={handleExportCsv} disabled={records.length === 0}>
              <Download className="h-4 w-4" />
              导出
            </Button>
            <Button className="h-9 gap-2" onClick={openCreateForm}>
              <FilePlus2 className="h-4 w-4" />
              新增投递
            </Button>
          </div>
        </div>

        <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-5">
          <div className={statCardClass}>
            <div className={statLabelClass}>总记录</div>
            <div className={statValueClass}>{total}</div>
            <div className="mt-1 text-xs text-content-tertiary">当前筛选结果</div>
          </div>
          <div className={statCardClass}>
            <div className={statLabelClass}>进行中</div>
            <div className={statValueClass}>{activeCount}</div>
            <div className="mt-1 text-xs text-content-tertiary">未结束的投递</div>
          </div>
          <button
            type="button"
            className={`${statCardClass} text-left transition-colors hover:border-amber-200 hover:bg-amber-50`}
            onClick={() => handleActionScopeChange("STALE_SUBMITTED")}
          >
            <div className={statLabelClass}>已投递及后续</div>
            <div className={statValueClass}>{submittedAndLaterCount}</div>
            {staleSubmittedRecords.length > 0 ? (
              <div className="mt-1 text-xs font-medium text-amber-700">{staleSubmittedRecords.length} 条投递超过 7 天未跟进</div>
            ) : null}
            <div className="mt-1 text-xs text-content-tertiary">已进入正式流程</div>
          </button>
          <div className={statCardClass}>
            <div className={statLabelClass}>笔面试中</div>
            <div className={statValueClass}>{interviewCount}</div>
            <div className="mt-1 text-xs text-content-tertiary">笔试 / 面试 / HR</div>
          </div>
          <button
            type="button"
            className={`${statCardClass} text-left transition-colors hover:border-red-200 hover:bg-red-50 ${followUpSummary.overdue > 0 ? "border-red-200 bg-red-50/60" : ""}`}
            onClick={() => handleActionScopeChange("OVERDUE_FOLLOW_UP")}
          >
            <div className={statLabelClass}>待跟进</div>
            <div className="mt-2 flex items-end gap-2">
              <span className={`text-2xl font-semibold ${followUpSummary.overdue > 0 ? "text-red-700" : "text-content-primary"}`}>{followUpSummary.pending}</span>
              {followUpSummary.overdue > 0 ? <span className="pb-1 text-xs font-medium text-red-600">{followUpSummary.overdue} 条已到期</span> : null}
            </div>
            <div className="mt-1 text-xs text-content-tertiary">需要主动处理</div>
          </button>
        </div>

        <div className="rounded-lg border border-surface-border bg-white p-4 shadow-sm">
          <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 className="text-base font-semibold text-content-primary">本周复盘</h2>
              <p className="mt-1 text-xs text-content-tertiary">{weeklyReview.hint}</p>
            </div>
            <Badge variant="outline" className="rounded-md">
              周一至今
            </Badge>
          </div>
          <div className="grid grid-cols-2 gap-2 md:grid-cols-3 xl:grid-cols-10">
            <div className="rounded-md border border-surface-border bg-gray-50 px-3 py-2">
              <div className="text-lg font-semibold text-content-primary">{weeklyReview.created}</div>
              <div className="text-xs text-content-tertiary">新增记录</div>
            </div>
            <div className="rounded-md border border-surface-border bg-gray-50 px-3 py-2">
              <div className="text-lg font-semibold text-blue-700">{weeklyReview.submittedAndLater}</div>
              <div className="text-xs text-content-tertiary">流程推进</div>
            </div>
            <div className="rounded-md border border-surface-border bg-gray-50 px-3 py-2">
              <div className="text-lg font-semibold text-purple-700">{weeklyReview.interviews}</div>
              <div className="text-xs text-content-tertiary">笔面试推进</div>
            </div>
            <div className="rounded-md border border-surface-border bg-gray-50 px-3 py-2">
              <div className="text-lg font-semibold text-emerald-700">{weeklyReview.offers}</div>
              <div className="text-xs text-content-tertiary">Offer 相关</div>
            </div>
            <button
              type="button"
              className="rounded-md border border-surface-border bg-gray-50 px-3 py-2 text-left transition-colors hover:border-red-200 hover:bg-red-50"
              onClick={() => handleActionScopeChange("OVERDUE_FOLLOW_UP")}
            >
              <div className="text-lg font-semibold text-red-700">{weeklyReview.overdue}</div>
              <div className="text-xs text-content-tertiary">逾期跟进</div>
            </button>
            <button
              type="button"
              className="rounded-md border border-surface-border bg-gray-50 px-3 py-2 text-left transition-colors hover:border-amber-200 hover:bg-amber-50"
              onClick={() => handleActionScopeChange("STALE_SUBMITTED")}
            >
              <div className="text-lg font-semibold text-amber-700">{weeklyReview.stale}</div>
              <div className="text-xs text-content-tertiary">静默风险</div>
            </button>
            <button
              type="button"
              className="rounded-md border border-surface-border bg-gray-50 px-3 py-2 text-left transition-colors hover:border-blue-200 hover:bg-blue-50"
              onClick={() => handleActionScopeChange("PROCESS_NEEDS_FOLLOW_UP")}
            >
              <div className="text-lg font-semibold text-sky-700">{weeklyReview.processNeedsFollowUp}</div>
              <div className="text-xs text-content-tertiary">流程待跟进</div>
            </button>
            <button
              type="button"
              className="rounded-md border border-surface-border bg-gray-50 px-3 py-2 text-left transition-colors hover:border-rose-200 hover:bg-rose-50"
              onClick={() => handleActionScopeChange("EXPIRED_DEADLINE")}
            >
              <div className="text-lg font-semibold text-rose-700">{weeklyReview.expiredDeadline}</div>
              <div className="text-xs text-content-tertiary">已过截止</div>
            </button>
            <button
              type="button"
              className="rounded-md border border-surface-border bg-gray-50 px-3 py-2 text-left transition-colors hover:border-cyan-200 hover:bg-cyan-50"
              onClick={() => handleActionScopeChange("UNKNOWN_DEADLINE")}
            >
              <div className="text-lg font-semibold text-cyan-700">{weeklyReview.unknownDeadline}</div>
              <div className="text-xs text-content-tertiary">截止未知</div>
            </button>
            <button
              type="button"
              className="rounded-md border border-surface-border bg-gray-50 px-3 py-2 text-left transition-colors hover:border-violet-200 hover:bg-violet-50"
              onClick={() => handleActionScopeChange("MISSING_APPLY_URL")}
            >
              <div className="text-lg font-semibold text-violet-700">{weeklyReview.missingApplyUrl}</div>
              <div className="text-xs text-content-tertiary">链接缺失</div>
            </button>
          </div>
        </div>

        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-9">
          <button
            type="button"
            className="rounded-lg border border-surface-border bg-white p-4 text-left shadow-sm transition-colors hover:border-blue-200 hover:bg-blue-50"
            onClick={() => handleActionScopeChange("DUE_TODAY")}
          >
            <div className="text-sm font-semibold text-content-primary">今日要投递</div>
            <div className="mt-2 text-2xl font-semibold text-blue-700">{todayTodo.toSubmit.length}</div>
            <MiniList items={todayTodo.toSubmit} emptyText="今天没有临近截止的准备项" />
          </button>
          <button
            type="button"
            className={`rounded-lg border p-4 text-left shadow-sm transition-colors hover:border-orange-200 hover:bg-orange-50 ${todayTodo.dueSoon.length > 0 ? "border-orange-200 bg-orange-50/60" : "border-surface-border bg-white"}`}
            onClick={() => handleActionScopeChange("DUE_SOON")}
          >
            <div className="text-sm font-semibold text-content-primary">临近截止</div>
            <div className={`mt-2 text-2xl font-semibold ${todayTodo.dueSoon.length > 0 ? "text-orange-700" : "text-content-primary"}`}>{todayTodo.dueSoon.length}</div>
            <MiniList items={todayTodo.dueSoon} emptyText="3 天内没有待处理截止项" />
          </button>
          <button
            type="button"
            className={`rounded-lg border p-4 text-left shadow-sm transition-colors hover:border-indigo-200 hover:bg-indigo-50 ${todayTodo.thisWeek.length > 0 ? "border-indigo-200 bg-indigo-50/60" : "border-surface-border bg-white"}`}
            onClick={() => handleActionScopeChange("THIS_WEEK")}
          >
            <div className="text-sm font-semibold text-content-primary">本周截止</div>
            <div className={`mt-2 text-2xl font-semibold ${todayTodo.thisWeek.length > 0 ? "text-indigo-700" : "text-content-primary"}`}>{todayTodo.thisWeek.length}</div>
            <MiniList items={todayTodo.thisWeek} emptyText="4-7 天内没有待处理截止项" />
          </button>
          <button
            type="button"
            className={`rounded-lg border p-4 text-left shadow-sm transition-colors hover:border-rose-200 hover:bg-rose-50 ${todayTodo.expiredDeadline.length > 0 ? "border-rose-200 bg-rose-50/70" : "border-surface-border bg-white"}`}
            onClick={() => handleActionScopeChange("EXPIRED_DEADLINE")}
          >
            <div className="text-sm font-semibold text-content-primary">已过截止</div>
            <div className={`mt-2 text-2xl font-semibold ${todayTodo.expiredDeadline.length > 0 ? "text-rose-700" : "text-content-primary"}`}>{todayTodo.expiredDeadline.length}</div>
            <MiniList items={todayTodo.expiredDeadline} emptyText="没有已过截止的活跃岗位" />
          </button>
          <button
            type="button"
            className={`rounded-lg border p-4 text-left shadow-sm transition-colors hover:border-cyan-200 hover:bg-cyan-50 ${todayTodo.unknownDeadline.length > 0 ? "border-cyan-200 bg-cyan-50/60" : "border-surface-border bg-white"}`}
            onClick={() => handleActionScopeChange("UNKNOWN_DEADLINE")}
          >
            <div className="text-sm font-semibold text-content-primary">截止未知</div>
            <div className={`mt-2 text-2xl font-semibold ${todayTodo.unknownDeadline.length > 0 ? "text-cyan-700" : "text-content-primary"}`}>{todayTodo.unknownDeadline.length}</div>
            <MiniList items={todayTodo.unknownDeadline} emptyText="活跃岗位都已补齐截止时间" />
          </button>
          <button
            type="button"
            className={`rounded-lg border p-4 text-left shadow-sm transition-colors hover:border-violet-200 hover:bg-violet-50 ${todayTodo.missingApplyUrl.length > 0 ? "border-violet-200 bg-violet-50/60" : "border-surface-border bg-white"}`}
            onClick={() => handleActionScopeChange("MISSING_APPLY_URL")}
          >
            <div className="text-sm font-semibold text-content-primary">链接缺失</div>
            <div className={`mt-2 text-2xl font-semibold ${todayTodo.missingApplyUrl.length > 0 ? "text-violet-700" : "text-content-primary"}`}>{todayTodo.missingApplyUrl.length}</div>
            <MiniList items={todayTodo.missingApplyUrl} emptyText="活跃投递都已补齐入口" />
          </button>
          <button
            type="button"
            className="rounded-lg border border-surface-border bg-white p-4 text-left shadow-sm transition-colors hover:border-emerald-200 hover:bg-emerald-50"
            onClick={() => handleActionScopeChange("OVERDUE_FOLLOW_UP")}
          >
            <div className="text-sm font-semibold text-content-primary">今日要跟进</div>
            <div className="mt-2 text-2xl font-semibold text-emerald-700">{todayTodo.toFollowUp.length}</div>
            <MiniList items={todayTodo.toFollowUp} emptyText="今天没有跟进安排" />
          </button>
          <div className="rounded-lg border border-surface-border bg-white p-4 shadow-sm">
            <div className="text-sm font-semibold text-content-primary">今日笔面试</div>
            <div className="mt-2 text-2xl font-semibold text-purple-700">{todayEvents.length}</div>
            {todayEvents.length ? (
              <div className="mt-2 grid gap-1.5">
                {todayEvents.slice(0, 3).map((event) => (
                  <div key={event.id} className="grid gap-0.5 text-xs text-content-secondary" title={`${eventTypeLabel(event.eventType)} / ${eventSubject(event)} / ${event.eventTitle}`}>
                    <div className="truncate">
                      {eventTypeLabel(event.eventType)} / {eventSubject(event)}
                      {eventUrgencyLabel(event.eventUrgency) ? <span className="ml-1 text-content-tertiary">{eventUrgencyLabel(event.eventUrgency)}</span> : null}
                    </div>
                    {event.suggestedPreparation ? <div className="line-clamp-1 text-content-tertiary">{event.suggestedPreparation}</div> : null}
                  </div>
                ))}
                {todayEvents.length > 3 ? <div className="text-xs text-content-tertiary">还有 {todayEvents.length - 3} 条</div> : null}
              </div>
            ) : (
              <div className="text-xs text-content-tertiary">今天没有笔试或面试安排</div>
            )}
          </div>
          <button
            type="button"
            className={`rounded-lg border p-4 text-left shadow-sm transition-colors hover:border-red-200 hover:bg-red-50 ${todayTodo.overdue.length > 0 ? "border-red-200 bg-red-50/70" : "border-surface-border bg-white"}`}
            onClick={() => handleActionScopeChange("OVERDUE_FOLLOW_UP")}
          >
            <div className="text-sm font-semibold text-content-primary">已逾期</div>
            <div className={`mt-2 text-2xl font-semibold ${todayTodo.overdue.length > 0 ? "text-red-700" : "text-content-primary"}`}>{todayTodo.overdue.length}</div>
            <MiniList items={todayTodo.overdue} emptyText="没有逾期跟进" />
          </button>
        </div>

        <div className="rounded-lg border border-surface-border bg-white p-4 shadow-sm">
          <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 className="text-base font-semibold text-content-primary">行动优先级</h2>
              <p className="mt-1 text-xs text-content-tertiary">当前查看：{actionScopeLabel}，按截止时间、跟进逾期和关注度自动排序。</p>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <Select value={actionScope} onValueChange={(value) => handleActionScopeChange(value as "all" | JobApplicationActionScope)}>
                <SelectTrigger className="h-8 w-36">
                  <SelectValue placeholder="行动范围" />
                </SelectTrigger>
                <SelectContent>
                  {ACTION_SCOPE_OPTIONS.map((item) => (
                    <SelectItem key={item.value} value={item.value}>
                      {item.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Badge variant="outline" className="rounded-md">
                {actionItems.length} 条待处理
              </Badge>
            </div>
          </div>
          {actionItems.length ? (
            <div className="grid gap-2 lg:grid-cols-2">
              {actionItems.slice(0, 6).map((record) => {
                const overdue = recordFollowUpOverdue(record)
                const riskText = deadlineRiskLabel(record.deadlineRisk)
                return (
                  <div key={record.id} className={`rounded-md border p-3 ${overdue ? "border-red-200 bg-red-50/60" : "border-surface-border bg-white"}`}>
                    <div className="flex flex-wrap items-start justify-between gap-2">
                      <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                          <Badge variant="outline" className={`rounded-md ${actionPriorityClass(record.actionPriority)}`}>
                            {actionPriorityLabel(record.actionPriority)}
                          </Badge>
                          <span className="truncate text-sm font-medium text-content-primary">
                            {record.companyName} / {record.position}
                          </span>
                        </div>
                        <div className="mt-2 text-sm text-content-secondary">{nextStepSuggestion(record)}</div>
                        <div className="mt-1 flex flex-wrap gap-2 text-xs text-content-tertiary">
                          {record.actionReason ? <span>{record.actionReason}</span> : null}
                          {riskText ? <span>{riskText}</span> : null}
                          {record.nextFollowUpAt ? <span>{followUpHint(record.nextFollowUpAt)}</span> : null}
                        </div>
                      </div>
                      <div className="flex shrink-0 flex-wrap justify-end gap-1.5">
                        <Button variant="outline" size="sm" className="h-8" onClick={() => openDetail(record.id)}>
                          详情
                        </Button>
                        {isFollowUpPending(record) ? (
                          <Button
                            variant="outline"
                            size="sm"
                            className="h-8 gap-1 text-emerald-700 hover:text-emerald-800"
                            disabled={completingFollowUpId === record.id}
                            onClick={() => handleCompleteFollowUp(record)}
                          >
                            <CheckCircle2 className="h-3.5 w-3.5" />
                            已跟进
                          </Button>
                        ) : null}
                        {record.applyUrl ? (
                          <Button variant="ghost" size="sm" className="h-8 px-2" asChild>
                            <a href={record.applyUrl} target="_blank" rel="noopener noreferrer" title="打开投递链接">
                              <ExternalLink className="h-4 w-4" />
                            </a>
                          </Button>
                        ) : null}
                      </div>
                    </div>
                  </div>
                )
              })}
            </div>
          ) : (
            <div className="rounded-md border border-dashed border-surface-border px-4 py-6 text-center text-sm text-content-tertiary">
              暂无需要优先处理的投递事项。
            </div>
          )}
        </div>

        {/* AI-GENERATED AIDEV-NOTE: quiet-submit review */}
        {staleSubmittedRecords.length ? (
          <div className="rounded-lg border border-amber-200 bg-amber-50/60 p-4 shadow-sm">
            <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 className="text-base font-semibold text-amber-950">静默投递复盘</h2>
                <p className="mt-1 text-xs text-amber-800">已投递超过 7 天且没有下一次跟进计划，建议检查邮箱、短信、官网状态或联系内推人。</p>
              </div>
              <Badge variant="outline" className="rounded-md border-amber-300 bg-white text-amber-800">
                {staleSubmittedRecords.length} 条待复盘
              </Badge>
            </div>
            <div className="grid gap-2 lg:grid-cols-2">
              {staleSubmittedRecords.slice(0, 4).map((record) => (
                <div key={record.id} className="rounded-md border border-amber-200 bg-white p-3">
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <Badge variant="outline" className="rounded-md border-amber-200 bg-amber-50 text-amber-800">
                          静默 {staleSubmittedDays(record)} 天
                        </Badge>
                        <span className="truncate text-sm font-medium text-content-primary">
                          {record.companyName} / {record.position}
                        </span>
                      </div>
                      <div className="mt-2 text-sm text-content-secondary">{nextStepSuggestion(record)}</div>
                      <div className="mt-1 text-xs text-content-tertiary">投递：{displayDate(record.submittedAt)}</div>
                    </div>
                    <div className="flex shrink-0 flex-wrap justify-end gap-1.5">
                      <Button variant="outline" size="sm" className="h-8" onClick={() => openDetail(record.id)}>
                        详情
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        className="h-8 gap-1 text-emerald-700 hover:text-emerald-800"
                        disabled={completingFollowUpId === record.id}
                        onClick={() => handleCompleteFollowUp(record, "静默投递复盘：已检查通知渠道并设置下次跟进")}
                      >
                        <CheckCircle2 className="h-3.5 w-3.5" />
                        已检查
                      </Button>
                      {record.applyUrl ? (
                        <Button variant="ghost" size="sm" className="h-8 px-2" asChild>
                          <a href={record.applyUrl} target="_blank" rel="noopener noreferrer" title="打开投递链接">
                            <ExternalLink className="h-4 w-4" />
                          </a>
                        </Button>
                      ) : null}
                    </div>
                  </div>
                </div>
              ))}
            </div>
            {staleSubmittedRecords.length > 4 ? (
              <div className="mt-2 text-center text-xs text-amber-800">还有 {staleSubmittedRecords.length - 4} 条，可在下方表格筛选“已投递”继续处理。</div>
            ) : null}
          </div>
        ) : null}

        {/* AI-GENERATED AIDEV-NOTE: process gap review */}
        {processNeedsFollowUpRecords.length ? (
          <div className="rounded-lg border border-sky-200 bg-sky-50/60 p-4 shadow-sm">
            <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 className="text-base font-semibold text-sky-950">流程断点处理</h2>
                <p className="mt-1 text-xs text-sky-800">已进入笔面试、HR 或 Offer 阶段，但没有下一次跟进计划，建议补齐复盘结论和提醒。</p>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <Badge variant="outline" className="rounded-md border-sky-300 bg-white text-sky-800">
                  {processNeedsFollowUpRecords.length} 条待补跟进
                </Badge>
                <Button variant="outline" size="sm" className="h-8 border-sky-200 bg-white text-sky-800 hover:bg-sky-100" onClick={() => handleActionScopeChange("PROCESS_NEEDS_FOLLOW_UP")}>
                  查看全部
                </Button>
              </div>
            </div>
            <div className="grid gap-2 lg:grid-cols-2">
              {processNeedsFollowUpRecords.slice(0, 4).map((record) => (
                <div key={record.id} className="rounded-md border border-sky-200 bg-white p-3">
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <Badge variant="outline" className="rounded-md border-sky-200 bg-sky-50 text-sky-800">
                          {record.currentStatusDesc || statusLabel(record.currentStatus)}
                        </Badge>
                        <span className="truncate text-sm font-medium text-content-primary">
                          {record.companyName} / {record.position}
                        </span>
                      </div>
                      <div className="mt-2 text-sm text-content-secondary">{nextStepSuggestion(record)}</div>
                      <div className="mt-1 text-xs text-content-tertiary">更新：{formatDate(record.updateTime)}</div>
                    </div>
                    <div className="flex shrink-0 flex-wrap justify-end gap-1.5">
                      <Button variant="outline" size="sm" className="h-8" onClick={() => openDetail(record.id)}>
                        详情
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        className="h-8 gap-1 text-emerald-700 hover:text-emerald-800"
                        disabled={completingFollowUpId === record.id}
                        onClick={() => handleCompleteFollowUp(record, "流程断点处理：已补充阶段复盘并设置下次跟进")}
                      >
                        <CheckCircle2 className="h-3.5 w-3.5" />
                        已复盘
                      </Button>
                      {record.applyUrl ? (
                        <Button variant="ghost" size="sm" className="h-8 px-2" asChild>
                          <a href={record.applyUrl} target="_blank" rel="noopener noreferrer" title="打开投递链接">
                            <ExternalLink className="h-4 w-4" />
                          </a>
                        </Button>
                      ) : null}
                    </div>
                  </div>
                </div>
              ))}
            </div>
            {processNeedsFollowUpRecords.length > 4 ? (
              <div className="mt-2 text-center text-xs text-sky-800">还有 {processNeedsFollowUpRecords.length - 4} 条，可点击“查看全部”进入行动队列处理。</div>
            ) : null}
          </div>
        ) : null}

        <section className="grid gap-3">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 className="text-base font-semibold text-content-primary">阶段看板</h2>
              <p className="mt-1 text-xs text-content-tertiary">按当前筛选结果查看每个投递阶段，列内优先展示最需要处理的记录。</p>
            </div>
            <Badge variant="outline" className="rounded-md">
              {summaryRecords.length} 条记录
            </Badge>
          </div>
          <div className="grid gap-3 xl:grid-cols-5">
            {boardColumns.map((column) => (
              <div key={column.key} className="rounded-lg border border-surface-border bg-white shadow-sm">
                <div className="border-b border-surface-border px-3 py-3">
                  <div className="flex items-center justify-between gap-2">
                    <h3 className="text-sm font-semibold text-content-primary">{column.label}</h3>
                    <Badge variant="secondary" className="rounded-md">
                      {column.total}
                    </Badge>
                  </div>
                  <div className="mt-1 text-xs text-content-tertiary">{column.hint}</div>
                </div>
                <div className="grid gap-2 p-3">
                  {column.records.map((record) => {
                    const overdue = recordFollowUpOverdue(record)
                    const riskText = deadlineRiskLabel(record.deadlineRisk)
                    return (
                      <button
                        key={record.id}
                        type="button"
                        className={`rounded-md border p-3 text-left transition-colors hover:bg-gray-50 ${
                          overdue ? "border-red-200 bg-red-50/60" : "border-surface-border bg-white"
                        }`}
                        onClick={() => openDetail(record.id)}
                      >
                        <div className="flex flex-wrap items-center gap-1.5">
                          <Badge variant={statusBadgeVariant(record)} className="rounded-md">
                            {record.currentStatusDesc || statusLabel(record.currentStatus)}
                          </Badge>
                          {hasActionPriority(record) ? (
                            <Badge variant="outline" className={`rounded-md ${actionPriorityClass(record.actionPriority)}`}>
                              {actionPriorityLabel(record.actionPriority)}
                            </Badge>
                          ) : null}
                        </div>
                        <div className="mt-2 truncate text-sm font-medium text-content-primary" title={`${record.companyName} / ${record.position}`}>
                          {record.companyName} / {record.position}
                        </div>
                        <div className="mt-1 line-clamp-2 text-xs leading-5 text-content-secondary">{nextStepSuggestion(record)}</div>
                        <div className="mt-2 flex flex-wrap gap-2 text-xs text-content-tertiary">
                          {riskText ? <span>{riskText}</span> : null}
                          {record.nextFollowUpAt ? <span>{followUpHint(record.nextFollowUpAt)}</span> : null}
                        </div>
                      </button>
                    )
                  })}
                  {column.records.length === 0 ? <div className="rounded-md border border-dashed border-surface-border p-4 text-center text-xs text-content-tertiary">暂无记录</div> : null}
                  {column.total > column.records.length ? (
                    <div className="text-center text-xs text-content-tertiary">还有 {column.total - column.records.length} 条，可在下方表格继续筛选</div>
                  ) : null}
                </div>
              </div>
            ))}
          </div>
        </section>

        <div className="grid gap-3 lg:grid-cols-3">
          <div className="rounded-lg border border-surface-border bg-white p-4 shadow-sm lg:col-span-2">
            <div className="mb-3 flex items-center justify-between gap-3">
              <div>
                <h2 className="text-base font-semibold text-content-primary">状态漏斗</h2>
                <p className="mt-1 text-xs text-content-tertiary">按当前筛选结果统计投递阶段分布。</p>
              </div>
              <div className="text-xs text-content-tertiary">基数：{summaryRecords.length}</div>
            </div>
            <div className="grid gap-2">
              {statusStats.length ? (
                statusStats.map((item) => {
                  const width = percentValue(item.count, Math.max(1, summaryRecords.length))
                  return (
                    <div key={item.value} className="grid gap-1">
                      <div className="flex items-center justify-between text-xs">
                        <span className="text-content-secondary">{item.label}</span>
                        <span className="font-medium text-content-primary">{item.count}</span>
                      </div>
                      <div className="h-2 overflow-hidden rounded-full bg-surface-muted">
                        <div className="h-full rounded-full bg-blue-500" style={{ width: `${width}%` }} />
                      </div>
                    </div>
                  )
                })
              ) : (
                <div className="text-sm text-content-tertiary">暂无可统计记录</div>
              )}
            </div>
          </div>
          <div className="rounded-lg border border-surface-border bg-white p-4 shadow-sm">
            <h2 className="text-base font-semibold text-content-primary">公司类型分布</h2>
            <p className="mt-1 text-xs text-content-tertiary">帮助判断投递组合是否过于集中。</p>
            <div className="mt-3 grid gap-2">
              {companyStats.length ? (
                companyStats.map((item) => (
                  <div key={item.label} className="flex items-center justify-between rounded-md bg-surface-muted px-3 py-2 text-sm">
                    <span className="text-content-secondary">{item.label}</span>
                    <span className="font-medium text-content-primary">{item.count}</span>
                  </div>
                ))
              ) : (
                <div className="text-sm text-content-tertiary">暂无公司类型数据</div>
              )}
            </div>
            <div className="mt-4 rounded-md border border-blue-100 bg-blue-50 px-3 py-2 text-xs text-blue-700">
              Offer 转化：{offerCount} / {submittedAndLaterCount}（{percentValue(offerCount, conversionBase)}%）
            </div>
          </div>
        </div>

        <div className="overflow-hidden rounded-lg border border-surface-border bg-white shadow-sm">
          <div className="border-b border-surface-border bg-white px-4 py-3">
            <div className="mb-3 flex items-center justify-between gap-3">
              <div>
                <h2 className="text-base font-semibold text-content-primary">投递记录</h2>
                <p className="mt-1 text-xs text-content-tertiary">每页最多显示 {PAGE_SIZE} 条，支持按公司、岗位、状态、关注度和跟进状态筛选。</p>
              </div>
              <div className="text-sm text-content-tertiary">
                共 <span className="font-medium text-content-primary">{total}</span> 条
              </div>
            </div>
            <div className="flex flex-1 flex-wrap items-center gap-2">
              <div className="relative w-full max-w-sm">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-content-tertiary" />
                <Input
                  value={companyName}
                  onChange={(event) => setCompanyName(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter") handleSearch()
                  }}
                  placeholder="按公司搜索"
                  className="h-9 pl-9"
                />
              </div>
              <div className="relative w-full max-w-sm">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-content-tertiary" />
                <Input
                  value={position}
                  onChange={(event) => setPosition(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter") handleSearch()
                  }}
                  placeholder="按岗位搜索"
                  className="h-9 pl-9"
                />
              </div>
              <Select value={status} onValueChange={(value) => handleStatusFilterChange(value as "all" | JobApplicationStatus)}>
                <SelectTrigger className="h-9 w-40">
                  <SelectValue placeholder="全部状态" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">全部状态</SelectItem>
                  {STATUS_OPTIONS.map((item) => (
                    <SelectItem key={item.value} value={item.value}>
                      {item.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Select value={companyType} onValueChange={handleCompanyTypeFilterChange}>
                <SelectTrigger className="h-9 w-36">
                  <SelectValue placeholder="公司类型" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">全部类型</SelectItem>
                  {COMPANY_TYPE_OPTIONS.map((item) => (
                    <SelectItem key={item.value} value={item.value}>
                      {item.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Select value={followUpScope} onValueChange={(value) => handleFollowUpScopeChange(value as "all" | JobApplicationFollowUpScope)}>
                <SelectTrigger className="h-9 w-36">
                  <SelectValue placeholder="跟进状态" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">全部跟进</SelectItem>
                  {FOLLOW_UP_SCOPE_OPTIONS.map((item) => (
                    <SelectItem key={item.value} value={item.value}>
                      {item.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Select value={attention} onValueChange={handleAttentionFilterChange}>
                <SelectTrigger className="h-9 w-32">
                  <SelectValue placeholder="关注度" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">全部关注度</SelectItem>
                  {ATTENTION_OPTIONS.map((item) => (
                    <SelectItem key={item.value} value={item.value}>
                      {item.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Button className="h-9 gap-2" onClick={handleSearch} disabled={loading}>
                <Search className="h-4 w-4" />
                查询
              </Button>
              <Button variant="outline" className="h-9" onClick={handleResetFilters} disabled={loading || !hasActiveFilters}>
                重置
              </Button>
            </div>
            {hasActiveFilters ? (
              <div className="mt-3 rounded-md border border-blue-100 bg-blue-50 px-3 py-2 text-xs text-blue-700">
                当前已启用筛选，统计卡片和表格均按筛选结果计算。
              </div>
            ) : null}
          </div>

          <div className="overflow-x-auto">
            <Table className="w-full border-collapse border-y border-surface-border text-center">
              <TableHeader>
                <TableRow className="border-b border-surface-border">
                  <TableHead className={`${tableHeadClass} min-w-[240px]`}>岗位</TableHead>
                  <TableHead className={`${tableHeadClass} w-[120px]`}>状态</TableHead>
                  <TableHead className={`${tableHeadClass} w-[110px]`}>关注度</TableHead>
                  <TableHead className={`${tableHeadClass} w-[170px]`}>下次跟进</TableHead>
                  <TableHead className={`${tableHeadClass} min-w-[220px]`}>状态推进</TableHead>
                  <TableHead className={`${tableHeadClass} w-[170px]`}>更新时间</TableHead>
                  <TableHead className={`${tableHeadClass} w-[280px]`}>操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {records.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={7} className="h-32 border-r-0 text-center text-content-tertiary">
                      <div className="flex flex-col items-center justify-center gap-3">
                        <div className="text-sm">
                          {loading ? "加载中..." : hasActiveFilters ? "没有符合筛选条件的投递记录" : "暂无投递记录"}
                        </div>
                        {!loading ? (
                          <div className="flex items-center justify-center gap-2">
                            {hasActiveFilters ? (
                              <Button variant="outline" size="sm" onClick={handleResetFilters}>
                                清空筛选
                              </Button>
                            ) : null}
                            <Button size="sm" onClick={openCreateForm}>
                              新增投递
                            </Button>
                          </div>
                        ) : null}
                      </div>
                    </TableCell>
                  </TableRow>
                ) : (
                  records.map((record) => {
                    const followUpOverdue = recordFollowUpOverdue(record)
                    const followUpPending = isFollowUpPending(record)
                    return (
                      <TableRow key={record.id} className={`border-b border-surface-border last:border-b-0 ${followUpOverdue ? "bg-red-50/70 hover:bg-red-50" : "hover:bg-gray-50"}`}>
                        <TableCell className={tableCellClass}>
                          <div className="font-medium text-content-primary">{record.companyName}</div>
                          <div className="mt-1 text-sm text-content-secondary">{record.position}</div>
                          {record.companyType ? <div className="mt-1 text-xs text-content-tertiary">类型：{companyTypeLabel(record.companyType)}</div> : null}
                          {record.submittedAt ? <div className="mt-1 text-xs text-content-tertiary">投递：{displayDate(record.submittedAt)}</div> : null}
                          {record.deadline ? <div className="mt-1 text-xs text-content-tertiary">截止：{record.deadline}</div> : null}
                          {hasActionPriority(record) ? (
                            <div className="mt-2 flex flex-wrap justify-center gap-1.5 text-xs">
                              <Badge variant="outline" className={`rounded-md ${actionPriorityClass(record.actionPriority)}`}>
                                {actionPriorityLabel(record.actionPriority)}
                              </Badge>
                              <span className="max-w-[220px] truncate text-content-tertiary" title={nextStepSuggestion(record)}>
                                {nextStepSuggestion(record)}
                              </span>
                            </div>
                          ) : null}
                        </TableCell>
                        <TableCell className={tableCellClass}>
                          <Badge variant={statusBadgeVariant(record)} className="rounded-md">
                            {record.currentStatusDesc || statusLabel(record.currentStatus)}
                          </Badge>
                        </TableCell>
                        <TableCell className={tableCellClass}>
                          <div className="flex items-center justify-center gap-1 text-sm text-amber-600">
                            <Star className="h-3.5 w-3.5" />
                            <span>{attentionStars(record.priority)}</span>
                          </div>
                        </TableCell>
                        <TableCell className={tableCellClass}>
                          <div className={`flex items-center justify-center gap-1 text-sm ${followUpOverdue ? "font-medium text-red-700" : "text-content-secondary"}`}>
                            <CalendarClock className="h-4 w-4" />
                            {displayDate(record.nextFollowUpAt)}
                          </div>
                          {record.nextFollowUpAt ? (
                            <div className={`mt-1 text-xs ${followUpOverdue ? "text-red-600" : "text-content-tertiary"}`}>{followUpHint(record.nextFollowUpAt)}</div>
                          ) : null}
                          {followUpOverdue ? (
                            <Badge variant="outline" className="mt-2 gap-1 rounded-md border-red-200 bg-red-50 text-red-700 hover:bg-red-50">
                              <AlertTriangle className="h-3.5 w-3.5" />
                              已到期
                            </Badge>
                          ) : null}
                        </TableCell>
                        <TableCell className={tableCellClass}>
                          <div className="flex flex-wrap justify-center gap-1.5">
                            {(NEXT_STATUS[record.currentStatus] || []).map((next) => (
                              <Button
                                key={next}
                                variant="outline"
                                size="sm"
                                className="h-8 px-2"
                                onClick={() => handleChangeStatus(record, next)}
                              >
                                {statusLabel(next)}
                              </Button>
                            ))}
                            {record.terminal ? (
                              <Button variant="outline" size="sm" className="h-8 gap-1 text-blue-700" onClick={() => handleReopen(record)}>
                                <RotateCcw className="h-3.5 w-3.5" />
                                重新打开
                              </Button>
                            ) : null}
                          </div>
                        </TableCell>
                        <TableCell className={`${tableCellClass} text-sm text-content-secondary`}>{formatDate(record.updateTime)}</TableCell>
                        <TableCell className={tableCellClass}>
                          <div className="flex flex-wrap items-center justify-center gap-2">
                            <Button variant="outline" size="sm" onClick={() => openDetail(record.id)}>
                              详情
                            </Button>
                            <Button variant="outline" size="sm" className="gap-1" onClick={() => openEditForm(record)}>
                              <Pencil className="h-3.5 w-3.5" />
                              编辑
                            </Button>
                            {followUpPending ? (
                              <Button
                                variant="outline"
                                size="sm"
                                className="gap-1 text-emerald-700 hover:text-emerald-800"
                                disabled={completingFollowUpId === record.id}
                                onClick={() => handleCompleteFollowUp(record)}
                              >
                                <CheckCircle2 className="h-3.5 w-3.5" />
                                {completingFollowUpId === record.id ? "记录中" : "已跟进"}
                              </Button>
                            ) : null}
                            <Button variant="ghost" size="sm" className="text-red-600 hover:text-red-700" onClick={() => handleDelete(record)}>
                              <Trash2 className="h-4 w-4" />
                            </Button>
                            {record.applyUrl ? (
                              <Button variant="ghost" size="sm" asChild>
                                <a href={record.applyUrl} target="_blank" rel="noopener noreferrer" title="打开投递链接">
                                  <ExternalLink className="h-4 w-4" />
                                </a>
                              </Button>
                            ) : null}
                          </div>
                        </TableCell>
                      </TableRow>
                    )
                  })
                )}
              </TableBody>
            </Table>
          </div>

          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-surface-border bg-surface-muted/50 p-4">
            <div className="text-sm text-content-tertiary">
              {total > 0 ? (
                <>
                  显示 <span className="font-medium text-content-primary">{currentPageStart}</span>-<span className="font-medium text-content-primary">{currentPageEnd}</span> 条，
                  共 <span className="font-medium text-content-primary">{total}</span> 条记录
                </>
              ) : (
                "暂无记录"
              )}
            </div>
            <div className="flex items-center gap-3">
              <div className="text-sm text-content-tertiary">
                第 <span className="font-medium text-content-primary">{page}</span> / {totalPages} 页
              </div>
              <div className="flex items-center gap-2">
                <Button variant="outline" className="h-9" disabled={page <= 1 || loading} onClick={() => setPage((v) => Math.max(1, v - 1))}>
                  上一页
                </Button>
                <Button
                  variant="outline"
                  className="h-9"
                  disabled={page >= totalPages || loading}
                  onClick={() => setPage((v) => Math.min(totalPages, v + 1))}
                >
                  下一页
                </Button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <Dialog
        open={formOpen}
        onOpenChange={(open) => {
          setFormOpen(open)
          if (!open) resetApplicationForm()
        }}
      >
        <DialogContent className="max-h-[88vh] max-w-4xl overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{editingRecord ? "编辑投递记录" : "新增投递记录"}</DialogTitle>
          </DialogHeader>
          <div className="grid gap-4 md:grid-cols-6">
            <div className="grid gap-1.5 md:col-span-3">
              <label className="text-sm font-medium text-content-secondary" htmlFor="application-company-name">
                公司名称 <span className="text-red-500">*</span>
              </label>
              <Input
                id="application-company-name"
                className="h-10"
                placeholder="请输入公司名称"
                value={form.companyName}
                onChange={(event) => setForm((v) => ({ ...v, companyName: event.target.value }))}
              />
            </div>
            <div className="grid gap-1.5 md:col-span-3">
              <label className="text-sm font-medium text-content-secondary" htmlFor="application-position">
                岗位名称 <span className="text-red-500">*</span>
              </label>
              <Input
                id="application-position"
                className="h-10"
                placeholder="请输入岗位名称"
                value={form.position}
                onChange={(event) => setForm((v) => ({ ...v, position: event.target.value }))}
              />
            </div>
            <div className="grid gap-1.5 md:col-span-4">
              <label className="text-sm font-medium text-content-secondary" htmlFor="application-apply-url">
                投递链接 <span className="font-normal text-content-tertiary">选填</span>
              </label>
              <Input
                id="application-apply-url"
                className="h-10"
                placeholder="官网、牛客、Boss 或内推链接"
                value={form.applyUrl}
                onChange={(event) => setForm((v) => ({ ...v, applyUrl: event.target.value }))}
              />
            </div>
            <div className="grid gap-1.5 md:col-span-2">
              <label className="text-sm font-medium text-content-secondary">
                公司类型 <span className="font-normal text-content-tertiary">选填</span>
              </label>
              <Select value={form.companyType || "未选择"} onValueChange={(value) => setForm((v) => ({ ...v, companyType: value === "未选择" ? "" : value }))}>
                <SelectTrigger className="h-10">
                  <SelectValue placeholder="公司类型" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="未选择">未选择</SelectItem>
                  {COMPANY_TYPE_OPTIONS.map((item) => (
                    <SelectItem key={item.value} value={item.value}>
                      {item.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid gap-1.5 md:col-span-2">
              <label className="text-sm font-medium text-content-secondary">
                关注度 <span className="font-normal text-content-tertiary">选填</span>
              </label>
              <Select value={form.priority} onValueChange={(value) => setForm((v) => ({ ...v, priority: value }))}>
                <SelectTrigger className="h-10">
                  <SelectValue placeholder="关注度" />
                </SelectTrigger>
                <SelectContent>
                  {ATTENTION_OPTIONS.map((item) => (
                    <SelectItem key={item.value} value={item.value}>
                      {item.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="text-xs text-content-tertiary">只用于排序和提醒，不区分普通/重点/冲刺。</p>
            </div>
            <div className="grid gap-1.5 md:col-span-2">
              <label className="text-sm font-medium text-content-secondary" htmlFor="application-submitted-at">
                投递时间 <span className="font-normal text-content-tertiary">选填</span>
              </label>
              <Input
                id="application-submitted-at"
                className="h-10"
                type="date"
                value={form.submittedAt}
                onChange={(event) => setForm((v) => ({ ...v, submittedAt: event.target.value }))}
              />
              <p className="text-xs text-content-tertiary">已经投递时填写，可用于统计投递节奏。</p>
            </div>
            <div className="grid gap-1.5 md:col-span-2">
              <label className="text-sm font-medium text-content-secondary" htmlFor="application-deadline">
                截止时间 <span className="font-normal text-content-tertiary">选填</span>
              </label>
              <Input
                id="application-deadline"
                className="h-10"
                type="date"
                value={form.deadline}
                onChange={(event) => setForm((v) => ({ ...v, deadline: event.target.value }))}
              />
              <p className="text-xs text-content-tertiary">用于记录网申、内推或岗位关闭时间。</p>
            </div>
            <div className="grid gap-1.5 md:col-span-2">
              <label className="text-sm font-medium text-content-secondary" htmlFor="application-next-follow-up-at">
                下次跟进时间 <span className="font-normal text-content-tertiary">选填</span>
              </label>
              <Input
                id="application-next-follow-up-at"
                className="h-10"
                type="date"
                value={form.nextFollowUpAt}
                onChange={(event) => setForm((v) => ({ ...v, nextFollowUpAt: event.target.value }))}
              />
              <p className="text-xs text-content-tertiary">用于提醒自己查看进度、补材料或联系内推人。</p>
            </div>
            <div className="grid gap-1.5 md:col-span-6">
              <label className="text-sm font-medium text-content-secondary" htmlFor="application-remark">
                备注 <span className="font-normal text-content-tertiary">选填</span>
              </label>
              <Textarea
                id="application-remark"
                className="min-h-28"
                placeholder="例如内推人、笔试信息、简历版本、跟进计划"
                value={form.remark}
                onChange={(event) => setForm((v) => ({ ...v, remark: event.target.value }))}
              />
            </div>
          </div>
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => setFormOpen(false)}>
              取消
            </Button>
            <Button onClick={handleSave} disabled={saving}>
              {saving ? "保存中..." : editingRecord ? "更新" : "保存"}
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      <Dialog open={!!detail} onOpenChange={(open) => !open && setDetail(null)}>
        <DialogContent className="max-h-[85vh] max-w-4xl overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{detail ? `${detail.companyName} / ${detail.position}` : "投递详情"}</DialogTitle>
          </DialogHeader>
          {detail ? (
            <div className="grid gap-4">
              <div className="grid gap-3 rounded-md border border-surface-border p-3 text-sm md:grid-cols-2">
                <div className="flex flex-wrap items-center justify-between gap-2 md:col-span-2">
                  <div>
                    当前状态：
                    <Badge variant={statusBadgeVariant(detail)} className="ml-1 rounded-md">
                      {detail.currentStatusDesc || statusLabel(detail.currentStatus)}
                    </Badge>
                  </div>
                  {detail.terminal ? (
                    <Button variant="outline" size="sm" className="gap-1 text-blue-700" onClick={() => handleReopen(detail)}>
                      <RotateCcw className="h-3.5 w-3.5" />
                      重新打开
                    </Button>
                  ) : null}
                </div>
                <div className="flex flex-wrap gap-2 md:col-span-2">
                  {detail.jobId ? (
                    <Button variant="outline" size="sm" asChild>
                      <Link href={`/job?id=${detail.jobId}`}>
                        <ExternalLink className="h-3.5 w-3.5" />
                        查看原岗位
                      </Link>
                    </Button>
                  ) : null}
                  <Button variant="outline" size="sm" asChild>
                    <Link href={sameCompanyJobHref(detail.companyName)}>
                      同公司校招
                    </Link>
                  </Button>
                  <Button variant="outline" size="sm" asChild>
                    <Link href={sameCompanyJobHref(detail.companyName, true)}>
                      同公司实习
                    </Link>
                  </Button>
                </div>
                <div>关注度：{attentionLabel(detail.priority)} {attentionStars(detail.priority) !== "-" ? `(${attentionStars(detail.priority)})` : ""}</div>
                <div>公司类型：{companyTypeLabel(detail.companyType)}</div>
                <div>投递时间：{displayDate(detail.submittedAt)}</div>
                <div>截止时间：{detail.deadline || "-"}</div>
                <div>下次跟进：{displayDate(detail.nextFollowUpAt)}</div>
                <div>来源：{detail.source || (detail.jobId ? `岗位库 #${detail.jobId}` : "手动创建")}</div>
                <div className="md:col-span-2">
                  投递链接：
                  {detail.applyUrl ? (
                    <a className="ml-1 text-blue-600 hover:underline" href={detail.applyUrl} target="_blank" rel="noopener noreferrer">
                      打开链接
                    </a>
                  ) : (
                    " -"
                  )}
                </div>
                <div className="rounded-md border border-blue-100 bg-blue-50 px-3 py-2 text-blue-700 md:col-span-2">
                  {nextStepSuggestion(detail)}
                  {detail.actionReason ? <div className="mt-1 text-xs text-blue-600">原因：{detail.actionReason}</div> : null}
                  {detail.deadlineRisk ? <div className="mt-1 text-xs text-blue-600">截止风险：{deadlineRiskLabel(detail.deadlineRisk)}</div> : null}
                </div>
                {detailNextEvent ? (
                  <div className="rounded-md border border-emerald-100 bg-emerald-50 px-3 py-2 md:col-span-2">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="flex flex-wrap items-center gap-2">
                        <Badge variant="outline" className="rounded-md border-emerald-200 bg-white text-emerald-700">
                          下一次关键事件
                        </Badge>
                        <span className="text-sm font-medium text-emerald-900">
                          {eventTypeLabel(detailNextEvent.eventType)} / {detailNextEvent.eventTitle}
                        </span>
                        {eventUrgencyLabel(detailNextEvent.eventUrgency) ? (
                          <span className="text-xs text-emerald-700">{eventUrgencyLabel(detailNextEvent.eventUrgency)}</span>
                        ) : null}
                      </div>
                      <span className="text-xs text-emerald-700">
                        {formatDate(detailNextEvent.eventTime)}
                        {detailNextEvent.hoursUntilEvent != null ? ` / 约 ${Math.max(0, detailNextEvent.hoursUntilEvent)} 小时后` : ""}
                      </span>
                    </div>
                    {detailNextEvent.suggestedPreparation ? (
                      <div className="mt-2 text-sm leading-5 text-emerald-800">{detailNextEvent.suggestedPreparation}</div>
                    ) : null}
                  </div>
                ) : null}
                <div className="md:col-span-2">复盘备注：{detail.remark || "-"}</div>
              </div>

              <div>
                <h3 className="mb-2 text-sm font-semibold text-content-primary">新增事件</h3>
                <div className="mb-2 flex flex-wrap gap-2">
                  {detailEventTemplates.map((template) => {
                    const recommended = detailRecommendedEventTypes.has(template.eventType)
                    return (
                      <Button
                        key={template.eventType}
                        type="button"
                        variant={recommended ? "default" : "outline"}
                        size="sm"
                        className="h-8 gap-1.5"
                        title={template.note}
                        onClick={() => applyEventTemplate(template)}
                      >
                        {template.label}
                        {recommended ? <span className="text-[10px] opacity-80">推荐</span> : null}
                      </Button>
                    )
                  })}
                </div>
                <div className="grid gap-3 rounded-md border border-surface-border p-3 md:grid-cols-2">
                  <div className="grid gap-1.5">
                    <label className="text-sm font-medium text-content-secondary">事件类型</label>
                    <Select value={eventForm.eventType} onValueChange={(value) => setEventForm((v) => ({ ...v, eventType: value }))}>
                      <SelectTrigger className="h-10">
                        <SelectValue placeholder="事件类型" />
                      </SelectTrigger>
                      <SelectContent>
                        {EVENT_TYPE_OPTIONS.map((item) => (
                          <SelectItem key={item.value} value={item.value}>
                            {item.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="grid gap-1.5">
                    <label className="text-sm font-medium text-content-secondary" htmlFor="application-event-title">
                      事件标题
                    </label>
                    <Input
                      id="application-event-title"
                      className="h-10"
                      placeholder="例如一面安排、笔试完成"
                      value={eventForm.eventTitle}
                      onChange={(event) => setEventForm((v) => ({ ...v, eventTitle: event.target.value }))}
                    />
                  </div>
                  <div className="grid gap-1.5">
                    <label className="text-sm font-medium text-content-secondary" htmlFor="application-event-time">
                      事件时间
                    </label>
                    <Input
                      id="application-event-time"
                      className="h-10"
                      type="datetime-local"
                      value={eventForm.eventTime}
                      onChange={(event) => setEventForm((v) => ({ ...v, eventTime: event.target.value }))}
                    />
                  </div>
                  <div className="grid gap-1.5">
                    <label className="text-sm font-medium text-content-secondary" htmlFor="application-event-result">
                      事件结果
                    </label>
                    <Input
                      id="application-event-result"
                      className="h-10"
                      placeholder="例如通过 / 待反馈"
                      value={eventForm.eventResult}
                      onChange={(event) => setEventForm((v) => ({ ...v, eventResult: event.target.value }))}
                    />
                  </div>
                  <div className="grid gap-1.5 md:col-span-2">
                    <label className="text-sm font-medium text-content-secondary" htmlFor="application-event-note">
                      补充说明
                    </label>
                    <Textarea
                      id="application-event-note"
                      className="min-h-24"
                      placeholder="例如面试官、题目、复盘、下一步计划"
                      value={eventForm.note}
                      onChange={(event) => setEventForm((v) => ({ ...v, note: event.target.value }))}
                    />
                  </div>
                  <div className="md:col-span-2 flex justify-end">
                    <Button onClick={handleSaveEvent} disabled={eventSaving}>
                      {eventSaving ? "保存中..." : "保存事件"}
                    </Button>
                  </div>
                </div>
              </div>

              <div>
                <h3 className="mb-2 text-sm font-semibold text-content-primary">投递事件</h3>
                <div className="divide-y divide-surface-border rounded-md border border-surface-border">
                  {detail.events?.length ? (
                    detail.events.map((event) => (
                      <div key={event.id} className="grid gap-1 p-3 text-sm">
                        <div className="flex flex-wrap items-center justify-between gap-3">
                          <span className="font-medium text-content-primary">
                            {eventTypeLabel(event.eventType)} / {event.eventTitle}
                            {eventUrgencyLabel(event.eventUrgency) ? <span className="ml-2 text-xs font-normal text-content-tertiary">{eventUrgencyLabel(event.eventUrgency)}</span> : null}
                          </span>
                          <span className="text-content-tertiary">{formatDate(event.eventTime)}</span>
                        </div>
                        {event.suggestedPreparation ? <div className="text-content-secondary">{event.suggestedPreparation}</div> : null}
                        {event.eventResult ? <div className="text-content-secondary">结果：{event.eventResult}</div> : null}
                        {event.note ? <div className="text-content-tertiary">备注：{event.note}</div> : null}
                      </div>
                    ))
                  ) : (
                    <div className="p-3 text-sm text-content-tertiary">暂无投递事件</div>
                  )}
                </div>
              </div>

              <div>
                <h3 className="mb-2 text-sm font-semibold text-content-primary">状态历史</h3>
                <div className="divide-y divide-surface-border rounded-md border border-surface-border">
                  {detail.statusLogs?.length ? (
                    detail.statusLogs.map((log) => (
                      <div key={log.id} className="flex items-center justify-between gap-3 p-3 text-sm">
                        <span>
                          {statusLabel(log.fromStatus)} → {statusLabel(log.toStatus)}
                        </span>
                        <span className="text-content-tertiary">{formatDate(log.eventTime)}</span>
                      </div>
                    ))
                  ) : (
                    <div className="p-3 text-sm text-content-tertiary">暂无状态历史</div>
                  )}
                </div>
              </div>
            </div>
          ) : null}
        </DialogContent>
      </Dialog>
    </div>
  )
}
