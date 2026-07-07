"use client"

import { ChevronDown, User } from "lucide-react"
import { usePathname, useRouter } from "next/navigation"
import { LoginPanel } from "@/components/auth/LoginPanel"
import { PiLogo } from "@/components/brand/PiLogo"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { useLoginModal } from "@/hooks/useLoginModal"
import { useLoginUser } from "@/hooks/useLoginUser"

const NAV_ITEMS = [
  { href: "/", label: "校招" },
  { href: "/internship", label: "实习" },
  { href: "/companies", label: "公司库" },
  { href: "/internal", label: "内推" },
  { href: "/chat", label: "AI 助手" },
  { href: "/applications", label: "我的求职" },
  { href: "/materials", label: "简历材料" },
  { href: "/calendar", label: "日历" },
]

export default function AppLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter()
  const pathname = usePathname()
  const { loginOpen, setLoginOpen } = useLoginModal()
  const { userInfo, logout } = useLoginUser()
  const hideFooter = pathname?.startsWith("/admin")
  const isActiveNav = (href: string) =>
    href === "/" ? pathname === "/" : pathname?.startsWith(href)

  return (
    <div className="flex min-h-screen flex-col bg-gray-50">
      <header className="fixed left-0 right-0 top-0 z-10 border-b bg-white">
        <div className="px-10">
          <div className="flex h-16 items-center justify-between">
            <div className="flex items-center space-x-8">
              <a href="/" className="transition-opacity hover:opacity-80">
                <PiLogo />
              </a>

              {pathname?.startsWith("/admin") ? (
                <nav className="flex space-x-6">
                  <span className="font-medium text-gray-700">管理后台</span>
                </nav>
              ) : (
                <nav className="flex space-x-6">
                  {NAV_ITEMS.map((item) => (
                    <a
                      key={item.href}
                      href={item.href}
                      className={`${isActiveNav(item.href) ? "font-medium text-blue-600" : "text-gray-700"} hover:text-blue-600`}
                    >
                      {item.label}
                    </a>
                  ))}
                </nav>
              )}
            </div>

            <div className="flex items-center space-x-4">
              {userInfo ? (
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <button className="flex items-center rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 focus-visible:ring-offset-2">
                      {userInfo.avatar ? (
                        <img
                          src={userInfo.avatar}
                          alt=""
                          className="h-8 w-8 rounded-full object-cover"
                          title={userInfo.nickname || `用户${userInfo.userId}`}
                        />
                      ) : (
                        <span className="flex h-8 w-8 items-center justify-center rounded-full bg-blue-50 text-sm font-medium text-blue-600">
                          {(userInfo.nickname || String(userInfo.userId)).slice(0, 1)}
                        </span>
                      )}
                      <ChevronDown className="ml-1 h-4 w-4 text-gray-500" />
                    </button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <div className="px-3 py-2">
                      <div className="font-medium">{userInfo.nickname || `用户${userInfo.userId}`}</div>
                      <div className="text-xs text-gray-500">
                        {userInfo.role === 3 ? "管理员" : userInfo.role === 2 ? "VIP 用户" : "普通用户"}
                      </div>
                    </div>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem onClick={() => router.push("/user")}>个人信息</DropdownMenuItem>
                    <DropdownMenuItem onClick={() => router.push("/applications")}>我的求职</DropdownMenuItem>
                    {userInfo.role === 3 ? (
                      <DropdownMenuItem onClick={() => router.push("/admin")}>管理后台</DropdownMenuItem>
                    ) : null}
                    <DropdownMenuSeparator />
                    <DropdownMenuItem onClick={logout}>退出</DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              ) : (
                <Dialog open={loginOpen} onOpenChange={setLoginOpen}>
                    <DialogTrigger asChild>
                      <Button variant="outline" size="sm">
                        <User className="mr-1 h-4 w-4" />
                        登录
                      </Button>
                    </DialogTrigger>
                    <DialogContent>
                      <DialogHeader>
                        <DialogTitle>登录求职派</DialogTitle>
                      </DialogHeader>

                      <LoginPanel redirectTo={pathname || "/"} />
                    </DialogContent>
                  </Dialog>
              )}
            </div>
          </div>
        </div>
      </header>

      <main className="flex-grow pt-16">{children}</main>

      {!hideFooter ? (
        <footer className="mt-auto border-t bg-white py-6">
          <div className="px-10 text-center text-sm text-gray-500">
            © {new Date().getFullYear()} 求职派 - 专注于校园招聘信息
          </div>
        </footer>
      ) : null}
    </div>
  )
}

