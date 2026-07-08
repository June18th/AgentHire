import { api } from "@/lib/api"

export const JOB_APPLICATIONS_CHANGED_EVENT = "jobclaw:job-applications-changed"

export type JobApplicationStatus =
  | "INTERESTED"
  | "PREPARING"
  | "SUBMITTED"
  | "WRITTEN_TEST"
  | "INTERVIEW_1"
  | "INTERVIEW_2"
  | "HR_INTERVIEW"
  | "OFFER"
  | "ACCEPTED"
  | "REJECTED"
  | "GAVE_UP"
  | "EXPIRED"
  | "CLOSED"

export type JobApplicationFollowUpScope = "PENDING" | "OVERDUE"
export type JobApplicationActionScope = "A" | "OVERDUE_FOLLOW_UP" | "DUE_TODAY" | "DUE_SOON" | "STALE_SUBMITTED" | "PROCESS_NEEDS_FOLLOW_UP"
export type JobApplicationDeadlineRisk = "NONE" | "UNKNOWN" | "EXPIRED" | "DUE_TODAY" | "DUE_SOON" | "THIS_WEEK" | "NORMAL" | string
export type JobApplicationActionPriority = "A" | "B" | "C" | "NONE" | string
export type JobApplicationEventUrgency = "UNKNOWN" | "PAST" | "TODAY" | "TOMORROW" | "THIS_WEEK" | "LATER" | string

export interface JobApplicationItem {
  id: number
  userId: number
  jobId?: number
  companyName: string
  position: string
  applyUrl: string
  companyType?: string
  currentStatus: JobApplicationStatus
  currentStatusDesc: string
  terminal: boolean
  source?: string
  priority?: number
  deadline?: string
  deadlineAt?: number
  daysUntilDeadline?: number
  deadlineRisk?: JobApplicationDeadlineRisk
  submittedAt?: number
  nextFollowUpAt?: number
  followUpOverdue?: boolean
  actionPriority?: JobApplicationActionPriority
  suggestedNextAction?: string
  actionReason?: string
  remark?: string
  state: number
  createTime: number
  updateTime: number
  nextKeyEvent?: JobApplicationEvent
  statusLogs?: JobApplicationStatusLog[]
  events?: JobApplicationEvent[]
}

export interface JobApplicationStatusLog {
  id: number
  applicationId: number
  fromStatus?: JobApplicationStatus
  toStatus: JobApplicationStatus
  operatorType: string
  operatorId: number
  reason?: string
  eventTime: number
}

export interface JobApplicationEvent {
  id: number
  applicationId: number
  companyName?: string
  position?: string
  currentStatus?: JobApplicationStatus
  currentStatusDesc?: string
  eventType: string
  eventTitle: string
  eventTime: number
  hoursUntilEvent?: number
  eventUrgency?: JobApplicationEventUrgency
  suggestedPreparation?: string
  eventResult?: string
  note?: string
  createTime: number
}

export interface JobApplicationListQuery {
  jobId?: number
  currentStatus?: JobApplicationStatus
  companyName?: string
  position?: string
  companyType?: string
  followUpScope?: JobApplicationFollowUpScope
  priority?: number
  page?: number
  size?: number
}

export interface JobApplicationListResponse {
  list: JobApplicationItem[]
  hasMore: boolean
  page: number
  size: number
  total: number
}

export interface JobApplicationBrief {
  total: number
  active: number
  actionCount: number
  priorityA: number
  priorityB: number
  priorityC: number
  overdueFollowUps: number
  dueToday: number
  dueSoon: number
  thisWeek: number
  submittedAndLater: number
  staleSubmitted: number
  processNeedsFollowUp: number
  interview: number
  offer: number
  todayEvents: number
  next7DayEvents: number
  summary: string
  upcomingEvents: JobApplicationEvent[]
  topActions: JobApplicationItem[]
}

export interface JobApplicationReview {
  weekStart: number
  weekEnd: number
  total: number
  createdThisWeek: number
  submittedAndLaterThisWeek: number
  interviewThisWeek: number
  offerThisWeek: number
  overdueFollowUps: number
  staleSubmitted: number
  processNeedsFollowUp: number
  summary: string
}

export interface JobApplicationSaveReq {
  id?: number
  jobId?: number
  companyName?: string
  position?: string
  applyUrl?: string
  companyType?: string
  currentStatus?: JobApplicationStatus
  source?: string
  priority?: number
  deadline?: string
  submittedAt?: number
  nextFollowUpAt?: number
  remark?: string
}

export interface JobApplicationEventSaveReq {
  applicationId: number
  eventType: string
  eventTitle: string
  eventTime?: number
  eventResult?: string
  note?: string
}

export interface JobApplicationFollowUpReq {
  id: number
  nextFollowUpAt?: number
  note?: string
}

function unwrap<T>(data: any, fallback: string): T {
  if (data && data.code === 0) {
    return data.data
  }
  throw new Error(data?.msg || fallback)
}

export function notifyJobApplicationsChanged() {
  if (typeof window === "undefined") return
  window.dispatchEvent(new CustomEvent(JOB_APPLICATIONS_CHANGED_EVENT))
}

export async function fetchJobApplications(params?: JobApplicationListQuery): Promise<JobApplicationListResponse> {
  const res = await api.get("/api/user/applications/list", { params })
  return unwrap<JobApplicationListResponse>(res.data, "获取投递记录失败")
}

export async function fetchJobApplicationDetail(id: number): Promise<JobApplicationItem> {
  const res = await api.get("/api/user/applications/detail", { params: { id } })
  return unwrap<JobApplicationItem>(res.data, "获取投递详情失败")
}

export async function fetchJobApplicationActionItems(limit = 20, scope?: JobApplicationActionScope): Promise<JobApplicationItem[]> {
  const res = await api.get("/api/user/applications/action-items", { params: { limit, scope } })
  return unwrap<JobApplicationItem[]>(res.data, "获取投递行动项失败")
}

export async function fetchJobApplicationBrief(limit = 5): Promise<JobApplicationBrief> {
  const res = await api.get("/api/user/applications/brief", { params: { limit } })
  return unwrap<JobApplicationBrief>(res.data, "获取求职行动简报失败")
}

export async function fetchJobApplicationReview(): Promise<JobApplicationReview> {
  const res = await api.get("/api/user/applications/review")
  return unwrap<JobApplicationReview>(res.data, "获取本周投递复盘失败")
}

export async function saveJobApplication(req: JobApplicationSaveReq): Promise<JobApplicationItem> {
  const res = await api.post("/api/user/applications/save", req)
  const saved = unwrap<JobApplicationItem>(res.data, "保存投递记录失败")
  notifyJobApplicationsChanged()
  return saved
}

export async function changeJobApplicationStatus(
  id: number,
  targetStatus: JobApplicationStatus,
  reason?: string
): Promise<JobApplicationItem> {
  const res = await api.post("/api/user/applications/status", { id, targetStatus, reason })
  const updated = unwrap<JobApplicationItem>(res.data, "更新投递状态失败")
  notifyJobApplicationsChanged()
  return updated
}

export async function reopenJobApplication(
  id: number,
  targetStatus: JobApplicationStatus = "PREPARING",
  reason?: string
): Promise<JobApplicationItem> {
  const res = await api.post("/api/user/applications/reopen", { id, targetStatus, reason })
  const reopened = unwrap<JobApplicationItem>(res.data, "重新打开投递失败")
  notifyJobApplicationsChanged()
  return reopened
}

export async function fetchApplicationsByJobIds(jobIds: Array<number | string>): Promise<JobApplicationItem[]> {
  if (jobIds.length === 0) return []
  const res = await api.get("/api/user/applications/by-jobs", { params: { jobIds: jobIds.join(",") } })
  return unwrap<JobApplicationItem[]>(res.data, "获取岗位投递状态失败")
}

export async function completeJobApplicationFollowUp(req: JobApplicationFollowUpReq): Promise<JobApplicationItem> {
  const res = await api.post("/api/user/applications/follow-up/complete", req)
  const updated = unwrap<JobApplicationItem>(res.data, "完成跟进失败")
  notifyJobApplicationsChanged()
  return updated
}

export async function deleteJobApplication(id: number): Promise<boolean> {
  const res = await api.post("/api/user/applications/delete", undefined, { params: { id } })
  const deleted = unwrap<boolean>(res.data, "删除投递记录失败")
  notifyJobApplicationsChanged()
  return deleted
}

export async function saveJobApplicationEvent(req: JobApplicationEventSaveReq): Promise<JobApplicationEvent> {
  const res = await api.post("/api/user/applications/events/save", req)
  const saved = unwrap<JobApplicationEvent>(res.data, "保存投递事件失败")
  notifyJobApplicationsChanged()
  return saved
}

export async function fetchJobApplicationEvents(applicationId: number): Promise<JobApplicationEvent[]> {
  const res = await api.get("/api/user/applications/events", { params: { applicationId } })
  return unwrap<JobApplicationEvent[]>(res.data, "获取投递事件失败")
}

export async function fetchJobApplicationEventsByDay(start: number, end: number): Promise<JobApplicationEvent[]> {
  const res = await api.get("/api/user/applications/events/day", { params: { start, end } })
  return unwrap<JobApplicationEvent[]>(res.data, "获取今日投递事件失败")
}
