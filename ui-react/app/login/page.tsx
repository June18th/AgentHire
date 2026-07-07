"use client"

import { Suspense } from "react"
import { BriefcaseBusiness, ShieldCheck, UserRoundCheck } from "lucide-react"
import { useSearchParams } from "next/navigation"
import { LoginPanel } from "@/components/auth/LoginPanel"
import { useLoginUser } from "@/hooks/useLoginUser"

function LoginPageContent() {
  const { userInfo } = useLoginUser()
  const searchParams = useSearchParams()
  const redirectTo = searchParams.get("redirect") || "/"

  return (
    <div className="min-h-screen bg-surface-muted px-6 py-10">
      <div className="mx-auto grid max-w-5xl gap-6 lg:grid-cols-[1.1fr_0.9fr]">
        <section className="rounded-lg border border-surface-border bg-white p-8 shadow-sm">
          <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-blue-50 text-blue-600">
            <BriefcaseBusiness className="h-5 w-5" />
          </div>
          <h1 className="mt-5 text-2xl font-semibold text-content-primary">登录求职派</h1>
          <p className="mt-3 text-sm leading-6 text-content-tertiary">
            登录后可以使用 AI 对话、岗位投递台账、个人画像、模型偏好和管理员后台等能力。
          </p>

          <div className="mt-8 grid gap-3">
            <div className="flex items-start gap-3 rounded-md border border-surface-border p-4">
              <UserRoundCheck className="mt-0.5 h-5 w-5 text-blue-600" />
              <div>
                <div className="text-sm font-medium text-content-primary">普通用户</div>
                <div className="mt-1 text-sm text-content-tertiary">记录秋招投递、维护个人信息、使用求职问答。</div>
              </div>
            </div>
            <div className="flex items-start gap-3 rounded-md border border-surface-border p-4">
              <ShieldCheck className="mt-0.5 h-5 w-5 text-blue-600" />
              <div>
                <div className="text-sm font-medium text-content-primary">管理员</div>
                <div className="mt-1 text-sm text-content-tertiary">管理岗位库、模型配置、用户权限和运营数据。</div>
              </div>
            </div>
          </div>
        </section>

        <section className="rounded-lg border border-surface-border bg-white p-8 shadow-sm">
          <h2 className="text-lg font-semibold text-content-primary">
            {userInfo ? "已登录" : "选择登录方式"}
          </h2>
          {userInfo ? (
            <div className="mt-4 rounded-md bg-blue-50 p-4 text-sm text-blue-700">
              当前账号：{userInfo.nickname || `用户${userInfo.userId}`}。可以从右上角用户菜单进入个人中心或退出登录。
            </div>
          ) : (
            <div className="mt-5">
              <LoginPanel redirectTo={redirectTo} />
            </div>
          )}
        </section>
      </div>
    </div>
  )
}

export default function LoginPage() {
  return (
    <Suspense fallback={<div className="min-h-screen bg-surface-muted" />}>
      <LoginPageContent />
    </Suspense>
  )
}
