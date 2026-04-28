"use client"
import React from "react"
import { usePathname } from "next/navigation"
import { SidebarProvider, Sidebar, SidebarMenu, SidebarMenuItem, SidebarMenuButton, SidebarContent } from "@/components/ui/sidebar"
import Link from "next/link"
import { Home } from "lucide-react"

const menu = [
    { label: "求职派Agent", path: "/admin/progress" },
    { label: "录入数据", path: "/admin/entry" },
    { label: "草稿列表", path: "/admin/drafts" },
    { label: "职位列表", path: "/admin/jobs" },
    { label: "字典管理", path: "/admin/dict" },
    { label: "用户管理", path: "/admin/users" },
    { label: "券码管理", path: "/admin/coupon" },
]

export default function AdminLayout({ children }: { children: React.ReactNode }) {
    const pathname = usePathname()
    return (
        <SidebarProvider>
            <div className="flex min-h-screen w-full">
                <Sidebar className="my-16 w-64 min-h-screen bg-sidebar text-sidebar-foreground border-r flex flex-col">
                    <div className="w-full border-b border-sidebar-border mb-2" />
                    <SidebarContent>
                        <SidebarMenu>
                            {menu.map((item) => (
                                <SidebarMenuItem key={item.path}>
                                    <SidebarMenuButton
                                        asChild
                                        isActive={pathname === item.path}
                                        className={`h-12 px-6 text-base rounded-none justify-start transition-all ${pathname === item.path
                                            ? "font-bold bg-sidebar-accent text-sidebar-accent-foreground border-l-4 border-blue-600"
                                            : "font-normal hover:bg-sidebar-accent hover:text-sidebar-accent-foreground"
                                            }`}
                                    >
                                        <Link href={item.path}>{item.label}</Link>
                                    </SidebarMenuButton>
                                </SidebarMenuItem>
                            ))}
                        </SidebarMenu>
                    </SidebarContent>
                </Sidebar>
                <main className="flex-grow bg-gray-50">{children}</main>
            </div>
        </SidebarProvider>
    )
}