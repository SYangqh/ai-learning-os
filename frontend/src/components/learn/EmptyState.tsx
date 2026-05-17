type Stage = { id: string; index: number; title: string; goal: string; status: string }

interface EmptyStateProps {
  pathTitle: string
  stages: Stage[]
  completedCount: number
  onOpenStage: (stage: Stage) => void
}

export default function EmptyState({ pathTitle, stages, completedCount, onOpenStage }: EmptyStateProps) {
  const nextStage = stages.find(s => s.status === 'active')

  return (
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
        {nextStage && (
          <div className="t-panel border t-border rounded-2xl p-4 space-y-2 shadow-sm">
            <p className="text-xs t-faint font-medium uppercase tracking-wide">今日目标</p>
            <p className="t-text font-semibold">{nextStage.title}</p>
            <p className="t-muted text-sm leading-relaxed">{nextStage.goal}</p>
            <button onClick={() => onOpenStage(nextStage)}
              className="mt-2 t-btn-primary text-sm font-semibold px-4 py-2.5 rounded-xl shadow-sm w-full">
              {completedCount === 0 ? '开始学习 →' : '继续学习 →'}
            </button>
          </div>
        )}
        {completedCount > 0 && (
          <div className="t-panel border t-border rounded-2xl p-4 space-y-3 shadow-sm">
            <p className="text-xs t-faint font-medium uppercase tracking-wide">已完成阶段</p>
            <div className="space-y-2">
              {stages.filter(s => s.status === 'completed').map(s => (
                <button key={s.id} onClick={() => onOpenStage(s)}
                  className="w-full flex items-center gap-2 text-sm text-left px-3 py-2 rounded-xl border border-emerald-200 bg-emerald-50/50 hover:border-emerald-300 transition-all">
                  <span className="text-emerald-500 flex-shrink-0">✓</span>
                  <span className="t-text font-medium flex-1">{s.title}</span>
                  <span className="text-xs t-faint flex-shrink-0">回看 →</span>
                </button>
              ))}
            </div>
          </div>
        )}
        {stages.length > 0 && !nextStage && completedCount === stages.length && (
          <div className="t-panel border t-border rounded-2xl p-4 text-center space-y-2 shadow-sm">
            <div className="text-3xl">🎓</div>
            <p className="t-text font-semibold">全部阶段已完成！</p>
            <p className="t-faint text-sm">恭喜你完成了整条学习路径</p>
          </div>
        )}
      </div>
    </div>
  )
}
