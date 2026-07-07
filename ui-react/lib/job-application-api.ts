import { api } from "@/lib/api"

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
  submittedAt?: number
  nextFollowUpAt?: number
  remark?: string
  state: number
  createTime: number
  updateTime: number
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
  eventType: string
  eventTitle: string
  eventTime: number
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

export async function fetchJobApplications(params?: JobApplicationListQuery): Promise<JobApplicationListResponse> {
  const res = await api.get("/api/user/applications/list", { params })
  return unwrap<JobApplicationListResponse>(res.data, "获取投递记录失败")
}

export async function fetchJobApplicationDetail(id: number): Promise<JobApplicationItem> {
  const res = await api.get("/api/user/applications/detail", { params: { id } })
  return unwrap<JobApplicationItem>(res.data, "获取投递详情失败")
}

export async function saveJobApplication(req: JobApplicationSaveReq): Promise<JobApplicationItem> {
  const res = await api.post("/api/user/applications/save", req)
  return unwrap<JobApplicationItem>(res.data, "保存投递记录失败")
}

export async function changeJobApplicationStatus(
  id: number,
  targetStatus: JobApplicationStatus,
  reason?: string
): Promise<JobApplicationItem> {
  const res = await api.post("/api/user/applications/status", { id, targetStatus, reason })
  return unwrap<JobApplicationItem>(res.data, "更新投递状态失败")
}

export async function reopenJobApplication(
  id: number,
  targetStatus: JobApplicationStatus = "PREPARING",
  reason?: string
): Promise<JobApplicationItem> {
  const res = await api.post("/api/user/applications/reopen", { id, targetStatus, reason })
  return unwrap<JobApplicationItem>(res.data, "重新打开投递失败")
}

export async function fetchApplicationsByJobIds(jobIds: Array<number | string>): Promise<JobApplicationItem[]> {
  if (jobIds.length === 0) return []
  const res = await api.get("/api/user/applications/by-jobs", { params: { jobIds: jobIds.join(",") } })
  return unwrap<JobApplicationItem[]>(res.data, "获取岗位投递状态失败")
}

export async function completeJobApplicationFollowUp(req: JobApplicationFollowUpReq): Promise<JobApplicationItem> {
  const res = await api.post("/api/user/applications/follow-up/complete", req)
  return unwrap<JobApplicationItem>(res.data, "完成跟进失败")
}

export async function deleteJobApplication(id: number): Promise<boolean> {
  const res = await api.post("/api/user/applications/delete", undefined, { params: { id } })
  return unwrap<boolean>(res.data, "删除投递记录失败")
}

export async function saveJobApplicationEvent(req: JobApplicationEventSaveReq): Promise<JobApplicationEvent> {
  const res = await api.post("/api/user/applications/events/save", req)
  return unwrap<JobApplicationEvent>(res.data, "保存投递事件失败")
}

export async function fetchJobApplicationEvents(applicationId: number): Promise<JobApplicationEvent[]> {
  const res = await api.get("/api/user/applications/events", { params: { applicationId } })
  return unwrap<JobApplicationEvent[]>(res.data, "获取投递事件失败")
}

export async function fetchJobApplicationEventsByDay(start: number, end: number): Promise<JobApplicationEvent[]> {
  const res = await api.get("/api/user/applications/events/day", { params: { start, end } })
  return unwrap<JobApplicationEvent[]>(res.data, "获取今日投递事件失败")
}
