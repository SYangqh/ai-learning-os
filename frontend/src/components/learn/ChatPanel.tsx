import { useRef, useEffect, useState } from 'react'
import ReactMarkdown from 'react-markdown'
import PresetAnswersPanel, { type PresetAnswer } from './PresetAnswersPanel'

type Message = { role: 'user' | 'assistant'; content: string }
type RubricResult = { passed: boolean; score: number; feedback: string; hints: string[] }
type ArtifactType = 'CODE' | 'NOTE' | 'DIAGRAM' | 'ESSAY' | 'PROOF' | 'NONE'
type ArtifactRecord = { id: string; type: string; content: string; status: string; node_key: string; created_at: string }
type InteractionMode = 'FREE_INPUT_ONLY' | 'PRESET_ONLY' | 'HYBRID'

const NODE_LABELS: Record<string, { label: string; color: string }> = {
  intro:    { label: '引入',  color: 'text-blue-600 border-blue-200 bg-blue-50' },
  concept:  { label: '概念',  color: 'text-amber-600 border-amber-200 bg-amber-50' },
  practice: { label: '练习',  color: 'text-orange-600 border-orange-200 bg-orange-50' },
  task:     { label: '任务',  color: 'text-red-600 border-red-200 bg-red-50' },
  review:   { label: '评审',  color: 'text-purple-600 border-purple-200 bg-purple-50' },
  retro:    { label: '复盘',  color: 'text-emerald-600 border-emerald-200 bg-emerald-50' },
}

const NODE_HINTS: Record<string, string> = {
  intro:    '💬 回答引导问题，让 AI 了解你的现有认知即可',
  concept:  '📖 阅读 AI 讲解，随时提问深入理解概念',
  practice: '✏️ 口头回答练习题即可，无需写代码',
  task:     '📝 在右侧产出区完成任务，写好后点击「提交作品」',
  review:   '📬 发送一条消息，AI 将立刻开始评审你的作品',
  retro:    '🧠 与 AI 一起回顾本阶段收获，可自由提问',
}

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

interface ChatPanelProps {
  messages: Message[]
  loading: boolean
  userInput: string
  currentNode: string
  stageComplete: boolean
  awaitsInput: boolean
  awaitsArtifact: boolean
  artifactType: ArtifactType
  rubricResult: RubricResult | null
  showPassCelebration: boolean
  artifacts: ArtifactRecord[]
  interactionMode: InteractionMode
  presetAnswers: PresetAnswer[]
  onUserInputChange: (value: string) => void
  onSendInput: () => void
  onAskHermes: () => void
  onRegenerateLast: () => void
  onLoadArtifactToCode: (content: string) => void
  onLoadArtifactToNote: (content: string) => void
}

export default function ChatPanel({
  messages,
  loading,
  userInput,
  currentNode,
  stageComplete,
  awaitsInput,
  awaitsArtifact,
  artifactType,
  rubricResult,
  showPassCelebration,
  artifacts,
  interactionMode,
  presetAnswers,
  onUserInputChange,
  onSendInput,
  onAskHermes,
  onRegenerateLast,
  onLoadArtifactToCode,
  onLoadArtifactToNote,
}: ChatPanelProps) {
  const messagesEndRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  // 调试日志：监控预制答案状态
  useEffect(() => {
    console.log('[ChatPanel] 预制答案状态:', {
      currentNode,
      interactionMode,
      presetAnswersCount: presetAnswers.length,
      presetAnswers,
      shouldShow: currentNode === 'practice' && ['HYBRID', 'PRESET_ONLY'].includes(interactionMode) && presetAnswers.length > 0
    })
  }, [currentNode, interactionMode, presetAnswers])

  const lastAssistantIdx = messages.map((m, i) => m.role === 'assistant' ? i : -1)
    .filter(i => i >= 0).pop() ?? -1

  return (
    <div className="flex-1 flex flex-col overflow-hidden">
      {/* Messages */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4 t-bg">
        {messages.map((m, i) => (
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
                <button onClick={onRegenerateLast}
                  className="self-start text-xs t-regen-btn px-1">
                  ↺ 重新生成
                </button>
              )}
            </div>
          </div>
        ))}
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
        {/* Rubric 结果卡片 */}
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
        {showPassCelebration && (
          <div className="flex justify-center">
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
                  <button onClick={() => onLoadArtifactToCode(a.content)}
                    className="text-xs t-accent-text font-medium mt-0.5">
                    👁 查看完整代码
                  </button>
                )}
                {(a.type === 'NOTE' || a.type === 'ESSAY' || a.type === 'PROOF') && (
                  <button onClick={() => onLoadArtifactToNote(a.content)}
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
          {/* Phase 9B: 预制答案卡片 */}
          {currentNode === 'practice' && ['HYBRID', 'PRESET_ONLY'].includes(interactionMode) && presetAnswers.length > 0 && (
            <PresetAnswersPanel 
              answers={presetAnswers} 
              onSelectAnswer={(ans) => onUserInputChange(ans.text)} 
            />
          )}
          {currentNode === 'practice' && interactionMode === 'FREE_INPUT_ONLY' && (
            <div className="text-xs t-faint border-t t-border-sub pt-2">
              ⚠ 本题要求独立作答，不提供预制答案
            </div>
          )}
          <textarea
            value={userInput}
            onChange={e => onUserInputChange(e.target.value)}
            onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); onSendInput() } }}
            placeholder={
              interactionMode === 'PRESET_ONLY' && currentNode === 'practice'
                ? '请选择上方预制答案' 
                : interactionMode === 'FREE_INPUT_ONLY' && currentNode === 'practice'
                ? '请手动输入答案（本题禁用预制答案）'
                : awaitsInput ? "输入你的回答（Shift+Enter 换行）..." : "AI 正在讲解中..."
            }
            disabled={!awaitsInput || loading || (interactionMode === 'PRESET_ONLY' && currentNode === 'practice')}
            rows={3}
            className={`w-full t-input-field border rounded-xl px-4 py-3 text-sm resize-none disabled:opacity-40 transition-all ${
              interactionMode === 'PRESET_ONLY' && currentNode === 'practice' ? 'opacity-50 cursor-not-allowed' : ''
            }`}
          />
          <div className="flex gap-2">
            <button onClick={onSendInput} disabled={!userInput.trim() || loading}
              className="flex-1 t-btn-primary text-sm font-semibold py-2.5 rounded-xl shadow-sm">
              提交
            </button>
            <button onClick={onAskHermes} disabled={!userInput.trim() || loading}
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
  )
}
