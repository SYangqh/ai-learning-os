'use client'
import { createContext, useContext, useEffect, useState, ReactNode } from 'react'

export type Theme = 'cute' | 'dark' | 'corporate' | 'cyber' | 'botanical' | 'accessible'

const VALID_THEMES: Theme[] = ['cute', 'dark', 'corporate', 'cyber', 'botanical', 'accessible']

const ThemeCtx = createContext<{ theme: Theme; setTheme: (t: Theme) => void }>({
  theme: 'cute',
  setTheme: () => {},
})

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, set] = useState<Theme>('cute')

  useEffect(() => {
    const saved = localStorage.getItem('app-theme') as Theme
    // 迁移旧主题名（normal/easy → cute）
    const valid: Theme = VALID_THEMES.includes(saved) ? saved : 'cute'
    set(valid)
    document.documentElement.setAttribute('data-theme', valid)
  }, [])

  const setTheme = (t: Theme) => {
    set(t)
    localStorage.setItem('app-theme', t)
    document.documentElement.setAttribute('data-theme', t)
  }

  return <ThemeCtx.Provider value={{ theme, setTheme }}>{children}</ThemeCtx.Provider>
}

export const useTheme = () => useContext(ThemeCtx)
