/** Storage keys and API helpers (JWT-based, Spring Boot backend) */

export const STORAGE_KEYS = {
  ACCESS_TOKEN: 'los_access_token',
  REFRESH_TOKEN: 'los_refresh_token',
  DEVICE_ID: 'los_device_id',
  USER_ID: 'los_user_id',
  PATH_ID: 'los_path_id',
  SESSION_ID: 'los_session_id',
}

export function getStored(key: string): string | null {
  if (typeof window === 'undefined') return null
  return localStorage.getItem(key)
}

export function setStored(key: string, value: string) {
  localStorage.setItem(key, value)
}

export function clearAuth() {
  if (typeof window === 'undefined') return
  Object.values(STORAGE_KEYS).forEach(k => localStorage.removeItem(k))
}

export function isLoggedIn(): boolean {
  return !!getStored(STORAGE_KEYS.ACCESS_TOKEN)
}

const BASE = '/api'

/** 刷新 access token，更新 localStorage，返回新 access token */
async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = getStored(STORAGE_KEYS.REFRESH_TOKEN)
  if (!refreshToken) return null
  try {
    const res = await fetch(`${BASE}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
    if (!res.ok) return null
    const data = await res.json()
    const newAccess: string = data?.data?.access_token
    const newRefresh: string = data?.data?.refresh_token
    if (newAccess) setStored(STORAGE_KEYS.ACCESS_TOKEN, newAccess)
    if (newRefresh) setStored(STORAGE_KEYS.REFRESH_TOKEN, newRefresh)
    return newAccess ?? null
  } catch {
    return null
  }
}

/**
 * 带 JWT 的 fetch 封装。
 * - 自动附加 Authorization: Bearer {access_token}
 * - access token 过期（401）时自动刷新一次并重试
 * - 若刷新失败则清空本地 auth 信息
 */
export async function apiFetch<T = unknown>(path: string, opts: RequestInit = {}): Promise<T> {
  const doRequest = async (token: string | null) => {
    const headers: HeadersInit = {
      'Content-Type': 'application/json',
      ...(opts.headers as Record<string, string> ?? {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    }
    return fetch(`${BASE}${path}`, { ...opts, headers })
  }

  let accessToken = getStored(STORAGE_KEYS.ACCESS_TOKEN)
  let res = await doRequest(accessToken)

  if (res.status === 401 && accessToken) {
    // 尝试刷新 token
    const newToken = await refreshAccessToken()
    if (newToken) {
      res = await doRequest(newToken)
    } else {
      // 刷新失败，清空认证信息
      clearAuth()
      throw new Error('SESSION_EXPIRED')
    }
  }

  if (!res.ok) {
    const text = await res.text()
    let message = text
    try {
      const json = JSON.parse(text)
      message = json?.message ?? json?.msg ?? text
    } catch {
      // 非 JSON 错误体，直接使用原文
    }
    throw new Error(message)
  }

  return res.json() as Promise<T>
}

