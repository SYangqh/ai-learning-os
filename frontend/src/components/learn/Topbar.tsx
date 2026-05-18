import { useState, useRef, useEffect } from 'react'
import type { Theme } from '@/components/ThemeProvider'

type Stage = { id: string; index: number; title: string; goal: string; status: string }

const THEMES: { id: Theme; label: string; emoji: string }[] = [
  { id: 'cute',       label: '可爱',   emoji: '🌸' },
  { id: 'dark',       label: '夜间',   emoji: '🌙' },
  { id: 'corporate',  label: '国企',   emoji: '🏗' },
  { id: 'cyber',      label: '未来',   emoji: '⚡' },
  { id: 'botanical',  label: '自然',   emoji: '🌿' },
  { id: 'accessible', label: '无障碍', emoji: '♏️' },
]

const NODE_LABELS: Record<string, { label: string; color: string }> = {
  intro:    { label: '引入',  color: 'text-blue-600 border-blue-200 bg-blue-50' },
  concept:  { label: '概念',  color: 'text-amber-600 border-amber-200 bg-amber-50' },
  practice: { label: '练习',  color: 'text-orange-600 border-orange-200 bg-orange-50' },
  task:     { label: '任务',  color: 'text-red-600 border-red-200 bg-red-50' },
  review:   { label: '评审',  color: 'text-purple-600 border-purple-200 bg-purple-50' },
  retro:    { label: '复盘',  color: 'text-emerald-600 border-emerald-200 bg-emerald-50' },
  complete: { label: '完成',  color: 'text-emerald-700 border-emerald-300 bg-emerald-50' },
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

interface TopbarProps {
  sidebarOpen: boolean
  isMobile: boolean
  activeStage: Stage | null
  stages: Stage[]
  currentNode: string
  nodeStatus: string
  theme: Theme
  soundEnabled: boolean
  reducedMotion: boolean
  hasArtifactPanel: boolean
  showCodePanel: boolean
  artifactType: 'CODE' | 'NOTE' | 'DIAGRAM' | 'ESSAY' | 'PROOF' | 'NONE'
  onToggleSidebar: () => void
  onSetTheme: (theme: Theme) => void
  onToggleSound: () => void
  onToggleReducedMotion: () => void
  onExportChat: () => void
  onToggleCodePanel: () => void
}

export default function Topbar({
  sidebarOpen,
  isMobile,
  activeStage,
  stages,
  currentNode,
  nodeStatus,
  theme,
  soundEnabled,
  reducedMotion,
  hasArtifactPanel,
  showCodePanel,
  artifactType,
  onToggleSidebar,
  onSetTheme,
  onToggleSound,
  onToggleReducedMotion,
  onExportChat,
  onToggleCodePanel,
}: TopbarProps) {
  const [showThemePicker, setShowThemePicker] = useState(false)
  const [showSettings, setShowSettings] = useState(false)
  const themePickerRef = useRef<HTMLDivElement>(null)

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

  useEffect(() => {
    if (!showSettings) return
    const handler = (e: MouseEvent) => {
      const target = e.target as Node
      if (!document.querySelector('.settings-panel')?.contains(target) &&
          !document.querySelector('.settings-btn')?.contains(target)) {
        setShowSettings(false)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [showSettings])

  return (
    <header
      className="flex items-center gap-2 px-3 py-2 border-b t-border t-panel shadow-sm mobile-safe-top"
      style={{ minHeight: '48px' }}
    >
      {/* 汉堡菜单 — 移动端更大触控区域 */}
      <button
        onClick={onToggleSidebar}
        className={`t-toggle-btn flex-shrink-0 rounded-lg ${isMobile ? 'p-2 text-xl touch-target' : 'text-lg'}`}
        aria-label="切换菜单"
      >
        ☰
      </button>

      {/* 当前阶段 & 节点信息 */}
      <div className="text-sm t-muted flex-1 min-w-0">
        {activeStage ? (
          <span className="flex items-center gap-1 flex-wrap">
            {!isMobile && (
              <>
                <span className="t-faint">阶段 {activeStage.index + 1} / {stages.length}</span>
                <span className="t-faint">|</span>
              </>
            )}
            <span className="t-text font-medium truncate max-w-[120px] md:max-w-none">
              {activeStage.title}
            </span>
            <span className="t-faint hidden sm:inline">·</span>
            <span className="hidden sm:inline">
              <NodeBadge node={currentNode} status={nodeStatus} />
            </span>
          </span>
        ) : (
          <span className="t-faint text-xs md:text-sm">选择一个阶段开始学习</span>
        )}
      </div>

      {/* 右侧工具栏 */}
      <div className="ml-auto flex items-center gap-1 flex-shrink-0">
        {/* 主题切换 */}
        <div ref={themePickerRef} className="relative">
          <button
            onClick={() => setShowThemePicker(p => !p)}
            className={`px-2 py-1.5 rounded-lg border t-border t-panel text-sm transition-colors ${showThemePicker ? 't-accent-text' : 't-faint'} ${isMobile ? 'touch-target' : ''}`}
            title="切换主题"
          >
            {THEMES.find(t => t.id === theme)?.emoji ?? '🎨'}
          </button>
          {showThemePicker && (
            <div className="absolute right-0 top-full mt-1 t-panel border t-border rounded-xl shadow-lg z-50 p-2 grid grid-cols-2 gap-1" style={{ minWidth: '140px' }}>
              {THEMES.map(t => (
                <button
                  key={t.id}
                  onClick={() => { onSetTheme(t.id); setShowThemePicker(false) }}
                  className={`flex items-center gap-1.5 px-2 py-2 rounded-lg text-xs transition-colors ${
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

        {/* 设置按钮 */}
        <div className="relative">
          <button
            onClick={() => setShowSettings(p => !p)}
            className={`settings-btn px-2 py-1.5 rounded-lg border t-border t-panel text-sm transition-colors ${showSettings ? 't-accent-text' : 't-faint'} ${isMobile ? 'touch-target' : ''}`}
            title="反馈设置"
          >
            ⚙️
          </button>
          {showSettings && (
            <div className="settings-panel absolute right-0 top-full mt-1 t-panel border t-border rounded-xl shadow-lg z-50 p-3 space-y-2" style={{ minWidth: '180px' }}>
              <div className="text-xs font-semibold t-text mb-2">反馈设置</div>
              <label className="flex items-center justify-between cursor-pointer py-1">
                <span className="text-xs t-text">音效</span>
                <input type="checkbox" checked={soundEnabled} onChange={onToggleSound} className="ml-2" />
              </label>
              <label className="flex items-center justify-between cursor-pointer py-1">
                <span className="text-xs t-text">低动效模式</span>
                <input type="checkbox" checked={reducedMotion} onChange={onToggleReducedMotion} className="ml-2" />
              </label>
              <div className="text-xs t-faint pt-1 border-t t-border-sub">
                无障碍主题已禁用所有动效
              </div>
            </div>
          )}
        </div>

        {/* 导出记录 — 移动端隐藏文字 */}
        {activeStage && (
          <>
            <button
              onClick={onExportChat}
              className={`text-xs px-2 py-1.5 rounded-lg border t-border t-panel t-muted transition-all ${isMobile ? 'touch-target' : ''}`}
              title="导出聊天记录"
            >
              {isMobile ? '⬇' : '⬇ 导出记录'}
            </button>
            {hasArtifactPanel && (
              <button
                onClick={onToggleCodePanel}
                className={`text-xs px-2 py-1.5 rounded-lg border transition-all ${
                  showCodePanel ? 't-stage-active t-accent-text' : 't-border t-panel t-muted'
                } ${isMobile ? 'touch-target' : ''}`}
                title={showCodePanel ? '隐藏产出区' : '打开产出区'}
              >
                {isMobile
                  ? (showCodePanel ? '✕' : (artifactType === 'CODE' ? '💻' : artifactType === 'DIAGRAM' ? '🖼️' : '📝'))
                  : (showCodePanel ? '隐藏产出区' : artifactType === 'CODE' ? '打开代码区' : artifactType === 'DIAGRAM' ? '打开链接区' : '打开笔记区')
                }
              </button>
            )}
          </>
        )}
      </div>
    </header>
  )
}
