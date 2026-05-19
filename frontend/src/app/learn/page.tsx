'use client'
import { useState, useEffect, useRef } from 'react'
import { useRouter } from 'next/navigation'
import { STORAGE_KEYS, getStored, setStored, apiFetch, clearAuth, isLoggedIn } from '@/lib/api'
import { useTheme } from '@/components/ThemeProvider'
import type { Theme } from '@/components/ThemeProvider'
import { useFeedback } from '@/hooks/useFeedback'
import { useOfflineDraft } from '@/hooks/useOfflineDraft'
import { useMobileCapabilities } from '@/hooks/useMobileCapabilities'
import { loadDraftFromDB } from '@/lib/draftDB'
import FeedbackToast from '@/components/FeedbackToast'
import Sidebar from '@/components/learn/Sidebar'
import Topbar from '@/components/learn/Topbar'
import EmptyState from '@/components/learn/EmptyState'
import NodeProgressBar from '@/components/learn/NodeProgressBar'
import ChatPanel from '@/components/learn/ChatPanel'
import ArtifactPanel from '@/components/learn/ArtifactPanel'

type Stage = { id: string; index: number; title: string; goal: string; status: string }
type Message = { role: 'user' | 'assistant'; content: string }
type ArtifactStatus = 'none' | 'submitted' | 'passed' | 'needs_revision'
type ArtifactRecord = { id: string; type: string; content: string; status: string; node_key: string; created_at: string }
type RubricResult = { passed: boolean; score: number; feedback: string; hints: string[] }
type ArtifactType = 'CODE' | 'NOTE' | 'DIAGRAM' | 'ESSAY' | 'PROOF' | 'NONE'
type InteractionMode = 'FREE_INPUT_ONLY' | 'PRESET_ONLY' | 'HYBRID'
type PresetAnswer = { id: string; text: string; confidence: 'HIGH' | 'LOW'; stageIndex?: number }
type InteractionConfig = { 
  mode: InteractionMode; 
  presetAnswers: PresetAnswer[];
  source?: 'YAML' | 'AI_GENERATED' | 'NONE';  // Phase 9D: 预制答案来源
}

export default function LearnPage() {
  const router = useRouter()
  const { theme, setTheme } = useTheme()
  const { feedback, trigger: triggerFeedback, clear: clearFeedback } = useFeedback()
  
  // 路径和阶段状态
  const [stages, setStages] = useState<Stage[]>([])
  const [pathTitle, setPathTitle] = useState('')
  const [activeStage, setActiveStage] = useState<Stage | null>(null)
  const [sessionId, setSessionId] = useState<string | null>(null)
  
  // 消息和输入状态
  const [messages, setMessages] = useState<Message[]>([])
  const [userInput, setUserInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [awaitsInput, setAwaitsInput] = useState(true)
  const [stageComplete, setStageComplete] = useState(false)
  
  // 节点状态
  const [currentNode, setCurrentNode] = useState<string>('intro')
  const [nodeStatus, setNodeStatus] = useState<string>('running')
  
  // Artifact 状态
  const [code, setCode] = useState('')
  const [noteContent, setNoteContent] = useState('')
  const [diagramUrl, setDiagramUrl] = useState('')
  const [artifactStatus, setArtifactStatus] = useState<ArtifactStatus>('none')
  const [artifactType, setArtifactType] = useState<ArtifactType>('CODE')
  const [artifactSubmitting, setArtifactSubmitting] = useState(false)
  const [artifacts, setArtifacts] = useState<ArtifactRecord[]>([])
  const [rubricResult, setRubricResult] = useState<RubricResult | null>(null)
  const [showPassCelebration, setShowPassCelebration] = useState(false)
  
  // UI 状态
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const [showCodePanel, setShowCodePanel] = useState(false)
  const [soundEnabled, setSoundEnabled] = useState(true)
  const [reducedMotion, setReducedMotion] = useState(false)
  
  // Phase 9C: 移动端能力
  const { isMobile, keyboardHeight } = useMobileCapabilities()
  // 移动端默认关闭侧边栏；桌面端保持打开
  const [sidebarInitialized, setSidebarInitialized] = useState(false)
  useEffect(() => {
    if (!sidebarInitialized) {
      setSidebarOpen(!isMobile)
      setSidebarInitialized(true)
    }
  }, [isMobile, sidebarInitialized])
  
  // Phase 9C: 离线草稿
  const { saveDraft, clearDraft } = useOfflineDraft({
    stageId: activeStage?.id ?? null,
    artifactType,
  })
  const [draftSavedAt, setDraftSavedAt] = useState<number | null>(null)
  
  // Token 消耗统计
  const [tokenUsage, setTokenUsage] = useState<{ totalTokens: number; estimatedCostCny: number; callCount: number } | null>(null)
  
  // Phase 9B: 交互模式配置
  const [interactionConfig, setInteractionConfig] = useState<InteractionConfig>({
    mode: 'HYBRID',
    presetAnswers: []
  })
  
  // Refs
  const prevArtifactStatusRef = useRef<ArtifactStatus>('none')
  const prevStageCompleteRef = useRef<boolean>(false)
  const prevMessageContentRef = useRef<string>('')
  const lastUserInputRef = useRef<string>('')

  // 推导状态
  const ARTIFACT_REQUIRED_NODES = new Set(['task'])
  const awaitsArtifact = ARTIFACT_REQUIRED_NODES.has(currentNode) && artifactStatus === 'none' && artifactType !== 'NONE'
  const hasArtifactPanel = artifactType !== 'NONE'
  const completedCount = stages.filter(s => s.status === 'completed').length

  // 初始化
  useEffect(() => {
    if (!isLoggedIn()) { router.push('/'); return }
    const pathId = getStored(STORAGE_KEYS.PATH_ID)
    if (!pathId) { router.push('/'); return }
    
    const savedSoundEnabled = localStorage.getItem('feedback-sound-enabled')
    if (savedSoundEnabled !== null) setSoundEnabled(savedSoundEnabled !== 'false')
    const savedReducedMotion = localStorage.getItem('feedback-reduced-motion')
    if (savedReducedMotion !== null) setReducedMotion(savedReducedMotion === 'true')
    
    loadPath()
  }, [])

  // 触发 review_pass 反馈演出
  useEffect(() => {
    if (artifactStatus === 'passed' && prevArtifactStatusRef.current !== 'passed') {
      setShowPassCelebration(true)
      triggerFeedback('review_pass')
      const t = setTimeout(() => setShowPassCelebration(false), 6000)
      return () => clearTimeout(t)
    }
    prevArtifactStatusRef.current = artifactStatus
  }, [artifactStatus, triggerFeedback])

  // 触发 stage_complete 反馈演出
  useEffect(() => {
    if (stageComplete && !prevStageCompleteRef.current) {
      triggerFeedback('stage_complete')
    }
    prevStageCompleteRef.current = stageComplete
  }, [stageComplete, triggerFeedback])

  // Phase 9A: 触发 answer_good 反馈演出
  useEffect(() => {
    if (messages.length === 0) return
    const lastMsg = messages[messages.length - 1]
    
    if (lastMsg.role !== 'assistant' || lastMsg.content === prevMessageContentRef.current) return
    prevMessageContentRef.current = lastMsg.content
    
    if (!['practice', 'concept'].includes(currentNode)) return
    
    const positiveKeywords = [
      '很好', '不错', '正确', '做得好', '回答得很好', '理解正确', '继续保持',
      'good job', 'well done', 'correct', 'great', 'excellent', 'you got it'
    ]
    const content = lastMsg.content.toLowerCase()
    const hasPositiveFeedback = positiveKeywords.some(kw => content.includes(kw.toLowerCase()))
    
    if (hasPositiveFeedback) {
      triggerFeedback('answer_good')
    }
  }, [messages, currentNode, triggerFeedback])

  // 拉取 token 消耗统计
  useEffect(() => {
    if (!sessionId) return
    apiFetch<{ data: { totalTokens: number; estimatedCostCny: number; callCount: number } }>(
      `/usage/session/${sessionId}`
    ).then(res => {
      if (res.data) setTokenUsage(res.data)
    }).catch(() => {})
  }, [sessionId])

  // Phase 9C: 草稿自动保存（code / note）
  useEffect(() => {
    if (!activeStage || artifactType === 'NONE') return
    if (artifactType === 'CODE' && code) {
      saveDraft(code)
      setDraftSavedAt(Date.now())
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [code])

  useEffect(() => {
    if (!activeStage || artifactType === 'NONE') return
    if (artifactType !== 'CODE' && noteContent) {
      saveDraft(noteContent)
      setDraftSavedAt(Date.now())
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [noteContent])

  // 保存反馈设置
  function toggleSound() {
    const newValue = !soundEnabled
    setSoundEnabled(newValue)
    localStorage.setItem('feedback-sound-enabled', String(newValue))
  }

  function toggleReducedMotion() {
    const newValue = !reducedMotion
    setReducedMotion(newValue)
    localStorage.setItem('feedback-reduced-motion', String(newValue))
  }

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

  // Phase 9C: 直接从 IndexedDB 读取特定 stage 的草稿（在 hook stageId 更新前使用）
  // 使用共享工具函数 loadDraftFromDB（src/lib/draftDB.ts），避免重复 IndexedDB 实现
  async function loadDraftFromStage(stageId: string, type: string): Promise<string | null> {
    return loadDraftFromDB(stageId, type)
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
    setDraftSavedAt(null)
    setLoading(true)
    try {
      type StartResp = {
        data: {
          session_id: string; content?: string; messages?: Message[];
          awaits_input?: boolean; current_node?: string;
          node_status?: string; awaits_artifact?: boolean; artifact_type?: string;
          rubric_passed?: boolean; rubric_score?: number;
          rubric_feedback?: string; rubric_hints?: string[]
          interaction_config?: {
            mode: InteractionMode
            preset_answers: PresetAnswer[]
          }
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
      const resolvedType = (d.artifact_type ?? 'CODE') as ArtifactType
      setArtifactType(resolvedType)
      if ((d.current_node ?? 'intro') === 'task' && resolvedType !== 'NONE') {
        setShowCodePanel(true)
        // Phase 9C: 恢复草稿
        const draft = await loadDraftFromStage(stage.id, resolvedType)
        if (draft) {
          if (resolvedType === 'CODE') setCode(draft)
          else setNoteContent(draft)
          setDraftSavedAt(Date.now())
        }
      }
      if (d.rubric_passed !== undefined) {
        setRubricResult({
          passed: d.rubric_passed,
          score: d.rubric_score ?? 0,
          feedback: d.rubric_feedback ?? '',
          hints: d.rubric_hints ?? [],
        })
      }
      if ((d.current_node ?? '') === 'complete') setStageComplete(true)
      if (d.interaction_config) {
        setInteractionConfig({
          mode: d.interaction_config.mode,
          presetAnswers: d.interaction_config.preset_answers,
          source: d.interaction_config.source  // Phase 9D: 接收来源标记
        })
      } else {
        setInteractionConfig({ mode: 'HYBRID', presetAnswers: [], source: 'NONE' })
      }
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
          interaction_config?: {
            mode: InteractionMode
            preset_answers: PresetAnswer[]
          }
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
      if (d.interaction_config) {
        console.log('[PresetAnswers] 接收到 interactionConfig:', {
          mode: d.interaction_config.mode,
          answersCount: d.interaction_config.preset_answers.length,
          answers: d.interaction_config.preset_answers,
          source: d.interaction_config.source,
          currentNode: d.current_node
        })
        setInteractionConfig({
          mode: d.interaction_config.mode,
          presetAnswers: d.interaction_config.preset_answers,
          source: d.interaction_config.source  // Phase 9D: 接收来源标记
        })
      } else {
        console.warn('[PresetAnswers] 未接收到 interaction_config，当前节点:', d.current_node)
      }
      if (d.rubric_passed !== undefined) {
        setRubricResult({
          passed: d.rubric_passed,
          score: d.rubric_score ?? 0,
          feedback: d.rubric_feedback ?? '',
          hints: d.rubric_hints ?? [],
        })
        setArtifactStatus(d.rubric_passed ? 'passed' : 'needs_revision')
      }
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
      : noteContent
    if (!content.trim() || !sessionId) return
    setArtifactSubmitting(true)
    try {
      type ArtifactResp = { data: { id: string; type: string; status: string; node_key: string } }
      await apiFetch<ArtifactResp>('/artifact', {
        method: 'POST',
        body: JSON.stringify({ sessionId, type: artifactType, content }),
      })
      setArtifactStatus('submitted')
      // Phase 9C: 提交成功后清除草稿
      clearDraft()
      setDraftSavedAt(null)
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
      if (list.length > 0) {
        const latest = list[0]
        if (latest.status === 'passed') setArtifactStatus('passed')
        else if (latest.status === 'needs_revision') setArtifactStatus('needs_revision')
        else setArtifactStatus('submitted')
      } else {
        setArtifactStatus('none')
      }
    } catch {}
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
    } catch {} finally {
      clearAuth()
      router.push('/')
    }
  }

  function handleLoadArtifact(artifact: ArtifactRecord) {
    if (artifact.type === 'CODE') {
      setCode(artifact.content)
      setShowCodePanel(true)
    } else if (artifact.type === 'NOTE' || artifact.type === 'ESSAY' || artifact.type === 'PROOF') {
      setNoteContent(artifact.content)
      setShowCodePanel(true)
    }
  }

  return (
    <div className="flex h-screen overflow-hidden t-bg">
      <Sidebar
        isOpen={sidebarOpen}
        isMobile={isMobile}
        pathTitle={pathTitle}
        stages={stages}
        activeStage={activeStage}
        completedCount={completedCount}
        tokenUsage={tokenUsage}
        onOpenStage={openStage}
        onClose={() => setSidebarOpen(false)}
        onLogout={handleLogout}
      />

      <div className="flex-1 flex flex-col overflow-hidden min-w-0">
        <Topbar
          sidebarOpen={sidebarOpen}
          isMobile={isMobile}
          activeStage={activeStage}
          stages={stages}
          currentNode={currentNode}
          nodeStatus={nodeStatus}
          theme={theme}
          soundEnabled={soundEnabled}
          reducedMotion={reducedMotion}
          hasArtifactPanel={hasArtifactPanel}
          showCodePanel={showCodePanel}
          artifactType={artifactType}
          onToggleSidebar={() => setSidebarOpen(o => !o)}
          onSetTheme={setTheme}
          onToggleSound={toggleSound}
          onToggleReducedMotion={toggleReducedMotion}
          onExportChat={exportChat}
          onToggleCodePanel={() => setShowCodePanel(p => !p)}
        />

        {activeStage && !stageComplete && (
          <NodeProgressBar currentNode={currentNode} />
        )}

        {!activeStage ? (
          <EmptyState
            pathTitle={pathTitle}
            stages={stages}
            completedCount={completedCount}
            onOpenStage={openStage}
          />
        ) : (
          <div className="flex-1 flex overflow-hidden min-w-0">
            <ChatPanel
              messages={messages}
              loading={loading}
              userInput={userInput}
              currentNode={currentNode}
              stageComplete={stageComplete}
              awaitsInput={awaitsInput}
              awaitsArtifact={awaitsArtifact}
              artifactType={artifactType}
              rubricResult={rubricResult}
              showPassCelebration={showPassCelebration}
              artifacts={artifacts}
              interactionMode={interactionConfig.mode}
              presetAnswers={interactionConfig.presetAnswers}
              presetAnswersSource={interactionConfig.source}  {/* Phase 9D: 传递来源标记 */}
              isMobile={isMobile}
              keyboardHeight={keyboardHeight}
              onUserInputChange={setUserInput}
              onSendInput={sendInput}
              onAskHermes={askHermes}
              onRegenerateLast={regenerateLast}
              onLoadArtifactToCode={(content) => { setCode(content); setShowCodePanel(true) }}
              onLoadArtifactToNote={(content) => { setNoteContent(content); setShowCodePanel(true) }}
            />

            {/* 桌面端：并排 ArtifactPanel；移动端：bottom sheet */}
            {hasArtifactPanel && (
              <ArtifactPanel
                artifactType={artifactType}
                artifactStatus={artifactStatus}
                code={code}
                noteContent={noteContent}
                diagramUrl={diagramUrl}
                artifactSubmitting={artifactSubmitting}
                loading={loading}
                stageComplete={stageComplete}
                artifacts={artifacts}
                isMobile={isMobile}
                isOpen={showCodePanel}
                draftSavedAt={draftSavedAt}
                onCodeChange={setCode}
                onNoteContentChange={setNoteContent}
                onDiagramUrlChange={setDiagramUrl}
                onSubmitArtifact={submitArtifact}
                onLoadArtifact={handleLoadArtifact}
                onToggle={() => setShowCodePanel(p => !p)}
              />
            )}
          </div>
        )}
      </div>

      {feedback.active && feedback.eventType && (
        <FeedbackToast
          theme={theme}
          eventType={feedback.eventType}
          variables={feedback.variables}
          onComplete={clearFeedback}
        />
      )}
    </div>
  )
}
