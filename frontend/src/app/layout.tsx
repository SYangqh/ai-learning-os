import type { Metadata } from 'next'
import './globals.css'

export const metadata: Metadata = {
  title: 'AI Learning OS',
  description: '你的个人 AI 学习操作系统',
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh">
      <body className="bg-gray-950 text-gray-100 font-mono min-h-screen">{children}</body>
    </html>
  )
}
