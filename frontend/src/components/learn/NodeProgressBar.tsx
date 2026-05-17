const NODE_SEQ = ['intro', 'concept', 'practice', 'task', 'review', 'retro']

const NODE_LABELS: Record<string, { label: string; color: string }> = {
  intro:    { label: '引入',  color: 'text-blue-600 border-blue-200 bg-blue-50' },
  concept:  { label: '概念',  color: 'text-amber-600 border-amber-200 bg-amber-50' },
  practice: { label: '练习',  color: 'text-orange-600 border-orange-200 bg-orange-50' },
  task:     { label: '任务',  color: 'text-red-600 border-red-200 bg-red-50' },
  review:   { label: '评审',  color: 'text-purple-600 border-purple-200 bg-purple-50' },
  retro:    { label: '复盘',  color: 'text-emerald-600 border-emerald-200 bg-emerald-50' },
}

interface NodeProgressBarProps {
  currentNode: string
}

export default function NodeProgressBar({ currentNode }: NodeProgressBarProps) {
  const nodeIdx = NODE_SEQ.indexOf(currentNode)

  return (
    <div className="flex items-center justify-center gap-1 px-4 py-2 border-b t-border t-panel text-xs overflow-x-auto flex-shrink-0">
      {NODE_SEQ.map((n, i) => {
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
  )
}
