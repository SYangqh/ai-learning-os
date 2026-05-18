/**
 * draftDB — 共享 IndexedDB 草稿工具模块
 *
 * 供 useOfflineDraft hook 和 learn/page.tsx 的 loadDraftFromStage 共同使用，
 * 避免重复实现 openDB / CRUD 逻辑。
 */

export const DB_NAME = 'ai-learning-os-drafts'
export const DB_VERSION = 1
export const STORE_NAME = 'drafts'

export interface DraftData {
  content: string
  savedAt: number
}

export function draftKey(stageId: string, type: string) {
  return `draft:${stageId}:${type}`
}

export function openDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION)
    req.onupgradeneeded = () => {
      req.result.createObjectStore(STORE_NAME)
    }
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
}

export async function saveDraftToDB(stageId: string, type: string, content: string) {
  try {
    const db = await openDB()
    const tx = db.transaction(STORE_NAME, 'readwrite')
    const data: DraftData = { content, savedAt: Date.now() }
    tx.objectStore(STORE_NAME).put(data, draftKey(stageId, type))
    await new Promise<void>((res, rej) => {
      tx.oncomplete = () => res()
      tx.onerror = () => rej(tx.error)
    })
    db.close()
  } catch {
    // IndexedDB 不可用时静默忽略（如无痕模式）
  }
}

export async function loadDraftFromDB(stageId: string, type: string): Promise<string | null> {
  try {
    const db = await openDB()
    const tx = db.transaction(STORE_NAME, 'readonly')
    const result = await new Promise<DraftData | undefined>((res, rej) => {
      const req = tx.objectStore(STORE_NAME).get(draftKey(stageId, type))
      req.onsuccess = () => res(req.result as DraftData | undefined)
      req.onerror = () => rej(req.error)
    })
    db.close()
    return result?.content ?? null
  } catch {
    return null
  }
}

export async function clearDraftFromDB(stageId: string, type: string) {
  try {
    const db = await openDB()
    const tx = db.transaction(STORE_NAME, 'readwrite')
    tx.objectStore(STORE_NAME).delete(draftKey(stageId, type))
    db.close()
  } catch {
    // 静默忽略
  }
}
