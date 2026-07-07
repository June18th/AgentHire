"use client"

import { useCallback, useEffect, useState } from "react"
import { useRouter } from "next/navigation"
import { Button } from "@/components/ui/button"
import { useLoginModal } from "@/hooks/useLoginModal"
import { useLoginUser } from "@/hooks/useLoginUser"
import { useSSE } from "@/hooks/useSSE"
import { useToast } from "@/hooks/use-toast"
import { devWxLogin, getWxSseUrl, postWxCallback, type GlobalConfigItemValue } from "@/lib/api"
import { getConfigValue } from "@/lib/config"

function parseJwt(token: string) {
  try {
    const base64Url = token.split(".")[1]
    const base64 = base64Url.replace(/-/g, "+").replace(/_/g, "/")
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split("")
        .map((char) => `%${(`00${char.charCodeAt(0).toString(16)}`).slice(-2)}`)
        .join("")
    )
    return JSON.parse(jsonPayload)
  } catch {
    return null
  }
}

function cdata(value: string | number) {
  return "<!" + "[CDATA[" + String(value) + "]]" + ">"
}

function buildWxLoginXml(type: "user" | "admin", loginCode: string) {
  const content = type === "user" ? "login" : "admin"
  return [
    "<xml>",
    `<URL>${cdata("https://hhui.top")}</URL>`,
    `<ToUserName>${cdata("jobclaw")}</ToUserName>`,
    `<FromUserName>${cdata(`demoUser-${content}`)}</FromUserName>`,
    `<CreateTime>${Math.floor(Date.now() / 1000)}</CreateTime>`,
    `<MsgType>${cdata("text")}</MsgType>`,
    `<Content>${cdata(loginCode)}</Content>`,
    `<MsgId>${Date.now()}</MsgId>`,
    "</xml>",
  ].join("")
}

export function LoginPanel({ redirectTo = "/" }: { redirectTo?: string }) {
  const router = useRouter()
  const { toast } = useToast()
  const { setLoginOpen } = useLoginModal()
  const { setUserInfo } = useLoginUser()

  const [mounted, setMounted] = useState(false)
  const [localHost, setLocalHost] = useState(false)
  const [env, setEnv] = useState<GlobalConfigItemValue[]>([])
  const [qr, setQr] = useState("")
  const [code, setCode] = useState("")
  const [sseUrl, setSseUrl] = useState("")
  const [loginLoading, setLoginLoading] = useState(false)
  const [adminLoading, setAdminLoading] = useState(false)

  useEffect(() => {
    setMounted(true)
    setLocalHost(["localhost", "127.0.0.1", "::1"].includes(window.location.hostname))
    getConfigValue("site", "env").then(setEnv).catch(() => setEnv([]))
  }, [])

  const isDevEnv = env.some((item) => String(item.value).toLowerCase() === "dev")
  const localLoginEnabled = mounted && (localHost || isDevEnv)

  useEffect(() => {
    setSseUrl(mounted && !localLoginEnabled ? getWxSseUrl() : "")
  }, [mounted, localLoginEnabled])

  const persistUser = useCallback(
    (info: { userId: number; role: number; nickname?: string; avatar?: string; timestamp: number }, token?: string) => {
      setUserInfo(info)
      localStorage.setItem("oc-user", JSON.stringify(info))
      if (token) {
        localStorage.setItem("oc-token", token)
      }
    },
    [setUserInfo]
  )

  const completeLogin = useCallback(() => {
    setLoginOpen(false)
    router.push(redirectTo)
  }, [redirectTo, router, setLoginOpen])

  const handleSSE = useCallback(
    (type: string, payload: string) => {
      if (type === "qr") setQr(payload)
      if (type === "init") setCode(payload)
      if (type !== "login" || !payload) return

      document.cookie = payload
      const token = payload.substring(payload.indexOf("=") + 1, payload.indexOf(";"))
      const jwt = parseJwt(token)
      if (!jwt) return

      persistUser(
        {
          userId: jwt.uid,
          role: jwt.r,
          nickname: jwt.un,
          avatar: jwt.av,
          timestamp: Date.now(),
        },
        token
      )
      completeLogin()
    },
    [completeLogin, persistUser]
  )

  useSSE(sseUrl, handleSSE)

  const handleWxLogin = async (type: "user" | "admin", loginCode: string) => {
    try {
      type === "user" ? setLoginLoading(true) : setAdminLoading(true)
      await postWxCallback(buildWxLoginXml(type, loginCode))
    } catch {
      toast({ title: "登录失败", description: "请刷新页面后重新登录", variant: "destructive" })
    } finally {
      setLoginLoading(false)
      setAdminLoading(false)
    }
  }

  const handleDevLogin = async (type: "user" | "admin") => {
    try {
      type === "user" ? setLoginLoading(true) : setAdminLoading(true)
      const data = await devWxLogin(type)
      persistUser(
        {
          userId: data.user.userId,
          role: data.user.role,
          nickname: data.user.displayName,
          avatar: data.user.avatar,
          timestamp: Date.now(),
        },
        data.token
      )
      completeLogin()
    } catch (error) {
      toast({
        title: "登录失败",
        description: error instanceof Error ? error.message : "请检查后端服务和数据库初始化状态",
        variant: "destructive",
      })
    } finally {
      setLoginLoading(false)
      setAdminLoading(false)
    }
  }

  if (!mounted) {
    return <div className="text-sm text-gray-600">登录组件加载中...</div>
  }

  if (localLoginEnabled) {
    return (
      <div className="flex flex-col gap-4">
        <div className="text-sm text-gray-600">当前为本地开发环境，可以直接选择登录身份。</div>
        <div className="flex flex-wrap gap-3">
          <Button onClick={() => handleDevLogin("user")} disabled={loginLoading || adminLoading}>
            {loginLoading ? "登录中..." : "普通用户登录"}
          </Button>
          <Button onClick={() => handleDevLogin("admin")} disabled={loginLoading || adminLoading} variant="secondary">
            {adminLoading ? "登录中..." : "管理员登录"}
          </Button>
        </div>
      </div>
    )
  }

  if (!qr) {
    return <div className="text-sm text-gray-600">微信登录二维码渲染中，请稍后...</div>
  }

  return (
    <div className="flex flex-col items-center">
      <div className="py-1 text-sm text-gray-600">关注二维码并输入验证码即可登录。</div>
      <img className="pt-2" src={qr} alt="登录二维码" width={180} height={180} />
      <div className="mt-2 text-lg font-bold">验证码：{code}</div>
      {isDevEnv && !localHost ? (
        <div className="mt-6 flex gap-4">
          <Button onClick={() => handleWxLogin("user", code)} disabled={loginLoading}>
            {loginLoading ? "登录中..." : "普通用户登录"}
          </Button>
          <Button onClick={() => handleWxLogin("admin", code)} disabled={adminLoading} variant="secondary">
            {adminLoading ? "登录中..." : "管理员登录"}
          </Button>
        </div>
      ) : null}
    </div>
  )
}
