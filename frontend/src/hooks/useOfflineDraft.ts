'use client'
import { useEffect, useRef, useCallback } from 'react'
import { saveDraftToDB, loadDraftFromDB, clearDraftFromDB } from '@/lib/draftDB'

interface UseOfflineDraftOptions {
  stageId: string | null
  artifactType: string
  debounceMs?: number
}

interface UseOfflineDraftReturn {
  /** 从 IndexedDB 加载草稿，返回草稿内容或 null */
  loadDraft: () => Promise<string | null>
  /** 保存草稿（已 debounce） */
  saveDraft: (content: string) => void
  /** 提交成功后清除草稿 */
  clearDraft: () => void
}

/**
 * useOfflineDraft：基于 IndexedDB 的离线草稿缓存 Hook
 *
 * - 按 stageId + artifactType 存储草稿
 * - 每次输入自动 debounce 保存（默认 1000ms）
 * - 提交成功后调用 clearDraft 清除缓存
 * - IndexedDB 不可用时（无痕模式等）静默降级，不影响主流程
 */
export function useOfflineDraft({
  stageId,
  artifactType,
  debounceMs = 1000,
}: UseOfflineDraftOptions): UseOfflineDraftReturn {
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  // 卸载标记：防止卸载后异步 saveDraftToDB 仍用旧的 stageId / artifactType 写入
  const mountedRef = useRef(true)

  const loadDraft = useCallback(async (): Promise<string | null> => {
    if (!stageId) return null
    return loadDraftFromDB(stageId, artifactType)
  }, [stageId, artifactType])

  const saveDraft = useCallback(
    (content: string) => {
      if (!stageId) return
      if (timerRef.current) clearTimeout(timerRef.current)
      timerRef.current = setTimeout(() => {
        // 若组件已卸载（或 stageId 已切换导致 cancel 被调用），跳过写入
        if (!mountedRef.current) return
        saveDraftToDB(stageId, artifactType, content)
      }, debounceMs)
    },
    [stageId, artifactType, debounceMs],
  )

  const clearDraft = useCallback(() => {
    if (!stageId) return
    if (timerRef.current) clearTimeout(timerRef.current)
    clearDraftFromDB(stageId, artifactType)
  }, [stageId, artifactType])

  // stageId / artifactType 变化时取消待执行的定时器，防止写入旧 stage 的草稿
  useEffect(() => {
    mountedRef.current = true
    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current)
        timerRef.current = null
      }
    }
  }, [stageId, artifactType])

  // 组件卸载时标记为 unmounted，阻止任何后续异步写入
  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
      if (timerRef.current) {
        clearTimeout(timerRef.current)
        timerRef.current = null
      }
    }
  }, [])

  return { loadDraft, saveDraft, clearDraft }
}
