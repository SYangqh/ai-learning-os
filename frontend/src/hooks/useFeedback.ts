/**
 * useFeedback — 反馈演出管理 Hook
 * 
 * 提供统一的反馈演出触发接口，管理 FeedbackToast 的生命周期。
 */

'use client'
import { useState, useCallback } from 'react'
import type { FeedbackEventType } from '@/lib/feedbackManifest'

export interface FeedbackState {
  active: boolean
  eventType: FeedbackEventType | null
  variables?: Record<string, string | number>
}

export function useFeedback() {
  const [feedback, setFeedback] = useState<FeedbackState>({
    active: false,
    eventType: null,
  })

  const trigger = useCallback(
    (eventType: FeedbackEventType, variables?: Record<string, string | number>) => {
      setFeedback({ active: true, eventType, variables })
    },
    []
  )

  const clear = useCallback(() => {
    setFeedback({ active: false, eventType: null })
  }, [])

  return { feedback, trigger, clear }
}
