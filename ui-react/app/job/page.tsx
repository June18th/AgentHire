"use client"

import { useEffect, useState } from "react"
import { useRouter, useSearchParams } from "next/navigation"
import {
  ArrowLeft,
  Briefcase,
  Building,
  Calendar,
  Clock,
  ExternalLink,
  FilePlus2,
  MapPin,
  Users,
} from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Separator } from "@/components/ui/separator"
import { ToastAction } from "@/components/ui/toast"
import { useLoginModal } from "@/hooks/useLoginModal"
import { useLoginUser } from "@/hooks/useLoginUser"
import { useToast } from "@/hooks/use-toast"
import { jobDetail } from "@/lib/api"
import { saveJobApplication } from "@/lib/job-application-api"

interface JobDetail {
  id: number
  draftId: number
  companyName: string
  companyType: string
  jobLocation: string
  recruitmentType: string
  recruitmentTarget: string
  position: string
  deliveryProgress: string
  lastUpdatedTime: string
  deadline: string
  relatedLink: string
  jobAnnouncement: string
  internalReferralCode: string
  remarks: string
  state: number
  createTime: number
  updateTime: number
}

function formatText(value?: string) {
  return value && value.trim() ? value : "-"
}

export default function JobDetailPage() {
  const searchParams = useSearchParams()
  const router = useRouter()
  const { toast } = useToast()
  const { userInfo } = useLoginUser()
  const { setLoginOpen } = useLoginModal()
  const [job, setJob] = useState<JobDetail | null>(null)
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const id = searchParams.get("id")
    if (!id) {
      setJob(null)
      setError("未指定职位 ID")
      return
    }

    setLoading(true)
    setError(null)
    jobDetail(Number(id))
      .then((data) => setJob(data))
      .catch((err) => {
        setJob(null)
        setError(err instanceof Error ? err.message : "获取职位信息失败")
      })
      .finally(() => setLoading(false))
  }, [searchParams])

  const handleAddApplication = async () => {
    if (!job) return
    if (!userInfo) {
      toast({ title: "请先登录", description: "登录后可以把岗位加入我的投递记录。" })
      setLoginOpen(true)
      return
    }

    setSaving(true)
    try {
      const saved = await saveJobApplication({
        jobId: job.id,
        companyName: job.companyName,
        position: job.position,
        applyUrl: job.relatedLink || job.jobAnnouncement,
        currentStatus: "INTERESTED",
        deadline: job.deadline,
        source: "job-detail",
        remark: job.internalReferralCode ? `内推码：${job.internalReferralCode}` : undefined,
      })
      toast({
        title: "已加入我的投递",
        description: `${job.companyName} / ${job.position}`,
        action: (
          <ToastAction altText="查看投递" onClick={() => router.push(`/applications?applicationId=${saved.id}`)}>
            查看
          </ToastAction>
        ),
      })
      router.push(`/applications?applicationId=${saved.id}`)
    } catch (err) {
      toast({
        title: "加入投递失败",
        description: err instanceof Error ? err.message : "请稍后重试",
        variant: "destructive",
      })
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <div className="text-lg text-gray-500">加载中...</div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <div className="text-center">
          <h2 className="mb-4 text-2xl font-bold text-red-600">{error}</h2>
          <Button onClick={() => router.back()}>返回列表</Button>
        </div>
      </div>
    )
  }

  if (!job) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-50">
        <div className="text-center">
          <h2 className="mb-4 text-2xl font-bold text-gray-900">职位不存在</h2>
          <Button onClick={() => router.back()}>返回列表</Button>
        </div>
      </div>
    )
  }

  const applyLink = job.relatedLink || job.jobAnnouncement

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="border-b bg-white">
        <div className="mx-auto max-w-5xl px-4 sm:px-6 lg:px-8">
          <div className="flex h-16 items-center">
            <Button variant="ghost" onClick={() => router.back()} className="mr-4">
              <ArrowLeft className="mr-2 h-4 w-4" />
              返回列表
            </Button>
            <h1 className="text-xl font-semibold text-gray-900">职位详情</h1>
          </div>
        </div>
      </header>

      <div className="mx-auto max-w-5xl px-4 py-8 sm:px-6 lg:px-8">
        <Card className="mb-6">
          <CardHeader>
            <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between">
              <div>
                <CardTitle className="mb-2 text-2xl">{job.companyName}</CardTitle>
                <CardDescription className="mb-4 text-lg text-gray-700">{job.position}</CardDescription>
                <div className="mb-4 flex flex-wrap gap-2">
                  <Badge variant={job.companyType === "民企" ? "default" : job.companyType === "央国企" ? "secondary" : "outline"}>
                    <Building className="mr-1 h-3 w-3" />
                    {formatText(job.companyType)}
                  </Badge>
                  <Badge variant="outline" className="border-pink-600 text-pink-600">
                    <Briefcase className="mr-1 h-3 w-3" />
                    {formatText(job.recruitmentType)}
                  </Badge>
                  <Badge variant="outline" className="border-blue-600 text-blue-600">
                    <Users className="mr-1 h-3 w-3" />
                    {formatText(job.recruitmentTarget)}
                  </Badge>
                </div>
                <div className="flex flex-wrap items-center gap-4 text-sm text-gray-600">
                  <div className="flex items-center">
                    <MapPin className="mr-1 h-4 w-4" />
                    {formatText(job.jobLocation)}
                  </div>
                  <div className="flex items-center">
                    <Calendar className="mr-1 h-4 w-4" />
                    更新：{formatText(job.lastUpdatedTime)}
                  </div>
                  <div className="flex items-center">
                    <Clock className="mr-1 h-4 w-4" />
                    截止：{formatText(job.deadline)}
                  </div>
                </div>
              </div>

              <div className="flex min-w-48 flex-col gap-2">
                <Button className="gap-2 bg-blue-600 hover:bg-blue-700" onClick={handleAddApplication} disabled={saving}>
                  <FilePlus2 className="h-4 w-4" />
                  {saving ? "加入中..." : "加入我的投递"}
                </Button>
                {applyLink ? (
                  <Button variant="outline" className="gap-2 bg-transparent" asChild>
                    <a href={applyLink} target="_blank" rel="noopener noreferrer">
                      <ExternalLink className="h-4 w-4" />
                      打开投递链接
                    </a>
                  </Button>
                ) : null}
              </div>
            </div>
          </CardHeader>
        </Card>

        <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
          <div className="space-y-6 lg:col-span-2">
            <Card>
              <CardHeader>
                <CardTitle>岗位备注</CardTitle>
              </CardHeader>
              <CardContent>
                <p className="whitespace-pre-wrap leading-relaxed text-gray-700">{formatText(job.remarks)}</p>
              </CardContent>
            </Card>

            {job.internalReferralCode ? (
              <Card>
                <CardHeader>
                  <CardTitle>内推信息</CardTitle>
                </CardHeader>
                <CardContent>
                  <p className="text-gray-700">{job.internalReferralCode}</p>
                </CardContent>
              </Card>
            ) : null}
          </div>

          <div className="space-y-6">
            <Card>
              <CardHeader>
                <CardTitle>公司信息</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <div>
                  <h4 className="mb-1 font-medium text-gray-900">公司名称</h4>
                  <p className="text-gray-600">{formatText(job.companyName)}</p>
                </div>
                <Separator />
                <div>
                  <h4 className="mb-1 font-medium text-gray-900">公司类型</h4>
                  <p className="text-gray-600">{formatText(job.companyType)}</p>
                </div>
                <Separator />
                <div>
                  <h4 className="mb-1 font-medium text-gray-900">工作地点</h4>
                  <p className="text-gray-600">{formatText(job.jobLocation)}</p>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>投递状态</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="py-2 text-center">
                  <div className="mb-4 inline-flex rounded-full bg-gray-100 px-3 py-1 text-sm text-gray-600">
                    {formatText(job.deliveryProgress)}
                  </div>
                  <p className="mb-4 text-sm text-gray-500">加入记录后，可在“我的投递”中推进笔试、面试、Offer 等状态。</p>
                  <Button className="w-full bg-blue-600 hover:bg-blue-700" onClick={handleAddApplication} disabled={saving}>
                    {saving ? "加入中..." : "加入我的投递"}
                  </Button>
                </div>
              </CardContent>
            </Card>

            {job.jobAnnouncement ? (
              <Card>
                <CardHeader>
                  <CardTitle>招聘公告</CardTitle>
                </CardHeader>
                <CardContent>
                  <Button variant="outline" className="w-full gap-2 bg-transparent" asChild>
                    <a href={job.jobAnnouncement} target="_blank" rel="noopener noreferrer">
                      <ExternalLink className="h-4 w-4" />
                      查看公告
                    </a>
                  </Button>
                </CardContent>
              </Card>
            ) : null}
          </div>
        </div>
      </div>
    </div>
  )
}
