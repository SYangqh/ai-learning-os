'use client'
import { useEffect, useState } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { STORAGE_KEYS, setStored, apiFetch, getStored } from '@/lib/api'
import { Suspense } from 'react'

function VerifyContent() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const [status, setStatus] = useState<'verifying' | 'success' | 'error'>('verifying')
  const [errorMsg, setErrorMsg] = useState('')

  useEffect(() => {
    const token = searchParams.get('token')
    if (!token) {
      setStatus('error')
      setErrorMsg('链接无效，缺少验证令牌。')
      return
    }
    verify(token)
  }, [searchParams])

  async function verify(token: string) {
    try {
      const deviceId = getStored(STORAGE_KEYS.DEVICE_ID)

      type VerifyResp = {
        data: {
          user_id: string
          access_token: string
          refresh_token: string
          pending_merge_guest_id?: string
        }
      }
      const res = await apiFetch<VerifyResp>('/auth/magic-link/verify', {
        method: 'POST',
        body: JSON.stringify({ token, deviceId }),
      })
      const d = res.data

      setStored(STORAGE_KEYS.ACCESS_TOKEN, d.access_token)
      setStored(STORAGE_KEYS.REFRESH_TOKEN, d.refresh_token)
      setStored(STORAGE_KEYS.USER_ID, d.user_id)

      // 若有游客账号待合并，自动合并
      if (d.pending_merge_guest_id) {
        try {
          await apiFetch('/auth/merge-guest', {
            method: 'POST',
            body: JSON.stringify({ guestUserId: d.pending_merge_guest_id }),
          })
        } catch {
          // 合并失败不阻断登录流程
        }
      }

      setStatus('success')
      // 跳回首页继续配置 API Key / 画像，或直接进学习页
      setTimeout(() => {
        const pathId = getStored(STORAGE_KEYS.PATH_ID)
        router.replace(pathId ? '/learn' : '/')
      }, 1500)
    } catch (e: unknown) {
      setStatus('error')
      setErrorMsg(e instanceof Error ? e.message : '验证失败，链接可能已过期或已使用。')
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-4">
      <div className="w-full max-w-sm text-center space-y-6">
        <div className="text-4xl">🧠</div>
        <h1 className="text-xl font-bold text-emerald-400">AI Learning OS</h1>

        {status === 'verifying' && (
          <div className="space-y-3">
            <div className="text-3xl animate-spin inline-block">🔄</div>
            <p className="text-gray-300 font-semibold">正在验证登录链接...</p>
          </div>
        )}

        {status === 'success' && (
          <div className="space-y-3">
            <div className="text-3xl">✅</div>
            <p className="text-emerald-400 font-semibold">登录成功！</p>
            <p className="text-gray-500 text-sm">正在跳转，请稍候...</p>
          </div>
        )}

        {status === 'error' && (
          <div className="space-y-4">
            <div className="text-3xl">❌</div>
            <p className="text-red-400 font-semibold">验证失败</p>
            <p className="text-gray-400 text-sm">{errorMsg}</p>
            <button
              onClick={() => router.replace('/')}
              className="mt-2 bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-semibold px-6 py-2 rounded-lg transition-colors"
            >
              返回首页
            </button>
          </div>
        )}
      </div>
    </div>
  )
}

export default function VerifyPage() {
  return (
    <Suspense fallback={
      <div className="min-h-screen flex items-center justify-center">
        <p className="text-gray-500">加载中...</p>
      </div>
    }>
      <VerifyContent />
    </Suspense>
  )
}
