<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue'
import {getTransitChart, interpretTransit, type TransitResponse} from '../../api/astrology'
import {useUserStore} from '../../store/user'

const userStore = useUserStore()

const step = ref<'form' | 'loading' | 'result'>('form')
const loadingText = ref('正在解析当前星体轨迹...')
const chartData = ref<TransitResponse | null>(null)
const interpretation = ref('')
const isInterpreting = ref(false)
const selectedInterpretType = ref('today_emotion')
const activeTab = ref<'today' | 'events' | 'interpret'>('today')
/**
 * 打字机版本计数器：每次发起新的解读时递增
 * 每个解读实例记住自己的 generation，循环时检查是否仍是最新版本
 */
let typingGeneration = 0

// 今日日期
const today = new Date()

// ── 目标日期选择 ──────────────────────────────────────
/** 用户选择的查询日期（yyyy-MM-dd），默认今日 */
const targetDateStr = ref<string>(formatDateToYMD(today))

/** 格式化为 yyyy-MM-dd */
function formatDateToYMD(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/** 格式化为展示文本 yyyy年M月d日 */
function formatDateDisplay(dateStr: string): string {
  if (!dateStr) return ''
  const [y, m, d] = dateStr.split('-')
  return `${y}年${parseInt(m)}月${parseInt(d)}日`
}

/** picker 的最小日期（2000-01-01）和最大日期（今后2年） */
const pickerMinDate = '2000-01-01'
const pickerMaxDate = (() => {
  const d = new Date()
  d.setFullYear(d.getFullYear() + 2)
  return formatDateToYMD(d)
})()

/** picker change 事件 */
function onDatePickerChange(e: any) {
  targetDateStr.value = e.detail.value
}

/** 是否为今日 */
const isToday = computed(() => targetDateStr.value === formatDateToYMD(today))

/** 日期展示文本 */
const targetDateDisplay = computed(() => {
  if (isToday.value) return formatDateDisplay(targetDateStr.value) + '（今日）'
  return formatDateDisplay(targetDateStr.value)
})

/** 结果页中展示的日期文本 */
const todayStr = computed(() => targetDateDisplay.value)

/**
 * 从 userStore 回填上次使用的目标日期
 */
function prefillTransitDateFromStore() {
  const u = userStore.userInfo
  if (u?.transitTargetDate) {
    targetDateStr.value = u.transitTargetDate
  }
}

onMounted(() => {
  prefillTransitDateFromStore()
})

// ── 出生信息判断 ──────────────────────────────────
function hasBirthInfo(): boolean {
  const info = userStore.userInfo
  return !!(info?.birthCity && info?.birthTime)
}

function getBirthInfoDisplay(): string {
  const info = userStore.userInfo
  if (!info?.birthCity || !info?.birthTime) return '未设置'
  return `${info.birthTime} · ${info.birthCity}`
}

// ── 跳转到出生信息设置页 ──────────────────────────
function goToNatalPage() {
  uni.navigateTo({ url: '/pages/astrology/natal' })
}

/**
 * 监听出生信息变化：当出生信息发生变更时，清空已有的流运结果
 * 因为出生信息变了，已有的流运结果不再有效
 */
watch(
  () => userStore.userInfo?.birthTime,
  (newVal, oldVal) => {
    if (oldVal && newVal !== oldVal) {
      chartData.value = null
      interpretation.value = ''
      if (step.value === 'result') {
        step.value = 'form'
      }
    }
  }
)

const INTERPRET_TYPES = [
  { key: 'today_emotion', label: '今日情绪', icon: '💭' },
  { key: 'relationship', label: '近期关系', icon: '💫' },
  { key: 'stress', label: '压力感知', icon: '⚡' },
  { key: 'growth', label: '成长方向', icon: '🌱' }
]

// ── 行星显示映射 ──────────────────────────────────────────
const PLANET_DISPLAY_T: Record<string, { symbol: string, name: string }> = {
  sun:          { symbol: '☉', name: '太阳' },
  moon:         { symbol: '☽', name: '月亮' },
  mercury:      { symbol: '☿', name: '水星' },
  venus:        { symbol: '♀', name: '金星' },
  mars:         { symbol: '♂', name: '火星' },
  jupiter:      { symbol: '♃', name: '木星' },
  saturn:       { symbol: '♄', name: '土星' },
  uranus:       { symbol: '⛢', name: '天王星' },
  neptune:      { symbol: '♆', name: '海王星' },
  pluto:        { symbol: '♇', name: '冥王星' },
  // 轴点与节点（兼容下划线和空格两种写法）
  north_node:   { symbol: '☊', name: '北交点' },
  south_node:   { symbol: '☋', name: '南交点' },
  'north node': { symbol: '☊', name: '北交点' },
  'south node': { symbol: '☋', name: '南交点' },
  ascendant:    { symbol: 'ASC', name: '上升点' },
  descendant:   { symbol: 'DSC', name: '下降点' },
  mc:           { symbol: 'MC', name: '天顶' },
  ic:           { symbol: 'IC', name: '天底' },
  midheaven:    { symbol: 'MC', name: '天顶' },
  chiron:       { symbol: '⚷', name: '凯龙星' },
}

// 相位名称映射（扩充）
const ASPECT_LABEL_T: Record<string, { label: string, symbol: string, harmony: 'positive' | 'challenge' | 'neutral' }> = {
  conjunction:    { label: '合相',       symbol: '☌', harmony: 'neutral' },
  sextile:        { label: '六分相',     symbol: '⚹', harmony: 'positive' },
  square:         { label: '四分相',     symbol: '□', harmony: 'challenge' },
  trine:          { label: '三分相',     symbol: '△', harmony: 'positive' },
  opposition:     { label: '对分相',     symbol: '☍', harmony: 'challenge' },
  quincunx:       { label: '十二分之五', symbol: '⚻', harmony: 'neutral' },
  semisextile:    { label: '十二分之一', symbol: '⚺', harmony: 'neutral' },
  sesquiquadrate: { label: '倍半四分',   symbol: '⚼', harmony: 'challenge' },
  semisquare:     { label: '八分相',     symbol: '∠', harmony: 'challenge' },
}

// 星座中文名映射
const ZODIAC_ZH_T: Record<string, string> = {
  aries:       '白羊座', taurus:      '金牛座', gemini:      '双子座',
  cancer:      '巨蟹座', leo:         '狮子座', virgo:       '处女座',
  libra:       '天秤座', scorpio:     '天蝎座', sagittarius: '射手座',
  capricorn:   '摩羯座', aquarius:    '水瓶座', pisces:      '双鱼座',
}
function zodiacZhT(en: string): string {
  return ZODIAC_ZH_T[en?.toLowerCase()?.trim()] || en || ''
}

/**
 * 从 chartData.events 中提取流运事件列表（真实数据）
 * 兼容多种 Python 返回字段名
 */
const realTransitEvents = computed(() => {
  const events = chartData.value?.events
  if (!events || !Array.isArray(events) || events.length === 0) return []
  return events.map((ev: any) => {
    // ── 行星字段名兼容 ─────────────────────────────────────
    const tpKey = (
      ev.transit_planet || ev.transiting_planet || ev.planet ||
      ev.t_planet || ev.tp || ev.body || ''
    ).toLowerCase().trim()
    const npKey = (
      ev.natal_planet || ev.natal_body || ev.native_planet ||
      ev.n_planet || ev.np || ''
    ).toLowerCase().trim()
    // ── 相位类型兼容 ─────────────────────────────────────
    const aspectType = (
      ev.aspect_type || ev.aspect || ev.type || ev.aspect_name || ''
    ).toLowerCase().trim()

    const tpDisplay = PLANET_DISPLAY_T[tpKey]
    const npDisplay = PLANET_DISPLAY_T[npKey]
    const aspectLabel = ASPECT_LABEL_T[aspectType]

    // 行星展示：带符号+名字
    const planetsLabel = tpDisplay ? `${tpDisplay.symbol} ${tpDisplay.name}` : tpKey || '行星'
    const natalLabel   = npDisplay ? `${npDisplay.symbol} ${npDisplay.name}` : npKey || ''
    const aspectStr    = aspectLabel ? `${aspectLabel.symbol} ${aspectLabel.label}` : (aspectType || '相位')
    const type         = aspectLabel?.harmony || 'neutral'

    // ── 容许度（orb）：角距，数值越小越精确 ─────────────────
    const orbVal = ev.orb ?? ev.orb_value ?? ev.exact_orb ?? null

    // ── 强度（strength）：0-1，数值越大影响越强 ───────────────
    const strengthVal = ev.strength ?? ev.intensity_value ?? null
    let intensity: 'strong' | 'medium' | 'weak' = 'medium'
    if (strengthVal != null) {
      const s = Number(strengthVal)
      intensity = s >= 0.7 ? 'strong' : s >= 0.3 ? 'medium' : 'weak'
    } else if (orbVal != null) {
      // 降级：用 orb 估算
      const o = Math.abs(Number(orbVal))
      intensity = o < 1 ? 'strong' : o < 3 ? 'medium' : 'weak'
    } else if (ev.intensity) {
      const raw = String(ev.intensity).toLowerCase()
      intensity = raw.includes('strong') || raw.includes('exact') ? 'strong'
        : raw.includes('weak') || raw.includes('loose') ? 'weak' : 'medium'
    }

    // ── impact 对象解析 ────────────────────────────────────
    const impact = ev.impact || {}
    const emotionVal: number | null = impact.emotion ?? null
    const pressureVal: number | null = impact.pressure ?? null
    const durationDays: number | null = impact.duration_days ?? ev.duration_days ?? null

    // ── tags ──────────────────────────────────────────────
    const tags: string[] = Array.isArray(ev.tags) ? ev.tags : []

    // ── 行星当前所在星座 ──────────────────────────────────
    const transitSign = zodiacZhT(ev.transit_sign || ev.t_sign || ev.sign || '')

    return {
      planets: planetsLabel,
      aspect: aspectStr,
      aspectRaw: aspectType,
      natal: natalLabel,
      transitSign,
      date: ev.date_range || ev.date || ev.period || '',
      durationDays,
      desc: ev.description || ev.interpretation || ev.meaning || '',
      orb: orbVal != null ? `${Math.abs(Number(orbVal)).toFixed(1)}°` : '',
      strength: strengthVal != null ? Math.round(Number(strengthVal) * 100) : null,
      emotionVal,
      pressureVal,
      tags,
      intensity,
      type
    }
  })
})

/**
 * 将 impact 数值转换为中文描述短句
 * emotion: -1(极负) ~ +1(极正)；pressure: 0(轻松) ~ 1(高压)
 */
function buildImpactDesc(
  emotionVal: number | null,
  pressureVal: number | null,
  tags: string[],
  harmony: 'positive' | 'challenge' | 'neutral',
  durationDays: number | null
): string {
  const parts: string[] = []

  if (emotionVal != null) {
    if (emotionVal >= 0.5) parts.push('情绪提升')
    else if (emotionVal >= 0.2) parts.push('情绪偏正向')
    else if (emotionVal <= -0.6) parts.push('情绪受压')
    else if (emotionVal <= -0.3) parts.push('情绪有挑战')
    else parts.push('情绪平稳')
  }

  if (pressureVal != null) {
    if (pressureVal >= 0.8) parts.push('压力感较强')
    else if (pressureVal >= 0.5) parts.push('压力中等')
    else if (pressureVal < 0.2) parts.push('轻松流动')
  }

  // tags 取前2个翻译展示
  const tagZh: Record<string, string> = {
    'self-other tension': '自我与他人的张力',
    'visibility': '关注度上升',
    'ego tension': '自我意识激活',
    'challenge': '需要突破',
    'inner world': '关注内心',
    'identity focus': '聚焦自我',
    'vitality': '活力增强',
    'confidence': '自信增加',
    'creative flow': '创意流动',
    'emotional resonance': '情感共鸣',
    'sensitivity': '感知敏锐',
    'self-expression': '自我表达',
    'emotional tension': '情绪波动',
    'mood swings': '心情起伏',
    'emotional conflict': '情感冲突',
    'vulnerability': '脆弱感',
    'comfort': '舒适安稳',
    'emotional ease': '情绪轻松',
    'career focus': '职业聚焦',
    'mental flow': '思维流畅',
    'insight': '洞察力强',
    'learning': '学习成长',
    'dialogue': '沟通顺畅',
    'mental clarity': '思维清晰',
    'communication': '表达沟通',
    'debate': '思想碰撞',
    'information conflict': '信息冲突',
    'attraction': '魅力吸引',
    'harmony': '和谐美好',
    'motivation': '动力充沛',
    'drive': '行动力强',
    'initiative': '主动出击',
    'action': '行动导向',
    'conflict': '冲突张力',
    'aggression': '冲动倾向',
    'discipline': '自律专注',
    'structure': '稳定建构',
    'challenge restriction': '限制挑战',
    'restriction': '受到制约',
    'patience': '耐心培养',
    'responsibility': '承担责任',
    'instability': '不稳定感',
    'rebellion': '突破常规',
    'revolution': '变革契机',
    'upheaval': '翻天覆地',
    'innovation': '创新突破',
    'freedom': '自由解放',
    'inspiration': '灵感涌现',
    'compassion': '慈悲共情',
    'intuition': '直觉敏锐',
    'creativity': '创意迸发',
    'evolution': '蜕变成长',
    'transformation': '深层转化',
    'power': '力量聚焦',
  }
  const tagTexts = tags.slice(0, 2).map(t => tagZh[t] || '').filter(Boolean)
  if (tagTexts.length > 0) parts.push(...tagTexts)

  if (durationDays != null) {
    if (durationDays >= 120) parts.push(`持续约${Math.round(durationDays/30)}个月`)
    else if (durationDays >= 14) parts.push(`持续约${Math.round(durationDays/7)}周`)
    else if (durationDays > 1) parts.push(`持续约${durationDays}天`)
    else parts.push('当日短暂影响')
  }

  return parts.length > 0 ? parts.join('，') : (harmony === 'positive' ? '正向流动' : harmony === 'challenge' ? '挑战张力' : '中性影响')
}

/**
 * 从 chartData.chart / summary 中提取重点流运行星（今日流运 Tab 展示）
 * 优先按 strength 排序，取最强的 5 个展示
 */
const realHighlights = computed(() => {
  const sum = chartData.value?.summary

  // ── 优先：summary.highlights ──────────────────────────
  const highlights = sum?.highlights || sum?.key_planets || sum?.featured_planets
  if (highlights && Array.isArray(highlights) && highlights.length > 0) {
    return highlights.slice(0, 5).map((h: any) => {
      const pKey = (h.planet || h.name || h.body || '').toLowerCase().trim()
      const pd = PLANET_DISPLAY_T[pKey]
      const aspectType = (h.aspect || h.aspect_type || '').toLowerCase()
      const aspectInfo = ASPECT_LABEL_T[aspectType]
      const harmony = h.harmony || aspectInfo?.harmony || 'neutral'
      return {
        symbol: pd?.symbol || h.symbol || '✦',
        name: pd?.name || h.name || pKey,
        aspect: aspectInfo ? `${aspectInfo.symbol} ${aspectInfo.label}` : (h.aspect || h.position || ''),
        impact: h.impact || h.description || h.interpretation || h.meaning || '',
        energy: h.energy ||
          (harmony === 'positive' ? 'positive' : harmony === 'challenge' ? 'caution' : 'deep'),
        sign: zodiacZhT(h.sign || h.transit_sign || ''),
        strength: null,
      }
    })
  }

  // ── 次优：chart.transits ──────────────────────────────
  const chartTransits = (chartData.value?.chart as any)?.transits
  if (chartTransits && Array.isArray(chartTransits) && chartTransits.length > 0) {
    return chartTransits.slice(0, 4).map((t: any) => {
      const pKey = (t.planet || t.body || t.transit_planet || '').toLowerCase().trim()
      const pd = PLANET_DISPLAY_T[pKey]
      const aspectType = (t.aspect || t.aspect_type || t.type || '').toLowerCase()
      const aspectInfo = ASPECT_LABEL_T[aspectType]
      const harmony = aspectInfo?.harmony || 'neutral'
      return {
        symbol: pd?.symbol || '✦',
        name: pd?.name || pKey,
        aspect: aspectInfo ? `${aspectInfo.symbol} ${aspectInfo.label}` : (aspectType || ''),
        impact: t.description || t.interpretation || t.impact || '',
        energy: harmony === 'positive' ? 'positive' : harmony === 'challenge' ? 'caution' : 'deep',
        sign: zodiacZhT(t.sign || t.transit_sign || ''),
        strength: null,
      }
    })
  }

  // ── 从 events 中按 strength 排序取最重要的 5 个 ──────────
  const events = chartData.value?.events
  if (!events || !Array.isArray(events)) return []

  // 按 strength 降序排序
  const sorted = [...events].sort((a: any, b: any) => {
    const sa = Number(a.strength ?? 0)
    const sb = Number(b.strength ?? 0)
    return sb - sa
  })

  return sorted.slice(0, 5).map((ev: any) => {
    const pKey = (
      ev.transit_planet || ev.transiting_planet || ev.planet ||
      ev.t_planet || ev.body || ''
    ).toLowerCase().trim()
    const npKey = (
      ev.natal_planet || ev.natal_body || ev.native_planet ||
      ev.n_planet || ev.np || ''
    ).toLowerCase().trim()
    const pd = PLANET_DISPLAY_T[pKey]
    const npDisplay = PLANET_DISPLAY_T[npKey]
    const aspectType = (ev.aspect_type || ev.aspect || ev.type || '').toLowerCase().trim()
    const aspectInfo = ASPECT_LABEL_T[aspectType]
    const harmony = aspectInfo?.harmony || 'neutral'
    const energy = harmony === 'positive' ? 'positive' : harmony === 'challenge' ? 'caution' : 'deep'

    const impact = ev.impact || {}
    const emotionVal: number | null = impact.emotion ?? null
    const pressureVal: number | null = impact.pressure ?? null
    const durationDays: number | null = impact.duration_days ?? ev.duration_days ?? null
    const tags: string[] = Array.isArray(ev.tags) ? ev.tags : []
    const strengthPct = ev.strength != null ? Math.round(Number(ev.strength) * 100) : null

    // 合相展示："行星 合相 本命行星"
    const natalSuffix = npDisplay ? `${npDisplay.symbol}${npDisplay.name}` : (npKey || '')
    const aspectStr = aspectInfo ? `${aspectInfo.symbol} ${aspectInfo.label}` : (aspectType || '')
    const nameDisplay = natalSuffix
      ? `${pd?.name || pKey} ${aspectStr} ${natalSuffix}`
      : (pd?.name || pKey || '行星')

    const impactText = ev.description || ev.interpretation || ev.meaning
      || buildImpactDesc(emotionVal, pressureVal, tags, harmony, durationDays)

    return {
      symbol: pd?.symbol || '✦',
      name: nameDisplay,
      aspect: aspectStr,
      impact: impactText,
      energy,
      sign: zodiacZhT(ev.transit_sign || ev.sign || ''),
      strength: strengthPct,
      durationDays,
    }
  })
})

// summary 字段映射
const EMOTIONAL_STATE_ZH: Record<string, { text: string, icon: string }> = {
  positive:     { text: '情绪积极向上，充满活力', icon: '😊' },
  negative:     { text: '情绪面临挑战，需要关怀自己', icon: '😔' },
  neutral:      { text: '情绪平稳，内心安定', icon: '😌' },
  mixed:        { text: '情绪交织复杂，起伏变化', icon: '🌊' },
  turbulent:    { text: '情绪波动较大，保持觉察', icon: '⚡' },
  calm:         { text: '情绪平静如水，适合沉淀', icon: '🌙' },
}

const ENERGY_LEVEL_ZH: Record<string, { text: string, icon: string }> = {
  high:         { text: '能量充沛，行动力强', icon: '🔥' },
  medium:       { text: '能量适中，稳步前行', icon: '⚡' },
  low:          { text: '能量偏低，适合休息蓄力', icon: '🌱' },
  very_high:    { text: '能量爆发，把握主动出击', icon: '🌟' },
  very_low:     { text: '能量低迷，需要好好休养', icon: '🌿' },
}

const LIFE_FOCUS_ZH: Record<string, { text: string, icon: string }> = {
  'inner world':    { text: '关注内在世界与自我成长', icon: '🌸' },
  'relationships':  { text: '人际关系是当前主题', icon: '💫' },
  'career':         { text: '职业与成就是当前焦点', icon: '🎯' },
  'creativity':     { text: '创意与表达正当其时', icon: '✨' },
  'healing':        { text: '疗愈与修复是当前需要', icon: '🌿' },
  'transformation': { text: '深层转变正在发生', icon: '🦋' },
  'communication':  { text: '沟通与表达是当前主题', icon: '💬' },
  'reflection':     { text: '反思与整合的好时机', icon: '🪞' },
}

/**
 * 今日星象关键主题（从 summary 提取文字摘要）
 * 适配后端返回的 emotional_state / energy_level / life_focus 字段
 */
const realSummaryThemes = computed(() => {
  const sum = chartData.value?.summary
  if (!sum) return []

  // ── 优先：key_themes / themes 数组 ──────────────────────
  const themes = sum.key_themes || sum.themes || sum.focus_areas || sum.highlights_text
  if (themes && Array.isArray(themes) && themes.length > 0) {
    return themes.slice(0, 4).map((t: any) => {
      if (typeof t === 'string') return { text: t, icon: '✦' }
      return { text: t.theme || t.title || t.text || String(t), icon: t.icon || '✦' }
    })
  }

  // ── 降级：使用 summary.description ──────────────────────
  const desc = sum.description || sum.overview || sum.summary_text
  if (desc && typeof desc === 'string') {
    return [{ text: desc, icon: '✦' }]
  }

  // ── 最终降级：从 emotional_state / energy_level / life_focus 构建 ──
  const result: { text: string, icon: string }[] = []

  const emotionalState = (sum.emotional_state || '').toLowerCase().trim()
  if (emotionalState) {
    const es = EMOTIONAL_STATE_ZH[emotionalState]
    result.push(es || { text: `情绪状态：${emotionalState}`, icon: '💭' })
  }

  const energyLevel = (sum.energy_level || '').toLowerCase().trim()
  if (energyLevel) {
    const el = ENERGY_LEVEL_ZH[energyLevel]
    result.push(el || { text: `能量水平：${energyLevel}`, icon: '⚡' })
  }

  const lifeFocus = (sum.life_focus || '').toLowerCase().trim()
  if (lifeFocus) {
    const lf = LIFE_FOCUS_ZH[lifeFocus]
    result.push(lf || { text: `当前焦点：${lifeFocus}`, icon: '🎯' })
  }

  return result
})

// energy_level 文字 → 百分比映射
const ENERGY_LEVEL_PCT: Record<string, number> = {
  very_low: 15, low: 30, medium: 55, high: 78, very_high: 95,
}

/**
 * 从 chartData.summary 中提取能量分布（0-100）
 * 支持数值类型和文字类型（high/medium/low 等）
 * 若 summary 中无数值，则从 events 统计整体情绪均值作为降级
 */
const realEnergy = computed(() => {
  const sum = chartData.value?.summary

  // ── 优先读取数值型字段 ─────────────────────────────────
  let overall: number | null = null
  let emotion: number | null = null
  let action: number | null = null
  let social: number | null = null

  if (sum) {
    // 数值型
    const ov = sum.overall_energy ?? sum.overall_score ?? null
    const em = sum.emotion_energy ?? sum.emotional_energy ?? null
    const ac = sum.action_energy ?? sum.action_score ?? null
    const so = sum.social_energy ?? sum.social_score ?? null

    if (ov != null && !isNaN(Number(ov))) overall = Math.round(Number(ov))
    if (em != null && !isNaN(Number(em))) emotion = Math.round(Number(em))
    if (ac != null && !isNaN(Number(ac))) action = Math.round(Number(ac))
    if (so != null && !isNaN(Number(so))) social = Math.round(Number(so))

    // ── 降级：energy_level 文字型 → 百分比 ─────────────────
    if (overall == null) {
      const elStr = (sum.energy_level || '').toLowerCase().trim()
      if (elStr && ENERGY_LEVEL_PCT[elStr] != null) {
        overall = ENERGY_LEVEL_PCT[elStr]
      }
    }
  }

  // ── 再降级：从 events 统计情绪/压力均值 ────────────────
  const events = chartData.value?.events
  if (events && Array.isArray(events) && events.length > 0) {
    if (emotion == null) {
      const emVals = events
        .map((ev: any) => ev.impact?.emotion)
        .filter((v: any) => v != null && !isNaN(Number(v)))
        .map(Number)
      if (emVals.length > 0) {
        const avg = emVals.reduce((a: number, b: number) => a + b, 0) / emVals.length
        // emotion 范围 -1~+1，转换为 0-100
        emotion = Math.round((avg + 1) / 2 * 100)
      }
    }
    if (action == null) {
      // 用 pressure 均值近似 action
      const prVals = events
        .map((ev: any) => ev.impact?.pressure)
        .filter((v: any) => v != null && !isNaN(Number(v)))
        .map(Number)
      if (prVals.length > 0) {
        const avg = prVals.reduce((a: number, b: number) => a + b, 0) / prVals.length
        action = Math.round(avg * 100)
      }
    }
  }

  if (overall == null && emotion == null && action == null && social == null) return null
  return { overall, emotion, action, social }
})

async function calculateTransit() {
  // 检查是否设置了出生信息
  if (!hasBirthInfo()) {
    uni.showModal({
      title: '未设置出生信息',
      content: '流运解读需要先设置出生信息，是否前往本命盘页面设置？',
      confirmText: '去设置',
      cancelText: '取消',
      success: (res) => {
        if (res.confirm) goToNatalPage()
      }
    })
    return
  }

  step.value = 'loading'
  const TEXTS = ['正在解析当前星体轨迹...', '连接今日宇宙能量...', '计算流运相位...', '生成能量地图...']
  let idx = 0
  const timer = setInterval(() => { loadingText.value = TEXTS[++idx % TEXTS.length] }, 1400)
  try {
    // 出生信息由后端从 user 表读取，前端传入用户选择的目标日期
    const result = await getTransitChart({ targetDate: targetDateStr.value })
    chartData.value = result

    // 计算成功后，将目标日期持久化到 userStore（本地缓存，方便下次回填）
    // 后端 AstrologyGatewayService 已自动写入 DB，此处同步到前端 store
    userStore.updateTransitDate(targetDateStr.value)
  } catch (e: any) {
    // 如果是出生信息未设置的错误（7001），提示去设置
    if (e?.code === 7001 || e?.message?.includes('出生信息')) {
      step.value = 'form'
      uni.showModal({
        title: '未设置出生信息',
        content: '请先设置出生信息再计算流运',
        confirmText: '去设置',
        cancelText: '取消',
        success: (res) => {
          if (res.confirm) goToNatalPage()
        }
      })
      clearInterval(timer)
      return
    }
    chartData.value = { events: [], summary: null, chart: null }
  } finally {
    clearInterval(timer)
    step.value = 'result'
  }
}

async function getInterpretation() {
  // 递增版本号，使所有之前的打字机实例在各自的循环中检测到已过期并退出
  const myGeneration = ++typingGeneration

  isInterpreting.value = true
  interpretation.value = ''
  activeTab.value = 'interpret'
  try {
    // chart 由后端从 DB 读取，前端只传 windowDays 和 focus
    const result = await interpretTransit({ windowDays: 30, focus: 'current', tone: 'gentle' })
    const text = result.interpretation
    for (let i = 0; i <= text.length; i++) {
      if (typingGeneration !== myGeneration) {
        interpretation.value = text
        return
      }
      await new Promise(r => setTimeout(r, 25))
      if (typingGeneration !== myGeneration) {
        interpretation.value = text
        return
      }
      interpretation.value = text.slice(0, i)
    }
  } catch {
    const mock = `💭 今天水星逆行的影响让你的思维可能转向内省，这不是推进新计划的最佳时机，但非常适合回顾与整理。\n\n木星正在与你的本命太阳形成柔和的拱相，这是一段内在力量被加强的时期，你的直觉和判断力比平时更加准确，可以信任自己的感受。\n\n今日情绪状态：较为内敛，但内心有稳定的支撑感。适合进行深度思考、写日记或与亲近的人进行真实的对话。避免重要决策的公开展示，但私下的沟通和整理效果会很好。🌙`
    for (let i = 0; i <= mock.length; i++) {
      if (typingGeneration !== myGeneration) {
        interpretation.value = mock
        return
      }
      await new Promise(r => setTimeout(r, 20))
      if (typingGeneration !== myGeneration) {
        interpretation.value = mock
        return
      }
      interpretation.value = mock.slice(0, i)
    }
  } finally {
    if (typingGeneration === myGeneration) {
      isInterpreting.value = false
    }
  }
}

function goInterpret() {
  activeTab.value = 'interpret'
  if (!interpretation.value) getInterpretation()
}
</script>

<template>
  <view class="transit-page">
    <!-- 星空背景 -->
    <view class="stars-bg">
      <view class="star" v-for="i in 30" :key="i" :style="{
        left: (i * 37 % 100) + '%',
        top: (i * 53 % 100) + '%',
        animationDelay: (i * 0.3 % 3) + 's',
        width: (i % 3 === 0 ? 3 : 2) + 'rpx',
        height: (i % 3 === 0 ? 3 : 2) + 'rpx',
        opacity: 0.3 + (i % 5) * 0.1
      }" />
    </view>
    <!-- 顶部光晕 -->
    <view class="top-glow" />

    <!-- ══ Loading ══════════════════════════════════════════ -->
    <view v-if="step === 'loading'" class="loading-screen">
      <view class="loading-cosmos">
        <!-- 外轨道行星 -->
        <view class="orbit orbit-3">
          <view class="orbital-planet op-blue" />
        </view>
        <view class="orbit orbit-2">
          <view class="orbital-planet op-purple" />
        </view>
        <view class="orbit orbit-1">
          <view class="orbital-planet op-gold" />
        </view>
        <!-- 中心土星符号 -->
        <view class="cosmos-center">
          <text class="cosmos-symbol">♄</text>
          <view class="cosmos-pulse" />
        </view>
      </view>
      <text class="loading-title">{{ loadingText }}</text>
      <text class="loading-sub">感应今日宇宙能量流动</text>
      <!-- 进度点 -->
      <view class="loading-dots">
        <view class="ldot" /><view class="ldot" /><view class="ldot" />
      </view>
    </view>

    <!-- ══ 表单页 ════════════════════════════════════════════ -->
    <scroll-view v-if="step === 'form'" class="page-scroll" scroll-y>
      <!-- 页面头部 -->
      <view class="form-hero">
        <view class="hero-icon-ring">
          <view class="hero-ring hr1" />
          <view class="hero-ring hr2" />
          <text class="hero-symbol">♄</text>
        </view>
        <text class="hero-title">流运解读</text>
        <text class="hero-date">{{ todayStr }}</text>
        <text class="hero-desc">天空行星如何与你的星盘共鸣</text>
      </view>

      <!-- 出生信息卡片（只读展示） -->
      <view class="info-card">
        <view class="card-header">
          <view class="card-header-left">
            <text class="card-icon-small">✦</text>
            <text class="card-title">出生信息</text>
          </view>
          <view class="edit-btn" @click="goToNatalPage">
            <text class="edit-btn-text">{{ hasBirthInfo() ? '✏ 修改' : '+ 去设置' }}</text>
          </view>
        </view>

        <view v-if="hasBirthInfo()" class="birth-rows">
          <view class="birth-row">
            <view class="birth-row-icon">
              <text>🕐</text>
            </view>
            <view class="birth-row-content">
              <text class="birth-row-label">出生时间</text>
              <text class="birth-row-value">{{ userStore.userInfo?.birthTime }}</text>
            </view>
          </view>
          <view class="birth-divider" />
          <view class="birth-row">
            <view class="birth-row-icon">
              <text>📍</text>
            </view>
            <view class="birth-row-content">
              <text class="birth-row-label">出生城市</text>
              <text class="birth-row-value">{{ userStore.userInfo?.birthCity }}</text>
            </view>
          </view>
          <view v-if="userStore.userInfo?.birthLat" class="birth-row birth-row-last">
            <view class="birth-row-icon">
              <text>🌐</text>
            </view>
            <view class="birth-row-content">
              <text class="birth-row-label">精确坐标</text>
              <text class="birth-row-value">{{ userStore.userInfo?.birthLat?.toFixed(4) }}°N, {{ userStore.userInfo?.birthLng?.toFixed(4) }}°E</text>
            </view>
          </view>
        </view>

        <view v-else class="birth-empty" @click="goToNatalPage">
          <text class="birth-empty-icon">✦</text>
          <text class="birth-empty-title">点击设置出生信息</text>
          <text class="birth-empty-hint">设置后才能进行流运解读</text>
        </view>
      </view>

      <!-- 目标日期选择 -->
      <view class="info-card date-card">
        <view class="card-header">
          <view class="card-header-left">
            <text class="card-icon-small">📅</text>
            <text class="card-title">查询日期</text>
          </view>
          <view v-if="!isToday" class="non-today-badge">
            <text class="non-today-text">非今日</text>
          </view>
        </view>
        <picker
          mode="date"
          :value="targetDateStr"
          :start="pickerMinDate"
          :end="pickerMaxDate"
          @change="onDatePickerChange"
        >
          <view class="date-picker-row">
            <view class="date-picker-left">
              <text class="date-picker-value">{{ targetDateDisplay }}</text>
            </view>
            <text class="date-picker-arrow">›</text>
          </view>
        </picker>
        <view v-if="!isToday" class="reset-today-row" @click="targetDateStr = formatDateToYMD(today)">
          <text class="reset-today-text">↩ 重置为今日</text>
        </view>
      </view>

      <!-- 提示卡 -->
      <view class="tip-card">
        <text class="tip-glyph">☽</text>
        <text class="tip-text">流运解读将自动使用你在本命盘中设置的出生信息，分析所选日期天空行星与本命盘的互动关系。</text>
      </view>

      <!-- 提交按钮 -->
      <view class="submit-wrap">
        <view class="submit-btn"
          :class="{ 'submit-disabled': !hasBirthInfo() }"
          @click="calculateTransit">
          <text class="submit-glyph">♄</text>
          <text class="submit-text">感应流运能量</text>
        </view>
      </view>

      <view style="height: 120rpx" />
    </scroll-view>

    <!-- ══ 结果页 ════════════════════════════════════════════ -->
    <view v-if="step === 'result'" class="result-page">
      <!-- 日期头部 -->
      <view class="result-header">
        <view class="result-badge-wrap">
          <view class="result-badge">{{ isToday ? '今日' : '流运' }}</view>
        </view>
        <view class="result-header-center">
          <text class="result-date">{{ todayStr }}</text>
        </view>
        <view v-if="realEnergy?.overall != null" class="result-energy">
          <text class="re-label">综合能量</text>
          <text class="re-val">{{ realEnergy.overall }}%</text>
        </view>
      </view>

      <!-- Tab 导航 -->
      <view class="tab-nav">
        <view class="tab-item" :class="{ 'tab-active': activeTab === 'today' }" @click="activeTab = 'today'">
          <text class="tab-text">今日流运</text>
          <view v-if="activeTab === 'today'" class="tab-line" />
        </view>
        <view class="tab-item" :class="{ 'tab-active': activeTab === 'events' }" @click="activeTab = 'events'">
          <text class="tab-text">流运事件</text>
          <view v-if="activeTab === 'events'" class="tab-line" />
        </view>
        <view class="tab-item" :class="{ 'tab-active': activeTab === 'interpret' }" @click="activeTab = 'interpret'">
          <text class="tab-text">AI 解读</text>
          <view v-if="activeTab === 'interpret'" class="tab-line" />
        </view>
      </view>

      <scroll-view class="result-scroll" scroll-y>

        <!-- ── 今日流运 Tab ───────────────────────────────── -->
        <view v-if="activeTab === 'today'" class="tab-panel">

          <!-- 能量仪表盘（进度条降级版，兼容性更好） -->
          <view v-if="realEnergy" class="energy-section">
            <text class="section-title">✦ 能量分布</text>
            <view class="energy-bars">
              <template v-for="item in [
                { key: 'overall', label: '综合', color: '#d4a847' },
                { key: 'emotion', label: '情绪', color: '#a87ed0' },
                { key: 'action',  label: '压力', color: '#e06060' },
                { key: 'social',  label: '社交', color: '#70b8e0' },
              ]" :key="item.key">
                <view v-if="realEnergy[item.key] != null" class="energy-bar-row">
                  <text class="energy-bar-label">{{ item.label }}</text>
                  <view class="energy-bar-track">
                    <view class="energy-bar-fill"
                      :style="{ width: realEnergy[item.key] + '%', background: item.color }" />
                  </view>
                  <text class="energy-bar-pct" :style="{ color: item.color }">{{ realEnergy[item.key] }}%</text>
                </view>
              </template>
            </view>
          </view>

          <!-- 今日星象关键主题 -->
          <view v-if="realSummaryThemes.length > 0" class="themes-section">
            <text class="section-title">✦ 今日星象主题</text>
            <view v-for="(theme, idx) in realSummaryThemes" :key="idx" class="theme-chip-t">
              <text class="theme-chip-icon">{{ theme.icon }}</text>
              <text class="theme-chip-text">{{ theme.text }}</text>
            </view>
          </view>

          <!-- 重点行星 -->
          <text class="section-title">✦ 当前重点行星</text>

          <view v-if="realHighlights.length === 0" class="empty-state">
            <text class="empty-icon">♄</text>
            <text class="empty-text">暂无流运行星数据</text>
          </view>

          <view v-for="(h, idx) in realHighlights" :key="idx" class="planet-card" :class="'pc-' + h.energy">
            <view class="planet-symbol-wrap" :class="'psw-' + h.energy">
              <text class="planet-symbol">{{ h.symbol }}</text>
            </view>
            <view class="planet-info">
              <view class="planet-title-row">
                <text class="planet-name">{{ h.name }}</text>
                <text v-if="h.sign" class="planet-sign-badge">{{ h.sign }}</text>
                <view v-if="h.aspect" class="aspect-badge" :class="'ab-' + h.energy">
                  <text class="aspect-text">{{ h.aspect }}</text>
                </view>
              </view>
              <text v-if="h.impact" class="planet-impact">{{ h.impact }}</text>
            </view>
          </view>
        </view>

        <!-- ── 流运事件 Tab ───────────────────────────────── -->
        <view v-if="activeTab === 'events'" class="tab-panel">
          <view v-if="realTransitEvents.length === 0" class="empty-state">
            <text class="empty-icon">📅</text>
            <text class="empty-text">暂无流运事件数据</text>
          </view>
          <view v-for="(ev, idx) in realTransitEvents" :key="idx" class="event-card" :class="'evc-' + ev.type">
            <!-- 卡片头部：行星相位 + 强度 -->
            <view class="event-header">
              <view class="event-planets">
                <view class="event-planet-wrap">
                  <text class="event-planet-name">{{ ev.planets }}</text>
                  <text v-if="ev.transitSign" class="event-planet-sign">{{ ev.transitSign }}</text>
                </view>
                <view class="event-aspect-tag" :class="'eat-' + ev.type">
                  <text class="event-aspect-text">{{ ev.aspect }}</text>
                </view>
                <text class="event-planet-name">{{ ev.natal }}</text>
              </view>
              <view class="event-right-meta">
                <view class="event-intensity-dot" :class="'eid-' + ev.intensity">
                  <text class="eid-text">{{ ev.intensity === 'strong' ? '强' : ev.intensity === 'weak' ? '弱' : '中' }}</text>
                </view>
                <text v-if="ev.orb" class="event-orb-text">{{ ev.orb }}</text>
              </view>
            </view>

            <!-- strength 进度条 -->
            <view v-if="ev.strength != null" class="event-strength-row">
              <text class="event-strength-label">影响强度</text>
              <view class="event-strength-track">
                <view class="event-strength-fill"
                  :class="'esf-' + ev.type"
                  :style="{ width: ev.strength + '%' }" />
              </view>
              <text class="event-strength-pct" :class="'esf-text-' + ev.type">{{ ev.strength }}%</text>
            </view>

            <!-- 持续时间 + orb -->
            <view class="event-meta">
              <text v-if="ev.durationDays != null" class="event-meta-text">
                ⏱ 持续 {{ ev.durationDays >= 120 ? Math.round(ev.durationDays/30) + '个月' : ev.durationDays >= 14 ? Math.round(ev.durationDays/7) + '周' : ev.durationDays + '天' }}
              </text>
              <text v-if="ev.date" class="event-meta-text">📅 {{ ev.date }}</text>
            </view>

            <!-- impact 情绪/压力迷你指示条 -->
            <view v-if="ev.emotionVal != null || ev.pressureVal != null" class="event-impact-bars">
              <view v-if="ev.emotionVal != null" class="event-impact-row">
                <text class="event-impact-label">情绪</text>
                <view class="event-impact-track">
                  <!-- 中心线 -->
                  <view class="event-impact-center" />
                  <!-- 情绪条：正值右延伸（绿），负值左延伸（红） -->
                  <view class="event-impact-fill-wrap">
                    <view
                      v-if="ev.emotionVal >= 0"
                      class="event-impact-pos"
                      :style="{ width: (ev.emotionVal * 50) + '%', left: '50%' }"
                    />
                    <view
                      v-else
                      class="event-impact-neg"
                      :style="{ width: (Math.abs(ev.emotionVal) * 50) + '%', right: '50%' }"
                    />
                  </view>
                </view>
                <text class="event-impact-val" :style="{ color: ev.emotionVal >= 0 ? '#40c878' : '#e05050' }">
                  {{ ev.emotionVal >= 0 ? '+' : '' }}{{ (ev.emotionVal * 100).toFixed(0) }}
                </text>
              </view>
              <view v-if="ev.pressureVal != null" class="event-impact-row">
                <text class="event-impact-label">压力</text>
                <view class="event-impact-track">
                  <view class="event-impact-fill-wrap">
                    <view
                      class="event-impact-pressure"
                      :style="{ width: (ev.pressureVal * 100) + '%' }"
                    />
                  </view>
                </view>
                <text class="event-impact-val" :style="{ color: ev.pressureVal >= 0.6 ? '#e08050' : '#70b8e0' }">
                  {{ (ev.pressureVal * 100).toFixed(0) }}%
                </text>
              </view>
            </view>

            <!-- tags 标签 -->
            <view v-if="ev.tags && ev.tags.length > 0" class="event-tags">
              <view v-for="(tag, ti) in ev.tags.slice(0, 4)" :key="ti" class="event-tag" :class="'etag-' + ev.type">
                <text class="event-tag-text">{{ tag }}</text>
              </view>
            </view>

            <!-- 文字描述（若有） -->
            <text v-if="ev.desc" class="event-desc">{{ ev.desc }}</text>
          </view>
        </view>

        <!-- ── AI 解读 Tab ───────────────────────────────── -->
        <view v-if="activeTab === 'interpret'" class="tab-panel interpret-panel">
          <!-- 解读类型选择 -->
          <view class="type-chips">
            <view v-for="t in INTERPRET_TYPES" :key="t.key"
              class="type-chip" :class="{ 'chip-active': selectedInterpretType === t.key }"
              @click="selectedInterpretType = t.key">
              <text class="chip-icon">{{ t.icon }}</text>
              <text class="chip-label">{{ t.label }}</text>
            </view>
          </view>

          <!-- 触发按钮 -->
          <view v-if="!interpretation && !isInterpreting" class="interpret-trigger" @click="getInterpretation">
            <view class="trigger-glow" />
            <text class="trigger-symbol">♄</text>
            <text class="trigger-title">感应今日流运能量</text>
            <text class="trigger-hint">AI 分析当前宇宙影响</text>
          </view>

          <!-- 解读内容 -->
          <view v-if="interpretation || isInterpreting" class="interpret-box">
            <view class="interpret-box-header">
              <text class="interpret-type-label">{{ INTERPRET_TYPES.find(t => t.key === selectedInterpretType)?.label }}</text>
              <view v-if="isInterpreting" class="typing-dots">
                <view class="tdot" /><view class="tdot" /><view class="tdot" />
              </view>
            </view>
            <text class="interpret-content">{{ interpretation }}</text>
          </view>

          <!-- 换个角度 -->
          <view v-if="interpretation && !isInterpreting" class="re-read-btn" @click="getInterpretation">
            <text class="re-read-text">↻ 换个角度解读</text>
          </view>
        </view>

        <view style="height: 180rpx" />
      </scroll-view>

      <!-- 底部操作栏 -->
      <view class="bottom-bar">
        <view class="bar-btn bar-outline" @click="step = 'form'">
          <text class="bar-btn-text">返回</text>
        </view>
        <view class="bar-btn bar-primary" @click="goInterpret">
          <text class="bar-glyph">♄</text>
          <text class="bar-btn-text">AI 解读流运</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style>
/* ═══════════════════════════════════════════════════════════
   流运解读页 · 深色宇宙风格
═══════════════════════════════════════════════════════════ */

:root {
  --page-bg: #0a0818;
  --gold: #d4a847;
  --gold-soft: rgba(212, 168, 71, 0.15);
  --gold-border: rgba(212, 168, 71, 0.25);
  --purple: #7c5cbf;
  --card-bg: rgba(255, 255, 255, 0.04);
  --card-border: rgba(255, 255, 255, 0.08);
  --text-primary: #f0eaf8;
  --text-secondary: rgba(240, 234, 248, 0.55);
  --text-muted: rgba(240, 234, 248, 0.35);
}

.transit-page {
  min-height: 100vh;
  background: linear-gradient(160deg, #0a0818 0%, #0d0b1e 40%, #0a1020 100%);
  position: relative;
  overflow: hidden;
}

/* ── 星空背景 ─────────────────────────────────────────── */
.stars-bg {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  pointer-events: none;
  z-index: 0;
}

.star {
  position: absolute;
  border-radius: 50%;
  background: #ffffff;
  animation: twinkle 3s ease-in-out infinite;
}

@keyframes twinkle {
  0%, 100% { opacity: 0.2; transform: scale(1); }
  50% { opacity: 0.8; transform: scale(1.3); }
}

.top-glow {
  position: fixed;
  top: -100rpx; left: 50%;
  transform: translateX(-50%);
  width: 600rpx; height: 400rpx;
  background: radial-gradient(ellipse at 50% 20%, rgba(124, 92, 191, 0.2) 0%, transparent 70%);
  pointer-events: none;
  z-index: 0;
}

/* ══ Loading ════════════════════════════════════════════ */
.loading-screen {
  height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 40rpx;
  position: relative;
  z-index: 1;
}

.loading-cosmos {
  position: relative;
  width: 240rpx; height: 240rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.orbit {
  position: absolute;
  border-radius: 50%;
  border: 1rpx solid rgba(212, 168, 71, 0.2);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  animation: orbitSpin linear infinite;
}

.orbit-1 { width: 120rpx; height: 120rpx; animation-duration: 4s; }
.orbit-2 { width: 180rpx; height: 180rpx; animation-duration: 8s; animation-direction: reverse; }
.orbit-3 { width: 240rpx; height: 240rpx; animation-duration: 14s; }

@keyframes orbitSpin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.orbital-planet {
  width: 14rpx; height: 14rpx;
  border-radius: 50%;
  margin-top: -7rpx;
}

.op-gold { background: radial-gradient(circle, #f5d080, #d4a847); box-shadow: 0 0 12rpx rgba(212, 168, 71, 0.8); }
.op-purple { background: radial-gradient(circle, #b08ee0, #7c5cbf); box-shadow: 0 0 12rpx rgba(124, 92, 191, 0.8); }
.op-blue { background: radial-gradient(circle, #80b8f0, #4080d0); box-shadow: 0 0 12rpx rgba(64, 128, 208, 0.8); }

.cosmos-center {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cosmos-symbol {
  font-size: 56rpx;
  color: #d4a847;
  z-index: 1;
  text-shadow: 0 0 30rpx rgba(212, 168, 71, 0.6);
}

.cosmos-pulse {
  position: absolute;
  width: 80rpx; height: 80rpx;
  border-radius: 50%;
  background: rgba(212, 168, 71, 0.1);
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { transform: scale(1); opacity: 0.5; }
  50% { transform: scale(1.5); opacity: 0; }
}

.loading-title {
  font-size: 30rpx;
  color: rgba(240, 234, 248, 0.8);
  letter-spacing: 2rpx;
}

.loading-sub {
  font-size: 24rpx;
  color: rgba(240, 234, 248, 0.35);
}

.loading-dots {
  display: flex;
  gap: 12rpx;
}

.ldot {
  width: 10rpx; height: 10rpx;
  border-radius: 50%;
  background: rgba(212, 168, 71, 0.6);
  animation: ldotBounce 1.4s ease-in-out infinite;
}

.ldot:nth-child(2) { animation-delay: 0.2s; }
.ldot:nth-child(3) { animation-delay: 0.4s; }

@keyframes ldotBounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.3; }
  40% { transform: scale(1); opacity: 1; }
}

/* ══ 表单页 ════════════════════════════════════════════ */
.page-scroll {
  position: relative;
  z-index: 1;
  padding: 0 28rpx;
}

/* 英雄区域 */
.form-hero {
  padding-top: 70rpx;
  padding-bottom: 44rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
}

.hero-icon-ring {
  position: relative;
  width: 110rpx; height: 110rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8rpx;
}

.hero-ring {
  position: absolute;
  border-radius: 50%;
  border: 1rpx solid rgba(212, 168, 71, 0.3);
}

.hr1 { width: 80rpx; height: 80rpx; animation: orbitSpin 8s linear infinite; }
.hr2 { width: 110rpx; height: 110rpx; animation: orbitSpin 16s linear infinite reverse; border-style: dashed; opacity: 0.5; }

.hero-symbol {
  font-size: 52rpx;
  color: #d4a847;
  text-shadow: 0 0 30rpx rgba(212, 168, 71, 0.5);
  z-index: 1;
}

.hero-title {
  font-size: 48rpx;
  color: #f0eaf8;
  font-weight: 700;
  letter-spacing: 3rpx;
}

.hero-date {
  font-size: 26rpx;
  color: #d4a847;
}

.hero-desc {
  font-size: 24rpx;
  color: rgba(240, 234, 248, 0.45);
  text-align: center;
  line-height: 1.6;
}

/* 信息卡片通用 */
.info-card {
  background: rgba(255, 255, 255, 0.04);
  border: 1rpx solid rgba(255, 255, 255, 0.08);
  border-radius: 24rpx;
  padding: 28rpx;
  margin-bottom: 18rpx;
  backdrop-filter: blur(10rpx);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 22rpx;
}

.card-header-left {
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.card-icon-small {
  font-size: 18rpx;
  color: #d4a847;
}

.card-title {
  font-size: 28rpx;
  color: #f0eaf8;
  font-weight: 600;
}

.edit-btn {
  background: rgba(212, 168, 71, 0.1);
  border: 1rpx solid rgba(212, 168, 71, 0.3);
  border-radius: 20rpx;
  padding: 9rpx 20rpx;
}

.edit-btn-text {
  font-size: 24rpx;
  color: #d4a847;
}

/* 出生信息行 */
.birth-rows {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.birth-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 14rpx 0;
}

.birth-row-last {
  border-bottom: none;
}

.birth-divider {
  height: 1rpx;
  background: rgba(255, 255, 255, 0.06);
}

.birth-row-icon {
  width: 40rpx;
  font-size: 24rpx;
  flex-shrink: 0;
}

.birth-row-content {
  flex: 1;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

.birth-row-label {
  font-size: 24rpx;
  color: rgba(240, 234, 248, 0.4);
}

.birth-row-value {
  font-size: 26rpx;
  color: #f0eaf8;
  font-weight: 500;
}

/* 出生信息为空 */
.birth-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  padding: 28rpx 0;
}

.birth-empty-icon {
  font-size: 40rpx;
  color: rgba(212, 168, 71, 0.5);
}

.birth-empty-title {
  font-size: 28rpx;
  color: #d4a847;
  font-weight: 600;
}

.birth-empty-hint {
  font-size: 23rpx;
  color: rgba(240, 234, 248, 0.35);
}

/* 日期卡片 */
.date-card .card-header {
  margin-bottom: 16rpx;
}

.non-today-badge {
  background: rgba(212, 168, 71, 0.1);
  border: 1rpx solid rgba(212, 168, 71, 0.3);
  border-radius: 10rpx;
  padding: 4rpx 14rpx;
}

.non-today-text {
  font-size: 22rpx;
  color: #d4a847;
}

.date-picker-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.05);
  border: 1rpx solid rgba(255, 255, 255, 0.10);
  border-radius: 16rpx;
  padding: 18rpx 22rpx;
}

.date-picker-left {
  flex: 1;
}

.date-picker-value {
  font-size: 30rpx;
  color: #f0eaf8;
  font-weight: 500;
}

.date-picker-arrow {
  font-size: 38rpx;
  color: #d4a847;
}

.reset-today-row {
  margin-top: 14rpx;
  text-align: right;
}

.reset-today-text {
  font-size: 23rpx;
  color: rgba(212, 168, 71, 0.7);
}

/* 提示卡 */
.tip-card {
  background: rgba(212, 168, 71, 0.05);
  border: 1rpx solid rgba(212, 168, 71, 0.15);
  border-radius: 16rpx;
  padding: 18rpx 22rpx;
  margin-bottom: 22rpx;
  display: flex;
  gap: 12rpx;
  align-items: flex-start;
}

.tip-glyph {
  font-size: 26rpx;
  color: #d4a847;
  flex-shrink: 0;
}

.tip-text {
  font-size: 23rpx;
  color: rgba(212, 168, 71, 0.7);
  line-height: 1.7;
}

/* 提交按钮 */
.submit-wrap {
  margin-top: 8rpx;
}

.submit-btn {
  background: linear-gradient(135deg, #c8983a, #a87830);
  border-radius: 24rpx;
  padding: 32rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14rpx;
  box-shadow: 0 8rpx 32rpx rgba(212, 168, 71, 0.3);
}

.submit-disabled {
  background: linear-gradient(135deg, #3a3535, #2a2828);
  box-shadow: none;
  opacity: 0.6;
}

.submit-glyph {
  font-size: 30rpx;
  color: rgba(255, 255, 255, 0.9);
}

.submit-text {
  font-size: 32rpx;
  color: white;
  font-weight: 600;
  letter-spacing: 3rpx;
}

/* ══ 结果页 ════════════════════════════════════════════ */
.result-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  position: relative;
  z-index: 1;
}

.result-header {
  padding: 16rpx 28rpx;
  display: flex;
  align-items: center;
  gap: 16rpx;
  flex-shrink: 0;
  background: rgba(10, 8, 24, 0.8);
  backdrop-filter: blur(20rpx);
  border-bottom: 1rpx solid rgba(255, 255, 255, 0.06);
}

.result-badge-wrap {
  flex-shrink: 0;
}

.result-badge {
  background: rgba(212, 168, 71, 0.15);
  border: 1rpx solid rgba(212, 168, 71, 0.35);
  border-radius: 14rpx;
  padding: 7rpx 18rpx;
  font-size: 22rpx;
  color: #d4a847;
  font-weight: 600;
}

.result-header-center {
  flex: 1;
}

.result-date {
  font-size: 26rpx;
  color: rgba(240, 234, 248, 0.65);
}

.result-energy {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 3rpx;
}

.re-label {
  font-size: 19rpx;
  color: rgba(240, 234, 248, 0.35);
}

.re-val {
  font-size: 28rpx;
  color: #d4a847;
  font-weight: 700;
}

/* Tab 导航 */
.tab-nav {
  display: flex;
  background: rgba(10, 8, 24, 0.7);
  border-bottom: 1rpx solid rgba(255, 255, 255, 0.06);
  flex-shrink: 0;
}

.tab-item {
  flex: 1;
  padding: 22rpx;
  text-align: center;
  position: relative;
}

.tab-text {
  font-size: 27rpx;
  color: rgba(240, 234, 248, 0.4);
}

.tab-active .tab-text {
  color: #d4a847;
  font-weight: 600;
}

.tab-line {
  position: absolute;
  bottom: 0; left: 20%; right: 20%;
  height: 3rpx;
  background: linear-gradient(90deg, transparent, #d4a847, transparent);
  border-radius: 2rpx;
}

.result-scroll {
  flex: 1;
  background: transparent;
}

.tab-panel {
  padding: 24rpx;
}

/* ── 能量分布（进度条版，替换 conic-gradient 圆环） ─── */
.energy-section {
  margin-bottom: 28rpx;
}

.section-title {
  font-size: 22rpx;
  color: rgba(212, 168, 71, 0.7);
  letter-spacing: 2rpx;
  display: block;
  margin-bottom: 18rpx;
}

.energy-bars {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.energy-bar-row {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.energy-bar-label {
  font-size: 22rpx;
  color: rgba(240, 234, 248, 0.55);
  width: 60rpx;
  flex-shrink: 0;
}

.energy-bar-track {
  flex: 1;
  height: 10rpx;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 5rpx;
  overflow: hidden;
}

.energy-bar-fill {
  height: 100%;
  border-radius: 5rpx;
  opacity: 0.85;
  transition: width 0.4s ease;
}

.energy-bar-pct {
  font-size: 22rpx;
  font-weight: 700;
  width: 60rpx;
  text-align: right;
  flex-shrink: 0;
}

/* ── 今日星象主题 ──────────────────────────────────── */
.themes-section {
  margin-bottom: 28rpx;
}

.theme-chip-t {
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
  padding: 14rpx 18rpx;
  background: rgba(212, 168, 71, 0.05);
  border: 1rpx solid rgba(212, 168, 71, 0.12);
  border-radius: 14rpx;
  margin-bottom: 10rpx;
}

.theme-chip-icon {
  font-size: 24rpx;
  color: #d4a847;
  flex-shrink: 0;
  margin-top: 2rpx;
}

.theme-chip-text {
  font-size: 24rpx;
  color: rgba(240, 234, 248, 0.65);
  line-height: 1.7;
  flex: 1;
}

/* ── 重点行星卡片 ──────────────────────────────────── */
.planet-card {
  display: flex;
  gap: 18rpx;
  align-items: flex-start;
  padding: 22rpx;
  border-radius: 20rpx;
  margin-bottom: 14rpx;
  border: 1rpx solid;
}

.pc-positive { background: rgba(64, 200, 120, 0.05); border-color: rgba(64, 200, 120, 0.2); }
.pc-caution { background: rgba(212, 168, 71, 0.06); border-color: rgba(212, 168, 71, 0.2); }
.pc-deep { background: rgba(124, 92, 191, 0.06); border-color: rgba(124, 92, 191, 0.2); }

.planet-symbol-wrap {
  width: 66rpx; height: 66rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.psw-positive { background: rgba(64, 200, 120, 0.12); border: 1rpx solid rgba(64, 200, 120, 0.3); }
.psw-caution { background: rgba(212, 168, 71, 0.12); border: 1rpx solid rgba(212, 168, 71, 0.3); }
.psw-deep { background: rgba(124, 92, 191, 0.12); border: 1rpx solid rgba(124, 92, 191, 0.3); }

.planet-symbol {
  font-size: 30rpx;
  color: #d4a847;
}

.planet-info { flex: 1; }

.planet-title-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 8rpx;
}

.planet-name {
  font-size: 28rpx;
  color: #f0eaf8;
  font-weight: 600;
}

.aspect-badge {
  padding: 4rpx 14rpx;
  border-radius: 20rpx;
}

.ab-positive { background: rgba(64, 200, 120, 0.15); border: 1rpx solid rgba(64, 200, 120, 0.3); }
.ab-caution { background: rgba(212, 168, 71, 0.15); border: 1rpx solid rgba(212, 168, 71, 0.3); }
.ab-deep { background: rgba(124, 92, 191, 0.15); border: 1rpx solid rgba(124, 92, 191, 0.3); }

.aspect-text {
  font-size: 21rpx;
  color: rgba(240, 234, 248, 0.7);
}

.planet-impact {
  font-size: 23rpx;
  color: rgba(240, 234, 248, 0.5);
  line-height: 1.72;
}

.planet-sign-badge {
  font-size: 19rpx;
  color: rgba(212, 168, 71, 0.75);
  background: rgba(212, 168, 71, 0.08);
  border: 1rpx solid rgba(212, 168, 71, 0.2);
  border-radius: 10rpx;
  padding: 2rpx 12rpx;
}

/* ── 流运事件卡片 ──────────────────────────────────── */
.event-card {
  padding: 22rpx;
  border-radius: 20rpx;
  margin-bottom: 14rpx;
  border: 1rpx solid;
  background: rgba(255, 255, 255, 0.03);
}

.evc-positive { border-color: rgba(64, 200, 120, 0.2); }
.evc-challenge { border-color: rgba(220, 80, 80, 0.2); }
.evc-neutral { border-color: rgba(124, 92, 191, 0.2); }

.event-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12rpx;
}

.event-planets {
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.event-planet-name {
  font-size: 26rpx;
  color: #f0eaf8;
  font-weight: 600;
}

.event-aspect-tag {
  padding: 4rpx 14rpx;
  border-radius: 20rpx;
}

.eat-positive { background: rgba(64, 200, 120, 0.12); }
.eat-challenge { background: rgba(220, 80, 80, 0.12); }
.eat-neutral { background: rgba(124, 92, 191, 0.12); }

.event-aspect-text {
  font-size: 21rpx;
  color: rgba(240, 234, 248, 0.6);
}

.event-planet-wrap {
  display: flex;
  flex-direction: column;
  gap: 2rpx;
}

.event-planet-sign {
  font-size: 18rpx;
  color: rgba(212, 168, 71, 0.6);
}

.event-right-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4rpx;
  flex-shrink: 0;
}

.event-orb-text {
  font-size: 17rpx;
  color: rgba(240, 234, 248, 0.3);
}

.event-intensity-dot {
  width: 40rpx; height: 40rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.eid-strong { background: rgba(220, 80, 80, 0.15); border: 1rpx solid rgba(220, 80, 80, 0.3); }
.eid-medium { background: rgba(212, 168, 71, 0.12); border: 1rpx solid rgba(212, 168, 71, 0.25); }
.eid-weak   { background: rgba(124, 92, 191, 0.10); border: 1rpx solid rgba(124, 92, 191, 0.2); }

.eid-text {
  font-size: 19rpx;
  color: rgba(240, 234, 248, 0.6);
}

.event-meta {
  display: flex;
  gap: 22rpx;
  margin-bottom: 10rpx;
}

.event-meta-text {
  font-size: 21rpx;
  color: rgba(240, 234, 248, 0.35);
}

.event-desc {
  font-size: 24rpx;
  color: rgba(240, 234, 248, 0.5);
  line-height: 1.78;
}

/* ── strength 进度条 ──────────────────────────────────── */
.event-strength-row {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-bottom: 12rpx;
}

.event-strength-label {
  font-size: 20rpx;
  color: rgba(240, 234, 248, 0.35);
  width: 64rpx;
  flex-shrink: 0;
}

.event-strength-track {
  flex: 1;
  height: 6rpx;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 3rpx;
  overflow: hidden;
}

.event-strength-fill {
  height: 100%;
  border-radius: 3rpx;
  opacity: 0.8;
}

.esf-positive { background: linear-gradient(90deg, #40c878, #60e8a0); }
.esf-challenge { background: linear-gradient(90deg, #e05050, #e87070); }
.esf-neutral { background: linear-gradient(90deg, #7c5cbf, #a080e0); }

.event-strength-pct {
  font-size: 19rpx;
  font-weight: 700;
  width: 48rpx;
  text-align: right;
  flex-shrink: 0;
}

.esf-text-positive { color: #40c878; }
.esf-text-challenge { color: #e05050; }
.esf-text-neutral { color: #a080e0; }

/* ── impact 情绪/压力迷你条 ────────────────────────────── */
.event-impact-bars {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  margin-bottom: 12rpx;
  padding: 12rpx 0;
  border-top: 1rpx solid rgba(255, 255, 255, 0.05);
}

.event-impact-row {
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.event-impact-label {
  font-size: 19rpx;
  color: rgba(240, 234, 248, 0.3);
  width: 36rpx;
  flex-shrink: 0;
}

.event-impact-track {
  flex: 1;
  height: 7rpx;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 4rpx;
  position: relative;
  overflow: hidden;
}

.event-impact-center {
  position: absolute;
  left: 50%; top: 0;
  width: 1rpx; height: 100%;
  background: rgba(255, 255, 255, 0.12);
}

.event-impact-fill-wrap {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
}

.event-impact-pos {
  position: absolute;
  top: 0; bottom: 0;
  background: rgba(64, 200, 120, 0.7);
  border-radius: 0 4rpx 4rpx 0;
}

.event-impact-neg {
  position: absolute;
  top: 0; bottom: 0;
  background: rgba(220, 80, 80, 0.7);
  border-radius: 4rpx 0 0 4rpx;
}

.event-impact-pressure {
  position: absolute;
  top: 0; left: 0; bottom: 0;
  background: linear-gradient(90deg, rgba(112, 184, 224, 0.6), rgba(224, 128, 80, 0.7));
  border-radius: 4rpx;
}

.event-impact-val {
  font-size: 18rpx;
  font-weight: 600;
  width: 52rpx;
  text-align: right;
  flex-shrink: 0;
}

/* ── tags 标签 ────────────────────────────────────────── */
.event-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
  margin-top: 10rpx;
}

.event-tag {
  padding: 4rpx 14rpx;
  border-radius: 20rpx;
  border: 1rpx solid;
}

.etag-positive {
  background: rgba(64, 200, 120, 0.06);
  border-color: rgba(64, 200, 120, 0.2);
}
.etag-challenge {
  background: rgba(220, 80, 80, 0.06);
  border-color: rgba(220, 80, 80, 0.2);
}
.etag-neutral {
  background: rgba(124, 92, 191, 0.06);
  border-color: rgba(124, 92, 191, 0.2);
}

.event-tag-text {
  font-size: 19rpx;
  color: rgba(240, 234, 248, 0.45);
}

/* ── AI 解读面板 ───────────────────────────────────── */
.interpret-panel { }

.type-chips {
  display: flex;
  gap: 12rpx;
  flex-wrap: wrap;
  margin-bottom: 24rpx;
}

.type-chip {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 11rpx 20rpx;
  border-radius: 30rpx;
  background: rgba(255, 255, 255, 0.05);
  border: 1rpx solid rgba(255, 255, 255, 0.1);
}

.chip-active {
  background: rgba(212, 168, 71, 0.12);
  border-color: rgba(212, 168, 71, 0.4);
}

.chip-icon { font-size: 22rpx; }

.chip-label {
  font-size: 24rpx;
  color: rgba(240, 234, 248, 0.5);
}

.chip-active .chip-label {
  color: #d4a847;
}

/* 触发按钮 */
.interpret-trigger {
  position: relative;
  background: rgba(124, 92, 191, 0.08);
  border: 1rpx solid rgba(124, 92, 191, 0.2);
  border-radius: 24rpx;
  padding: 50rpx 28rpx;
  text-align: center;
  overflow: hidden;
}

.trigger-glow {
  position: absolute;
  top: -60rpx; left: 50%;
  transform: translateX(-50%);
  width: 240rpx; height: 240rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(124, 92, 191, 0.2), transparent);
}

.trigger-symbol {
  font-size: 52rpx;
  color: #d4a847;
  display: block;
  margin-bottom: 16rpx;
  text-shadow: 0 0 20rpx rgba(212, 168, 71, 0.5);
}

.trigger-title {
  font-size: 32rpx;
  color: #f0eaf8;
  font-weight: 600;
  display: block;
  margin-bottom: 10rpx;
}

.trigger-hint {
  font-size: 24rpx;
  color: rgba(240, 234, 248, 0.4);
  display: block;
}

/* 解读内容框 */
.interpret-box {
  background: rgba(255, 255, 255, 0.04);
  border: 1rpx solid rgba(255, 255, 255, 0.08);
  border-radius: 20rpx;
  padding: 26rpx;
}

.interpret-box-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18rpx;
}

.interpret-type-label {
  font-size: 25rpx;
  color: #d4a847;
  font-weight: 600;
}

.typing-dots {
  display: flex;
  gap: 7rpx;
  align-items: center;
}

.tdot {
  width: 9rpx; height: 9rpx;
  border-radius: 50%;
  background: #d4a847;
  animation: ldotBounce 1.2s ease-in-out infinite;
}

.tdot:nth-child(2) { animation-delay: 0.2s; }
.tdot:nth-child(3) { animation-delay: 0.4s; }

.interpret-content {
  font-size: 27rpx;
  color: rgba(240, 234, 248, 0.8);
  line-height: 2;
  white-space: pre-wrap;
}

/* 换个角度 */
.re-read-btn {
  text-align: center;
  padding: 22rpx;
  margin-top: 16rpx;
  border: 1rpx solid rgba(212, 168, 71, 0.25);
  border-radius: 16rpx;
  background: rgba(212, 168, 71, 0.05);
}

.re-read-text {
  font-size: 25rpx;
  color: rgba(212, 168, 71, 0.8);
}

/* ── 空状态 ────────────────────────────────────────── */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14rpx;
  padding: 60rpx 0;
  opacity: 0.6;
}

.empty-icon {
  font-size: 44rpx;
  color: rgba(212, 168, 71, 0.4);
}

.empty-text {
  font-size: 26rpx;
  color: rgba(240, 234, 248, 0.35);
}

/* ══ 底部操作栏 ══════════════════════════════════════ */
.bottom-bar {
  padding: 18rpx 24rpx;
  padding-bottom: calc(18rpx + env(safe-area-inset-bottom));
  display: flex;
  gap: 14rpx;
  background: rgba(10, 8, 24, 0.92);
  backdrop-filter: blur(20rpx);
  border-top: 1rpx solid rgba(255, 255, 255, 0.06);
  flex-shrink: 0;
}

.bar-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  padding: 24rpx;
  border-radius: 18rpx;
}

.bar-outline {
  flex: 1;
  border: 1rpx solid rgba(212, 168, 71, 0.3);
  background: rgba(212, 168, 71, 0.05);
}

.bar-primary {
  flex: 2;
  background: linear-gradient(135deg, #c8983a, #a87830);
  box-shadow: 0 4rpx 20rpx rgba(212, 168, 71, 0.3);
}

.bar-glyph {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.9);
}

.bar-btn-text {
  font-size: 28rpx;
  color: #f0eaf8;
  font-weight: 600;
}
</style>

