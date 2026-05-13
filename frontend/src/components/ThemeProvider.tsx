'use client'
import { createContext, useContext, useEffect, useState, ReactNode } from 'react'

export type Theme = 'normal' | 'dark' | 'easy'

const ThemeCtx = createContext<{ theme: Theme; setTheme: (t: Theme) => void }>({
  theme: 'normal',
  setTheme: () => {},
})

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, set] = useState<Theme>('normal')

  useEffect(() => {
    const saved = (localStorage.getItem('app-theme') as Theme) || 'normal'
    set(saved)
    document.documentElement.setAttribute('data-theme', saved)
  }, [])

  const setTheme = (t: Theme) => {
    set(t)
    localStorage.setItem('app-theme', t)
    document.documentElement.setAttribute('data-theme', t)
  }

  return <ThemeCtx.Provider value={{ theme, setTheme }}>{children}</ThemeCtx.Provider>
}

export const useTheme = () => useContext(ThemeCtx)
