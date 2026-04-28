"use client";

import { useState, useCallback, useEffect } from "react";
import { useLoginModal } from "@/hooks/useLoginModal";
import { Bell, User, ChevronDown } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Home } from "lucide-react"
import { PiLogo } from "@/components/brand/PiLogo";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
} from "@/components/ui/dropdown-menu";
import { useRouter, usePathname } from "next/navigation";
import { useLoginUser } from "@/hooks/useLoginUser";
import { useToast } from "@/hooks/use-toast";
import { getConfigValue } from "@/lib/config";
import { getWxSseUrl, postWxCallback, GlobalConfigItemValue, getUserDetail } from "@/lib/api";
import { useSSE } from "@/hooks/useSSE";

export default function AppLayout({ children }: { children: React.ReactNode }) {
  const { toast } = useToast();
  const router = useRouter();
  const pathname = usePathname();
  const { loginOpen, setLoginOpen } = useLoginModal();
  const [qr, setQr] = useState("");
  const [code, setCode] = useState("");
  const [session, setSession] = useState("");
  const [mounted, setMounted] = useState(false);
  const [loginLoading, setLoginLoading] = useState(false);
  const [adminLoading, setAdminLoading] = useState(false);
  const { userInfo, setUserInfo, logout } = useLoginUser();
  const [sseUrl, setSseUrl] = useState("");
  const [env, setEnv] = useState<GlobalConfigItemValue[]>([]);

  useEffect(() => {
    setMounted(true);
    getConfigValue("site", "env").then(setEnv);
  }, []);

  useEffect(() => {
    if (loginOpen) {
      setSseUrl(getWxSseUrl());
    } else {
      setSseUrl("");
    }
  }, [loginOpen]);

  const handleSSE = useCallback(
    (type: string, payload: string) => {
      if (type === "qr") setQr(payload);
      if (type === "init") setCode(payload);
      if (type === "login") {
        setSession(payload);
        setLoginOpen(false);
        if (payload) {
          document.cookie = payload;
          const token = payload.substring(
            payload.indexOf("=") + 1,
            payload.indexOf(";")
          );
          // 解析 jwt 并设置全局用户信息
          const jwt = (() => {
            try {
              const base64Url = token.split(".")[1];
              const base64 = base64Url.replace(/-/g, "+").replace(/_/g, "/");
              const jsonPayload = decodeURIComponent(
                atob(base64)
                  .split("")
                  .map(function (c) {
                    return (
                      "%" + ("00" + c.charCodeAt(0).toString(16)).slice(-2)
                    );
                  })
                  .join("")
              );
              return JSON.parse(jsonPayload);
            } catch {
              return null;
            }
          })();
          if (jwt) {
            const info = {
              userId: jwt.uid,
              role: jwt.r,
              nickname: jwt.un,
              avatar: jwt.av,
              timestamp: Date.now(),
            };
            setUserInfo(info);
            if (typeof window !== "undefined") {
              localStorage.setItem("oc-user", JSON.stringify(info));
              localStorage.setItem("oc-token", token);
            }

            // 自动刷新当前页面
            window.location.reload();
          }
        }
      }
    },
    [setUserInfo]
  );

  useSSE(sseUrl, handleSSE);

  const handleWxLogin = async (type: "user" | "admin", code: String) => {
    const content = type === "user" ? "login" : "admin";
    const xml = `<xml><URL><![CDATA[https://hhui.top]]></URL><ToUserName><![CDATA[一灰灰blog]]></ToUserName><FromUserName><![CDATA[demoUser-${content}]]></FromUserName><CreateTime>${Math.floor(
      Date.now() / 1000
    )}</CreateTime><MsgType><![CDATA[text]]></MsgType><Content><![CDATA[${code}]]></Content><MsgId>${Date.now()}</MsgId></xml>`;
    try {
      if (type === "user") setLoginLoading(true);
      else setAdminLoading(true);
      await postWxCallback(xml);
    } catch (err) {
      toast({
        title: "登录提醒",
        description: "登录失败，请刷新页面再重新登录吧",
        variant: "destructive",
      });
    } finally {
      setLoginLoading(false);
      setAdminLoading(false);
    }
  };

  // Periodically check user info validity (5 minutes)
  useEffect(() => {
    const checkUserInfoValidity = async () => {
      if (userInfo && userInfo.timestamp) {
        const now = Date.now();
        const fiveMinutes = 5 * 60 * 1000; // 5分钟有效期
        if (now - userInfo.timestamp > fiveMinutes) {
          try {
            const updatedUser = await getUserDetail();
            if (updatedUser) {
              const info = {
                userId: updatedUser.userId,
                role: updatedUser.role,
                nickname: updatedUser.nickname,
                avatar: updatedUser.avatar,
                timestamp: now,
              };
              setUserInfo(info);
              if (typeof window !== "undefined") {
                localStorage.setItem("oc-user", JSON.stringify(info));
              }
            }
          } catch (error) {
            console.error("Failed to update user info:", error);
          }
        }
      }
    };

    // Check immediately on mount
    checkUserInfoValidity();

    // Then check every 30 seconds
    const interval = setInterval(checkUserInfoValidity, 30000);

    return () => clearInterval(interval);
  }, [userInfo, setUserInfo]);

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col">
      {/* 顶部导航栏 */}
      <header className="bg-white border-b fixed top-0 left-0 right-0 z-10">
        <div className="px-10">
          <div className="flex justify-between items-center h-16">
            <div className="flex items-center space-x-8">
              <div className="flex items-center">
                {pathname?.startsWith("/admin") ? (
                  <div className="h-16 flex items-center justify-center font-bold text-xl tracking-wide mb-0 select-none flex-row gap-2 w-full">
                    <button
                      className="p-2 rounded-full hover:bg-gray-200 transition-colors"
                      title="返回主页"
                      onClick={() => (window.location.href = "/")}
                    >
                      <Home className="w-7 h-7 text-blue-600" />
                    </button>
                    <PiLogo />
                  </div>
                ) : (
                  <a href="/" className="transition-opacity hover:opacity-80">
                    <PiLogo />
                  </a>
                )}
              </div>
              {pathname?.startsWith("/admin") ? (
                <nav className="flex space-x-6">
                  <a href="#" className="text-gray-700 hover:text-blue-600">
                    管理后台
                  </a>
                </nav>
              ) : (
                <nav className="flex space-x-6">
                  <a
                    href="/"
                    className={`${
                      pathname === "/"
                        ? "text-blue-600 font-medium"
                        : "text-gray-700"
                    } hover:text-blue-600`}
                  >
                    校招
                  </a>
                  <a
                    href="/internship"
                    className={`${
                      pathname === "/internship"
                        ? "text-blue-600 font-medium"
                        : "text-gray-700"
                    } hover:text-blue-600`}
                  >
                    实习
                  </a>
                  <a
                    href="/internal"
                    className={`${
                      pathname === "/internal"
                        ? "text-blue-600 font-medium"
                        : "text-gray-700"
                    } hover:text-blue-600`}
                  >
                    内推广场
                  </a>
                </nav>
              )}
            </div>
            <div className="flex items-center space-x-4">
              <Bell className="h-5 w-5 text-gray-500" />
              {userInfo ? (
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <span className="flex items-center cursor-pointer">
                      <img
                        src={userInfo.avatar}
                        alt="avatar"
                        className="w-8 h-8 rounded-full cursor-pointer"
                        title={userInfo.nickname || `用户${userInfo.userId}`}
                      />
                      <ChevronDown className="w-4 h-4 ml-1 text-gray-500" />
                    </span>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <div className="px-3 py-2">
                      <div className="font-medium">
                        {userInfo.nickname || `用户${userInfo.userId}`}
                      </div>
                      <div className="text-xs text-gray-500">
                        {userInfo.role === 1
                          ? "普通用户"
                          : userInfo.role === 2
                          ? "VIP用户"
                          : userInfo.role === 3
                          ? "管理员"
                          : "未知"}
                      </div>
                    </div>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem
                      onClick={() => {
                        router.push("/user");
                      }}
                    >
                      个人信息
                    </DropdownMenuItem>
                    {userInfo.role === 3 && (
                      <DropdownMenuItem onClick={() => router.push("/admin")}>
                        管理后台
                      </DropdownMenuItem>
                    )}
                    <DropdownMenuSeparator />
                    <DropdownMenuItem onClick={logout}>退出</DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              ) : (
                mounted && (
                  <Dialog open={loginOpen} onOpenChange={setLoginOpen}>
                    <DialogTrigger asChild>
                      <Button variant="outline" size="sm">
                        <User className="h-4 w-4 mr-1" />
                        登录
                      </Button>
                    </DialogTrigger>
                    <DialogContent>
                      <DialogHeader>
                        <DialogTitle>扫码登录</DialogTitle>
                      </DialogHeader>
                      {qr ? (
                        <div className="flex flex-col items-center">
                          <div className="text-gray text-sm py-1">
                            关注下方二维码，在对话框中输入验证码，既可以实现自动登录哦~
                          </div>
                          <img
                            className="pt-2"
                            src={qr}
                            alt="登录二维码"
                            width={180}
                            height={180}
                          />
                          <div className="mt-2 text-lg font-bold">
                            验证码：{code}
                          </div>
                          {env && env.length > 0 && env[0].value == "dev" && (
                            <div className="flex gap-4 mt-6">
                              <Button
                                onClick={() => handleWxLogin("user", code)}
                                disabled={loginLoading}
                              >
                                {loginLoading ? "登录中..." : "普通用户登录"}
                              </Button>
                              <Button
                                onClick={() => handleWxLogin("admin", code)}
                                disabled={adminLoading}
                                variant="secondary"
                              >
                                {adminLoading ? "登录中..." : "管理员登录"}
                              </Button>
                            </div>
                          )}
                        </div>
                      ) : (
                        <div>微信登录二维码渲染中，请稍后...</div>
                      )}
                    </DialogContent>
                  </Dialog>
                )
              )}
            </div>
          </div>
        </div>
      </header>

      {/* 正文内容 */}
      <main className="flex-grow pt-16">{children}</main>

      {/* 页脚 */}
      <footer className="bg-white border-t py-6 mt-auto">
        <div className="px-10">
          <div className="flex flex-col items-center justify-center text-center">
            <p className="text-gray-500 mb-2">© {new Date().getFullYear()} 求职派 - 专注于校园招聘信息</p>
            <div className="flex space-x-4 text-sm text-gray-500">
              <a href="#" className="hover:text-blue-600 transition-colors">关于我们</a>
              <a href="#" className="hover:text-blue-600 transition-colors">隐私政策</a>
              <a href="#" className="hover:text-blue-600 transition-colors">使用条款</a>
              <a href="#" className="hover:text-blue-600 transition-colors">联系我们</a>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}
