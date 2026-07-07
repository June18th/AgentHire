import type React from "react"
import type { Metadata } from "next"
import "./globals.css"
import { LoginUserProvider } from "@/hooks/useLoginUser"
import { LoginModalProvider } from "@/hooks/useLoginModal"
import { Toaster } from "@/components/ui/toaster"
import AppLayout from "@/components/layout/AppLayout"

export const metadata: Metadata = {
  title: "求职派 - 职位招聘平台",
  description: "专业的职位招聘和求职平台",
  generator: '一灰灰',
  icons: {
    icon: [
      { url: '/favicon.svg', type: 'image/svg+xml' },
      { url: '/favicon.ico' },
    ],
  }
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="zh-CN" suppressHydrationWarning>
      <body className="font-sans" suppressHydrationWarning>
        <LoginUserProvider>
          <LoginModalProvider>
            <AppLayout>
              {children}
            </AppLayout>
          </LoginModalProvider>
        </LoginUserProvider>
        <Toaster />
      </body>
    </html>
  )
}
