"use client"

import { useEffect, useMemo, useState } from "react"
import Link from "next/link"
import { useSearchParams } from "next/navigation"
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
  fetchJobApplicationDetail,
  fetchJobApplicationEventsByDay,
  fetchJobApplications,
  reopenJobApplication,
  saveJobApplication,
  saveJobApplicationEvent,
  type JobApplicationEvent,
  type JobApplicationFollowUpScope,
  type JobApplicationItem,
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

function statusLabel(status?: string) {
  return STATUS_OPTIONS.find((item) => item.value === status)?.label || status || "-"
}

function eventTypeLabel(type?: string) {
  return EVENT_TYPE_OPTIONS.find((item) => item.value === type)?.label || type || "-"
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

function nextStepSuggestion(record: JobApplicationItem) {
  if (record.terminal) return "如仍需继续，可重新打开并回到准备投递状态。"
  if (isFollowUpOverdue(record)) return "跟进时间已到期，建议先完成跟进并记录结果。"
  if (record.nextFollowUpAt) return `下一步：${followUpHint(record.nextFollowUpAt)}。`
  if (record.currentStatus === "INTERESTED") return "建议确认岗位匹配度，补齐投递链接和截止时间。"
  if (record.currentStatus === "PREPARING") return "建议整理简历版本、内推信息，并设置下次跟进时间。"
  if (INTERVIEW_PROCESS_STATUS.includes(record.currentStatus)) return "建议记录笔面试安排、题目和复盘备注。"
  if (record.currentStatus === "OFFER") return "建议补充 Offer 沟通结果、薪资信息或最终选择备注。"
  return "建议保持状态更新，并记录下一步跟进计划。"
}

export default function ApplicationsPage() {
  const { toast } = useToast()
  const { userInfo } = useLoginUser()
  const searchParams = useSearchParams()
  const [records, setRecords] = useState<JobApplicationItem[]>([])
  const [summaryRecords, setSummaryRecords] = useState<JobApplicationItem[]>([])
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const [companyName, setCompanyName] = useState("")
  const [position, setPosition] = useState("")
  const [status, setStatus] = useState<"all" | JobApplicationStatus>("all")
  const [companyType, setCompanyType] = useState("all")
  const [followUpScope, setFollowUpScope] = useState<"all" | JobApplicationFollowUpScope>("all")
  const [attention, setAttention] = useState("all")
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
  const followUpSummary = useMemo(() => {
    const pendingRecords = summaryRecords.filter(isFollowUpPending)
    return {
      pending: pendingRecords.length,
      overdue: pendingRecords.filter((record) => isFollowUpOverdue(record)).length,
    }
  }, [summaryRecords])

  const activeCount = useMemo(() => summaryRecords.filter((record) => !record.terminal).length, [summaryRecords])
  const submittedAndLaterCount = useMemo(() => summaryRecords.filter((record) => ACTIVE_PROCESS_STATUS.includes(record.currentStatus)).length, [summaryRecords])
  const interviewCount = useMemo(() => summaryRecords.filter((record) => INTERVIEW_PROCESS_STATUS.includes(record.currentStatus)).length, [summaryRecords])
  const todayTodo = useMemo(() => {
    const today = formatDateOnly(Date.now())
    const toSubmit = summaryRecords.filter(
      (record) => !record.terminal && ["INTERESTED", "PREPARING"].includes(record.currentStatus) && formatDateOnly(record.deadline) === today
    )
    const toFollowUp = summaryRecords.filter((record) => !record.terminal && formatDateOnly(record.nextFollowUpAt) === today)
    return {
      toSubmit,
      toFollowUp,
      overdue: summaryRecords.filter(isFollowUpOverdue),
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

  useEffect(() => {
    if (!userInfo) return
    const applicationId = Number(searchParams.get("applicationId"))
    if (!applicationId || Number.isNaN(applicationId)) return

    openDetail(applicationId)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userInfo, searchParams])

  useEffect(() => {
    loadRecords()
    loadSummaryRecords()
    loadTodayEvents()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userInfo, page, status, companyType, followUpScope, attention])

  const handleSearch = () => {
    setPage(1)
    if (page === 1) {
      loadRecords()
      loadSummaryRecords()
    }
  }

  const handleRefresh = () => {
    loadRecords()
    loadSummaryRecords()
    loadTodayEvents()
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
      setTotal((value) => Math.max(0, value - 1))
      if (detail?.id === record.id) {
        setDetail(null)
      }
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
    const header = ["公司", "岗位", "状态", "公司类型", "关注度", "投递时间", "截止时间", "下次跟进", "投递链接", "备注", "更新时间"]
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
      resetEventForm()
      loadTodayEvents()
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
      toast({ title: "状态已更新", description: `${record.companyName} / ${statusLabel(targetStatus)}` })
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
    } catch (error) {
      toast({
        title: "重新打开失败",
        description: error instanceof Error ? error.message : "请稍后重试",
        variant: "destructive",
      })
    }
  }

  const handleCompleteFollowUp = async (record: JobApplicationItem) => {
    setCompletingFollowUpId(record.id)
    try {
      const updated = await completeJobApplicationFollowUp({
        id: record.id,
        note: "从我的投递列表完成跟进",
      })
      setRecords((current) => current.map((item) => (item.id === record.id ? updated : item)))
      setSummaryRecords((current) => current.map((item) => (item.id === record.id ? updated : item)))
      if (detail?.id === record.id) {
        setDetail(await fetchJobApplicationDetail(record.id))
      }
      toast({ title: "已记录本次跟进", description: `${record.companyName} / ${record.position}` })
      loadRecords()
      loadSummaryRecords()
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
          <div className={statCardClass}>
            <div className={statLabelClass}>已投递及后续</div>
            <div className={statValueClass}>{submittedAndLaterCount}</div>
            <div className="mt-1 text-xs text-content-tertiary">已进入正式流程</div>
          </div>
          <div className={statCardClass}>
            <div className={statLabelClass}>笔面试中</div>
            <div className={statValueClass}>{interviewCount}</div>
            <div className="mt-1 text-xs text-content-tertiary">笔试 / 面试 / HR</div>
          </div>
          <div className={`${statCardClass} ${followUpSummary.overdue > 0 ? "border-red-200 bg-red-50/60" : ""}`}>
            <div className={statLabelClass}>待跟进</div>
            <div className="mt-2 flex items-end gap-2">
              <span className={`text-2xl font-semibold ${followUpSummary.overdue > 0 ? "text-red-700" : "text-content-primary"}`}>{followUpSummary.pending}</span>
              {followUpSummary.overdue > 0 ? <span className="pb-1 text-xs font-medium text-red-600">{followUpSummary.overdue} 条已到期</span> : null}
            </div>
            <div className="mt-1 text-xs text-content-tertiary">需要主动处理</div>
          </div>
        </div>

        <div className="grid gap-3 lg:grid-cols-4">
          <div className="rounded-lg border border-surface-border bg-white p-4 shadow-sm">
            <div className="text-sm font-semibold text-content-primary">今日要投递</div>
            <div className="mt-2 text-2xl font-semibold text-blue-700">{todayTodo.toSubmit.length}</div>
            <MiniList items={todayTodo.toSubmit} emptyText="今天没有临近截止的准备项" />
          </div>
          <div className="rounded-lg border border-surface-border bg-white p-4 shadow-sm">
            <div className="text-sm font-semibold text-content-primary">今日要跟进</div>
            <div className="mt-2 text-2xl font-semibold text-emerald-700">{todayTodo.toFollowUp.length}</div>
            <MiniList items={todayTodo.toFollowUp} emptyText="今天没有跟进安排" />
          </div>
          <div className="rounded-lg border border-surface-border bg-white p-4 shadow-sm">
            <div className="text-sm font-semibold text-content-primary">今日笔面试</div>
            <div className="mt-2 text-2xl font-semibold text-purple-700">{todayEvents.length}</div>
            {todayEvents.length ? (
              <div className="mt-2 grid gap-1.5">
                {todayEvents.slice(0, 3).map((event) => (
                  <div key={event.id} className="truncate text-xs text-content-secondary" title={`${eventTypeLabel(event.eventType)} / ${event.eventTitle}`}>
                    {eventTypeLabel(event.eventType)} / {event.eventTitle}
                  </div>
                ))}
                {todayEvents.length > 3 ? <div className="text-xs text-content-tertiary">还有 {todayEvents.length - 3} 条</div> : null}
              </div>
            ) : (
              <div className="text-xs text-content-tertiary">今天没有笔试或面试安排</div>
            )}
          </div>
          <div className={`rounded-lg border p-4 shadow-sm ${todayTodo.overdue.length > 0 ? "border-red-200 bg-red-50/70" : "border-surface-border bg-white"}`}>
            <div className="text-sm font-semibold text-content-primary">已逾期</div>
            <div className={`mt-2 text-2xl font-semibold ${todayTodo.overdue.length > 0 ? "text-red-700" : "text-content-primary"}`}>{todayTodo.overdue.length}</div>
            <MiniList items={todayTodo.overdue} emptyText="没有逾期跟进" />
          </div>
        </div>

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
                    const followUpOverdue = isFollowUpOverdue(record)
                    const followUpPending = isFollowUpPending(record)
                    return (
                      <TableRow key={record.id} className={`border-b border-surface-border last:border-b-0 ${followUpOverdue ? "bg-red-50/70 hover:bg-red-50" : "hover:bg-gray-50"}`}>
                        <TableCell className={tableCellClass}>
                          <div className="font-medium text-content-primary">{record.companyName}</div>
                          <div className="mt-1 text-sm text-content-secondary">{record.position}</div>
                          {record.companyType ? <div className="mt-1 text-xs text-content-tertiary">类型：{companyTypeLabel(record.companyType)}</div> : null}
                          {record.submittedAt ? <div className="mt-1 text-xs text-content-tertiary">投递：{displayDate(record.submittedAt)}</div> : null}
                          {record.deadline ? <div className="mt-1 text-xs text-content-tertiary">截止：{record.deadline}</div> : null}
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
                </div>
                <div className="md:col-span-2">复盘备注：{detail.remark || "-"}</div>
              </div>

              <div>
                <h3 className="mb-2 text-sm font-semibold text-content-primary">新增事件</h3>
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
                          </span>
                          <span className="text-content-tertiary">{formatDate(event.eventTime)}</span>
                        </div>
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
