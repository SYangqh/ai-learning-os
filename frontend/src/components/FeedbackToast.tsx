/**
 * FeedbackToast — 主题化正反馈演出组件
 * 
 * 根据 FeedbackEffectManifest 配置渲染反馈演出，支持：
 * - 主题感知的文案/动效/音效
 * - 静音跟随、音效总开关、无障碍低动效模式
 * - 播放失败降级文本提示
 * - 触感反馈（移动端）
 */

'use client'
import { useEffect, useRef, useState } from 'react'
import { getFeedbackEffect } from '@/lib/feedbackManifest'
import type { FeedbackEventType } from '@/lib/feedbackManifest'
import type { Theme } from '@/components/ThemeProvider'

export interface FeedbackToastProps {
  theme: Theme
  eventType: FeedbackEventType
  variables?: Record<string, string | number>
  onComplete?: () => void
  /** 音效总开关（默认 true） */
  soundEnabled?: boolean
  /** 是否尊重系统静音（默认 true） */
  respectSystemMute?: boolean
  /** 是否使用低动效模式（默认 false，无障碍主题强制 true） */
  reducedMotion?: boolean
}

export default function FeedbackToast({
  theme,
  eventType,
  variables,
  onComplete,
  soundEnabled = true,
  respectSystemMute = true,
  reducedMotion = false,
}: FeedbackToastProps) {
  const [visible, setVisible] = useState(true)
  const [soundFailed, setSoundFailed] = useState(false)
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const hasPlayedSound = useRef(false)

  // 从 localStorage 读取用户设置
  const userSoundEnabled = typeof window !== 'undefined'
    ? localStorage.getItem('feedback-sound-enabled') !== 'false' // 默认开启
    : true
  const userReducedMotion = typeof window !== 'undefined'
    ? localStorage.getItem('feedback-reduced-motion') === 'true' // 默认关闭
    : false

  const effect = getFeedbackEffect(theme, eventType, variables)
  
  // 无障碍主题强制使用无动效模式，或用户偏好低动效
  const shouldReduceMotion = reducedMotion || theme === 'accessible' || userReducedMotion
  
  // 最终是否播放音效（用户设置优先）
  const finalSoundEnabled = soundEnabled && userSoundEnabled
  
  // 最终动画类型（低动效时降级为 none 或 fade）
  const finalAnimation = shouldReduceMotion
    ? (effect.animation === 'none' ? 'none' : 'fade')
    : effect.animation

  useEffect(() => {
    // 播放音效
    if (finalSoundEnabled && effect.sound && !hasPlayedSound.current) {
      hasPlayedSound.current = true
      playSound(effect.sound, effect.soundVolume, respectSystemMute)
        .catch(() => setSoundFailed(true))
    }

    // 触感反馈（移动端）
    if (effect.haptic !== 'none' && 'vibrate' in navigator) {
      const duration = effect.haptic === 'light' ? 50 : effect.haptic === 'medium' ? 100 : 200
      navigator.vibrate(duration)
    }

    // 自动隐藏
    const hideDelay = Math.max(effect.animationDuration + 3000, 4000)
    const timer = setTimeout(() => {
      setVisible(false)
      onComplete?.()
    }, hideDelay)

    return () => clearTimeout(timer)
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  if (!visible) return null

  return (
    <div
      className={`feedback-toast ${getAnimationClass(finalAnimation)} t-panel border-2 t-border rounded-xl shadow-2xl px-6 py-4 max-w-md mx-auto`}
      style={{
        position: 'fixed',
        top: '50%',
        left: '50%',
        transform: 'translate(-50%, -50%)',
        zIndex: 9999,
        animationDuration: `${effect.animationDuration}ms`,
      }}
    >
      <div className="text-center">
        <div className="text-2xl font-bold t-accent-text mb-1">
          {soundFailed ? effect.fallbackText : effect.text}
        </div>
        {effect.subtext && !soundFailed && (
          <div className="text-sm t-muted">{effect.subtext}</div>
        )}
      </div>
    </div>
  )
}

function getAnimationClass(animation: string): string {
  switch (animation) {
    case 'bounce':
      return 'animate-bounce-in'
    case 'fade':
      return 'animate-fade-in'
    case 'slide':
      return 'animate-slide-up'
    case 'glow':
      return 'animate-glow'
    case 'none':
    default:
      return ''
  }
}

/**
 * 播放音效（支持静音检测和降级）
 */
async function playSound(
  soundPath: string,
  volume: number,
  respectSystemMute: boolean
): Promise<void> {
  try {
    const audio = new Audio(soundPath)
    audio.volume = Math.max(0, Math.min(1, volume))

    // 检查系统静音（通过 AudioContext）
    if (respectSystemMute && typeof AudioContext !== 'undefined') {
      const ctx = new AudioContext()
      if (ctx.state === 'suspended') {
        // 系统静音或浏览器策略阻止自动播放
        await ctx.close()
        throw new Error('AudioContext suspended (system muted or autoplay blocked)')
      }
      await ctx.close()
    }

    await audio.play()
  } catch (err) {
    // 播放失败（系统静音、文件404、autoplay policy）
    console.debug('[FeedbackToast] Sound playback failed:', err)
    throw err
  }
}
