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
  gentle_sister: { label: '温柔姐姐', desc: '温柔陪伴，细腻共情', emoji: '🌸' },
  rational_mentor: { label: '理性导师', desc: '冷静分析，引导成长', emoji: '🎯' },
  snarky_friend: { label: '毒舌朋友', desc: '搞笑吐槽，化解压力', emoji: '😏' },
  midnight_hollow: { label: '深夜树洞', desc: '安静倾听，深夜陪伴', emoji: '🌙' }
}

export function getEmotionInfo(code: string) {
  return EMOTION_MAP[code] || EMOTION_MAP.neutral
}

export function getPersonalityInfo(code: string) {
  return PERSONALITY_MAP[code] || PERSONALITY_MAP.gentle_sister
}

export function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()

  if (diff < 60 * 1000) return '刚刚'
  if (diff < 60 * 60 * 1000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 24 * 60 * 60 * 1000) return `${Math.floor(diff / 3600000)}小时前`
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

