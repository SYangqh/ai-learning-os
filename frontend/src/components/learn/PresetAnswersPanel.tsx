export type PresetAnswer = {
  id: string
  text: string
  confidence: 'HIGH' | 'LOW'
  stageIndex?: number
}

interface PresetAnswersPanelProps {
  answers: PresetAnswer[]
  onSelectAnswer: (answer: PresetAnswer) => void
}

export default function PresetAnswersPanel({ answers, onSelectAnswer }: PresetAnswersPanelProps) {
  if (answers.length === 0) return null

  return (
    <div className="flex flex-col gap-2 px-4 py-3 border-b t-border t-panel">
      <p className="text-xs t-faint font-medium">预制答案（点击快速回复）</p>
      <div className="flex flex-wrap gap-2">
        {answers.map(a => (
          <button
            key={a.id}
            onClick={() => onSelectAnswer(a)}
            className={`text-xs px-3 py-1.5 rounded-lg border transition-all ${
              a.confidence === 'HIGH'
                ? 't-border hover:t-accent-text hover:border-current t-panel shadow-sm'
                : 't-border-sub t-faint hover:t-text opacity-75'
            }`}
          >
            {a.text}
          </button>
        ))}
      </div>
    </div>
  )
}
