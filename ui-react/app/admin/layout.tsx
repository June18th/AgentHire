"use client"
import React from "react"
import { usePathname } from "next/navigation"
import { SidebarProvider, Sidebar, SidebarMenu, SidebarMenuItem, SidebarMenuButton, SidebarContent } from "@/components/ui/sidebar"
import Link from "next/link"

const menu = [
    { label: "求职派Agent", path: "/admin/progress" },
    { label: "录入数据", path: "/admin/entry" },
    { label: "草稿列表", path: "/admin/drafts" },
    { label: "职位列表", path: "/admin/jobs" },
    { label: "字典管理", path: "/admin/dict" },
    { label: "用户管理", path: "/admin/users" },
    { label: "LLM供应商", path: "/admin/llm-providers" },
    { label: "大模型监控", path: "/admin/llm-monitor" },
    { label: "券码管理", path: "/admin/coupon" },
]

export default function AdminLayout({ children }: { children: React.ReactNode }) {
    const pathname = usePathname()
    return (
        <SidebarProvider style={{ "--sidebar-width": "12rem" } as React.CSSProperties}>
            <div className="flex min-h-screen w-full">
                <Sidebar className="my-16 border-r border-surface-border bg-white text-content-primary">
                    <div className="w-full border-b border-surface-border" />
                    <SidebarContent className="px-3 py-4">
                        <SidebarMenu className="gap-1.5">
                            {menu.map((item) => (
                                <SidebarMenuItem key={item.path}>
                                    <SidebarMenuButton
                                        asChild
                                        isActive={pathname === item.path}
                                        className={`h-10 justify-start rounded-md px-3 text-sm transition-all ${pathname === item.path
                                            ? "!bg-blue-50 font-semibold !text-blue-600 shadow-sm"
                                            : "font-medium text-content-secondary hover:bg-surface-hover hover:text-content-primary"
                                            }`}
                                    >
                                        <Link href={item.path}>{item.label}</Link>
                                    </SidebarMenuButton>
                                </SidebarMenuItem>
                            ))}
                        </SidebarMenu>
                    </SidebarContent>
                </Sidebar>
                <main className="min-w-0 flex-grow bg-surface-muted">{children}</main>
            </div>
        </SidebarProvider>
    )
}
