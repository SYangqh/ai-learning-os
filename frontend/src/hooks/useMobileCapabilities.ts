'use client'
import { useState, useEffect, useCallback } from 'react'

export interface SafeAreaInsets {
  top: number
  right: number
  bottom: number
  left: number
}

export interface MobileCapabilities {
  isMobile: boolean
  isTablet: boolean
  safeAreaInsets: SafeAreaInsets
  keyboardHeight: number
  hasHaptics: boolean
  hasNotifications: boolean
  notificationPermission: NotificationPermission | 'unsupported'
  requestHapticsTest: () => void
  requestNotificationPermission: () => Promise<boolean>
}

/** 从 CSS env() 读取安全区值（单位 px） */
function readSafeAreaInsets(): SafeAreaInsets {
  if (typeof window === 'undefined') return { top: 0, right: 0, bottom: 0, left: 0 }
  const style = getComputedStyle(document.documentElement)
  const parse = (v: string) => parseFloat(v) || 0
  return {
    top:    parse(style.getPropertyValue('--safe-inset-top').trim() || '0'),
    right:  parse(style.getPropertyValue('--safe-inset-right').trim() || '0'),
    bottom: parse(style.getPropertyValue('--safe-inset-bottom').trim() || '0'),
    left:   parse(style.getPropertyValue('--safe-inset-left').trim() || '0'),
  }
}

/** 通过 visualViewport API 计算软键盘高度 */
function calcKeyboardHeight(): number {
  if (typeof window === 'undefined' || !window.visualViewport) return 0
  const kbHeight = window.innerHeight - window.visualViewport.height - window.visualViewport.offsetTop
  return Math.max(0, kbHeight)
}

/**
 * useMobileCapabilities
 *
 * 检测：
 * - isMobile（<768px）/ isTablet（768-1024px）
 * - safeAreaInsets（notch / home indicator）
 * - keyboardHeight（visualViewport 实时计算）
 * - hasHaptics（Vibration API）
 * - hasNotifications / notificationPermission
 *
 * 权限请求：
 * - requestHapticsTest：触发一次短震动（iOS 不支持时静默失败）
 * - requestNotificationPermission：请求推送权限，返回是否获得授权（不支持/拒绝时返回 false，不抛出异常）
 */
export function useMobileCapabilities(): MobileCapabilities {
  const [windowWidth, setWindowWidth] = useState(
    typeof window !== 'undefined' ? window.innerWidth : 1024,
  )
  const [safeAreaInsets, setSafeAreaInsets] = useState<SafeAreaInsets>({
    top: 0, right: 0, bottom: 0, left: 0,
  })
  const [keyboardHeight, setKeyboardHeight] = useState(0)
  const [notificationPermission, setNotificationPermission] = useState<
    NotificationPermission | 'unsupported'
  >('unsupported')

  // 窗口宽度监听
  useEffect(() => {
    const handleResize = () => setWindowWidth(window.innerWidth)
    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  // 安全区初始化（CSS env() 在 layout 渲染后才可读）
  useEffect(() => {
    setSafeAreaInsets(readSafeAreaInsets())
  }, [])

  // 软键盘高度监听（visualViewport）
  useEffect(() => {
    if (typeof window === 'undefined' || !window.visualViewport) return
    const handleVPResize = () => {
      const kh = calcKeyboardHeight()
      setKeyboardHeight(kh)
      // 动态注入 CSS 变量，供 .keyboard-aware 使用
      document.documentElement.style.setProperty('--keyboard-height', `${kh}px`)
    }
    window.visualViewport.addEventListener('resize', handleVPResize)
    window.visualViewport.addEventListener('scroll', handleVPResize)
    return () => {
      window.visualViewport!.removeEventListener('resize', handleVPResize)
      window.visualViewport!.removeEventListener('scroll', handleVPResize)
    }
  }, [])

  // 通知权限初始化
  useEffect(() => {
    if (typeof window === 'undefined' || !('Notification' in window)) {
      setNotificationPermission('unsupported')
      return
    }
    setNotificationPermission(Notification.permission)
  }, [])

  const isMobile = windowWidth < 768
  const isTablet = windowWidth >= 768 && windowWidth < 1024
  const hasHaptics = typeof navigator !== 'undefined' && 'vibrate' in navigator
  const hasNotifications = notificationPermission !== 'unsupported' && notificationPermission !== 'denied'

  /** 触发短震动（iOS Safari 不支持 Vibration API，静默降级） */
  const requestHapticsTest = useCallback(() => {
    if (typeof navigator === 'undefined' || !('vibrate' in navigator)) return
    try {
      navigator.vibrate([30, 20, 60])
    } catch {
      // 静默忽略
    }
  }, [])

  /** 请求通知权限，返回是否成功获得 granted */
  const requestNotificationPermission = useCallback(async (): Promise<boolean> => {
    if (typeof window === 'undefined' || !('Notification' in window)) return false
    if (Notification.permission === 'granted') return true
    if (Notification.permission === 'denied') return false
    try {
      const result = await Notification.requestPermission()
      setNotificationPermission(result)
      return result === 'granted'
    } catch {
      // Safari 旧版不支持 Promise 形式
      return false
    }
  }, [])

  return {
    isMobile,
    isTablet,
    safeAreaInsets,
    keyboardHeight,
    hasHaptics,
    hasNotifications,
    notificationPermission,
    requestHapticsTest,
    requestNotificationPermission,
  }
}
