"use client"
import React from "react"
import { usePathname } from "next/navigation"
import { SidebarProvider, Sidebar, SidebarMenu, SidebarMenuItem, SidebarMenuButton, SidebarContent } from "@/components/ui/sidebar"
import Link from "next/link"
import {
    Activity,
    BookOpenText,
    Bot,
    BriefcaseBusiness,
    FilePlus2,
    Files,
    ServerCog,
    TicketPercent,
    Users,
    type LucideIcon,
} from "lucide-react"

interface AdminMenuItem {
    label: string
    path: string
    icon: LucideIcon
}

const menu: AdminMenuItem[] = [
    { label: "求职派Agent", path: "/admin/progress", icon: Bot },
    { label: "录入数据", path: "/admin/entry", icon: FilePlus2 },
    { label: "草稿列表", path: "/admin/drafts", icon: Files },
    { label: "职位列表", path: "/admin/jobs", icon: BriefcaseBusiness },
    { label: "字典管理", path: "/admin/dict", icon: BookOpenText },
    { label: "用户管理", path: "/admin/users", icon: Users },
    { label: "LLM供应商", path: "/admin/llm-providers", icon: ServerCog },
    { label: "大模型监控", path: "/admin/llm-monitor", icon: Activity },
    { label: "券码管理", path: "/admin/coupon", icon: TicketPercent },
]

export default function AdminLayout({ children }: { children: React.ReactNode }) {
    const pathname = usePathname()
    return (
        <SidebarProvider
            className="h-[calc(100svh-4rem)] min-h-0 overflow-hidden"
            style={{ "--sidebar-width": "12rem" } as React.CSSProperties}
        >
            <div className="flex h-full min-h-0 w-full">
                <Sidebar className="!top-16 !bottom-0 !h-auto border-r border-surface-border bg-white text-content-primary">
                    <div className="w-full border-b border-surface-border" />
                    <SidebarContent className="px-3 py-4">
                        <SidebarMenu className="gap-1.5">
                            {menu.map((item) => {
                                const Icon = item.icon
                                return (
                                    <SidebarMenuItem key={item.path}>
                                        <SidebarMenuButton
                                            asChild
                                            isActive={pathname === item.path}
                                            className={`h-10 justify-start rounded-md px-3 text-sm transition-all ${pathname === item.path
                                                ? "!bg-blue-50 font-semibold !text-blue-600 shadow-sm"
                                                : "font-medium text-content-secondary hover:bg-surface-hover hover:text-content-primary"
                                                }`}
                                        >
                                            <Link href={item.path}>
                                                <Icon className="h-4 w-4" />
                                                <span>{item.label}</span>
                                            </Link>
                                        </SidebarMenuButton>
                                    </SidebarMenuItem>
                                )
                            })}
                        </SidebarMenu>
                    </SidebarContent>
                </Sidebar>
                <main className="min-h-0 min-w-0 flex-grow overflow-y-auto bg-surface-muted">{children}</main>
            </div>
        </SidebarProvider>
    )
}
