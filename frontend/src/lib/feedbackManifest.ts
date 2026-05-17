/**
 * FeedbackEffectManifest — 主题化正反馈演出配置
 * 
 * 按 theme + event_type 定义文案、动效、音效、触感反馈和降级策略。
 * 新增主题或事件类型时，只需扩展此配置，无需修改业务代码。
 */

import type { Theme } from '@/components/ThemeProvider'

export type FeedbackEventType =
  | 'answer_good'        // 回答不错（PRACTICE/CONCEPT 节点）
  | 'review_pass'        // 通过评审（REVIEW 节点）
  | 'stage_complete'     // 阶段完成（RETRO 节点完成）
  | 'streak_achieved'    // 连续学习达成（未来扩展）

export interface FeedbackEffect {
  // 文案
  text: string
  subtext?: string
  // 动效
  animation: 'bounce' | 'fade' | 'slide' | 'glow' | 'none'
  animationDuration: number // ms
  // 音效（相对路径或 null）
  sound: string | null
  soundVolume: number // 0-1
  // 触感反馈（移动端）
  haptic: 'light' | 'medium' | 'heavy' | 'none'
  // 降级策略
  fallbackText: string // 音效/动画失败时显示的纯文本
}

export type FeedbackManifest = {
  [T in Theme]: {
    [E in FeedbackEventType]: FeedbackEffect
  }
}

/**
 * 反馈演出配置清单
 * 
 * 设计原则：
 * - cute: 活泼、可爱、高频互动
 * - dark: 克制、低调、微妙提示
 * - corporate: 正式、专业、简洁
 * - cyber: 未来感、科技感、快速反馈
 * - botanical: 温暖、自然、舒缓
 * - accessible: 无障碍、高对比、零装饰
 */
export const FEEDBACK_MANIFEST: FeedbackManifest = {
  cute: {
    answer_good: {
      text: '回答得真棒！ ✨',
      subtext: '继续保持这样的思路~',
      animation: 'bounce',
      animationDuration: 600,
      sound: '/sounds/cute-ding.mp3',
      soundVolume: 0.6,
      haptic: 'light',
      fallbackText: '回答得真棒！✨',
    },
    review_pass: {
      text: '太棒了！评审通过！ 🎉',
      subtext: '你的作品质量很高',
      animation: 'bounce',
      animationDuration: 800,
      sound: '/sounds/cute-success.mp3',
      soundVolume: 0.7,
      haptic: 'medium',
      fallbackText: '评审通过！🎉',
    },
    stage_complete: {
      text: '恭喜完成本阶段！ 🏆',
      subtext: '你已经掌握了核心要点',
      animation: 'bounce',
      animationDuration: 1000,
      sound: '/sounds/cute-celebration.mp3',
      soundVolume: 0.8,
      haptic: 'heavy',
      fallbackText: '阶段完成！🏆',
    },
    streak_achieved: {
      text: '连续学习 {days} 天！ 🔥',
      animation: 'bounce',
      animationDuration: 800,
      sound: '/sounds/cute-streak.mp3',
      soundVolume: 0.7,
      haptic: 'medium',
      fallbackText: '连续学习达成！🔥',
    },
  },
  dark: {
    answer_good: {
      text: '思路正确',
      subtext: '继续推进',
      animation: 'fade',
      animationDuration: 400,
      sound: '/sounds/dark-tick.mp3',
      soundVolume: 0.4,
      haptic: 'light',
      fallbackText: '✓ 正确',
    },
    review_pass: {
      text: '评审通过',
      subtext: '质量达标',
      animation: 'glow',
      animationDuration: 600,
      sound: '/sounds/dark-confirm.mp3',
      soundVolume: 0.5,
      haptic: 'light',
      fallbackText: '✓ 通过',
    },
    stage_complete: {
      text: '阶段完成',
      subtext: '进入下一阶段',
      animation: 'fade',
      animationDuration: 800,
      sound: '/sounds/dark-complete.mp3',
      soundVolume: 0.6,
      haptic: 'medium',
      fallbackText: '✓ 完成',
    },
    streak_achieved: {
      text: '连续 {days} 天',
      animation: 'fade',
      animationDuration: 600,
      sound: '/sounds/dark-streak.mp3',
      soundVolume: 0.5,
      haptic: 'light',
      fallbackText: '连续学习 {days} 天',
    },
  },
  corporate: {
    answer_good: {
      text: '回答符合要求',
      animation: 'fade',
      animationDuration: 300,
      sound: null,
      soundVolume: 0,
      haptic: 'none',
      fallbackText: '符合要求',
    },
    review_pass: {
      text: '评审通过',
      subtext: '已达到标准',
      animation: 'fade',
      animationDuration: 400,
      sound: '/sounds/corporate-pass.mp3',
      soundVolume: 0.3,
      haptic: 'light',
      fallbackText: '评审通过',
    },
    stage_complete: {
      text: '本阶段已完成',
      subtext: '可继续下一阶段',
      animation: 'fade',
      animationDuration: 500,
      sound: '/sounds/corporate-complete.mp3',
      soundVolume: 0.4,
      haptic: 'light',
      fallbackText: '阶段已完成',
    },
    streak_achieved: {
      text: '已连续学习 {days} 天',
      animation: 'none',
      animationDuration: 0,
      sound: null,
      soundVolume: 0,
      haptic: 'none',
      fallbackText: '已连续学习 {days} 天',
    },
  },
  cyber: {
    answer_good: {
      text: '[ VALID ]',
      subtext: 'PROCEED',
      animation: 'glow',
      animationDuration: 300,
      sound: '/sounds/cyber-beep.mp3',
      soundVolume: 0.5,
      haptic: 'light',
      fallbackText: '[ VALID ]',
    },
    review_pass: {
      text: '[ APPROVED ]',
      subtext: 'ACCESS GRANTED',
      animation: 'glow',
      animationDuration: 500,
      sound: '/sounds/cyber-success.mp3',
      soundVolume: 0.6,
      haptic: 'medium',
      fallbackText: '[ APPROVED ]',
    },
    stage_complete: {
      text: '[ COMPLETE ]',
      subtext: 'NEXT STAGE UNLOCKED',
      animation: 'glow',
      animationDuration: 700,
      sound: '/sounds/cyber-complete.mp3',
      soundVolume: 0.7,
      haptic: 'heavy',
      fallbackText: '[ COMPLETE ]',
    },
    streak_achieved: {
      text: '[ STREAK: {days} DAYS ]',
      animation: 'glow',
      animationDuration: 500,
      sound: '/sounds/cyber-streak.mp3',
      soundVolume: 0.6,
      haptic: 'medium',
      fallbackText: '[ STREAK: {days} ]',
    },
  },
  botanical: {
    answer_good: {
      text: '很好 🌱',
      subtext: '理解得很透彻',
      animation: 'fade',
      animationDuration: 500,
      sound: '/sounds/botanical-chime.mp3',
      soundVolume: 0.5,
      haptic: 'light',
      fallbackText: '很好 🌱',
    },
    review_pass: {
      text: '评审通过 🌿',
      subtext: '你的努力开花结果了',
      animation: 'fade',
      animationDuration: 700,
      sound: '/sounds/botanical-success.mp3',
      soundVolume: 0.6,
      haptic: 'medium',
      fallbackText: '评审通过 🌿',
    },
    stage_complete: {
      text: '阶段完成 🌳',
      subtext: '又成长了一截',
      animation: 'fade',
      animationDuration: 900,
      sound: '/sounds/botanical-complete.mp3',
      soundVolume: 0.7,
      haptic: 'medium',
      fallbackText: '阶段完成 🌳',
    },
    streak_achieved: {
      text: '连续浇灌 {days} 天 💧',
      animation: 'fade',
      animationDuration: 700,
      sound: '/sounds/botanical-water.mp3',
      soundVolume: 0.6,
      haptic: 'light',
      fallbackText: '连续学习 {days} 天 💧',
    },
  },
  accessible: {
    answer_good: {
      text: '回答正确',
      animation: 'none',
      animationDuration: 0,
      sound: null,
      soundVolume: 0,
      haptic: 'none',
      fallbackText: '回答正确',
    },
    review_pass: {
      text: '评审通过',
      animation: 'none',
      animationDuration: 0,
      sound: null,
      soundVolume: 0,
      haptic: 'none',
      fallbackText: '评审通过',
    },
    stage_complete: {
      text: '阶段完成',
      animation: 'none',
      animationDuration: 0,
      sound: null,
      soundVolume: 0,
      haptic: 'none',
      fallbackText: '阶段完成',
    },
    streak_achieved: {
      text: '已连续学习 {days} 天',
      animation: 'none',
      animationDuration: 0,
      sound: null,
      soundVolume: 0,
      haptic: 'none',
      fallbackText: '已连续学习 {days} 天',
    },
  },
}

/**
 * 获取反馈演出配置
 * @param theme 当前主题
 * @param eventType 事件类型
 * @param variables 动态变量（如 {days}）
 * @returns 反馈效果配置
 */
export function getFeedbackEffect(
  theme: Theme,
  eventType: FeedbackEventType,
  variables?: Record<string, string | number>
): FeedbackEffect {
  const effect = FEEDBACK_MANIFEST[theme][eventType]
  if (!variables) return effect

  // 替换文案中的变量占位符
  const replaceVars = (text: string) =>
    Object.entries(variables).reduce((t, [k, v]) => t.replace(`{${k}}`, String(v)), text)

  return {
    ...effect,
    text: replaceVars(effect.text),
    subtext: effect.subtext ? replaceVars(effect.subtext) : undefined,
    fallbackText: replaceVars(effect.fallbackText),
  }
}
