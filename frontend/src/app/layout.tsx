import type { Metadata, Viewport } from 'next'
import './globals.css'
import { ThemeProvider } from '@/components/ThemeProvider'

export const metadata: Metadata = {
  title: 'AI Learning OS',
  description: '你的个人 AI 学习操作系统',
  manifest: '/manifest.json',
  appleWebApp: {
    capable: true,
    statusBarStyle: 'default',
    title: 'AI Learning OS',
  },
}

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  maximumScale: 1,
  userScalable: false,
  viewportFit: 'cover',
  // 软键盘弹出时压缩视口高度而不是平移内容
  interactiveWidget: 'resizes-content',
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh">
      <head>
        {/* Fonts for special themes: Orbitron (cyber), Playfair Display (botanical) */}
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link
          href="https://fonts.googleapis.com/css2?family=Orbitron:wght@600;700&family=Playfair+Display:ital,wght@0,600;1,400&display=swap"
          rel="stylesheet"
        />
        {/* iOS Safari 主题色 */}
        <meta name="theme-color" content="#0f172a" />
        <meta name="apple-mobile-web-app-capable" content="yes" />
        <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent" />
      </head>
      <body className="min-h-screen" style={{ backgroundColor: 'var(--c-bg)', color: 'var(--c-text)' }}>
        <ThemeProvider>{children}</ThemeProvider>
      </body>
    </html>
  )
}
