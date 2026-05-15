'use client'
import { useState, useEffect, useRef } from 'react'
import { useRouter } from 'next/navigation'
import ReactMarkdown from 'react-markdown'
import { STORAGE_KEYS, getStored, setStored, apiFetch, clearAuth, isLoggedIn } from '@/lib/api'
import { useTheme } from '@/components/ThemeProvider'
import type { Theme } from '@/components/ThemeProvider'

const THEMES: { id: Theme; label: string; emoji: string }[] = [
  { id: 'cute',       label: '可爱',   emoji: '🌸' },
  { id: 'dark',       label: '夜间',   emoji: '🌙' },
  { id: 'corporate',  label: '国企',   emoji: '🏗' },
  { id: 'cyber',      label: '未来',   emoji: '⚡' },
  { id: 'botanical',  label: '自然',   emoji: '🌿' },
  { id: 'accessible', label: '无障碍', emoji: '♏️' },
]

type Stage = { id: string; index: number; title: string; goal: string; status: string }
type Message = { role: 'user' | 'assistant'; content: string }
type ArtifactStatus = 'none' | 'submitted' | 'passed' | 'needs_revision'
type ArtifactRecord = { id: string; type: string; content: string; status: string; node_key: string; created_at: string }
type RubricResult = { passed: boolean; score: number; feedback: string; hints: string[] }
type ArtifactType = 'CODE' | 'NOTE' | 'DIAGRAM' | 'ESSAY' | 'PROOF' | 'NONE'

const NODE_LABELS: Record<string, { label: string; color: string }> = {
  intro:    { label: '引入',  color: 'text-blue-600 border-blue-200 bg-blue-50' },
  concept:  { label: '概念',  color: 'text-amber-600 border-amber-200 bg-amber-50' },
  practice: { label: '练习',  color: 'text-orange-600 border-orange-200 bg-orange-50' },
  task:     { label: '任务',  color: 'text-red-600 border-red-200 bg-red-50' },
  review:   { label: '评审',  color: 'text-purple-600 border-purple-200 bg-purple-50' },
  retro:    { label: '复盘',  color: 'text-emerald-600 border-emerald-200 bg-emerald-50' },
  complete: { label: '完成',  color: 'text-emerald-700 border-emerald-300 bg-emerald-50' },
}

/** 每个节点的操作提示 */
const NODE_HINTS: Record<string, string> = {
  intro:    '💬 回答引导问题，让 AI 了解你的现有认知即可',
  concept:  '📖 阅读 AI 讲解，随时提问深入理解概念',
  practice: '✏️ 口头回答练习题即可，无需写代码',
  task:     '📝 在右侧产出区完成任务，写好后点击「提交作品」',
  review:   '📬 发送一条消息，AI 将立刻开始评审你的作品',
  retro:    '🧠 与 AI 一起回顾本阶段收获，可自由提问',
  complete: '✅ 本阶段已完成，可查看聊天记录或进入下一阶段',
}

/** 根据 artifact_type 生成任务节点提示 */
function getTaskHint(artifactType: ArtifactType): string {
  switch (artifactType) {
    case 'CODE':    return '💻 在右侧代码区完成任务，写好后点击「提交作品」'
    case 'NOTE':    return '📝 在右侧笔记区完成回答或论述，写好后点击「提交笔记」'
    case 'DIAGRAM': return '🖼️ 在右侧输入图表/脑图的链接，点击「提交链接」'
    case 'ESSAY':   return '✍️ 在右侧写作区完成论述，写好后点击「提交论述」'
    case 'PROOF':   return '📊 在右侧完成证明或推导，写好后点击「提交证明」'
    case 'NONE':    return '💬 直接与 AI 对话即可推进，本节点无需提交作品'
    default:        return '📝 在右侧完成任务后提交'
  }
}

const NODE_SEQ = ['intro', 'concept', 'practice', 'task', 'review', 'retro']

const ARTIFACT_STATUS_LABELS: Record<ArtifactStatus, { label: string; color: string }> = {
  none:            { label: '',           color: '' },
  submitted:       { label: '✓ 已提交 · 等待评审', color: 'text-amber-600' },
  passed:          { label: '✓ 评审通过',           color: 'text-emerald-600' },
  needs_revision:  { label: '✗ 需要修改',            color: 'text-red-500' },
}

function NodeBadge({ node, status }: { node: string; status: string }) {
  const info = NODE_LABELS[node] ?? { label: node, color: 'text-gray-500 border-gray-200 bg-gray-50' }
  const statusDot = status === 'failed' ? '✗' : status === 'passed' ? '✓' : '●'
  const dotColor = status === 'failed' ? 'text-red-500' : status === 'passed' ? 'text-emerald-500' : 'text-gray-400'
  return (
    <span className={`inline-flex items-center gap-1 text-xs font-mono px-2 py-0.5 rounded-full border ${info.color}`}>
      <span className={dotColor}>{statusDot}</span>
      {info.label}
    </span>
  )
}

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false)
  const copy = () => {
    navigator.clipboard.writeText(text).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    })
  }
  return (
    <button
      onClick={copy}
      className="absolute top-2 right-2 opacity-0 group-hover:opacity-100 transition-opacity text-xs text-gray-400 hover:text-gray-700 px-1.5 py-0.5 rounded bg-white border border-gray-200 shadow-sm"
    >
      {copied ? '✓' : '复制'}
    </button>
  )
}

export default function LearnPage() {
  const router = useRouter()
  const { theme, setTheme } = useTheme()
  const [stages, setStages] = useState<Stage[]>([])
  const [pathTitle, setPathTitle] = useState('')
  const [activeStage, setActiveStage] = useState<Stage | null>(null)
  const [sessionId, setSessionId] = useState<string | null>(null)
  const [messages, setMessages] = useState<Message[]>([])
  const [userInput, setUserInput] = useState('')
  const [code, setCode] = useState('')
  const [showCodePanel, setShowCodePanel] = useState(false)
  const [awaitsInput, setAwaitsInput] = useState(true)
  const [stageComplete, setStageComplete] = useState(false)
  const [loading, setLoading] = useState(false)
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const [showThemePicker, setShowThemePicker] = useState(false)
  // 节点状态
  const [currentNode, setCurrentNode] = useState<string>('intro')
  const [nodeStatus, setNodeStatus] = useState<string>('running')
  // Artifact 状态
  const [artifactStatus, setArtifactStatus] = useState<ArtifactStatus>('none')
  const [artifactType, setArtifactType] = useState<ArtifactType>('CODE')
  const [noteContent, setNoteContent] = useState('')
  const [diagramUrl, setDiagramUrl] = useState('')
  const [artifactSubmitting, setArtifactSubmitting] = useState(false)
  const [artifacts, setArtifacts] = useState<ArtifactRecord[]>([])
  const [rubricResult, setRubricResult] = useState<RubricResult | null>(null)
  const [showPassCelebration, setShowPassCelebration] = useState(false)
  const prevArtifactStatusRef = useRef<ArtifactStatus>('none')
  const lastUserInputRef = useRef<string>('')
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const themePickerRef = useRef<HTMLDivElement>(null)

  // 从当前节点状态推导——只有在 task 节点且尚未提交时才需要产出作品
  const ARTIFACT_REQUIRED_NODES = new Set(['task'])
  const awaitsArtifact = ARTIFACT_REQUIRED_NODES.has(currentNode) && artifactStatus === 'none' && artifactType !== 'NONE'
  const hasArtifactPanel = artifactType !== 'NONE'
  const completedCount = stages.filter(s => s.status === 'completed').length

  useEffect(() => {
    if (!isLoggedIn()) { router.push('/'); return }
    const pathId = getStored(STORAGE_KEYS.PATH_ID)
    if (!pathId) { router.push('/'); return }
    loadPath()
  }, [])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  useEffect(() => {
    if (artifactStatus === 'passed' && prevArtifactStatusRef.current !== 'passed') {
      setShowPassCelebration(true)
      const t = setTimeout(() => setShowPassCelebration(false), 6000)
      return () => clearTimeout(t)
    }
    prevArtifactStatusRef.current = artifactStatus
  }, [artifactStatus])

  useEffect(() => {
    if (!showThemePicker) return
    const handler = (e: MouseEvent) => {
      if (themePickerRef.current && !themePickerRef.current.contains(e.target as Node)) {
        setShowThemePicker(false)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [showThemePicker])

  async function loadPath() {
    try {
      type PathResp = { data: { path_id: string; title: string; stages: Stage[] } }
      const res = await apiFetch<PathResp>('/path')
      if (!res.data?.path_id) { router.push('/'); return }
      setPathTitle(res.data.title)
      setStages(res.data.stages)
    } catch (e: unknown) {
      if (e instanceof Error && e.message === 'SESSION_EXPIRED') {
        router.push('/')
      }
    }
  }

  async function openStage(stage: Stage) {
    if (stage.status === 'locked') return
    setActiveStage(stage)
    setMessages([])
    setCode('')
    setNoteContent('')
    setDiagramUrl('')
    setStageComplete(false)
    setCurrentNode('intro')
    setNodeStatus('running')
    setArtifactStatus('none')
    setArtifactType('CODE')
    setArtifacts([])
    setRubricResult(null)
    setShowPassCelebration(false)
    setLoading(true)
    try {
      type StartResp = {
        data: {
          session_id: string; content?: string; messages?: Message[];
          awaits_input?: boolean; current_node?: string;
          node_status?: string; awaits_artifact?: boolean; artifact_type?: string;
          rubric_passed?: boolean; rubric_score?: number;
          rubric_feedback?: string; rubric_hints?: string[]
        }
      }
      const res = await apiFetch<StartResp>(`/stage/${stage.id}/start`, { method: 'POST' })
      const d = res.data
      setSessionId(d.session_id)
      setStored(STORAGE_KEYS.SESSION_ID, d.session_id)
      if (d.messages) {
        setMessages(d.messages)
      } else if (d.content) {
        setMessages([{ role: 'assistant', content: d.content }])
      }
      setAwaitsInput(d.awaits_input ?? true)
      setCurrentNode(d.current_node ?? 'intro')
      setNodeStatus(d.node_status ?? 'running')
      setArtifactType((d.artifact_type ?? 'CODE') as ArtifactType)
      // 开始时如果是 task 节点且有产出类型，自动展开产出面板
      if ((d.current_node ?? 'intro') === 'task' && (d.artifact_type ?? 'CODE') !== 'NONE') {
        setShowCodePanel(true)
      }
      // 保存 rubric 评审结果（仅 REVIEW 节点返回）
      if (d.rubric_passed !== undefined) {
        setRubricResult({
          passed: d.rubric_passed,
          score: d.rubric_score ?? 0,
          feedback: d.rubric_feedback ?? '',
          hints: d.rubric_hints ?? [],
        })
      }
      if ((d.current_node ?? '') === 'complete') setStageComplete(true)
      // 加载已有 artifact（如果是恢复的 session）
      if (d.session_id) {
        loadArtifacts(d.session_id)
      }
    } catch (e: unknown) {
      if (e instanceof Error && e.message === 'SESSION_EXPIRED') router.push('/')
    } finally {
      setLoading(false)
    }
  }

  async function sendInput() {
    if (!userInput.trim() && !code.trim()) return
    const msg = userInput || '[提交代码]'
    lastUserInputRef.current = msg
    setMessages(prev => [...prev, { role: 'user', content: msg }])
    setUserInput('')
    setLoading(true)
    try {
      type AdvanceResp = {
        data: {
          content: string; current_node: string; node_status: string;
          awaits_artifact: boolean; stage_complete: boolean; awaits_input: boolean;
          artifact_type?: string;
          rubric_passed?: boolean; rubric_score?: number; rubric_feedback?: string; rubric_hints?: string[]
        }
      }
      const res = await apiFetch<AdvanceResp>('/session/advance', {
        method: 'POST',
        body: JSON.stringify({
          sessionId,
          userInput: userInput || '提交作品',
          code: artifactType === 'CODE' ? (code || undefined) : undefined,
        }),
      })
      const d = res.data
      setMessages(prev => [...prev, { role: 'assistant', content: d.content }])
      setAwaitsInput(d.awaits_input ?? true)
      setStageComplete(d.stage_complete ?? false)
      setCurrentNode(d.current_node ?? 'intro')
      setNodeStatus(d.node_status ?? 'running')
      if (d.artifact_type) setArtifactType(d.artifact_type as ArtifactType)
      // Rubric 评审结果（REVIEW 节点返回）：直接设置状态，不依赖 loadArtifacts
      if (d.rubric_passed !== undefined) {
        setRubricResult({
          passed: d.rubric_passed,
          score: d.rubric_score ?? 0,
          feedback: d.rubric_feedback ?? '',
          hints: d.rubric_hints ?? [],
        })
        setArtifactStatus(d.rubric_passed ? 'passed' : 'needs_revision')
      }
      // 每次 advance 后刷新 artifact 历史（状态可能被 REVIEW 节点更新）
      if (sessionId) {
        await loadArtifacts(sessionId)
      }
      if (d.stage_complete) {
        await loadPath()
      }
    } catch (e: unknown) {
      if (e instanceof Error && e.message === 'SESSION_EXPIRED') router.push('/')
    } finally {
      setLoading(false)
    }
  }

  async function submitArtifact() {
    const content = artifactType === 'CODE' ? code
      : artifactType === 'DIAGRAM' ? diagramUrl
      : noteContent  // NOTE / ESSAY / PROOF 都用 noteContent
    if (!content.trim() || !sessionId) return
    setArtifactSubmitting(true)
    try {
      type ArtifactResp = { data: { id: string; type: string; status: string; node_key: string } }
      await apiFetch<ArtifactResp>('/artifact', {
        method: 'POST',
        body: JSON.stringify({ sessionId, type: artifactType, content }),
      })
      setArtifactStatus('submitted')
      const typeLabel = artifactType === 'CODE' ? '代码'
        : artifactType === 'DIAGRAM' ? '图表链接'
        : '笔记'
      setMessages(prev => [...prev, {
        role: 'assistant',
        content: `✅ ${typeLabel}已收到！请发送一条消息（如："开始评审"），AI 将立刻对你的${typeLabel}进行 Rubric 评审。`,
      }])
    } catch (e: unknown) {
      if (e instanceof Error && e.message === 'SESSION_EXPIRED') router.push('/')
    } finally {
      setArtifactSubmitting(false)
    }
  }

  async function loadArtifacts(sid: string) {
    try {
      type ArtifactsResp = { data: ArtifactRecord[] }
      const res = await apiFetch<ArtifactsResp>(`/session/${sid}/artifacts`)
      const list = res.data ?? []
      setArtifacts(list)
      // 从最新记录推导顶部状态徽章
      if (list.length > 0) {
        const latest = list[0]
        if (latest.status === 'passed') setArtifactStatus('passed')
        else if (latest.status === 'needs_revision') setArtifactStatus('needs_revision')
        else setArtifactStatus('submitted')
      } else {
        setArtifactStatus('none')
      }
    } catch {
      // ignore
    }
  }

  async function regenerateLast() {
    if (!sessionId || loading) return
    setLoading(true)
    try {
      type RegenResp = { data: { content: string } }
      const res = await apiFetch<RegenResp>('/session/regenerateLast', {
        method: 'POST',
        body: JSON.stringify({ sessionId }),
      })
      // 替换消息列表中最后一条 assistant 消息
      setMessages(prev => {
        const lastAssistantIdx = prev.map((m, i) => m.role === 'assistant' ? i : -1)
          .filter(i => i >= 0).pop()
        if (lastAssistantIdx !== undefined) {
          const updated = [...prev]
          updated[lastAssistantIdx] = { role: 'assistant', content: res.data.content }
          return updated
        }
        return [...prev, { role: 'assistant', content: res.data.content }]
      })
    } catch (e: unknown) {
      if (e instanceof Error && e.message === 'SESSION_EXPIRED') router.push('/')
    } finally {
      setLoading(false)
    }
  }

  function exportChat() {
    if (!activeStage || messages.length === 0) return
    const lines: string[] = [
      `# ${pathTitle} — ${activeStage.title}`,
      `导出时间：${new Date().toLocaleString('zh-CN')}`,
      '',
    ]
    messages.forEach(m => {
      lines.push(m.role === 'user' ? '**你：**' : '**AI 导师：**')
      lines.push(m.content)
      lines.push('')
    })
    const blob = new Blob([lines.join('\n')], { type: 'text/markdown;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${activeStage.title}-聊天记录.md`
    a.click()
    URL.revokeObjectURL(url)
  }

  async function askHermes() {
    if (!userInput.trim()) return
    const msg = userInput
    setMessages(prev => [...prev, { role: 'user', content: msg }])
    setUserInput('')
    setLoading(true)
    try {
      type ChatResp = { data: { response: string } }
      const res = await apiFetch<ChatResp>('/chat', {
        method: 'POST',
        body: JSON.stringify({ sessionId, message: msg }),
      })
      setMessages(prev => [...prev, { role: 'assistant', content: res.data.response }])
    } catch (e: unknown) {
      if (e instanceof Error && e.message === 'SESSION_EXPIRED') router.push('/')
    } finally {
      setLoading(false)
    }
  }

  async function handleLogout() {
    const sid = getStored(STORAGE_KEYS.SESSION_ID)
    try {
      if (sid) {
        await apiFetch('/auth/logout', {
          method: 'POST',
          body: JSON.stringify({ sessionId: sid }),
        })
      }
    } catch {
      // ignore logout errors
    } finally {
      clearAuth()
      router.push('/')
    }
  }

  return (
    <div className="flex h-screen overflow-hidden t-bg">
      {/* Sidebar */}
      <aside className={`${sidebarOpen ? 'w-72' : 'w-0'} transition-all duration-300 overflow-hidden t-panel border-r t-border flex flex-col shadow-sm`}>
        <div className="p-4 border-b t-border-sub">
          <div className="t-accent-text font-bold text-sm truncate">{pathTitle}</div>
          <div className="t-faint text-xs mt-1">学习路径</div>
        </div>
        <div className="flex-1 overflow-y-auto p-3 space-y-1.5">
          {stages.map(stage => (
            <button key={stage.id} onClick={() => openStage(stage)}
              className={`w-full text-left px-3 py-3 rounded-xl border transition-all ${
                activeStage?.id === stage.id ? 't-stage-active shadow-sm' :
                stage.status === 'completed' ? 'border-emerald-200 bg-emerald-50/50 hover:border-emerald-300' :
                stage.status === 'active' ? 't-border t-panel cursor-pointer' :
                't-border-sub opacity-40 cursor-not-allowed t-bg'
              }`}>
              <div className="flex items-center gap-2">
                <span className={`text-xs font-bold ${
                  stage.status === 'completed' ? 'text-emerald-500' :
                  activeStage?.id === stage.id ? 't-accent-text' : 't-faint'
                }`}>
                  {stage.status === 'completed' ? '✓' : stage.status === 'active' ? '▶' : '○'}
                </span>
                <span className={`text-sm font-medium ${
                  activeStage?.id === stage.id ? 't-accent-text' :
                  stage.status === 'completed' ? 'text-emerald-700' : 't-text'
                }`}>{stage.title}</span>
              </div>
              <p className="text-xs t-faint mt-1 ml-5 line-clamp-2">{stage.goal}</p>
            </button>
          ))}
        </div>
        <div className="p-3 border-t t-border-sub space-y-2">
          {stages.length > 0 && (
            <div>
              <div className="flex items-center justify-between text-xs mb-1.5">
                <span className="t-faint">整体进度</span>
                <span className="t-accent-text font-semibold">{completedCount} / {stages.length}</span>
              </div>
              <div className="h-1.5 rounded-full overflow-hidden bg-gray-100">
                <div className="h-full t-accent-bg rounded-full transition-all duration-500"
                  style={{ width: `${stages.length ? Math.round((completedCount / stages.length) * 100) : 0}%` }} />
              </div>
            </div>
          )}
          <button onClick={handleLogout}
            className="w-full text-xs t-faint hover:text-red-500 py-2 transition-colors">
            退出登录
          </button>
        </div>
      </aside>

      {/* Main */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* Topbar */}
        <header className="flex items-center gap-3 px-4 py-3 border-b t-border t-panel shadow-sm">
          <button onClick={() => setSidebarOpen(o => !o)}
            className="t-toggle-btn text-lg">☰</button>
          <div className="text-sm t-muted">
            {activeStage ? (
              <span>
                <span className="t-faint">阶段 {activeStage.index + 1} / {stages.length}</span>
                <span className="mx-2 t-faint">|</span>
                <span className="t-text font-medium">{activeStage.title}</span>
                <span className="mx-2 t-faint">·</span>
                <NodeBadge node={currentNode} status={nodeStatus} />
              </span>
            ) : (
              <span className="t-faint">选择一个阶段开始学习</span>
            )}
          </div>
          <div className="ml-auto flex items-center gap-2">
            <div ref={themePickerRef} className="relative">
              <button
                onClick={() => setShowThemePicker(p => !p)}
                className={`px-2.5 py-1.5 rounded-lg border t-border t-panel text-sm transition-colors ${showThemePicker ? 't-accent-text' : 't-faint'}`}
                title="切换主题"
              >
                {THEMES.find(t => t.id === theme)?.emoji ?? '🎨'}
              </button>
              {showThemePicker && (
                <div className="absolute right-0 top-full mt-1 t-panel border t-border rounded-xl shadow-lg z-50 p-2 grid grid-cols-2 gap-1" style={{ minWidth: '140px' }}>
                  {THEMES.map(t => (
                    <button
                      key={t.id}
                      onClick={() => { setTheme(t.id); setShowThemePicker(false) }}
                      className={`flex items-center gap-1.5 px-2 py-1.5 rounded-lg text-xs transition-colors ${
                        theme === t.id ? 't-stage-active t-accent-text font-semibold' : 't-faint hover:t-text'
                      }`}
                    >
                      <span>{t.emoji}</span>
                      <span>{t.label}</span>
                    </button>
                  ))}
                </div>
              )}
            </div>
            {activeStage && (
              <>
                <button onClick={exportChat}
                  className="text-xs px-3 py-1.5 rounded-lg border t-border t-panel t-muted transition-all">
                  ⬇ 导出记录
                </button>
                {hasArtifactPanel && (
                  <button onClick={() => setShowCodePanel(p => !p)}
                    className={`text-xs px-3 py-1.5 rounded-lg border transition-all ${
                      showCodePanel ? 't-stage-active t-accent-text' : 't-border t-panel t-muted'
                    }`}>
                    {showCodePanel ? '隐藏产出区' : artifactType === 'CODE' ? '打开代码区' : artifactType === 'DIAGRAM' ? '打开链接区' : '打开笔记区'}
                  </button>
                )}
              </>
            )}
          </div>
        </header>

        {/* Node progress strip */}
        {activeStage && !stageComplete && (
          <div className="flex items-center justify-center gap-1 px-4 py-2 border-b t-border t-panel text-xs overflow-x-auto flex-shrink-0">
            {NODE_SEQ.map((n, i) => {
              const nodeIdx = NODE_SEQ.indexOf(currentNode)
              const isActive = n === currentNode
              const isPast = i < nodeIdx
              const info = NODE_LABELS[n] ?? { label: n, color: 't-faint' }
              return (
                <div key={n} className="flex items-center">
                  <div className={`flex items-center gap-1 px-2 py-0.5 rounded-full whitespace-nowrap transition-all ${
                    isActive ? `font-semibold ${info.color}` :
                    isPast ? 'text-emerald-600 bg-emerald-50 border border-emerald-100' :
                    't-faint'
                  }`}>
                    <span>{isPast ? '✓' : isActive ? '●' : '○'}</span>
                    <span>{info.label}</span>
                  </div>
                  {i < NODE_SEQ.length - 1 && (
                    <span className="t-faint mx-1 opacity-40">→</span>
                  )}
                </div>
              )
            })}
          </div>
        )}
        {/* Content area */}
        {!activeStage ? (
          <div className="flex-1 overflow-y-auto p-6">
            <div className="max-w-lg mx-auto space-y-5">
              <div>
                <h2 className="text-xl font-semibold t-text">{pathTitle || '我的学习路径'}</h2>
                <p className="t-faint text-sm mt-1">
                  {completedCount > 0
                    ? `已完成 ${completedCount} / ${stages.length} 个阶段`
                    : '点击左侧阶段开始学习'}
                </p>
              </div>
              {stages.length > 0 && (
                <div>
                  <div className="flex items-center justify-between text-xs t-faint mb-1.5">
                    <span>整体进度</span>
                    <span className="t-accent-text font-medium">
                      {Math.round((completedCount / stages.length) * 100)}%
                    </span>
                  </div>
                  <div className="h-2 rounded-full overflow-hidden bg-gray-100 border t-border-sub">
                    <div className="h-full t-accent-bg rounded-full transition-all duration-700"
                      style={{ width: `${Math.round((completedCount / stages.length) * 100)}%` }} />
                  </div>
                </div>
              )}
              {(() => {
                const nextStage = stages.find(s => s.status === 'active')
                if (!nextStage) return null
                return (
                  <div className="t-panel border t-border rounded-2xl p-4 space-y-2 shadow-sm">
                    <p className="text-xs t-faint font-medium uppercase tracking-wide">今日目标</p>
                    <p className="t-text font-semibold">{nextStage.title}</p>
                    <p className="t-muted text-sm leading-relaxed">{nextStage.goal}</p>
                    <button onClick={() => openStage(nextStage)}
                      className="mt-2 t-btn-primary text-sm font-semibold px-4 py-2.5 rounded-xl shadow-sm w-full">
                      {completedCount === 0 ? '开始学习 →' : '继续学习 →'}
                    </button>
                  </div>
                )
              })()}
              {completedCount > 0 && (
                <div className="t-panel border t-border rounded-2xl p-4 space-y-3 shadow-sm">
                  <p className="text-xs t-faint font-medium uppercase tracking-wide">已完成阶段</p>
                  <div className="space-y-2">
                    {stages.filter(s => s.status === 'completed').map(s => (
                      <button key={s.id} onClick={() => openStage(s)}
                        className="w-full flex items-center gap-2 text-sm text-left px-3 py-2 rounded-xl border border-emerald-200 bg-emerald-50/50 hover:border-emerald-300 transition-all">
                        <span className="text-emerald-500 flex-shrink-0">✓</span>
                        <span className="t-text font-medium flex-1">{s.title}</span>
                        <span className="text-xs t-faint flex-shrink-0">回看 →</span>
                      </button>
                    ))}
                  </div>
                </div>
              )}
              {stages.length > 0 && !stages.find(s => s.status === 'active') && completedCount === stages.length && (
                <div className="t-panel border t-border rounded-2xl p-4 text-center space-y-2 shadow-sm">
                  <div className="text-3xl">🎓</div>
                  <p className="t-text font-semibold">全部阶段已完成！</p>
                  <p className="t-faint text-sm">恭喜你完成了整条学习路径</p>
                </div>
              )}
            </div>
          </div>
        ) : (
          <div className="flex-1 flex overflow-hidden">
            {/* Chat panel */}
            <div className="flex-1 flex flex-col overflow-hidden">
              {/* Messages */}
              <div className="flex-1 overflow-y-auto p-4 space-y-4 t-bg">
                {(() => {
                  const lastAssistantIdx = messages.map((m, i) => m.role === 'assistant' ? i : -1)
                    .filter(i => i >= 0).pop() ?? -1
                  return messages.map((m, i) => (
                  <div key={i} className={`flex ${m.role === 'user' ? 'justify-end' : 'justify-start'}`}>
                    {m.role === 'assistant' && (
                      <div className="w-8 h-8 rounded-full t-accent-bg border-2 t-border flex items-center justify-center text-sm mr-2 mt-1 flex-shrink-0 shadow-sm">
                        🧠
                      </div>
                    )}
                    <div className="flex flex-col gap-1 max-w-[85%]">
                      <div className={`group relative rounded-2xl px-4 py-3 ${
                        m.role === 'user'
                          ? 't-user-bubble text-white text-sm shadow-sm'
                          : 't-panel border t-border shadow-sm'
                      }`}>
                        {m.role === 'assistant' ? (
                          <div className="prose-theme text-sm">
                            <ReactMarkdown>{m.content}</ReactMarkdown>
                          </div>
                        ) : (
                          <p className="whitespace-pre-wrap">{m.content}</p>
                        )}
                        <CopyButton text={m.content} />
                      </div>
                      {m.role === 'assistant' && i === lastAssistantIdx && !loading && !stageComplete && (
                        <button onClick={regenerateLast}
                          className="self-start text-xs t-regen-btn px-1">
                          ↺ 重新生成
                        </button>
                      )}
                    </div>
                  </div>
                  ))
                })()}
                {loading && (
                  <div className="flex justify-start">
                    <div className="w-8 h-8 rounded-full t-accent-bg border-2 t-border flex items-center justify-center text-sm mr-2 mt-1 flex-shrink-0 shadow-sm">🧠</div>
                    <div className="t-panel border t-border rounded-2xl px-4 py-3 shadow-sm">
                      <div className="flex gap-1.5">
                        {[0,1,2].map(i => <div key={i} className="w-2 h-2 t-accent-dot rounded-full animate-bounce" style={{animationDelay: `${i*0.15}s`}} />)}
                      </div>
                    </div>
                  </div>
                )}
                {/* Rubric 结构化评审结果卡片 */}
                {rubricResult && !stageComplete && (
                  <div className={`mx-2 rounded-2xl border p-4 shadow-sm ${
                    rubricResult.passed
                      ? 'border-emerald-200 bg-emerald-50'
                      : 'border-red-200 bg-red-50'
                  }`}>
                    <div className="flex items-center justify-between mb-2">
                      <span className={`text-sm font-semibold ${
                        rubricResult.passed ? 'text-emerald-700' : 'text-red-700'
                      }`}>
                        {rubricResult.passed ? '✅ Rubric 评审通过' : '❌ Rubric 评审未通过'}
                      </span>
                      <span className={`text-xs font-mono px-2.5 py-0.5 rounded-full border font-semibold ${
                        rubricResult.score >= 80
                          ? 'text-emerald-700 border-emerald-300 bg-emerald-100'
                          : rubricResult.score >= 60
                          ? 'text-amber-700 border-amber-300 bg-amber-100'
                          : 'text-red-700 border-red-300 bg-red-100'
                      }`}>
                        {rubricResult.score} 分
                      </span>
                    </div>
                    {rubricResult.feedback && (
                      <p className="text-sm text-gray-600 mb-2">{rubricResult.feedback}</p>
                    )}
                    {rubricResult.hints.length > 0 && (
                      <ul className="space-y-1">
                        {rubricResult.hints.map((h, i) => (
                          <li key={i} className="text-xs text-amber-700 flex gap-1.5">
                            <span className="flex-shrink-0">▸</span>
                            <span>{h}</span>
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                )}
                {showPassCelebration && (                  <div className="flex justify-center">
                    <div className="relative bg-gradient-to-br from-emerald-50 via-teal-50 to-sky-50 border-2 border-emerald-300 rounded-2xl px-8 py-5 text-center shadow-lg shadow-emerald-100">
                      <div className="absolute -top-3 -right-3 text-2xl animate-spin" style={{animationDuration:'3s'}}>⭐</div>
                      <div className="absolute -bottom-2 -left-2 text-xl">✨</div>
                      <div className="text-4xl mb-2">🏆</div>
                      <p className="text-emerald-700 font-bold text-lg tracking-wide">代码评审通过！</p>
                      <p className="text-emerald-500 text-sm mt-1">你的作品已获 AI 认可，继续前进吧！</p>
                    </div>
                  </div>
                )}
                {stageComplete && (
                  <div className="flex justify-center">
                    <div className="bg-gradient-to-br from-indigo-50 via-purple-50 to-sky-50 border border-indigo-200 rounded-2xl px-8 py-6 text-center shadow-md shadow-indigo-100">
                      <div className="text-4xl mb-2">🎓</div>
                      <p className="text-indigo-700 font-bold text-xl">阶段完成！</p>
                      <p className="text-gray-500 text-sm mt-2">从左侧选择下一阶段继续</p>
                      <div className="mt-3 pt-3 border-t border-indigo-100 flex items-center justify-center gap-3 text-xs text-gray-400">
                        <span>💬 聊天记录已保存</span>
                        <span>·</span>
                        <span>📝 代码记录可回放</span>
                      </div>
                    </div>
                  </div>
                )}
                {stageComplete && artifacts.length > 0 && (
                  <div className="mx-2 space-y-2">
                    <p className="text-xs t-faint font-medium px-1">📋 本阶段产出记录</p>
                    {artifacts.map(a => (
                      <div key={a.id} className="t-panel border t-border rounded-xl p-3 space-y-1.5">
                        <div className="flex items-center justify-between text-xs">
                          <span className="font-mono t-faint">{a.type} · {NODE_LABELS[a.node_key]?.label ?? a.node_key}</span>
                          <span className={`font-medium ${
                            a.status === 'passed' ? 'text-emerald-600' :
                            a.status === 'needs_revision' ? 'text-red-500' : 'text-amber-600'
                          }`}>
                            {a.status === 'passed' ? '✓ 通过' : a.status === 'needs_revision' ? '✗ 需修改' : '● 待评审'}
                          </span>
                        </div>
                        <pre className="text-xs t-muted overflow-hidden whitespace-pre-wrap line-clamp-4 leading-relaxed">
                          {a.content.slice(0, 300)}{a.content.length > 300 ? '…' : ''}
                        </pre>
                        {a.type === 'CODE' && (
                          <button onClick={() => { setCode(a.content); setShowCodePanel(true) }}
                            className="text-xs t-accent-text font-medium mt-0.5">
                            👁 查看完整代码
                          </button>
                        )}
                        {(a.type === 'NOTE' || a.type === 'ESSAY' || a.type === 'PROOF') && (
                          <button onClick={() => { setNoteContent(a.content); setShowCodePanel(true) }}
                            className="text-xs t-accent-text font-medium mt-0.5">
                            👁 查看完整笔记
                          </button>
                        )}
                      </div>
                    ))}
                  </div>
                )}
                <div ref={messagesEndRef} />
              </div>

              {/* Input area */}
              {!stageComplete && (
                <div className="border-t t-border p-4 space-y-3 t-panel">
                  <textarea
                    value={userInput}
                    onChange={e => setUserInput(e.target.value)}
                    onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendInput() } }}
                    placeholder={awaitsInput ? "输入你的回答（Shift+Enter 换行）..." : "AI 正在讲解中..."}
                    disabled={!awaitsInput || loading}
                    rows={3}
                    className="w-full t-input-field border rounded-xl px-4 py-3 text-sm resize-none disabled:opacity-40 transition-all"
                  />
                  <div className="flex gap-2">
                    <button onClick={sendInput} disabled={(!userInput.trim() && !code.trim()) || loading}
                      className="flex-1 t-btn-primary text-sm font-semibold py-2.5 rounded-xl shadow-sm">
                      提交
                    </button>
                    <button onClick={askHermes} disabled={!userInput.trim() || loading}
                      className="px-4 t-btn-secondary text-sm py-2.5 rounded-xl border shadow-sm">
                      问 AI
                    </button>
                  </div>
                  <p className="text-xs t-faint">
                    "提交" 推进学习进度 · "问 AI" 自由提问不影响进度
                  </p>
                  {NODE_HINTS[currentNode] && currentNode !== 'task' && (
                    <p className="text-xs text-sky-600 font-medium">
                      {NODE_HINTS[currentNode]}
                    </p>
                  )}
                  {currentNode === 'task' && (
                    <p className="text-xs text-sky-600 font-medium">
                      {getTaskHint(artifactType)}
                    </p>
                  )}
                  {awaitsArtifact && (
                    <p className="text-xs text-amber-600 font-medium bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
                      ⚠ 下一步需要先提交作品。请在右侧{artifactType === 'CODE' ? '代码区' : artifactType === 'DIAGRAM' ? '链接区' : '笔记区'}完成产出，点击「提交作品」后再发送消息推进。
                    </p>
                  )}
                </div>
              )}
            </div>

            {/* Artifact panel — CODE / NOTE / DIAGRAM / ESSAY / PROOF */}
            {showCodePanel && hasArtifactPanel && (
              <div className="w-96 border-l t-border flex flex-col t-panel shadow-sm">
                {/* Panel header */}
                <div className="px-4 py-3 border-b t-border-sub flex items-center justify-between t-bg">
                  <span className="text-sm t-muted font-medium">
                    {artifactType === 'CODE' ? '代码编辑区'
                      : artifactType === 'DIAGRAM' ? '图表链接'
                      : '笔记区'}
                  </span>
                  <span className="text-xs t-faint font-mono uppercase">{artifactType}</span>
                </div>

                {/* Input area — by type */}
                {artifactType === 'CODE' ? (
                  <textarea
                    value={code}
                    onChange={e => setCode(e.target.value)}
                    placeholder="// 在这里写代码，完成后点击「提交作品」"
                    className="flex-1 t-input-field text-sm font-mono p-4 resize-none border-b t-border-sub"
                    spellCheck={false}
                  />
                ) : artifactType === 'DIAGRAM' ? (
                  <div className="flex-1 p-4 border-b t-border-sub space-y-3">
                    <p className="text-xs t-faint">粘贴图表、思维导图或架构图的链接（支持 Mermaid Live / draw.io / Excalidraw / 图床 URL）</p>
                    <input
                      type="url"
                      value={diagramUrl}
                      onChange={e => setDiagramUrl(e.target.value)}
                      placeholder="https://..."
                      className="w-full t-input-field border rounded-xl px-3 py-2 text-sm"
                    />
                    {diagramUrl && (
                      <a href={diagramUrl} target="_blank" rel="noopener noreferrer"
                        className="text-xs t-accent-text block truncate">
                        🔗 预览链接 →
                      </a>
                    )}
                  </div>
                ) : (
                  /* NOTE / ESSAY / PROOF */
                  <textarea
                    value={noteContent}
                    onChange={e => setNoteContent(e.target.value)}
                    placeholder={artifactType === 'ESSAY'
                      ? '在这里写论述（支持 Markdown 格式）...'
                      : artifactType === 'PROOF'
                      ? '在这里写证明或推导过程...'
                      : '在这里写笔记或回答（支持 Markdown 格式）...'}
                    className="flex-1 t-input-field text-sm p-4 resize-none border-b t-border-sub leading-relaxed"
                    spellCheck={false}
                  />
                )}

                {/* Review progress */}
                {artifactStatus !== 'none' && !stageComplete && (
                  <div className="px-4 py-3 border-b t-border-sub t-bg">
                    <p className="text-xs t-muted font-medium mb-2">评审进度</p>
                    <div className="flex items-center justify-between text-xs">
                      <div className="flex flex-col items-center gap-0.5">
                        <span className="text-emerald-500 font-bold">✓</span>
                        <span className="text-emerald-600">已提交</span>
                      </div>
                      <div className="flex-1 h-0.5 mx-2 bg-emerald-300 rounded" />
                      <div className="flex flex-col items-center gap-0.5">
                        <span className={`font-bold ${artifactStatus === 'submitted' ? 'text-amber-500 animate-pulse' : 'text-emerald-500'}`}>
                          {artifactStatus === 'submitted' ? '●' : '✓'}
                        </span>
                        <span className={artifactStatus === 'submitted' ? 'text-amber-600' : 'text-emerald-600'}>
                          {artifactStatus === 'submitted' ? 'AI 评审中' : '已评审'}
                        </span>
                      </div>
                      <div className={`flex-1 h-0.5 mx-2 rounded ${artifactStatus === 'passed' || artifactStatus === 'needs_revision' ? 'bg-emerald-300' : 'bg-gray-200'}`} />
                      <div className="flex flex-col items-center gap-0.5">
                        <span className={`font-bold ${artifactStatus === 'passed' ? 'text-emerald-500' : artifactStatus === 'needs_revision' ? 'text-red-500' : 'text-gray-400'}`}>
                          {artifactStatus === 'passed' ? '✓' : artifactStatus === 'needs_revision' ? '✗' : '○'}
                        </span>
                        <span className={artifactStatus === 'passed' ? 'text-emerald-600' : artifactStatus === 'needs_revision' ? 'text-red-500' : 'text-gray-400'}>
                          {artifactStatus === 'passed' ? '已通过' : artifactStatus === 'needs_revision' ? '需修改' : '待结果'}
                        </span>
                      </div>
                    </div>
                    {artifactStatus === 'submitted' && (
                      <p className="text-xs text-amber-600 mt-2 bg-amber-50 border border-amber-100 rounded px-2 py-1">▸ 发送消息让 AI 开始评审你的作品</p>
                    )}
                  </div>
                )}

                {/* Submit button */}
                <div className="p-3 space-y-2 t-panel">
                  <button
                    onClick={submitArtifact}
                    disabled={
                      (artifactType === 'CODE' ? !code.trim() : artifactType === 'DIAGRAM' ? !diagramUrl.trim() : !noteContent.trim())
                      || artifactSubmitting || loading
                      || artifactStatus === 'passed' || artifactStatus === 'submitted'
                    }
                    className="w-full t-btn-primary text-sm font-semibold py-2.5 rounded-xl shadow-sm">
                    {artifactSubmitting ? '提交中…'
                      : artifactStatus === 'passed' ? '✓ 已通过'
                      : artifactStatus === 'submitted' ? '⏳ 评审中…'
                      : artifactType === 'CODE' ? '提交作品'
                      : artifactType === 'DIAGRAM' ? '提交链接'
                      : '提交笔记'}
                  </button>
                  {artifactStatus !== 'none' && (
                    <p className={`text-xs font-medium text-center ${ARTIFACT_STATUS_LABELS[artifactStatus].color}`}>
                      {ARTIFACT_STATUS_LABELS[artifactStatus].label}
                    </p>
                  )}
                </div>

                {/* 已提交历史 */}
                {artifacts.length > 0 && (
                  <div className="border-t t-border-sub p-3 space-y-2 overflow-y-auto max-h-48">
                    <p className="text-xs t-faint font-medium">提交历史</p>
                    {artifacts.map(a => (
                      <div key={a.id} className="text-xs rounded-xl border t-border p-2.5 space-y-1 t-bg">
                        <div className="flex items-center justify-between">
                          <span className="font-mono t-faint">{a.type} · {a.node_key}</span>
                          <span className={`font-medium ${
                            a.status === 'passed' ? 'text-emerald-600' :
                            a.status === 'needs_revision' ? 'text-red-500' : 'text-amber-600'
                          }`}>
                            {a.status === 'passed' ? '✓ 通过' : a.status === 'needs_revision' ? '✗ 需修改' : '● 待评审'}
                          </span>
                        </div>
                        <pre className="t-muted overflow-hidden line-clamp-3 whitespace-pre-wrap">{a.content.slice(0, 200)}{a.content.length > 200 ? '…' : ''}</pre>
                        {a.type === 'CODE' && (
                          <button onClick={() => { setCode(a.content); setShowCodePanel(true) }}
                            className="text-xs t-accent-text transition-colors mt-1 block font-medium">
                            {stageComplete ? '👁 查看代码' : '↩ 加载到编辑器'}
                          </button>
                        )}
                        {(a.type === 'NOTE' || a.type === 'ESSAY' || a.type === 'PROOF') && (
                          <button onClick={() => { setNoteContent(a.content); setShowCodePanel(true) }}
                            className="text-xs t-accent-text transition-colors mt-1 block font-medium">
                            ↩ 加载到笔记区
                          </button>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}