'use client'
import { useState, useEffect, useRef } from 'react'
import { useRouter } from 'next/navigation'
import ReactMarkdown from 'react-markdown'
import { STORAGE_KEYS, getStored, setStored, apiFetch, clearAuth, isLoggedIn } from '@/lib/api'

type Stage = { id: string; index: number; title: string; goal: string; status: string }
type Message = { role: 'user' | 'assistant'; content: string }
type ArtifactStatus = 'none' | 'submitted' | 'passed' | 'needs_revision'
type ArtifactRecord = { id: string; type: string; content: string; status: string; node_key: string; created_at: string }

const NODE_LABELS: Record<string, { label: string; color: string }> = {
  intro:    { label: '引入',  color: 'text-blue-400 border-blue-700' },
  concept:  { label: '概念',  color: 'text-yellow-400 border-yellow-700' },
  practice: { label: '练习',  color: 'text-orange-400 border-orange-700' },
  task:     { label: '任务',  color: 'text-red-400 border-red-700' },
  review:   { label: '评审',  color: 'text-purple-400 border-purple-700' },
  retro:    { label: '复盘',  color: 'text-emerald-400 border-emerald-700' },
  complete: { label: '完成',  color: 'text-emerald-300 border-emerald-600' },
}

const ARTIFACT_STATUS_LABELS: Record<ArtifactStatus, { label: string; color: string }> = {
  none:            { label: '',           color: '' },
  submitted:       { label: '✓ 已提交 · 等待评审', color: 'text-amber-400' },
  passed:          { label: '✓ 评审通过',           color: 'text-emerald-400' },
  needs_revision:  { label: '✗ 需要修改',            color: 'text-red-400' },
}

function NodeBadge({ node, status }: { node: string; status: string }) {
  const info = NODE_LABELS[node] ?? { label: node, color: 'text-gray-400 border-gray-700' }
  const statusDot = status === 'failed' ? '✗' : status === 'passed' ? '✓' : '●'
  const dotColor = status === 'failed' ? 'text-red-400' : status === 'passed' ? 'text-emerald-400' : 'text-gray-500'
  return (
    <span className={`inline-flex items-center gap-1 text-xs font-mono px-2 py-0.5 rounded border ${info.color}`}>
      <span className={dotColor}>{statusDot}</span>
      {info.label}
    </span>
  )
}

export default function LearnPage() {
  const router = useRouter()
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
  // 节点状态
  const [currentNode, setCurrentNode] = useState<string>('intro')
  const [nodeStatus, setNodeStatus] = useState<string>('running')
  const [awaitsArtifact, setAwaitsArtifact] = useState<boolean>(false)
  // Artifact 状态
  const [artifactStatus, setArtifactStatus] = useState<ArtifactStatus>('none')
  const [artifactSubmitting, setArtifactSubmitting] = useState(false)
  const [artifacts, setArtifacts] = useState<ArtifactRecord[]>([])
  const [showPassCelebration, setShowPassCelebration] = useState(false)
  const prevArtifactStatusRef = useRef<ArtifactStatus>('none')
  const messagesEndRef = useRef<HTMLDivElement>(null)

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
    setStageComplete(false)
    setCurrentNode('intro')
    setNodeStatus('running')
    setAwaitsArtifact(false)
    setArtifactStatus('none')
    setArtifacts([])
    setLoading(true)
    try {
      type StartResp = {
        data: {
          session_id: string; content?: string; messages?: Message[];
          awaits_input?: boolean; current_node?: string;
          node_status?: string; awaits_artifact?: boolean
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
      setAwaitsArtifact(d.awaits_artifact ?? false)
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
    setMessages(prev => [...prev, { role: 'user', content: msg }])
    setUserInput('')
    setLoading(true)
    try {
      type AdvanceResp = {
        data: {
          content: string; current_node: string; node_status: string;
          awaits_artifact: boolean; stage_complete: boolean; awaits_input: boolean
        }
      }
      const res = await apiFetch<AdvanceResp>('/session/advance', {
        method: 'POST',
        body: JSON.stringify({
          sessionId,
          userInput: userInput || '提交代码',
          code: code || undefined,
        }),
      })
      const d = res.data
      setMessages(prev => [...prev, { role: 'assistant', content: d.content }])
      setAwaitsInput(d.awaits_input ?? true)
      setStageComplete(d.stage_complete ?? false)
      setCurrentNode(d.current_node ?? 'intro')
      setNodeStatus(d.node_status ?? 'running')
      setAwaitsArtifact(d.awaits_artifact ?? false)
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
    if (!code.trim() || !sessionId) return
    setArtifactSubmitting(true)
    try {
      type ArtifactResp = { data: { id: string; type: string; status: string; node_key: string } }
      await apiFetch<ArtifactResp>('/artifact', {
        method: 'POST',
        body: JSON.stringify({ sessionId, type: 'CODE', content: code }),
      })
      setArtifactStatus('submitted')
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
    <div className="flex h-screen overflow-hidden">
      {/* Sidebar */}
      <aside className={`${sidebarOpen ? 'w-72' : 'w-0'} transition-all duration-300 overflow-hidden bg-gray-900 border-r border-gray-800 flex flex-col`}>
        <div className="p-4 border-b border-gray-800">
          <div className="text-emerald-400 font-bold text-sm truncate">{pathTitle}</div>
          <div className="text-gray-500 text-xs mt-1">学习路径</div>
        </div>
        <div className="flex-1 overflow-y-auto p-3 space-y-2">
          {stages.map(stage => (
            <button key={stage.id} onClick={() => openStage(stage)}
              className={`w-full text-left px-3 py-3 rounded-lg border transition-all ${
                activeStage?.id === stage.id ? 'border-emerald-500 bg-emerald-950' :
                stage.status === 'completed' ? 'border-emerald-900/40 bg-gray-800/50 hover:border-emerald-800' :
                stage.status === 'active' ? 'border-gray-600 hover:border-gray-500 bg-gray-800' :
                'border-gray-800 opacity-40 cursor-not-allowed'
              }`}>
              <div className="flex items-center gap-2">
                <span className="text-xs font-mono">
                  {stage.status === 'completed' ? '✓' : stage.status === 'active' ? '▶' : '○'}
                </span>
                <span className={`text-sm font-medium ${
                  activeStage?.id === stage.id ? 'text-emerald-300' :
                  stage.status === 'completed' ? 'text-emerald-700' : 'text-gray-300'
                }`}>{stage.title}</span>
              </div>
              <p className="text-xs text-gray-500 mt-1 ml-5 line-clamp-2">{stage.goal}</p>
            </button>
          ))}
        </div>
        <div className="p-3 border-t border-gray-800">
          <button onClick={handleLogout}
            className="w-full text-xs text-gray-600 hover:text-red-400 py-2 transition-colors">
            退出登录
          </button>
        </div>
      </aside>

      {/* Main */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* Topbar */}
        <header className="flex items-center gap-3 px-4 py-3 border-b border-gray-800 bg-gray-950">
          <button onClick={() => setSidebarOpen(o => !o)}
            className="text-gray-500 hover:text-gray-300 transition-colors text-lg">☰</button>
          <div className="text-sm text-gray-400">
            {activeStage ? (
              <span>
                <span className="text-gray-600">阶段 {activeStage.index + 1} / {stages.length}</span>
                <span className="mx-2 text-gray-700">|</span>
                <span className="text-gray-200">{activeStage.title}</span>
                <span className="mx-2 text-gray-700">·</span>
                <NodeBadge node={currentNode} status={nodeStatus} />
              </span>
            ) : (
              <span className="text-gray-600">选择一个阶段开始学习</span>
            )}
          </div>
          {activeStage && (
            <button onClick={() => setShowCodePanel(p => !p)}
              className={`ml-auto text-xs px-3 py-1.5 rounded border transition-all ${
                showCodePanel ? 'border-emerald-500 text-emerald-400 bg-emerald-950' : 'border-gray-700 text-gray-500 hover:border-gray-500'
              }`}>
              {showCodePanel ? '隐藏代码区' : '打开代码区'}
            </button>
          )}
        </header>

        {/* Content area */}
        {!activeStage ? (
          <div className="flex-1 flex items-center justify-center text-center p-8">
            <div>
              <div className="text-5xl mb-4">📚</div>
              <h2 className="text-xl font-semibold text-gray-300 mb-2">选择一个阶段开始学习</h2>
              <p className="text-gray-600 text-sm">从左侧选择当前激活的阶段，AI 导师会陪你完成整个学习过程。</p>
            </div>
          </div>
        ) : (
          <div className="flex-1 flex overflow-hidden">
            {/* Chat panel */}
            <div className="flex-1 flex flex-col overflow-hidden">
              {/* Messages */}
              <div className="flex-1 overflow-y-auto p-4 space-y-4">
                {messages.map((m, i) => (
                  <div key={i} className={`flex ${m.role === 'user' ? 'justify-end' : 'justify-start'}`}>
                    {m.role === 'assistant' && (
                      <div className="w-7 h-7 rounded-full bg-emerald-900 border border-emerald-700 flex items-center justify-center text-xs mr-2 mt-1 flex-shrink-0">
                        🧠
                      </div>
                    )}
                    <div className={`max-w-[85%] rounded-xl px-4 py-3 ${
                      m.role === 'user'
                        ? 'bg-gray-800 text-gray-200 text-sm'
                        : 'bg-gray-900 border border-gray-800'
                    }`}>
                      {m.role === 'assistant' ? (
                        <div className="prose-dark text-sm">
                          <ReactMarkdown>{m.content}</ReactMarkdown>
                        </div>
                      ) : (
                        <p className="whitespace-pre-wrap">{m.content}</p>
                      )}
                    </div>
                  </div>
                ))}
                {loading && (
                  <div className="flex justify-start">
                    <div className="w-7 h-7 rounded-full bg-emerald-900 border border-emerald-700 flex items-center justify-center text-xs mr-2 mt-1">🧠</div>
                    <div className="bg-gray-900 border border-gray-800 rounded-xl px-4 py-3">
                      <div className="flex gap-1.5">
                        {[0,1,2].map(i => <div key={i} className="w-2 h-2 bg-emerald-600 rounded-full animate-bounce" style={{animationDelay: `${i*0.15}s`}} />)}
                      </div>
                    </div>
                  </div>
                )}
                {showPassCelebration && (
                  <div className="flex justify-center">
                    <div className="relative bg-gradient-to-br from-emerald-950 via-emerald-900 to-teal-950 border-2 border-emerald-400 rounded-2xl px-8 py-5 text-center shadow-2xl shadow-emerald-900/60">
                      <div className="absolute -top-3 -right-3 text-2xl animate-spin" style={{animationDuration:'3s'}}>⭐</div>
                      <div className="absolute -bottom-2 -left-2 text-xl">✨</div>
                      <div className="text-4xl mb-2">🏆</div>
                      <p className="text-emerald-200 font-bold text-lg tracking-wide">代码评审通过！</p>
                      <p className="text-emerald-500 text-sm mt-1">你的作品已获 AI 认可，继续前进吧！</p>
                    </div>
                  </div>
                )}
                {stageComplete && (
                  <div className="flex justify-center">
                    <div className="bg-gradient-to-br from-emerald-950 via-emerald-900 to-teal-950 border border-emerald-500 rounded-2xl px-8 py-6 text-center shadow-lg shadow-emerald-900/40">
                      <div className="text-4xl mb-2">🎓</div>
                      <p className="text-emerald-200 font-bold text-xl">阶段完成！</p>
                      <p className="text-gray-400 text-sm mt-2">从左侧选择下一阶段继续</p>
                      <div className="mt-3 pt-3 border-t border-emerald-900 flex items-center justify-center gap-3 text-xs text-gray-500">
                        <span>💬 聊天记录已保存</span>
                        <span>·</span>
                        <span>📝 代码记录可回放</span>
                      </div>
                    </div>
                  </div>
                )}
                <div ref={messagesEndRef} />
              </div>

              {/* Input area */}
              {!stageComplete && (
                <div className="border-t border-gray-800 p-4 space-y-3">
                  <textarea
                    value={userInput}
                    onChange={e => setUserInput(e.target.value)}
                    onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendInput() } }}
                    placeholder={awaitsInput ? "输入你的回答（Shift+Enter 换行）..." : "AI 正在讲解中..."}
                    disabled={!awaitsInput || loading}
                    rows={3}
                    className="w-full bg-gray-900 border border-gray-700 rounded-lg px-4 py-3 text-sm text-gray-200 placeholder-gray-600 focus:outline-none focus:border-emerald-500 resize-none disabled:opacity-40 transition-colors"
                  />
                  <div className="flex gap-2">
                    <button onClick={sendInput} disabled={(!userInput.trim() && !code.trim()) || loading}
                      className="flex-1 bg-emerald-700 hover:bg-emerald-600 disabled:opacity-40 disabled:cursor-not-allowed text-white text-sm font-semibold py-2 rounded-lg transition-colors">
                      提交
                    </button>
                    <button onClick={askHermes} disabled={!userInput.trim() || loading}
                      className="px-4 bg-gray-800 hover:bg-gray-700 disabled:opacity-40 disabled:cursor-not-allowed text-gray-300 text-sm py-2 rounded-lg border border-gray-700 transition-colors">
                      问 AI
                    </button>
                  </div>
                  <p className="text-xs text-gray-600">
                    "提交" 推进学习进度 · "问 AI" 自由提问不影响进度
                  </p>
                  {awaitsArtifact && (
                    <p className="text-xs text-amber-500 font-medium">
                      ⚠ 此节点需要提交代码作品。请在右侧代码区写好代码后点击「提交作品」，再发送消息推进。
                    </p>
                  )}
                </div>
              )}
            </div>

            {/* Code panel */}
            {showCodePanel && (
              <div className="w-96 border-l border-gray-800 flex flex-col bg-gray-950">
                <div className="px-4 py-3 border-b border-gray-800 flex items-center justify-between">
                  <span className="text-sm text-gray-400">代码编辑区</span>
                  <span className="text-xs text-gray-600">main.js</span>
                </div>
                <textarea
                  value={code}
                  onChange={e => setCode(e.target.value)}
                  placeholder="// 在这里写代码，完成后点击「提交作品」"
                  className="flex-1 bg-transparent text-sm text-gray-300 font-mono p-4 focus:outline-none resize-none placeholder-gray-700"
                  spellCheck={false}
                />
                {currentNode === 'review' && artifactStatus !== 'none' && (
                  <div className="px-4 py-3 border-t border-gray-800">
                    <p className="text-xs text-gray-500 font-medium mb-2">评审进度</p>
                    <div className="flex items-center justify-between text-xs">
                      <div className="flex flex-col items-center gap-0.5">
                        <span className="text-emerald-400 font-bold">✓</span>
                        <span className="text-emerald-400">已提交</span>
                      </div>
                      <div className="flex-1 h-px mx-2 bg-emerald-700" />
                      <div className="flex flex-col items-center gap-0.5">
                        <span className={`font-bold ${artifactStatus === 'submitted' ? 'text-amber-400 animate-pulse' : 'text-emerald-400'}`}>
                          {artifactStatus === 'submitted' ? '●' : '✓'}
                        </span>
                        <span className={artifactStatus === 'submitted' ? 'text-amber-400' : 'text-emerald-400'}>
                          {artifactStatus === 'submitted' ? 'AI 评审中' : '已评审'}
                        </span>
                      </div>
                      <div className={`flex-1 h-px mx-2 ${artifactStatus === 'passed' || artifactStatus === 'needs_revision' ? 'bg-emerald-700' : 'bg-gray-700'}`} />
                      <div className="flex flex-col items-center gap-0.5">
                        <span className={`font-bold ${artifactStatus === 'passed' ? 'text-emerald-400' : artifactStatus === 'needs_revision' ? 'text-red-400' : 'text-gray-600'}`}>
                          {artifactStatus === 'passed' ? '✓' : artifactStatus === 'needs_revision' ? '✗' : '○'}
                        </span>
                        <span className={artifactStatus === 'passed' ? 'text-emerald-400' : artifactStatus === 'needs_revision' ? 'text-red-400' : 'text-gray-600'}>
                          {artifactStatus === 'passed' ? '已通过' : artifactStatus === 'needs_revision' ? '需修改' : '待结果'}
                        </span>
                      </div>
                    </div>
                    {artifactStatus === 'submitted' && (
                      <p className="text-xs text-amber-500 mt-2">▸ 发送消息让 AI 开始评审你的代码</p>
                    )}
                  </div>
                )}
                <div className="p-3 border-t border-gray-800 space-y-2">
                  {/* Artifact 提交按钮 */}
                  <button
                    onClick={submitArtifact}
                    disabled={!code.trim() || artifactSubmitting || loading || artifactStatus === 'passed' || artifactStatus === 'submitted'}
                    className="w-full bg-emerald-700 hover:bg-emerald-600 disabled:opacity-40 text-white text-sm font-semibold py-2 rounded-lg transition-colors">
                    {artifactSubmitting ? '提交中…' : artifactStatus === 'passed' ? '✓ 已通过' : artifactStatus === 'submitted' ? '⏳ 评审中…' : '提交作品'}
                  </button>
                  {/* Artifact 状态徽章 */}
                  {artifactStatus !== 'none' && (
                    <p className={`text-xs font-medium text-center ${ARTIFACT_STATUS_LABELS[artifactStatus].color}`}>
                      {ARTIFACT_STATUS_LABELS[artifactStatus].label}
                    </p>
                  )}
                </div>
                {/* 已提交历史 */}
                {artifacts.length > 0 && (
                  <div className="border-t border-gray-800 p-3 space-y-2 overflow-y-auto max-h-48">
                    <p className="text-xs text-gray-600 font-medium">提交历史</p>
                    {artifacts.map(a => (
                      <div key={a.id} className="text-xs rounded border border-gray-800 p-2 space-y-1">
                        <div className="flex items-center justify-between">
                          <span className="font-mono text-gray-500">{a.type} · {a.node_key}</span>
                          <span className={
                            a.status === 'passed' ? 'text-emerald-400' :
                            a.status === 'needs_revision' ? 'text-red-400' : 'text-amber-400'
                          }>
                            {a.status === 'passed' ? '✓ 通过' : a.status === 'needs_revision' ? '✗ 需修改' : '● 待评审'}
                          </span>
                        </div>
                        <pre className="text-gray-400 overflow-hidden line-clamp-3 whitespace-pre-wrap">{a.content.slice(0, 200)}{a.content.length > 200 ? '…' : ''}</pre>
                        {a.type === 'CODE' && (
                          <button onClick={() => { setCode(a.content); if (stageComplete) setShowCodePanel(true) }}
                            className="text-xs text-blue-400 hover:text-blue-300 transition-colors mt-1 block">
                            {stageComplete ? '👁 查看代码' : '↩ 加载到编辑器'}
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