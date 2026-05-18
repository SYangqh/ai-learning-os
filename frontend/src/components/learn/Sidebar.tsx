import { useEffect } from 'react'

type Stage = { id: string; index: number; title: string; goal: string; status: string }

interface SidebarProps {
  isOpen: boolean
  isMobile: boolean
  pathTitle: string
  stages: Stage[]
  activeStage: Stage | null
  completedCount: number
  tokenUsage: { totalTokens: number; estimatedCostCny: number; callCount: number } | null
  onOpenStage: (stage: Stage) => void
  onClose: () => void
  onLogout: () => void
}

export default function Sidebar({
  isOpen,
  isMobile,
  pathTitle,
  stages,
  activeStage,
  completedCount,
  tokenUsage,
  onOpenStage,
  onClose,
  onLogout,
}: SidebarProps) {
  // 移动端打开时禁止 body 滚动
  useEffect(() => {
    if (!isMobile) return
    if (isOpen) {
      document.body.style.overflow = 'hidden'
    } else {
      document.body.style.overflow = ''
    }
    return () => { document.body.style.overflow = '' }
  }, [isOpen, isMobile])

  const sidebarContent = (
    <aside
      className={`
        flex flex-col h-full t-panel border-r t-border shadow-sm
        ${isMobile ? 'w-72' : (isOpen ? 'w-72' : 'w-0')}
        ${!isMobile ? 'transition-all duration-300 overflow-hidden' : ''}
      `}
      style={isMobile ? { width: '18rem' } : undefined}
    >
      <div className="p-4 border-b t-border-sub">
        <div className="t-accent-text font-bold text-sm truncate">{pathTitle}</div>
        <div className="t-faint text-xs mt-1">学习路径</div>
      </div>
      <div className="flex-1 overflow-y-auto p-3 space-y-1.5">
        {stages.map(stage => (
          <button key={stage.id}
            onClick={() => { onOpenStage(stage); if (isMobile) onClose() }}
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
        {tokenUsage && tokenUsage.totalTokens > 0 && (
          <div className="text-xs t-faint space-y-0.5 pt-1 border-t t-border-sub">
            <div className="flex items-center justify-between">
              <span>Token 消耗</span>
              <span className="font-mono">{tokenUsage.totalTokens.toLocaleString()}</span>
            </div>
            <div className="flex items-center justify-between">
              <span>估算费用</span>
              <span className="font-mono">¥{tokenUsage.estimatedCostCny.toFixed(4)}</span>
            </div>
          </div>
        )}
        <button onClick={onLogout}
          className="w-full text-xs t-faint hover:text-red-500 py-2 transition-colors">
          退出登录
        </button>
      </div>
    </aside>
  )

  // 桌面端：保持原推入布局
  if (!isMobile) {
    return sidebarContent
  }

  // 移动端：覆盖抽屉 + 遮罩
  return (
    <>
      {/* 遮罩 */}
      {isOpen && (
        <div
          className="fixed inset-0 bg-black/50 z-40 sidebar-overlay"
          onClick={onClose}
          aria-hidden="true"
        />
      )}
      {/* 抽屉面板 */}
      <div
        className={`fixed top-0 left-0 h-full z-50 transition-transform duration-300 ${
          isOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
        style={{ paddingTop: 'env(safe-area-inset-top, 0px)' }}
      >
        {sidebarContent}
      </div>
    </>
  )
}
