/**
 * 情绪工具函数
 */

export const EMOTION_MAP: Record<string, { label: string; emoji: string; color: string }> = {
  anxiety: { label: '焦虑', emoji: '😰', color: '#f59e0b' },
  depression: { label: '抑郁', emoji: '😔', color: '#6366f1' },
  anger: { label: '愤怒', emoji: '😤', color: '#ef4444' },
  loneliness: { label: '孤独', emoji: '😞', color: '#8b5cf6' },
  sadness: { label: '悲伤', emoji: '😢', color: '#3b82f6' },
  happiness: { label: '快乐', emoji: '😊', color: '#22c55e' },
  fear: { label: '恐惧', emoji: '😨', color: '#f97316' },
  neutral: { label: '平静', emoji: '😐', color: '#94a3b8' },
  stress: { label: '压力', emoji: '😩', color: '#dc2626' }
}

export const PERSONALITY_MAP: Record<string, { label: string; desc: string; emoji: string }> = {
  // 新版：4 种风格 × 男女各一
  gentle_female:   { label: '小柔', desc: '温柔陪伴，细腻共情', emoji: '🌸' },
  gentle_male:     { label: '阿暖', desc: '温暖守护，踏实陪伴', emoji: '☀️' },
  rational_female: { label: '知微', desc: '冷静分析，清醒引导', emoji: '🎯' },
  rational_male:   { label: '林析', desc: '逻辑清晰，引导成长', emoji: '📐' },
  snarky_female:   { label: '辣辣', desc: '毒嘴心软，笑中化解', emoji: '😏' },
  snarky_male:     { label: '损哥', desc: '损嘴暖心，搞笑减压', emoji: '😤' },
  midnight_female: { label: '夜笙', desc: '深夜守候，静静陪伴', emoji: '🌙' },
  midnight_male:   { label: '深渊', desc: '沉默守护，深夜同行', emoji: '🌊' },
}

export function getEmotionInfo(code: string) {
  return EMOTION_MAP[code] || EMOTION_MAP.neutral
}

/**
 * 根据 code 获取人格展示信息，找不到时返回通用兜底（展示 code 本身）
 * 推荐：有接口数据时直接用接口字段，此函数作为离线/历史兼容兜底
 */
export function getPersonalityInfo(code: string): { label: string; desc: string; emoji: string } {
  return PERSONALITY_MAP[code] || { label: code, desc: '', emoji: '🤖' }
}

/**
 * 跨平台日期解析：将 "yyyy-MM-dd HH:mm:ss" 转为 iOS 兼容的 ISO 8601 格式
 * iOS Safari 不支持 "yyyy-MM-dd HH:mm:ss"（空格分隔），需替换为 "T" 连接
 */
export function parseDate(dateStr: string): Date {
  if (!dateStr) return new Date(NaN)
  // "2026-05-28 12:38:30" → "2026-05-28T12:38:30"
  return new Date(dateStr.replace(' ', 'T'))
}

export function formatDate(dateStr: string): string {
  const date = parseDate(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()

  if (diff < 60 * 1000) return '刚刚'
  if (diff < 60 * 60 * 1000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 24 * 60 * 60 * 1000) return `${Math.floor(diff / 3600000)}小时前`
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

