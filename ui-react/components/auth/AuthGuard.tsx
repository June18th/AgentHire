"use client"

import type { ReactNode } from "react"
import { ShieldAlert, UserRoundCheck } from "lucide-react"
import { usePathname, useRouter } from "next/navigation"
import { Button } from "@/components/ui/button"
import { useLoginUser } from "@/hooks/useLoginUser"

export function AuthGuard({
  children,
  requireAdmin = false,
  title,
  description,
}: {
  children: ReactNode
  requireAdmin?: boolean
  title?: string
  description?: string
}) {
  const router = useRouter()
  const pathname = usePathname()
  const { userInfo } = useLoginUser()

  if (!userInfo) {
    const redirect = pathname || "/"
    return (
      <div className="min-h-[calc(100svh-4rem)] bg-surface-muted px-6 py-10">
        <div className="mx-auto max-w-xl rounded-lg border border-surface-border bg-white p-8 text-center shadow-sm">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-lg bg-blue-50 text-blue-600">
            <UserRoundCheck className="h-6 w-6" />
          </div>
          <h1 className="mt-5 text-xl font-semibold text-content-primary">{title || "请先登录"}</h1>
          <p className="mt-2 text-sm leading-6 text-content-tertiary">
            {description || "登录后可以继续访问当前页面，并使用求职派的个人化能力。"}
          </p>
          <Button className="mt-6" onClick={() => router.push(`/login?redirect=${encodeURIComponent(redirect)}`)}>
            前往登录
          </Button>
        </div>
      </div>
    )
  }

  if (requireAdmin && userInfo.role !== 3) {
    return (
      <div className="min-h-[calc(100svh-4rem)] bg-surface-muted px-6 py-10">
        <div className="mx-auto max-w-xl rounded-lg border border-surface-border bg-white p-8 text-center shadow-sm">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-lg bg-red-50 text-red-600">
            <ShieldAlert className="h-6 w-6" />
          </div>
          <h1 className="mt-5 text-xl font-semibold text-content-primary">无管理员权限</h1>
          <p className="mt-2 text-sm leading-6 text-content-tertiary">
            当前账号没有访问管理后台的权限。请切换管理员账号，或返回普通用户页面继续使用。
          </p>
          <div className="mt-6 flex justify-center gap-3">
            <Button variant="outline" onClick={() => router.push("/")}>
              返回首页
            </Button>
            <Button onClick={() => router.push("/login?redirect=/admin")}>切换账号</Button>
          </div>
        </div>
      </div>
    )
  }

  return <>{children}</>
}
