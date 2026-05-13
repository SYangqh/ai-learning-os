import type { Metadata } from 'next'
import './globals.css'
import { ThemeProvider } from '@/components/ThemeProvider'

export const metadata: Metadata = {
  title: 'AI Learning OS',
  description: '你的个人 AI 学习操作系统',
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh">
      <body className="font-mono min-h-screen" style={{ backgroundColor: 'var(--c-bg)', color: 'var(--c-text)' }}>
        <ThemeProvider>{children}</ThemeProvider>
      </body>
    </html>
  )
}
