"use client"

import React from "react"
import Link from "next/link"
import { usePathname, useSearchParams } from "next/navigation"
import {
  Activity,
  BookOpenText,
  Bot,
  BriefcaseBusiness,
  DatabaseZap,
  FilePlus2,
  Files,
  ListChecks,
  ServerCog,
  ShieldCheck,
  TicketPercent,
  Users,
  type LucideIcon,
} from "lucide-react"
import {
  Sidebar,
  SidebarContent,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
} from "@/components/ui/sidebar"
import { AuthGuard } from "@/components/auth/AuthGuard"

interface AdminMenuItem {
  label: string
  path: string
  href?: string
  tab?: string
  icon: LucideIcon
}

interface AdminMenuGroup {
  title: string
  items: AdminMenuItem[]
}

const menuGroups: AdminMenuGroup[] = [
  {
    title: "采集工作台",
    items: [
      { label: "新建投料", path: "/admin/entry", tab: "entry", icon: FilePlus2 },
      { label: "采集任务", path: "/admin/entry", href: "/admin/entry?tab=tasks", tab: "tasks", icon: ListChecks },
      { label: "草稿审核", path: "/admin/drafts", icon: Files },
      { label: "职位库", path: "/admin/jobs", icon: BriefcaseBusiness },
    ],
  },
  {
    title: "Agent 作业台",
    items: [{ label: "作业链", path: "/admin/progress", icon: Bot }],
  },
  {
    title: "采集源管理",
    items: [{ label: "来源列表", path: "/admin/sources", icon: DatabaseZap }],
  },
  {
    title: "平台配置",
    items: [
      { label: "字典管理", path: "/admin/dict", icon: BookOpenText },
      { label: "用户管理", path: "/admin/users", icon: Users },
      { label: "权限管理", path: "/admin/permissions", icon: ShieldCheck },
      { label: "LLM 供应商", path: "/admin/llm-providers", icon: ServerCog },
      { label: "大模型监控", path: "/admin/llm-monitor", icon: Activity },
      { label: "券码管理", path: "/admin/coupon", icon: TicketPercent },
    ],
  },
]

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const currentEntryTab = searchParams.get("tab") || "entry"

  return (
    <AuthGuard
      requireAdmin
      title="请先登录管理员账号"
      description="登录管理员账号后可以访问岗位、用户、权限、模型配置和运营后台。"
    >
      <SidebarProvider
        className="h-[calc(100svh-4rem)] min-h-0 overflow-hidden"
        style={{ "--sidebar-width": "12rem" } as React.CSSProperties}
      >
        <div className="flex h-full min-h-0 w-full">
          <Sidebar className="!bottom-0 !top-16 !h-auto border-r border-surface-border bg-white text-content-primary">
            <div className="w-full border-b border-surface-border" />
            <SidebarContent className="px-3 py-4">
              {menuGroups.map((group) => (
                <div key={group.title} className="mb-5 last:mb-0">
                  <div className="mb-2 px-3 text-xs font-semibold text-content-tertiary">
                    {group.title}
                  </div>
                  <SidebarMenu className="gap-1.5">
                    {group.items.map((item) => {
                      const Icon = item.icon
                      const active = item.tab
                        ? pathname === item.path && currentEntryTab === item.tab
                        : pathname === item.path || pathname.startsWith(`${item.path}/`)

                      return (
                        <SidebarMenuItem key={`${group.title}-${item.label}`}>
                          <SidebarMenuButton
                            asChild
                            isActive={active}
                            className={`h-10 justify-start rounded-md px-3 text-sm transition-all ${
                              active
                                ? "!bg-blue-50 font-semibold !text-blue-600 shadow-sm"
                                : "font-medium text-content-secondary hover:bg-surface-hover hover:text-content-primary"
                            }`}
                          >
                            <Link href={item.href || item.path}>
                              <Icon className="h-4 w-4" />
                              <span>{item.label}</span>
                            </Link>
                          </SidebarMenuButton>
                        </SidebarMenuItem>
                      )
                    })}
                  </SidebarMenu>
                </div>
              ))}
            </SidebarContent>
          </Sidebar>
          <main className="min-h-0 min-w-0 flex-grow overflow-y-auto bg-surface-muted">{children}</main>
        </div>
      </SidebarProvider>
    </AuthGuard>
  )
}
