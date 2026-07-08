"use client";
import { useState, type MouseEvent } from "react";

import { useCallback, useEffect } from "react";
import { useLoginModal } from "@/hooks/useLoginModal";
import { CalendarDays, CheckCircle2, ExternalLink, FilePlus2, FileText, Lock, Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { ToastAction } from "@/components/ui/toast";
import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination";

import Link from "next/link";
import {
  fetchJobList,
  type JobListQuery,
  JobListResponse,
  GlobalConfigItemValue,
  getUserDetail,
} from "@/lib/api";
import { useRouter, useSearchParams } from "next/navigation";
import { useLoginUser } from "@/hooks/useLoginUser";
import { getConfigValue } from "@/lib/config";
import { useToast } from "@/hooks/use-toast";
import {
  fetchApplicationsByJobIds,
  fetchJobApplicationBrief,
  JOB_APPLICATIONS_CHANGED_EVENT,
  saveJobApplication,
  type JobApplicationBrief,
  type JobApplicationEvent,
  type JobApplicationItem,
  type JobApplicationStatus,
} from "@/lib/job-application-api";

interface JobOffer {
  id: string | number;
  companyName: string;
  companyType: string;
  companyIndustry: string;
  location: string;
  recruitmentType: string;
  recruitmentTarget: string;
  position: string;
  applicationProgress: string;
  updateTime: string;
  deadline: string;
  relatedLinks: string;
  recruitmentNotice: string;
  referralCode: string;
  notes: string;
  locked?: boolean;
  applicationId?: number;
  applicationStatus?: JobApplicationStatus;
  applicationStatusDesc?: string;
  applicationTerminal?: boolean;
}

const ALL_TAG = "-1";
const QUICK_STATUS_OPTIONS: Array<{ value: JobApplicationStatus; label: string }> = [
  { value: "INTERESTED", label: "感兴趣" },
  { value: "PREPARING", label: "准备投递" },
  { value: "SUBMITTED", label: "已投递" },
];

function jobStatusLabel(status?: string) {
  return QUICK_STATUS_OPTIONS.find((item) => item.value === status)?.label || status || "已加入";
}

function actionPriorityLabel(priority?: string) {
  if (priority === "A") return "A";
  if (priority === "B") return "B";
  if (priority === "C") return "C";
  return "-";
}

function actionPriorityClass(priority?: string) {
  if (priority === "A") return "border-red-200 bg-red-50 text-red-700";
  if (priority === "B") return "border-amber-200 bg-amber-50 text-amber-700";
  if (priority === "C") return "border-blue-200 bg-blue-50 text-blue-700";
  return "border-gray-200 bg-gray-50 text-gray-600";
}

function actionHint(item: JobApplicationItem) {
  const suggested = item.suggestedNextAction?.trim();
  if (suggested) return suggested;
  if (item.followUpOverdue) return "跟进已到期，建议先处理并记录结果。";
  if (["INTERESTED", "PREPARING"].includes(item.currentStatus)) return "建议确认岗位匹配度并准备投递材料。";
  return "建议更新投递状态并确认下一步。";
}

function briefEventTypeLabel(type?: string) {
  if (type === "WRITTEN_TEST") return "笔试";
  if (type === "INTERVIEW") return "面试";
  if (type === "HR") return "HR";
  if (type === "OFFER") return "Offer";
  return type || "日程";
}

function briefEventUrgencyLabel(urgency?: string) {
  if (urgency === "TODAY") return "今天";
  if (urgency === "TOMORROW") return "明天";
  if (urgency === "THIS_WEEK") return "本周";
  if (urgency === "PAST") return "待复盘";
  if (urgency === "LATER") return "后续";
  return "";
}

function briefEventUrgencyClass(urgency?: string) {
  if (urgency === "TODAY") return "border-red-200 bg-red-50 text-red-700";
  if (urgency === "TOMORROW") return "border-amber-200 bg-amber-50 text-amber-700";
  if (urgency === "THIS_WEEK") return "border-blue-200 bg-blue-50 text-blue-700";
  if (urgency === "PAST") return "border-slate-200 bg-slate-50 text-slate-600";
  return "border-emerald-200 bg-emerald-50 text-emerald-700";
}

function briefEventTitle(event: JobApplicationEvent) {
  return event.eventTitle?.trim() || briefEventTypeLabel(event.eventType);
}

function briefEventSubject(event: JobApplicationEvent) {
  if (event.companyName || event.position) {
    return [event.companyName, event.position].filter(Boolean).join(" / ");
  }
  return briefEventTitle(event);
}

function formatBriefEventTime(timestamp?: number) {
  if (!timestamp) return "待定";
  const date = new Date(timestamp);
  const today = new Date();
  const todayStart = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime();
  const eventStart = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
  const dayDiff = Math.round((eventStart - todayStart) / 86400000);
  const time = date.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit", hour12: false });
  if (dayDiff === 0) return `今天 ${time}`;
  if (dayDiff === 1) return `明天 ${time}`;
  return `${date.getMonth() + 1}/${date.getDate()} ${time}`;
}

export default function HomePage() {
  const { toast } = useToast();
  const [currentView, setCurrentView] = useState<"frontend" | "admin">(
    "frontend"
  );
  const [filteredOffers, setFilteredOffers] = useState<JobOffer[]>([]);
  const [searchFilters, setSearchFilters] = useState({
    companyName: "",
    companyType: "", // 公司类型搜索
    companyIndustry: "", // 行业搜索
    location: "",
    recruitmentType: "",
    recruitmentTypeExcept: "实习",
    recruitmentTarget: "",
    position: "",
  });
  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 10;
  const [total, setTotal] = useState(0);
  const [locked, setLocked] = useState(false);
  const [online, setOnline] = useState(1);
  const [queryParams, setQueryParams] = useState<JobListQuery>({});
  const [quickSavingId, setQuickSavingId] = useState<string | null>(null);
  const [applicationBrief, setApplicationBrief] = useState<JobApplicationBrief | null>(null);
  const router = useRouter();
  const searchParams = useSearchParams();
  const { setLoginOpen } = useLoginModal();
  const { userInfo, setUserInfo } = useLoginUser();

  const [env, setEnv] = useState<GlobalConfigItemValue[]>([]);
  const [companyTypes, setCompanyTypes] = useState<GlobalConfigItemValue[]>([]);
  const [recruitmentTypes, setRecruitmentTypes] = useState<
    GlobalConfigItemValue[]
  >([]);
  const [recruitmentTarget, setRecruitmentTarget] = useState<
    GlobalConfigItemValue[]
  >([]);

  useEffect(() => {
    getConfigValue("site", "env").then(setEnv);
    getConfigValue("oc", "CompanyTypeEnum").then(setCompanyTypes);
    getConfigValue("oc", "RecruitmentTypeEnum").then(setRecruitmentTypes);
    getConfigValue("oc", "RecruitmentTargetEnum").then(setRecruitmentTarget);
  }, []);

  useEffect(() => {
    const companyName = searchParams.get("companyName") || "";
    if (!companyName) {
      return;
    }

    setCurrentPage(1);
    setSearchFilters((prev) =>
      prev.companyName === companyName ? prev : { ...prev, companyName }
    );
  }, [searchParams]);

  // 请求岗位数据（带分页）
  const loadJobList = (params: JobListQuery = {}, page = currentPage) => {
    const nextParams: JobListQuery = {
      recruitmentTypeExcept: "实习",
      ...params,
    };

    // 查询实习相关的岗位
    fetchJobList({
      page,
      size: itemsPerPage,
      ...nextParams,
    })
      .then(async (data: JobListResponse) => {
        const mapped: JobOffer[] = data.list.map((item) => ({
          id: item.id,
          companyName: item.companyName || "",
          companyType: item.companyType || "",
          companyIndustry: item.companyIndustry || "",
          location: item.jobLocation || "",
          recruitmentType: item.recruitmentType || "",
          recruitmentTarget: item.recruitmentTarget || "",
          position: item.position || "",
          applicationProgress: item.deliveryProgress || "",
          updateTime: item.lastUpdatedTime
            ? item.lastUpdatedTime.split("T")[0]
            : "",
          deadline: item.deadline || "",
          relatedLinks: item.relatedLink || "",
          recruitmentNotice: item.jobAnnouncement || "",
          referralCode: item.internalReferralCode || "",
          notes: item.remarks || "",
        }));
        setLocked(data.locked);
        setTotal(data.total);
        setOnline(data.online ? data.online : 1);
        if (!userInfo || data.locked || mapped.length === 0) {
          setFilteredOffers(mapped);
          return;
        }
        try {
          const applications = await fetchApplicationsByJobIds(mapped.map((item) => item.id));
          const applicationByJobId = new Map(applications.map((item) => [String(item.jobId), item]));
          setFilteredOffers(
            mapped.map((offer) => {
              const application = applicationByJobId.get(String(offer.id));
              return application
                ? {
                    ...offer,
                    applicationId: application.id,
                    applicationStatus: application.currentStatus,
                    applicationStatusDesc: application.currentStatusDesc,
                    applicationTerminal: application.terminal,
                  }
                : offer;
            })
          );
        } catch {
          setFilteredOffers(mapped);
        }
      })
      .catch((err: unknown) => {
        console.error("获取岗位数据失败", err);
      });
  };

  useEffect(() => {
    handleSearch()
  }, [currentPage, searchFilters, userInfo]);

  const loadApplicationBrief = useCallback(() => {
    if (!userInfo) {
      setApplicationBrief(null);
      return;
    }
    fetchJobApplicationBrief(6)
      .then((brief) => {
        setApplicationBrief(brief);
      })
      .catch(() => {
        setApplicationBrief(null);
      });
  }, [userInfo]);

  useEffect(() => {
    loadApplicationBrief();
  }, [loadApplicationBrief]);

  useEffect(() => {
    if (typeof window === "undefined") return;
    const listener = () => loadApplicationBrief();
    window.addEventListener(JOB_APPLICATIONS_CHANGED_EVENT, listener);
    return () => {
      window.removeEventListener(JOB_APPLICATIONS_CHANGED_EVENT, listener);
    };
  }, [loadApplicationBrief]);

  const handleSearch = () => {
    const params = {
      companyName: searchFilters.companyName || undefined,
      companyType:
        (searchFilters.companyType == ALL_TAG
          ? undefined
          : searchFilters.companyType) || undefined,
      jobLocation: searchFilters.location || undefined,
      recruitmentType:
        (searchFilters.recruitmentType == ALL_TAG
          ? undefined
          : searchFilters.recruitmentType) || undefined,
      recruitmentTarget:
        (searchFilters.recruitmentTarget == ALL_TAG
          ? undefined
          : searchFilters.recruitmentTarget) || undefined,
      position: searchFilters.position || undefined,
      companyIndustry: searchFilters.companyIndustry || undefined,
    };
    setQueryParams(params);
    loadJobList(params, currentPage);
  };

  const handleReset = () => {
    setSearchFilters({
      companyName: "",
      companyType: "",
      companyIndustry: "",
      location: "",
      recruitmentType: "",
      recruitmentTypeExcept: "实习",
      recruitmentTarget: "",
      position: "",
    });
    setQueryParams({});
    setCurrentPage(1);
    if (searchParams.get("companyName")) {
      router.replace("/");
    }
  };

  const handleQuickAddApplication = async (
    event: MouseEvent<HTMLButtonElement>,
    offer: JobOffer,
    status: JobApplicationStatus
  ) => {
    event.preventDefault();
    event.stopPropagation();
    if (locked) {
      if (!userInfo) {
        setLoginOpen(true);
      } else {
        router.push("/user");
      }
      return;
    }
    if (!userInfo) {
      toast({ title: "请先登录", description: "登录后可以把岗位加入我的投递记录。" });
      setLoginOpen(true);
      return;
    }

    const savingKey = `${offer.id}-${status}`;
    setQuickSavingId(savingKey);
    try {
      const saved = await saveJobApplication({
        jobId: Number(offer.id),
        companyName: offer.companyName,
        position: offer.position,
        applyUrl: offer.relatedLinks || offer.recruitmentNotice,
        currentStatus: status,
        deadline: offer.deadline,
        source: "job-list",
        remark: offer.referralCode ? `内推码：${offer.referralCode}` : undefined,
      });
      setFilteredOffers((current) =>
        current.map((item) =>
          String(item.id) === String(offer.id)
            ? {
                ...item,
                applicationId: saved.id,
                applicationStatus: saved.currentStatus,
                applicationStatusDesc: saved.currentStatusDesc,
                applicationTerminal: saved.terminal,
              }
            : item
        )
      );
      toast({
        title: "已加入我的投递",
        description: `${offer.companyName} / ${jobStatusLabel(status)}`,
        action: (
          <ToastAction altText="查看投递" onClick={() => router.push(`/applications?applicationId=${saved.id}`)}>
            查看
          </ToastAction>
        ),
      });
    } catch (err) {
      toast({
        title: "加入投递失败",
        description: err instanceof Error ? err.message : "请稍后重试",
        variant: "destructive",
      });
    } finally {
      setQuickSavingId(null);
    }
  };

  const totalPages = Math.ceil(total / itemsPerPage);
  const paginatedOffers = filteredOffers; // 直接用接口返回的分页数据
  const actionItems = applicationBrief?.topActions || [];
  const upcomingEvents = applicationBrief?.upcomingEvents || [];
  const applicationActionCount = applicationBrief?.actionCount ?? actionItems.length;
  const briefMetrics = applicationBrief
    ? [
        { label: "A级优先", value: applicationBrief.priorityA, className: "text-red-600" },
        { label: "逾期跟进", value: applicationBrief.overdueFollowUps, className: "text-red-600" },
        { label: "今日截止", value: applicationBrief.dueToday, className: "text-amber-600" },
        { label: "临近截止", value: applicationBrief.dueSoon, className: "text-amber-600" },
        { label: "静默投递", value: applicationBrief.staleSubmitted, className: "text-blue-600" },
        { label: "今日日程", value: applicationBrief.todayEvents, className: "text-emerald-600" },
      ]
    : [];

  if (currentView === "admin" && userInfo?.role === 3) {
    // 使用 userInfo 判断
    router.push("/admin");
    return null;
  }

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Search Filters */}
      <div className="mx-auto px-4 sm:px-6 lg:px-8 py-6">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-blue-100 bg-blue-50 px-5 py-4">
          <div>
            <div className="text-base font-semibold text-blue-900">秋招投递台账</div>
            <div className="mt-1 text-sm text-blue-700">把感兴趣的岗位加入记录，持续跟踪笔试、面试、Offer 和跟进事件。</div>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" className="gap-2 bg-white" onClick={() => router.push("/calendar")}>
              <CalendarDays className="h-4 w-4" />
              日历
            </Button>
            <Button variant="outline" className="gap-2 bg-white" onClick={() => router.push("/materials")}>
              <FileText className="h-4 w-4" />
              材料
            </Button>
            <Button className="gap-2 bg-blue-600 hover:bg-blue-700" onClick={() => router.push("/applications")}>
              <FilePlus2 className="h-4 w-4" />
              我的投递
            </Button>
          </div>
        </div>

        {userInfo ? (
          <div className="mb-4 rounded-lg border border-surface-border bg-white p-4 shadow-sm">
            <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 className="text-base font-semibold text-content-primary">今日行动</h2>
                <p className="mt-1 text-xs text-content-tertiary">
                  {applicationBrief?.summary || "优先处理 A 级事项，再回到岗位库继续筛选。"}
                </p>
              </div>
              <Badge variant="outline" className="rounded-md">
                {applicationActionCount} 项
              </Badge>
            </div>
            {briefMetrics.length ? (
              <div className="mb-3 grid grid-cols-2 gap-2 md:grid-cols-3 xl:grid-cols-6">
                {briefMetrics.map((metric) => (
                  <div key={metric.label} className="rounded-md border border-surface-border bg-gray-50 px-3 py-2">
                    <div className={`text-lg font-semibold ${metric.className}`}>{metric.value}</div>
                    <div className="text-xs text-content-tertiary">{metric.label}</div>
                  </div>
                ))}
              </div>
            ) : null}
            {upcomingEvents.length ? (
              <div className="mb-3 rounded-md border border-emerald-100 bg-emerald-50/60 p-3">
                <div className="mb-2 flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2 text-sm font-medium text-emerald-900">
                    <CalendarDays className="h-4 w-4" />
                    未来 7 天关键日程
                  </div>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="h-7 px-2 text-xs text-emerald-700 hover:text-emerald-800"
                    onClick={() => router.push("/calendar")}
                  >
                    日历
                  </Button>
                </div>
                <div className="grid gap-2 md:grid-cols-2 lg:grid-cols-3">
                  {upcomingEvents.slice(0, 3).map((event) => (
                    <button
                      key={event.id || `${event.applicationId}-${event.eventTime}-${event.eventType}`}
                      type="button"
                      className="rounded-md border border-emerald-100 bg-white p-3 text-left transition-colors hover:border-emerald-200 hover:bg-emerald-50"
                      onClick={() =>
                        event.applicationId
                          ? router.push(`/applications?applicationId=${event.applicationId}`)
                          : router.push("/calendar")
                      }
                    >
                      <div className="flex items-center justify-between gap-2">
                        <Badge variant="outline" className="rounded-md border-emerald-200 bg-emerald-50 text-emerald-700">
                          {briefEventTypeLabel(event.eventType)}
                        </Badge>
                        {briefEventUrgencyLabel(event.eventUrgency) ? (
                          <Badge variant="outline" className={`rounded-md ${briefEventUrgencyClass(event.eventUrgency)}`}>
                            {briefEventUrgencyLabel(event.eventUrgency)}
                          </Badge>
                        ) : null}
                        <span className="shrink-0 text-xs text-content-tertiary">{formatBriefEventTime(event.eventTime)}</span>
                      </div>
                      <div className="mt-2 truncate text-sm font-medium text-content-primary">{briefEventSubject(event)}</div>
                      <div className="mt-1 truncate text-xs text-content-secondary">{briefEventTitle(event)}</div>
                      {event.suggestedPreparation ? (
                        <div className="mt-1 line-clamp-2 text-xs leading-5 text-content-tertiary">{event.suggestedPreparation}</div>
                      ) : null}
                    </button>
                  ))}
                </div>
              </div>
            ) : null}
            {actionItems.length ? (
              <div className="grid gap-2 lg:grid-cols-3">
                {actionItems.slice(0, 3).map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    className={`rounded-md border p-3 text-left transition-colors hover:bg-gray-50 ${
                      item.followUpOverdue ? "border-red-200 bg-red-50/60" : "border-surface-border bg-white"
                    }`}
                    onClick={() => router.push(`/applications?applicationId=${item.id}`)}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <Badge variant="outline" className={`rounded-md ${actionPriorityClass(item.actionPriority)}`}>
                        {actionPriorityLabel(item.actionPriority)}
                      </Badge>
                      <ExternalLink className="h-4 w-4 shrink-0 text-content-tertiary" />
                    </div>
                    <div className="mt-2 truncate text-sm font-medium text-content-primary">
                      {item.companyName} / {item.position}
                    </div>
                    <div className="mt-1 line-clamp-2 text-xs leading-5 text-content-secondary">{actionHint(item)}</div>
                  </button>
                ))}
              </div>
            ) : (
              <div className="rounded-md border border-dashed border-surface-border p-4 text-center text-sm text-content-tertiary">
                暂无需要优先处理的投递事项。
              </div>
            )}
          </div>
        ) : null}

        <div className="bg-white rounded-lg shadow p-6 mb-6">
          <div className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-6 gap-4 mb-4">
            <Input
              placeholder="公司名称"
              value={searchFilters.companyName}
              onChange={(e) =>
                setSearchFilters((prev) => ({
                  ...prev,
                  companyName: e.target.value,
                }))
              }
            />

            {companyTypes && (
              <Select
                value={searchFilters.companyType}
                onValueChange={(value) =>
                  setSearchFilters((prev) => ({ ...prev, companyType: value }))
                }
              >
                <SelectTrigger>
                  <SelectValue placeholder="公司类型" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL_TAG}>全部公司类型</SelectItem>
                  {companyTypes.map((option) => (
                    <SelectItem
                      key={option.intro as string}
                      value={option.intro as string}
                    >
                      {option.intro}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}

            {recruitmentTypes && (
              <Select
                value={searchFilters.recruitmentType || ""}
                onValueChange={(value) =>
                  setSearchFilters((prev) => ({
                    ...prev,
                    recruitmentType: value,
                  }))
                }
              >
                <SelectTrigger>
                  <SelectValue placeholder="招聘类型" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL_TAG}>全部招聘类型</SelectItem>
                  {recruitmentTypes.map((type) => {
                    if (!type.intro.includes("实习")) {
                      return (
                        <SelectItem
                          key={type.intro as string}
                          value={type.intro as string}
                        >
                          {type.intro}
                        </SelectItem>
                      );
                    }
                  })}
                </SelectContent>
              </Select>
            )}

            {recruitmentTarget && (
              <Select
                value={searchFilters.recruitmentTarget || ""}
                onValueChange={(value) =>
                  setSearchFilters((prev) => ({
                    ...prev,
                    recruitmentTarget: value,
                  }))
                }
              >
                <SelectTrigger>
                  <SelectValue placeholder="招聘对象" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL_TAG}>全部招聘对象</SelectItem>
                  {recruitmentTarget.map((type) => (
                    <SelectItem
                      key={type.intro as string}
                      value={type.intro as string}
                    >
                      {type.intro}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}

            <Input
              placeholder="所属行业"
              value={searchFilters.companyIndustry}
              onChange={(e) =>
                setSearchFilters((prev) => ({
                  ...prev,
                  companyIndustry: e.target.value,
                }))
              }
            />
            <Input
              placeholder="工作地点"
              value={searchFilters.location}
              onChange={(e) =>
                setSearchFilters((prev) => ({
                  ...prev,
                  location: e.target.value,
                }))
              }
            />
            <Input
              placeholder="岗位名称"
              value={searchFilters.position}
              onChange={(e) =>
                setSearchFilters((prev) => ({
                  ...prev,
                  position: e.target.value,
                }))
              }
            />
            <div className="flex space-x-4">
              <Button
                onClick={handleSearch}
                className="bg-blue-500 hover:bg-blue-600"
              >
                <Search className="h-4 w-4 mr-2" />
                查询
              </Button>
              <Button variant="outline" onClick={handleReset}>
                重置
              </Button>
            </div>
          </div>
        </div>

        {/* Job Listings Table */}
        <div className="bg-white rounded-lg shadow overflow-x-auto">
          <Table className="min-w-[1350px] table-fixed">
            <TableHeader>
              <TableRow>
                <TableHead className="w-32 break-words">公司名称</TableHead>
                <TableHead className="w-32 break-words">公司类型</TableHead>
                <TableHead className="w-32 break-words">所属行业</TableHead>
                <TableHead className="w-32 break-words">工作地点</TableHead>
                <TableHead className="w-24 break-words">招聘类型</TableHead>
                <TableHead className="w-28 break-words">招聘对象</TableHead>
                <TableHead className="w-56 break-words">
                  岗位(大都不限专业)
                </TableHead>
                {/* <TableHead className="w-20 break-words">投递进度</TableHead> */}
                <TableHead className="w-24 break-words">更新时间</TableHead>
                <TableHead className="w-24 break-words">投递截止</TableHead>
                <TableHead className="w-40 break-words">投递状态</TableHead>
                <TableHead className="w-32 break-words">相关链接</TableHead>
                <TableHead className="w-32 break-words">招聘公告</TableHead>
                <TableHead className="w-24 break-words">内推码</TableHead>
                <TableHead className="w-32 break-words">备注</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {paginatedOffers.map((offer) => (
                <TableRow
                  key={String(offer.id)}
                  className={`hover:bg-gray-50 cursor-pointer relative ${
                    locked ? "opacity-70" : ""
                  }`}
                  onClick={(e) => {
                    if (locked) {
                      e.preventDefault();
                      e.stopPropagation();
                      if (!userInfo) {
                        // 未登录时，需要打开登录弹窗
                        setLoginOpen(true);
                      } else {
                        router.push("/user");
                      }
                    }
                  }}
                >
                  <TableCell className="font-medium break-words w-32 relative">
                    <Link
                      href={locked ? "#" : `/job?id=${offer.id}`}
                      className="text-blue-600 hover:underline"
                    >
                      {offer.companyName}
                    </Link>
                  </TableCell>
                  <TableCell className="break-words w-32">
                    <Badge
                      className="rounded-md"
                      variant={
                        offer.companyType === "民企"
                          ? "default"
                          : offer.companyType === "央国企"
                          ? "secondary"
                          : "outline"
                      }
                    >
                      {offer.companyType}
                    </Badge>
                  </TableCell>
                  <TableCell className="break-words w-32">
                    {offer.companyIndustry}
                  </TableCell>
                  <TableCell className="break-words w-32">
                    {offer.location}
                  </TableCell>
                  <TableCell className="break-words w-24">
                    <Badge
                      variant="outline"
                      className="text-pink-600 border-pink-600 rounded-md"
                    >
                      {offer.recruitmentType}
                    </Badge>
                  </TableCell>
                  <TableCell className="break-words w-28">
                    <Badge
                      variant="outline"
                      className="text-blue-600 border-blue-600 rounded-md"
                    >
                      {offer.recruitmentTarget}
                    </Badge>
                  </TableCell>
                  <TableCell className="max-w-xs break-words w-56">
                    <div
                      className="whitespace-pre-line break-words"
                      title={offer.position}
                    >
                      {offer.position}
                    </div>
                  </TableCell>
                  {/* <TableCell className="break-words w-20">{offer.applicationProgress}</TableCell> */}
                  <TableCell className="break-words w-24">
                    {offer.updateTime}
                  </TableCell>
                  <TableCell className="break-words w-24">
                    {offer.deadline}
                  </TableCell>
                  <TableCell className="break-words w-40">
                    {offer.applicationStatus ? (
                      <div className="grid gap-2">
                        <Badge variant="secondary" className="w-fit rounded-md">
                          <CheckCircle2 className="mr-1 h-3 w-3" />
                          {offer.applicationStatusDesc || jobStatusLabel(offer.applicationStatus)}
                        </Badge>
                        <Button
                          size="sm"
                          variant="outline"
                          className="h-8 w-fit bg-transparent"
                          onClick={(event) => {
                            event.preventDefault();
                            event.stopPropagation();
                            router.push(`/applications?applicationId=${offer.applicationId}`);
                          }}
                        >
                          查看投递
                        </Button>
                      </div>
                    ) : (
                      <div className="grid gap-1.5">
                        <div className="text-xs text-gray-500">未加入</div>
                        <div className="flex flex-wrap gap-1">
                          {QUICK_STATUS_OPTIONS.map((option) => (
                            <Button
                              key={option.value}
                              size="sm"
                              variant="outline"
                              className="h-7 gap-1 px-2 text-xs bg-transparent"
                              disabled={quickSavingId === `${offer.id}-${option.value}`}
                              onClick={(event) => handleQuickAddApplication(event, offer, option.value)}
                            >
                              <FilePlus2 className="h-3 w-3" />
                              {option.label}
                            </Button>
                          ))}
                        </div>
                      </div>
                    )}
                  </TableCell>
                  <TableCell className="break-words w-32">
                    <a
                      href={offer.relatedLinks}
                      className="inline-flex items-center justify-center rounded-md text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:opacity-50 disabled:pointer-events-none bg-blue-500 hover:bg-blue-600 text-white h-8 px-3"
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      投递
                    </a>
                  </TableCell>
                  <TableCell className="break-words w-32">
                    <div className="flex space-x-2">
                      <Button
                        size="sm"
                        variant="outline"
                        className="text-green-600 border-green-600 hover:bg-green-50 bg-transparent"
                      >
                        公告
                      </Button>
                    </div>
                  </TableCell>
                  <TableCell className="break-words w-24">
                    {offer.referralCode}
                  </TableCell>
                  <TableCell className="break-words w-32">
                    {offer.notes}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
            {locked && (
              <div className="absolute inset-0 flex flex-col items-center justify-center bg-gray-200/70 pointer-events-none">
                <Lock className="h-8 w-8 text-red-600 mb-2" />
                <span className="text-sm font-medium text-gray-800">
                  加入会员，即刻解锁全部招聘信息
                </span>
              </div>
            )}
          </Table>
        </div>

        {/* Pagination */}
        <div className="mt-6 flex flex-col items-center">
          <div className="text-sm text-gray-700 mb-2">
            共 {total} 条记录 当前在线人数: {online}
          </div>
          <div>
            <Pagination>
              <PaginationContent>
                <PaginationItem>
                  <PaginationPrevious
                    href="#"
                    onClick={(e) => {
                      e.preventDefault();
                      if (currentPage > 1) setCurrentPage(currentPage - 1);
                    }}
                  />
                </PaginationItem>
                {Array.from({ length: Math.min(5, totalPages) }, (_, i) => (
                  <PaginationItem key={i + 1}>
                    <PaginationLink
                      href="#"
                      isActive={currentPage === i + 1}
                      onClick={(e) => {
                        e.preventDefault();
                        setCurrentPage(i + 1);
                      }}
                    >
                      {i + 1}
                    </PaginationLink>
                  </PaginationItem>
                ))}
                <PaginationItem>
                  <PaginationNext
                    href="#"
                    onClick={(e) => {
                      e.preventDefault();
                      if (currentPage < totalPages)
                        setCurrentPage(currentPage + 1);
                    }}
                  />
                </PaginationItem>
              </PaginationContent>
            </Pagination>
          </div>
        </div>
      </div>
    </div>
  );
}
