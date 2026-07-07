"use client"

import Link from "next/link"
import { useEffect, useMemo, useState } from "react"
import {
  AlertTriangle,
  BriefcaseBusiness,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Clock,
  ExternalLink,
  RefreshCw,
} from "lucide-react"
import { AuthGuard } from "@/components/auth/AuthGuard"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { useLoginUser } from "@/hooks/useLoginUser"
import { useToast } from "@/hooks/use-toast"
import {
  fetchJobApplicationActionItems,
  fetchJobApplicationEventsByDay,
  fetchJobApplications,
  type JobApplicationEvent,
  type JobApplicationItem,
} from "@/lib/job-application-api"

const SUMMARY_SIZE = 1000
const WEEK_DAYS = ["一", "二", "三", "四", "五", "六", "日"]
const EVENT_TYPE_LABELS: Record<string, string> = {
  FOLLOW_UP: "跟进",
  WRITTEN_TEST: "笔试",
  INTERVIEW: "面试",
  HR: "HR 沟通",
  OFFER: "Offer",
  OTHER: "其他",
}

type CalendarMode = "month" | "week"
type CalendarItemType = "deadline" | "follow-up" | "event"

interface CalendarItem {
  id: string
  type: CalendarItemType
  dateKey: string
  title: string
  subtitle: string
  applicationId?: number
  companyName?: string
  position?: string
  time?: number
  overdue?: boolean
  eventUrgency?: string
  suggestedPreparation?: string
}

function startOfDay(date: Date) {
  const next = new Date(date)
  next.setHours(0, 0, 0, 0)
  return next
}

function addDays(date: Date, days: number) {
  const next = new Date(date)
  next.setDate(next.getDate() + days)
  return next
}

function addMonths(date: Date, months: number) {
  const next = new Date(date)
  next.setMonth(next.getMonth() + months)
  return next
}

function formatDateKey(value?: number | string | Date) {
  if (!value) return ""
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return ""
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, "0")
  const day = String(date.getDate()).padStart(2, "0")
  return `${year}-${month}-${day}`
}

function formatMonthTitle(date: Date) {
  return `${date.getFullYear()} 年 ${date.getMonth() + 1} 月`
}

function formatDateTitle(dateKey: string) {
  const date = new Date(`${dateKey}T00:00:00`)
  if (Number.isNaN(date.getTime())) return dateKey
  return date.toLocaleDateString("zh-CN", { month: "long", day: "numeric", weekday: "long" })
}

function formatTime(value?: number) {
  if (!value) return ""
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ""
  return date.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit", hour12: false })
}

function dateOnlyToTime(value: string, endOfDay = false) {
  const date = new Date(`${value}T${endOfDay ? "23:59:59" : "00:00:00"}`)
  return date.getTime()
}

function normalizeDeadlineDate(value?: string) {
  if (!value) return ""
  const trimmed = value.trim()
  if (!trimmed) return ""
  if (/^\d{4}-\d{2}-\d{2}/.test(trimmed)) return trimmed.slice(0, 10)
  return formatDateKey(trimmed)
}

function isTerminal(record: JobApplicationItem) {
  return record.terminal || ["ACCEPTED", "REJECTED", "GAVE_UP", "EXPIRED", "CLOSED"].includes(record.currentStatus)
}

function recordFollowUpOverdue(record: JobApplicationItem, fallbackDateKey: string, todayKey: string) {
  return record.followUpOverdue ?? fallbackDateKey < todayKey
}

function eventTypeLabel(value: string) {
  return EVENT_TYPE_LABELS[value] || value || "事件"
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
  if (record.followUpOverdue) return "跟进时间已到期，建议先完成跟进并记录结果。"
  if (record.nextFollowUpAt) return "按计划完成本次跟进，并记录沟通结果。"
  if (record.deadlineRisk === "DUE_TODAY") return "今天截止，建议优先确认材料并完成投递。"
  if (record.deadlineRisk === "DUE_SOON") return "岗位临近截止，建议尽快完成投递准备。"
  return "保持状态更新，并确认下一步跟进计划。"
}

function buildCalendarItems(records: JobApplicationItem[], events: JobApplicationEvent[], todayKey: string) {
  const items: CalendarItem[] = []

  records.forEach((record) => {
    if (!isTerminal(record)) {
      const deadlineKey = normalizeDeadlineDate(record.deadline)
      if (deadlineKey && ["INTERESTED", "PREPARING"].includes(record.currentStatus)) {
        items.push({
          id: `deadline-${record.id}`,
          type: "deadline",
          dateKey: deadlineKey,
          title: "投递截止",
          subtitle: `${record.companyName} / ${record.position}`,
          applicationId: record.id,
          companyName: record.companyName,
          position: record.position,
          overdue: record.deadlineRisk === "EXPIRED" || deadlineKey < todayKey,
        })
      }

      const followUpKey = formatDateKey(record.nextFollowUpAt)
      if (followUpKey) {
        items.push({
          id: `follow-up-${record.id}`,
          type: "follow-up",
          dateKey: followUpKey,
          title: "跟进",
          subtitle: `${record.companyName} / ${record.position}`,
          applicationId: record.id,
          companyName: record.companyName,
          position: record.position,
          time: record.nextFollowUpAt,
          overdue: recordFollowUpOverdue(record, followUpKey, todayKey),
        })
      }
    }
  })

  events.forEach((event) => {
    const dateKey = formatDateKey(event.eventTime)
    if (!dateKey) return
    items.push({
      id: `event-${event.id}`,
      type: "event",
      dateKey,
      title: eventTypeLabel(event.eventType),
      subtitle: event.companyName || event.position
        ? [event.companyName, event.position].filter(Boolean).join(" / ")
        : event.eventTitle,
      applicationId: event.applicationId,
      companyName: event.companyName,
      position: event.position,
      time: event.eventTime,
      eventUrgency: event.eventUrgency,
      suggestedPreparation: event.suggestedPreparation,
    })
  })

  return items.sort((a, b) => {
    if (a.dateKey !== b.dateKey) return a.dateKey.localeCompare(b.dateKey)
    return (a.time || 0) - (b.time || 0)
  })
}

function itemBadgeClass(type: CalendarItemType, overdue?: boolean) {
  if (overdue) return "border-red-200 bg-red-50 text-red-700"
  if (type === "deadline") return "border-amber-200 bg-amber-50 text-amber-700"
  if (type === "follow-up") return "border-emerald-200 bg-emerald-50 text-emerald-700"
  return "border-blue-200 bg-blue-50 text-blue-700"
}

function eventUrgencyLabel(urgency?: string) {
  if (urgency === "TODAY") return "今天"
  if (urgency === "TOMORROW") return "明天"
  if (urgency === "THIS_WEEK") return "本周"
  if (urgency === "PAST") return "待复盘"
  if (urgency === "LATER") return "后续"
  return ""
}

function getVisibleDays(anchor: Date, mode: CalendarMode) {
  if (mode === "week") {
    const day = anchor.getDay() || 7
    const monday = addDays(startOfDay(anchor), 1 - day)
    return Array.from({ length: 7 }, (_, index) => addDays(monday, index))
  }

  const first = new Date(anchor.getFullYear(), anchor.getMonth(), 1)
  const firstDay = first.getDay() || 7
  const gridStart = addDays(first, 1 - firstDay)
  return Array.from({ length: 42 }, (_, index) => addDays(gridStart, index))
}

export default function CalendarPage() {
  const { userInfo } = useLoginUser()
  const { toast } = useToast()
  const [mode, setMode] = useState<CalendarMode>("month")
  const [anchorDate, setAnchorDate] = useState(() => startOfDay(new Date()))
  const [records, setRecords] = useState<JobApplicationItem[]>([])
  const [events, setEvents] = useState<JobApplicationEvent[]>([])
  const [actionItems, setActionItems] = useState<JobApplicationItem[]>([])
  const [loading, setLoading] = useState(false)
  const [selectedDateKey, setSelectedDateKey] = useState(() => formatDateKey(new Date()))

  const todayKey = formatDateKey(new Date())
  const visibleDays = useMemo(() => getVisibleDays(anchorDate, mode), [anchorDate, mode])
  const visibleStartKey = formatDateKey(visibleDays[0])
  const visibleEndKey = formatDateKey(visibleDays[visibleDays.length - 1])
  const calendarItems = useMemo(() => buildCalendarItems(records, events, todayKey), [records, events, todayKey])
  const itemMap = useMemo(() => {
    const map = new Map<string, CalendarItem[]>()
    calendarItems.forEach((item) => {
      const current = map.get(item.dateKey) || []
      current.push(item)
      map.set(item.dateKey, current)
    })
    return map
  }, [calendarItems])
  const selectedItems = itemMap.get(selectedDateKey) || []
  const overdueItems = calendarItems.filter((item) => item.overdue)
  const upcomingItems = calendarItems.filter((item) => item.dateKey >= todayKey).slice(0, 6)

  const loadCalendar = async () => {
    if (!userInfo || visibleDays.length === 0) return
    setLoading(true)
    try {
      const [recordRes, eventRes, actionRes] = await Promise.all([
        fetchJobApplications({ page: 1, size: SUMMARY_SIZE }),
        fetchJobApplicationEventsByDay(dateOnlyToTime(visibleStartKey), dateOnlyToTime(visibleEndKey, true)),
        fetchJobApplicationActionItems(20),
      ])
      setRecords(recordRes.list || [])
      setEvents(eventRes || [])
      setActionItems(actionRes || [])
    } catch (error) {
      toast({
        title: "日历加载失败",
        description: error instanceof Error ? error.message : "请稍后重试",
        variant: "destructive",
      })
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadCalendar()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userInfo, visibleStartKey, visibleEndKey])

  const shiftCalendar = (direction: -1 | 1) => {
    setAnchorDate((current) => (mode === "month" ? addMonths(current, direction) : addDays(current, direction * 7)))
  }

  const jumpToday = () => {
    const today = startOfDay(new Date())
    setAnchorDate(today)
    setSelectedDateKey(formatDateKey(today))
  }

  if (!userInfo) {
    return (
      <AuthGuard title="请先登录" description="登录后可以查看投递截止、跟进提醒、笔试面试和 Offer 沟通安排。">
        <div />
      </AuthGuard>
    )
  }

  return (
    <main className="min-h-[calc(100vh-4rem)] bg-surface-muted">
      <div className="mx-auto grid max-w-[1440px] gap-4 px-6 py-6 xl:grid-cols-[minmax(0,1fr)_360px]">
        <section className="rounded-lg border border-surface-border bg-white shadow-sm">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-surface-border px-5 py-4">
            <div>
              <div className="flex items-center gap-2 text-sm font-medium text-blue-700">
                <CalendarDays className="h-4 w-4" />
                求职日历
              </div>
              <h1 className="mt-1 text-xl font-semibold text-content-primary">{formatMonthTitle(anchorDate)}</h1>
              <p className="mt-1 text-sm text-content-tertiary">按日期管理投递截止、跟进提醒、笔试面试和 Offer 沟通。</p>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              <div className="flex rounded-md border border-surface-border bg-white p-1">
                <Button size="sm" variant={mode === "month" ? "default" : "ghost"} className="h-8" onClick={() => setMode("month")}>
                  月
                </Button>
                <Button size="sm" variant={mode === "week" ? "default" : "ghost"} className="h-8" onClick={() => setMode("week")}>
                  周
                </Button>
              </div>
              <Button variant="outline" size="sm" onClick={() => shiftCalendar(-1)}>
                <ChevronLeft className="h-4 w-4" />
              </Button>
              <Button variant="outline" size="sm" onClick={jumpToday}>
                今天
              </Button>
              <Button variant="outline" size="sm" onClick={() => shiftCalendar(1)}>
                <ChevronRight className="h-4 w-4" />
              </Button>
              <Button variant="outline" size="sm" className="gap-1" onClick={loadCalendar} disabled={loading}>
                <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`} />
                刷新
              </Button>
            </div>
          </div>

          <div className="grid grid-cols-7 border-b border-surface-border bg-blue-50">
            {WEEK_DAYS.map((day) => (
              <div key={day} className="border-r border-blue-100 px-3 py-2 text-center text-sm font-semibold text-blue-700 last:border-r-0">
                周{day}
              </div>
            ))}
          </div>

          <div className="grid grid-cols-7">
            {visibleDays.map((day) => {
              const dateKey = formatDateKey(day)
              const dayItems = itemMap.get(dateKey) || []
              const inCurrentMonth = day.getMonth() === anchorDate.getMonth()
              const selected = dateKey === selectedDateKey
              const isToday = dateKey === todayKey

              return (
                <button
                  key={dateKey}
                  type="button"
                  onClick={() => setSelectedDateKey(dateKey)}
                  className={`min-h-[132px] border-r border-t border-surface-border p-3 text-left last:border-r-0 hover:bg-blue-50/60 ${
                    selected ? "bg-blue-50 ring-2 ring-inset ring-blue-500" : ""
                  } ${!inCurrentMonth && mode === "month" ? "bg-gray-50 text-content-tertiary" : "bg-white"}`}
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className={`flex h-7 w-7 items-center justify-center rounded-md text-sm font-medium ${isToday ? "bg-blue-600 text-white" : "text-content-primary"}`}>
                      {day.getDate()}
                    </span>
                    {dayItems.length ? <span className="text-xs text-content-tertiary">{dayItems.length} 项</span> : null}
                  </div>

                  <div className="mt-2 grid gap-1">
                    {dayItems.slice(0, 3).map((item) => (
                      <div key={item.id} className={`truncate rounded-md border px-2 py-1 text-xs ${itemBadgeClass(item.type, item.overdue)}`}>
                        {item.title} · {item.subtitle}
                      </div>
                    ))}
                    {dayItems.length > 3 ? <div className="text-xs text-content-tertiary">还有 {dayItems.length - 3} 项</div> : null}
                  </div>
                </button>
              )
            })}
          </div>
        </section>

        <aside className="grid gap-4 self-start">
          <section className="rounded-lg border border-surface-border bg-white p-4 shadow-sm">
            <div className="flex items-center justify-between gap-3">
              <div>
                <h2 className="text-base font-semibold text-content-primary">行动优先级</h2>
                <p className="mt-1 text-xs text-content-tertiary">从全部投递中筛出最该先处理的事项。</p>
              </div>
              <Badge variant="outline" className="rounded-md">
                {actionItems.length}
              </Badge>
            </div>
            <div className="mt-3 grid gap-2">
              {actionItems.slice(0, 5).map((item) => (
                <CalendarActionItem key={item.id} item={item} />
              ))}
              {!actionItems.length ? <div className="text-sm text-content-tertiary">暂无高优先级行动项</div> : null}
            </div>
            {actionItems.length > 5 ? (
              <Button asChild variant="outline" className="mt-3 w-full">
                <Link href="/applications">查看全部行动项</Link>
              </Button>
            ) : null}
          </section>

          <section className={`rounded-lg border p-4 shadow-sm ${overdueItems.length ? "border-red-200 bg-red-50/70" : "border-surface-border bg-white"}`}>
            <div className="flex items-center justify-between gap-3">
              <div>
                <h2 className="text-base font-semibold text-content-primary">逾期提醒</h2>
                <p className="mt-1 text-xs text-content-tertiary">截止或跟进日期已经过去的事项。</p>
              </div>
              <Badge variant="outline" className={`${overdueItems.length ? "border-red-200 bg-red-50 text-red-700" : ""}`}>
                {overdueItems.length}
              </Badge>
            </div>
            <div className="mt-3 grid gap-2">
              {overdueItems.slice(0, 5).map((item) => (
                <CalendarListItem key={item.id} item={item} />
              ))}
              {!overdueItems.length ? <div className="text-sm text-content-tertiary">暂无逾期事项</div> : null}
            </div>
          </section>

          <section className="rounded-lg border border-surface-border bg-white p-4 shadow-sm">
            <div className="flex items-center justify-between gap-3">
              <div>
                <h2 className="text-base font-semibold text-content-primary">{formatDateTitle(selectedDateKey)}</h2>
                <p className="mt-1 text-xs text-content-tertiary">当天全部安排。</p>
              </div>
              <Badge variant="secondary" className="rounded-md">
                {selectedItems.length} 项
              </Badge>
            </div>
            <div className="mt-3 grid gap-2">
              {selectedItems.map((item) => (
                <CalendarListItem key={item.id} item={item} />
              ))}
              {!selectedItems.length ? <div className="rounded-md border border-dashed p-4 text-center text-sm text-content-tertiary">这一天没有求职安排</div> : null}
            </div>
          </section>

          <section className="rounded-lg border border-surface-border bg-white p-4 shadow-sm">
            <h2 className="text-base font-semibold text-content-primary">接下来</h2>
            <p className="mt-1 text-xs text-content-tertiary">从今天起最近的求职事项。</p>
            <div className="mt-3 grid gap-2">
              {upcomingItems.map((item) => (
                <CalendarListItem key={item.id} item={item} showDate />
              ))}
              {!upcomingItems.length ? <div className="text-sm text-content-tertiary">暂无后续安排</div> : null}
            </div>
          </section>
        </aside>
      </div>
    </main>
  )
}

function CalendarListItem({ item, showDate = false }: { item: CalendarItem; showDate?: boolean }) {
  const icon =
    item.type === "deadline" ? (
      <AlertTriangle className="h-4 w-4" />
    ) : item.type === "follow-up" ? (
      <Clock className="h-4 w-4" />
    ) : (
      <BriefcaseBusiness className="h-4 w-4" />
    )

  return (
    <div className={`rounded-md border p-3 ${itemBadgeClass(item.type, item.overdue)}`}>
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 gap-2">
          <div className="mt-0.5 shrink-0">{icon}</div>
          <div className="min-w-0">
            <div className="font-medium">
              {item.title}
              {item.type === "event" && eventUrgencyLabel(item.eventUrgency) ? (
                <span className="ml-2 text-xs font-normal opacity-80">{eventUrgencyLabel(item.eventUrgency)}</span>
              ) : null}
              {showDate ? <span className="ml-2 text-xs font-normal opacity-80">{item.dateKey}</span> : null}
            </div>
            <div className="mt-1 truncate text-sm opacity-90" title={item.subtitle}>
              {item.subtitle}
            </div>
            {item.time ? <div className="mt-1 text-xs opacity-80">{formatTime(item.time)}</div> : null}
            {item.suggestedPreparation ? (
              <div className="mt-1 line-clamp-2 text-xs leading-5 opacity-80">{item.suggestedPreparation}</div>
            ) : null}
          </div>
        </div>
        {item.applicationId ? (
          <Button asChild variant="ghost" size="icon" className="h-8 w-8 shrink-0">
            <Link href={`/applications?applicationId=${item.applicationId}`} title="查看投递">
              <ExternalLink className="h-4 w-4" />
            </Link>
          </Button>
        ) : null}
      </div>
    </div>
  )
}

function CalendarActionItem({ item }: { item: JobApplicationItem }) {
  const riskText = deadlineRiskLabel(item.deadlineRisk)
  const showPriority = hasActionPriority(item)

  return (
    <div className={`rounded-md border p-3 ${item.followUpOverdue ? "border-red-200 bg-red-50/60" : "border-surface-border bg-white"}`}>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            {showPriority ? (
              <Badge variant="outline" className={`rounded-md ${actionPriorityClass(item.actionPriority)}`}>
                {actionPriorityLabel(item.actionPriority)}
              </Badge>
            ) : null}
            <span className="truncate text-sm font-medium text-content-primary">
              {item.companyName} / {item.position}
            </span>
          </div>
          <div className="mt-2 text-sm leading-5 text-content-secondary">{nextStepSuggestion(item)}</div>
          <div className="mt-1 flex flex-wrap gap-2 text-xs text-content-tertiary">
            {item.actionReason ? <span>{item.actionReason}</span> : null}
            {riskText ? <span>{riskText}</span> : null}
            {item.nextFollowUpAt ? <span>跟进：{formatDateKey(item.nextFollowUpAt)}</span> : null}
          </div>
        </div>
        <Button asChild variant="ghost" size="icon" className="h-8 w-8 shrink-0">
          <Link href={`/applications?applicationId=${item.id}`} title="查看投递">
            <ExternalLink className="h-4 w-4" />
          </Link>
        </Button>
      </div>
    </div>
  )
}
