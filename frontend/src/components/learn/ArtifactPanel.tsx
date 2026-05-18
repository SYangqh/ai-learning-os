import { useEffect } from 'react'

type ArtifactType = 'CODE' | 'NOTE' | 'DIAGRAM' | 'ESSAY' | 'PROOF' | 'NONE'
type ArtifactStatus = 'none' | 'submitted' | 'passed' | 'needs_revision'
type ArtifactRecord = { id: string; type: string; content: string; status: string; node_key: string; created_at: string }

const NODE_LABELS: Record<string, { label: string; color: string }> = {
  intro:    { label: '引入',  color: 'text-blue-600 border-blue-200 bg-blue-50' },
  concept:  { label: '概念',  color: 'text-amber-600 border-amber-200 bg-amber-50' },
  practice: { label: '练习',  color: 'text-orange-600 border-orange-200 bg-orange-50' },
  task:     { label: '任务',  color: 'text-red-600 border-red-200 bg-red-50' },
  review:   { label: '评审',  color: 'text-purple-600 border-purple-200 bg-purple-50' },
  retro:    { label: '复盘',  color: 'text-emerald-600 border-emerald-200 bg-emerald-50' },
}

const ARTIFACT_STATUS_LABELS: Record<ArtifactStatus, { label: string; color: string }> = {
  none:            { label: '',           color: '' },
  submitted:       { label: '✓ 已提交 · 等待评审', color: 'text-amber-600' },
  passed:          { label: '✓ 评审通过',           color: 'text-emerald-600' },
  needs_revision:  { label: '✗ 需要修改',            color: 'text-red-500' },
}

interface ArtifactPanelProps {
  artifactType: ArtifactType
  artifactStatus: ArtifactStatus
  code: string
  noteContent: string
  diagramUrl: string
  artifactSubmitting: boolean
  loading: boolean
  stageComplete: boolean
  artifacts: ArtifactRecord[]
  isMobile?: boolean
  isOpen?: boolean
  draftSavedAt?: number | null
  onCodeChange: (value: string) => void
  onNoteContentChange: (value: string) => void
  onDiagramUrlChange: (value: string) => void
  onSubmitArtifact: () => void
  onLoadArtifact: (artifact: ArtifactRecord) => void
  onToggle?: () => void
}

/** 移动端底部 Sheet — 含 Escape 键关闭和焦点管理 */
function MobileSheet({
  isOpen,
  panelLabel,
  artifactType,
  artifactStatus,
  code,
  noteContent,
  diagramUrl,
  artifactSubmitting,
  loading,
  stageComplete,
  artifacts,
  draftSavedAt,
  onCodeChange,
  onNoteContentChange,
  onDiagramUrlChange,
  onSubmitArtifact,
  onLoadArtifact,
  onToggle,
}: Omit<ArtifactPanelProps, 'isMobile'> & { panelLabel: string }) {
  // Escape 键关闭 sheet
  useEffect(() => {
    if (!isOpen) return
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onToggle?.()
    }
    document.addEventListener('keydown', handler)
    return () => document.removeEventListener('keydown', handler)
  }, [isOpen, onToggle])

  return (
    <>
      {/* FAB：sheet 收起时显示 */}
      {!isOpen && (
        <button
          onClick={onToggle}
          className="fixed bottom-20 right-4 z-30 t-btn-primary text-sm px-4 py-2.5 rounded-full shadow-lg flex items-center gap-2 touch-target"
          style={{ marginBottom: 'env(safe-area-inset-bottom, 0px)' }}
          aria-label={`打开${panelLabel}`}
        >
          {artifactType === 'CODE' ? '💻' : artifactType === 'DIAGRAM' ? '🖼️' : '📝'}
          <span>{panelLabel}</span>
        </button>
      )}

      {/* 底部 Sheet 面板 */}
      {isOpen && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label={panelLabel}
          className="fixed bottom-0 left-0 right-0 z-40 t-panel border-t t-border shadow-2xl sheet-enter flex flex-col"
          style={{
            maxHeight: '65vh',
            paddingBottom: 'env(safe-area-inset-bottom, 0px)',
          }}
        >
          {/* Sheet header */}
          <div className="px-4 py-3 border-b t-border-sub flex items-center justify-between flex-shrink-0">
            <span className="text-sm t-muted font-medium flex items-center gap-2">
              {artifactType === 'CODE' ? '💻' : artifactType === 'DIAGRAM' ? '🖼️' : '📝'}
              {panelLabel}
              {draftSavedAt && (
                <span className="text-xs t-faint font-normal">（草稿已保存）</span>
              )}
            </span>
            <div className="flex items-center gap-2">
              <span className="text-xs t-faint font-mono uppercase">{artifactType}</span>
              <button
                onClick={onToggle}
                className="t-faint hover:t-text text-lg px-2 touch-target"
                aria-label="收起面板（Escape）"
              >
                ✕
              </button>
            </div>
          </div>

          {/* Input area */}
          <div className="flex-1 flex flex-col overflow-hidden">
            {artifactType === 'CODE' ? (
              <textarea
                value={code}
                onChange={e => onCodeChange(e.target.value)}
                placeholder="// 在这里写代码，完成后点击「提交作品」"
                className="flex-1 t-input-field text-sm font-mono p-3 resize-none"
                spellCheck={false}
                rows={8}
              />
            ) : artifactType === 'DIAGRAM' ? (
              <div className="flex-1 p-4 space-y-3">
                <p className="text-xs t-faint">粘贴图表链接（Mermaid / draw.io / Excalidraw / 图床 URL）</p>
                <input
                  type="url"
                  value={diagramUrl}
                  onChange={e => onDiagramUrlChange(e.target.value)}
                  placeholder="https://..."
                  className="w-full t-input-field border rounded-xl px-3 py-2.5 text-sm"
                />
                {diagramUrl && (
                  <a href={diagramUrl} target="_blank" rel="noopener noreferrer"
                    className="text-xs t-accent-text block truncate">
                    🔗 预览链接 →
                  </a>
                )}
              </div>
            ) : (
              <textarea
                value={noteContent}
                onChange={e => onNoteContentChange(e.target.value)}
                placeholder={artifactType === 'ESSAY'
                  ? '在这里写论述（支持 Markdown 格式）...'
                  : artifactType === 'PROOF'
                  ? '在这里写证明或推导过程...'
                  : '在这里写笔记或回答...'}
                className="flex-1 t-input-field text-sm p-3 resize-none leading-relaxed"
                spellCheck={false}
                rows={8}
              />
            )}
          </div>

          {/* Submit */}
          <div className="p-3 flex-shrink-0 border-t t-border-sub">
            <button
              onClick={onSubmitArtifact}
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
              <p className={`text-xs font-medium text-center mt-1 ${ARTIFACT_STATUS_LABELS[artifactStatus].color}`}>
                {ARTIFACT_STATUS_LABELS[artifactStatus].label}
              </p>
            )}
          </div>

          {/* 已提交历史 */}
          {artifacts.length > 0 && !stageComplete && (
            <div className="border-t t-border-sub p-3 space-y-2 overflow-y-auto max-h-32">
              <p className="text-xs t-faint font-medium">提交历史</p>
              {artifacts.slice(0, 2).map(a => (
                <div key={a.id} className="text-xs rounded-xl border t-border p-2 t-bg">
                  <div className="flex items-center justify-between">
                    <span className="font-mono t-faint">{a.type} · {a.node_key}</span>
                    <button onClick={() => { onLoadArtifact(a); onToggle?.() }}
                      className="text-xs t-accent-text font-medium">
                      ↩ 加载
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </>
  )
}

export default function ArtifactPanel({
  artifactType,
  artifactStatus,
  code,
  noteContent,
  diagramUrl,
  artifactSubmitting,
  loading,
  stageComplete,
  artifacts,
  isMobile = false,
  isOpen = false,
  draftSavedAt,
  onCodeChange,
  onNoteContentChange,
  onDiagramUrlChange,
  onSubmitArtifact,
  onLoadArtifact,
  onToggle,
}: ArtifactPanelProps) {
  if (artifactType === 'NONE') return null

  const panelLabel = artifactType === 'CODE' ? '代码编辑区'
    : artifactType === 'DIAGRAM' ? '图表链接'
    : '笔记区'

  // 移动端：底部 sheet，展开/收起
  if (isMobile) {
    return (
      <MobileSheet
        isOpen={isOpen}
        panelLabel={panelLabel}
        artifactType={artifactType}
        artifactStatus={artifactStatus}
        code={code}
        noteContent={noteContent}
        diagramUrl={diagramUrl}
        artifactSubmitting={artifactSubmitting}
        loading={loading}
        stageComplete={stageComplete}
        artifacts={artifacts}
        draftSavedAt={draftSavedAt}
        onCodeChange={onCodeChange}
        onNoteContentChange={onNoteContentChange}
        onDiagramUrlChange={onDiagramUrlChange}
        onSubmitArtifact={onSubmitArtifact}
        onLoadArtifact={onLoadArtifact}
        onToggle={onToggle}
      />
    )
  }

  // 桌面端：原有并排面板（不可见时不渲染）
  if (!isOpen) return null

  return (
    <div className="w-96 border-l t-border flex flex-col t-panel shadow-sm">
      {/* Panel header */}
      <div className="px-4 py-3 border-b t-border-sub flex items-center justify-between t-bg">
        <span className="text-sm t-muted font-medium flex items-center gap-2">
          {panelLabel}
          {draftSavedAt && (
            <span className="text-xs t-faint font-normal">（草稿已保存）</span>
          )}
        </span>
        <div className="flex items-center gap-2">
          <span className="text-xs t-faint font-mono uppercase">{artifactType}</span>
          {onToggle && (
            <button
              onClick={onToggle}
              className="t-faint hover:t-text text-base px-1.5"
              aria-label="关闭面板"
              title="关闭面板"
            >
              ✕
            </button>
          )}
        </div>
      </div>

      {/* Input area — by type */}
      {artifactType === 'CODE' ? (
        <textarea
          value={code}
          onChange={e => onCodeChange(e.target.value)}
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
            onChange={e => onDiagramUrlChange(e.target.value)}
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
          onChange={e => onNoteContentChange(e.target.value)}
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
          onClick={onSubmitArtifact}
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
              <button onClick={() => onLoadArtifact(a)}
                className="text-xs t-accent-text transition-colors mt-1 block font-medium">
                {stageComplete ? '👁 查看' : '↩ 加载到编辑器'}
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}