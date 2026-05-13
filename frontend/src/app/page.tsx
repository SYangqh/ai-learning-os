'use client'
import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { STORAGE_KEYS, getStored, setStored, apiFetch, isLoggedIn } from '@/lib/api'

// 步骤：账号 → API Key → 画像 → 生成路径
type Step = 'account' | 'apikey' | 'profile' | 'generating'
type AccountMode = 'guest' | 'email' | 'email_sent'

const BACKGROUNDS = ['前端工程师', '硬件工程师', '金融从业者', '产品经理', '其他']
const TARGETS = ['后端开发 (Node.js)', 'Python & 数据分析', '人工智能/ML', '全栈开发', '移动端开发']
const SKILLS_MAP: Record<string, string[]> = {
  '前端工程师': ['HTML/CSS', 'JavaScript', 'React', 'Vue', 'TypeScript'],
  '硬件工程师': ['C/C++', 'FPGA', '嵌入式', '电路设计'],
  '金融从业者': ['Excel', 'SQL', '数据分析', 'Bloomberg'],
  '产品经理': ['原型设计', 'SQL基础', '用户研究'],
  '其他': ['其他技能'],
}

type ProviderId = 'anthropic' | 'openai' | 'deepseek' | 'alibaba' | 'zhipu' | 'other'
const PROVIDERS: { id: ProviderId; label: string; emoji: string; link: string | null; placeholder: string }[] = [
  { id: 'anthropic', label: 'Anthropic', emoji: '🟠', link: 'https://console.anthropic.com', placeholder: 'sk-ant-api03-...' },
  { id: 'openai',    label: 'OpenAI',    emoji: '🟢', link: 'https://platform.openai.com/api-keys', placeholder: 'sk-...' },
  { id: 'deepseek',  label: 'DeepSeek',  emoji: '💙', link: 'https://platform.deepseek.com', placeholder: 'sk-...' },
  { id: 'alibaba',   label: '通义千问',  emoji: '🟣', link: 'https://dashscope.aliyun.com', placeholder: 'sk-...' },
  { id: 'zhipu',     label: '智谱 AI',   emoji: '⚡', link: 'https://open.bigmodel.cn', placeholder: 'API Key...' },
  { id: 'other',     label: '其他兼容',  emoji: '⚪', link: null, placeholder: 'API Key...' },
]

export default function Home() {
  const router = useRouter()
  const [step, setStep] = useState<Step>('account')
  const [accountMode, setAccountMode] = useState<AccountMode>('guest')
  const [email, setEmail] = useState('')
  const [apiKey, setApiKey] = useState('')
  const [providerHint, setProviderHint] = useState<ProviderId | null>(null)
  const [background, setBackground] = useState('')
  const [target, setTarget] = useState('')
  const [selectedSkills, setSelectedSkills] = useState<string[]>([])
  const [dailyTime, setDailyTime] = useState(60)
  const [analogyBasis, setAnalogyBasis] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (isLoggedIn()) {
      const pid = getStored(STORAGE_KEYS.PATH_ID)
      if (pid) {
        router.push('/learn')
      } else {
        // 已登录但没有路径，跳到 API Key 步骤
        setStep('apikey')
      }
    }
  }, [router])

  // ── Step 1: 账号 ──────────────────────────────────────────────────────────

  async function handleGuestLogin() {
    setLoading(true)
    setError('')
    try {
      const deviceId = getStored(STORAGE_KEYS.DEVICE_ID)
      type GuestResp = { data: { user_id: string; device_id: string; access_token: string; refresh_token: string } }
      const res = await apiFetch<GuestResp>('/auth/guest', {
        method: 'POST',
        body: JSON.stringify({ deviceId }),
      })
      const d = res.data
      setStored(STORAGE_KEYS.ACCESS_TOKEN, d.access_token)
      setStored(STORAGE_KEYS.REFRESH_TOKEN, d.refresh_token)
      setStored(STORAGE_KEYS.DEVICE_ID, d.device_id)
      setStored(STORAGE_KEYS.USER_ID, d.user_id)
      setStep('apikey')
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '登录失败，请重试')
    } finally {
      setLoading(false)
    }
  }

  async function handleEmailRequest() {
    if (!email.trim().includes('@')) {
      setError('请输入有效的邮箱地址')
      return
    }
    setLoading(true)
    setError('')
    try {
      const deviceId = getStored(STORAGE_KEYS.DEVICE_ID)
      await apiFetch('/auth/magic-link/request', {
        method: 'POST',
        body: JSON.stringify({ email: email.trim(), deviceId }),
      })
      setAccountMode('email_sent')
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '发送失败，请重试')
    } finally {
      setLoading(false)
    }
  }

  // ── Step 2: API Key ───────────────────────────────────────────────────────

  function detectProvider(key: string): ProviderId | null {
    if (key.startsWith('sk-ant-')) return 'anthropic'
    if (key.startsWith('sk-')) return 'openai'
    return null
  }

  async function handleApiKeySubmit() {
    const trimmed = apiKey.trim()
    if (!trimmed) {
      setError('请输入 API Key')
      return
    }
    const provider = providerHint ?? detectProvider(trimmed)
    if (!provider) {
      setError('请先选择服务商，或输入以 sk- 开头的 Key 自动识别')
      return
    }
    // 映射到后端 provider key（alibaba/zhipu/other 复用 OpenAI 兼容协议）
    const backendProviderKey = (['anthropic', 'openai', 'deepseek', 'alibaba', 'zhipu'] as const).includes(provider as never)
      ? (provider === 'other' ? 'openai' : provider)
      : 'openai'
    setLoading(true)
    setError('')
    try {
      await apiFetch('/llm/credentials', {
        method: 'PUT',
        body: JSON.stringify({
          providerKey: backendProviderKey,
          apiKey: trimmed,
        }),
      })
      setStep('profile')
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '保存 API Key 失败')
    } finally {
      setLoading(false)
    }
  }

  // ── Step 3: 画像 + 生成路径 ───────────────────────────────────────────────

  async function handleProfileSubmit() {
    if (!background || !target) {
      setError('请选择你的背景和学习目标')
      return
    }
    setLoading(true)
    setError('')
    setStep('generating')
    try {
      // 保存画像
      await apiFetch('/profile', {
        method: 'POST',
        body: JSON.stringify({
          background,
          skills: selectedSkills,
          target,
          dailyTime,
          analogyBasis: analogyBasis.trim() || undefined,
        }),
      })

      // 生成学习路径
      type PathResp = { data: { path_id: string } }
      const pathRes = await apiFetch<PathResp>('/path/generate', { method: 'POST' })
      setStored(STORAGE_KEYS.PATH_ID, pathRes.data.path_id)

      router.push('/learn')
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '生成路径失败，请重试')
      setStep('profile')
    } finally {
      setLoading(false)
    }
  }

  function toggleSkill(skill: string) {
    setSelectedSkills(prev =>
      prev.includes(skill) ? prev.filter(s => s !== skill) : [...prev, skill]
    )
  }

  // ── 步骤标题 ──────────────────────────────────────────────────────────────

  const STEP_LABELS: Record<Step, string> = {
    account: '账号',
    apikey: 'API Key',
    profile: '学习画像',
    generating: '生成路径',
  }
  const STEPS: Step[] = ['account', 'apikey', 'profile', 'generating']
  const currentStepIndex = STEPS.indexOf(step)

  return (
    <div className="min-h-screen t-bg flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        {/* Header */}
        <div className="text-center mb-10">
          <div className="text-4xl mb-3">🧠</div>
          <h1 className="text-2xl font-bold t-accent-text">AI Learning OS</h1>
          <p className="t-faint text-sm mt-2">你的个人 AI 学习操作系统</p>
        </div>

        {/* Step indicator */}
        <div className="flex items-center justify-center gap-2 mb-8">
          {STEPS.slice(0, 3).map((s, i) => (
            <div key={s} className="flex items-center gap-2">
              <div className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold border-2 transition-all ${
                i === currentStepIndex
                  ? 't-stage-active t-accent-text'
                  : i < currentStepIndex
                  ? 'border-emerald-400 bg-emerald-100 text-emerald-600 dark:bg-emerald-900 dark:text-emerald-300'
                  : 't-border t-faint'
              }`}>{i + 1}</div>
              {i < 2 && (
                <div className={`w-12 h-0.5 ${i < currentStepIndex ? 'bg-emerald-400' : ''} t-border`} />
              )}
            </div>
          ))}
        </div>

        <div className="t-panel border t-border rounded-2xl p-6 shadow-sm">

          {/* ── Step 1: 账号 ── */}
          {step === 'account' && (
            <div className="space-y-4">
              <div>
                <h2 className="text-lg font-semibold t-text mb-1">开始你的学习</h2>
                <p className="t-faint text-sm">选择游客体验，或用邮筱登录以保存进度。</p>
              </div>

              <div className="flex gap-2">
                <button onClick={() => { setAccountMode('guest'); setError('') }}
                  className={`flex-1 py-2 rounded-lg text-sm border transition-all ${accountMode === 'guest' ? 't-stage-active t-accent-text font-semibold' : 't-border t-faint hover:t-text'}`}>
                  游客开始
                </button>
                <button onClick={() => { setAccountMode('email'); setError('') }}
                  className={`flex-1 py-2 rounded-lg text-sm border transition-all ${accountMode === 'email' || accountMode === 'email_sent' ? 't-stage-active t-accent-text font-semibold' : 't-border t-faint hover:t-text'}`}>
                  邮箱登录
                </button>
              </div>

              {accountMode === 'guest' && (
                <>
                  <p className="text-xs t-faint">游客模式数据存储在本设备，可随时升级到邮筱账号。</p>
                  {error && <p className="text-red-400 text-xs">{error}</p>}
                  <button onClick={handleGuestLogin} disabled={loading}
                    className="w-full t-btn-primary font-semibold py-3 rounded-lg">
                    {loading ? '创建中...' : '以游客身份继续'}
                  </button>
                </>
              )}

              {accountMode === 'email' && (
                <>
                  <input type="email" value={email} onChange={e => setEmail(e.target.value)}
                    onKeyDown={e => e.key === 'Enter' && handleEmailRequest()}
                    placeholder="your@email.com"
                    className="w-full t-input-field border rounded-lg px-4 py-3 text-sm transition-colors" />
                  {error && <p className="text-red-400 text-xs">{error}</p>}
                  <button onClick={handleEmailRequest} disabled={loading}
                    className="w-full t-btn-primary font-semibold py-3 rounded-lg">
                    {loading ? '发送中...' : '发送登录链接'}
                  </button>
                </>
              )}

              {accountMode === 'email_sent' && (
                <div className="text-center py-4">
                  <div className="text-3xl mb-3">📬</div>
                  <p className="t-accent-text font-semibold">邮件已发送！</p>
                  <p className="t-faint text-sm mt-2">请检查 <span className="t-text font-medium">{email}</span> 的收件筱，点击邮件中的链接完成登录。</p>
                  <p className="t-muted text-xs mt-3">链接 10 分钟内有效</p>
                  <button onClick={() => setAccountMode('email')} className="mt-4 t-faint text-xs underline hover:t-text transition-colors">重新发送</button>
                </div>
              )}
            </div>
          )}

          {/* ── Step 2: API Key ── */}
          {step === 'apikey' && (
            <div className="space-y-4">
              <div>
                <h2 className="text-lg font-semibold t-text mb-1">接入你的 AI Key</h2>
                <p className="t-faint text-sm">Key 加密存储在服务端，不会明文泄露。支持主流大模型服务商。</p>
              </div>

              <div>
                <label className="text-xs t-faint uppercase tracking-wider mb-2 block">选择服务商</label>
                <div className="grid grid-cols-3 gap-2">
                  {PROVIDERS.map(p => (
                    <button key={p.id} onClick={() => { setProviderHint(p.id); setError('') }}
                      className={`px-2 py-2 rounded-lg text-xs border transition-all text-center ${
                        providerHint === p.id
                          ? 't-stage-active t-accent-text font-semibold'
                          : 't-border t-faint'
                      }`}>
                      <span className="block text-base mb-0.5">{p.emoji}</span>
                      {p.label}
                    </button>
                  ))}
                </div>
              </div>

              {providerHint && PROVIDERS.find(p => p.id === providerHint)?.link && (
                <a href={PROVIDERS.find(p => p.id === providerHint)!.link!} target="_blank" rel="noreferrer"
                  className="block text-xs t-accent-text hover:underline">
                  → 获取 {PROVIDERS.find(p => p.id === providerHint)!.label} API Key
                </a>
              )}

              <div className="relative">
                <input type="password" value={apiKey}
                  onChange={e => { setApiKey(e.target.value); if (!providerHint) setProviderHint(detectProvider(e.target.value.trim())); setError('') }}
                  onKeyDown={e => e.key === 'Enter' && handleApiKeySubmit()}
                  placeholder={providerHint ? (PROVIDERS.find(p => p.id === providerHint)?.placeholder ?? 'API Key...') : '先选服务商，再输入 Key...'}
                  className="w-full t-input-field border rounded-lg px-4 py-3 text-sm transition-colors" />
                {providerHint && (
                  <span className="absolute right-3 top-3 text-xs px-2 py-0.5 rounded-full t-accent-bg t-accent-text">
                    {PROVIDERS.find(p => p.id === providerHint)?.label}
                  </span>
                )}
              </div>
              {error && <p className="text-red-400 text-xs">{error}</p>}
              <button onClick={handleApiKeySubmit} disabled={loading}
                className="w-full t-btn-primary font-semibold py-3 rounded-lg">
                {loading ? '保存中...' : '继续'}
              </button>
              <div className="flex gap-2">
                <button onClick={() => setStep('account')} disabled={loading}
                  className="flex-1 t-faint text-sm py-2 hover:t-text transition-colors disabled:opacity-50">
                  ← 返回
                </button>
                <button onClick={() => setStep('profile')} disabled={loading}
                  className="flex-1 t-faint text-sm py-2 hover:t-text transition-colors disabled:opacity-50">
                  跳过
                </button>
              </div>
            </div>
          )}

          {/* ── Step 3: 画像 ── */}
          {step === 'profile' && (
            <div className="space-y-5">
              <div>
                <h2 className="text-lg font-semibold t-text mb-1">告诉我你的背景</h2>
                <p className="t-faint text-sm">AI 会用你熟悉的方式解释新知识。</p>
              </div>

              <div>
                <label className="text-xs t-faint uppercase tracking-wider mb-2 block">我目前是</label>
                <div className="grid grid-cols-2 gap-2">
                  {BACKGROUNDS.map(b => (
                    <button key={b} onClick={() => { setBackground(b); setSelectedSkills([]) }}
                      className={`px-3 py-2 rounded-lg text-sm border transition-all ${
                        background === b ? 't-stage-active t-accent-text font-semibold' : 't-border t-faint'
                      }`}>{b}</button>
                  ))}
                </div>
              </div>

              {background && SKILLS_MAP[background] && (
                <div>
                  <label className="text-xs t-faint uppercase tracking-wider mb-2 block">我会的技能</label>
                  <div className="flex flex-wrap gap-2">
                    {SKILLS_MAP[background].map(s => (
                      <button key={s} onClick={() => toggleSkill(s)}
                        className={`px-3 py-1.5 rounded-full text-xs border transition-all ${
                          selectedSkills.includes(s) ? 't-stage-active t-accent-text font-semibold' : 't-border t-faint'
                        }`}>{s}</button>
                    ))}
                  </div>
                </div>
              )}

              <div>
                <label className="text-xs t-faint uppercase tracking-wider mb-2 block">我想学</label>
                <div className="grid grid-cols-1 gap-2">
                  {TARGETS.map(t => (
                    <button key={t} onClick={() => setTarget(t)}
                      className={`px-3 py-2 rounded-lg text-sm border text-left transition-all ${
                        target === t ? 't-stage-active t-accent-text font-semibold' : 't-border t-faint'
                      }`}>{t}</button>
                  ))}
                </div>
              </div>

              <div>
                <label className="text-xs t-faint uppercase tracking-wider mb-2 block">
                  每天学习时间：<span className="t-accent-text">{dailyTime} 分钟</span>
                </label>
                <input type="range" min={15} max={180} step={15} value={dailyTime}
                  onChange={e => setDailyTime(+e.target.value)}
                  className="w-full" style={{ accentColor: 'var(--c-accent)' }} />
                <div className="flex justify-between text-xs t-faint mt-1">
                  <span>15分钟</span><span>3小时</span>
                </div>
              </div>

              <div>
                <label className="text-xs t-faint uppercase tracking-wider mb-2 block">
                  AI 类比参考 <span className="t-muted normal-case">（可选）</span>
                </label>
                <textarea
                  value={analogyBasis}
                  onChange={e => setAnalogyBasis(e.target.value)}
                  placeholder="例如：「我做过 3 年 Excel 数据透视表」或「我懂电路图，可以用电流类比数据流」"
                  rows={2}
                  className="w-full t-input-field border rounded-lg px-4 py-3 text-sm resize-none transition-colors"
                />
                <p className="text-xs t-faint mt-1">AI 导师会用你熟悉的领域打比方，让新知识更好理解。</p>
              </div>

              {error && <p className="text-red-400 text-xs">{error}</p>}

              <button onClick={handleProfileSubmit} disabled={loading}
                className="w-full t-btn-primary font-semibold py-3 rounded-lg">
                {loading ? '保存中...' : '生成我的学习路径'}
              </button>
              <button onClick={() => setStep('apikey')} disabled={loading}
                className="w-full t-faint text-sm py-2 hover:t-text transition-colors disabled:opacity-50">
                ← 返回上一步
              </button>
            </div>
          )}

          {/* ── 生成中占位 ── */}
          {step === 'generating' && (
            <div className="text-center py-10 space-y-4">
              <div className="text-4xl animate-spin inline-block">⚙️</div>
              <p className="t-accent-text font-semibold">AI 正在为你生成专属学习路径...</p>
              <p className="t-faint text-sm">通常需要 10~30 秒</p>
            </div>
          )}

        </div>
      </div>
    </div>
  )
}

