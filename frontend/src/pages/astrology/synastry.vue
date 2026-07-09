<script setup lang="ts">
import {computed, onMounted, reactive, ref, watch} from 'vue'
import {getSynastryChart, interpretSynastry, type SynastryResponse} from '@/api/astrology'
import {updateSynastryPartner} from '@/api/auth'
import {useUserStore} from '@/store/user'

// ── 行星显示映射 ──────────────────────────────────────────
const PLANET_DISPLAY_S: Record<string, { symbol: string, name: string }> = {
  sun:     { symbol: '☉', name: '太阳' },
  moon:    { symbol: '☽', name: '月亮' },
  mercury: { symbol: '☿', name: '水星' },
  venus:   { symbol: '♀', name: '金星' },
  mars:    { symbol: '♂', name: '火星' },
  jupiter: { symbol: '♃', name: '木星' },
  saturn:  { symbol: '♄', name: '土星' },
  uranus:  { symbol: '⛢', name: '天王星' },
  neptune: { symbol: '♆', name: '海王星' },
  pluto:   { symbol: '♇', name: '冥王星' },
  // 额外节点
  'north node': { symbol: '☊', name: '北交点' },
  'south node': { symbol: '☋', name: '南交点' },
  chiron:       { symbol: '⚷', name: '凯龙星' },
  // 轴点：Python 库(kerykeion/flatlib)可能以多种名称返回 ASC / MC / IC / DC
  ascendant:      { symbol: 'AC', name: '上升点' },
  asc:            { symbol: 'AC', name: '上升点' },
  'asc.':         { symbol: 'AC', name: '上升点' },
  as:             { symbol: 'AC', name: '上升点' },
  midheaven:      { symbol: 'MC', name: '天顶' },
  mc:             { symbol: 'MC', name: '天顶' },
  'mc.':          { symbol: 'MC', name: '天顶' },
  'medium coeli': { symbol: 'MC', name: '天顶' },
  // IC（天底）——Python 返回 "IC" / "Imum Coeli" 等
  ic:             { symbol: 'IC', name: '天底' },
  'ic.':          { symbol: 'IC', name: '天底' },
  'imum coeli':   { symbol: 'IC', name: '天底' },
  // DC（下降点）
  descendant:     { symbol: 'DC', name: '下降点' },
  dc:             { symbol: 'DC', name: '下降点' },
  'dc.':          { symbol: 'DC', name: '下降点' },
}

// 相位名称映射
const ASPECT_LABEL_S: Record<string, { label: string, symbol: string, harmony: 'positive' | 'challenge' | 'neutral' }> = {
  conjunction:    { label: '合相',       symbol: '☌', harmony: 'neutral' },
  sextile:        { label: '六分相',     symbol: '⚹', harmony: 'positive' },
  square:         { label: '四分相',     symbol: '□', harmony: 'challenge' },
  trine:          { label: '三分相',     symbol: '△', harmony: 'positive' },
  opposition:     { label: '对分相',     symbol: '☍', harmony: 'challenge' },
  quincunx:       { label: '十二分之五', symbol: '⚻', harmony: 'neutral' },
  semisextile:    { label: '十二分之一', symbol: '⚺', harmony: 'neutral' },
  sesquiquadrate: { label: '倍半四分',   symbol: '⚼', harmony: 'challenge' },
  semisquare:     { label: '八分相',     symbol: '∠', harmony: 'challenge' },
  quintile:       { label: '五分相',     symbol: 'Q',  harmony: 'positive' },
  biquintile:     { label: '二五分相',   symbol: 'bQ', harmony: 'positive' },
}

// 星座中文名映射（英文小写 → 中文）
const ZODIAC_ZH_S: Record<string, string> = {
  aries:       '白羊座', taurus:      '金牛座', gemini:      '双子座',
  cancer:      '巨蟹座', leo:         '狮子座', virgo:       '处女座',
  libra:       '天秤座', scorpio:     '天蝎座', sagittarius: '射手座',
  capricorn:   '摩羯座', aquarius:    '水瓶座', pisces:      '双鱼座',
}
function zodiacZhS(en: string): string {
  return ZODIAC_ZH_S[en?.toLowerCase()?.trim()] || en || ''
}

const userStore = useUserStore()

const step = ref<'form' | 'loading' | 'result'>('form')
const loadingText = ref('正在解析双人星盘能量...')
const chartData = ref<SynastryResponse | null>(null)
const interpretation = ref('')
const isInterpreting = ref(false)
const selectedInterpretType = ref('love')
const activeTab = ref<'analysis' | 'aspects' | 'themes' | 'interpret'>('analysis')
/** 版本计数器：每次发起新请求时自增，旧实例通过比较版本号安全退出，彻底解决竞态问题 */
let typingGeneration = 0

const YEARS = Array.from({ length: 80 }, (_, i) => 1950 + i)
const MONTHS = Array.from({ length: 12 }, (_, i) => i + 1)
const DAYS = Array.from({ length: 31 }, (_, i) => i + 1)
const HOURS = Array.from({ length: 24 }, (_, i) => i)
const MINUTES = Array.from({ length: 60 }, (_, i) => i)
const YEAR_OPTIONS = YEARS.map(y => ({ label: y + '年', value: y }))
const MONTH_OPTIONS = MONTHS.map(m => ({ label: m + '月', value: m }))
const DAY_OPTIONS = DAYS.map(d => ({ label: d + '日', value: d }))
const HOUR_OPTIONS = HOURS.map(h => ({ label: h + '时', value: h }))
const MINUTE_OPTIONS = MINUTES.map(m => ({ label: String(m).padStart(2, '0') + '分', value: m }))

/** 判断用户是否已设置出生信息 */
function hasBirthInfo(): boolean {
  const u = userStore.userInfo
  return !!(u?.birthCity && u?.birthTime && u?.birthLat && u?.birthLng)
}

/** 判断是否已设置对方信息 */
function hasSynastryPartner(): boolean {
  const u = userStore.userInfo
  return !!(u?.synastryPartnerCity && u?.synastryPartnerTime)
}

/** 跳转到本命盘页（设置出生信息入口） */
function goToNatalPage() {
  uni.navigateTo({ url: '/pages/astrology/natal' })
}

// ── 对方信息表单 ────────────────────────────────────────
const partnerForm = reactive({ year: 1997, month: 3, day: 22, hour: 10, minute: 0, city: '', lat: null as number | null, lng: null as number | null })
const partnerName = ref('Ta')

function onPartnerYearChange(e: any) { partnerForm.year = YEAR_OPTIONS[e.detail.value].value }
function onPartnerMonthChange(e: any) { partnerForm.month = MONTH_OPTIONS[e.detail.value].value }
function onPartnerDayChange(e: any) { partnerForm.day = DAY_OPTIONS[e.detail.value].value }
function onPartnerHourChange(e: any) { partnerForm.hour = HOUR_OPTIONS[e.detail.value].value }
function onPartnerMinuteChange(e: any) { partnerForm.minute = MINUTE_OPTIONS[e.detail.value].value }

const INTERPRET_TYPES = [
  { key: 'love', label: '爱情关系', icon: '💞' },
  { key: 'marriage', label: '婚姻契合', icon: '💍' },
  { key: 'soul', label: '灵魂连接', icon: '✨' },
  { key: 'emotion', label: '情绪共鸣', icon: '🌊' }
]

// ── 从真实数据提取关系分析维度 ──────────────────────────────

/**
 * 将原始分数归一化为 0-100 的百分比。
 * @param raw    原始分数（如 28.79）
 * @param refMax 参考上限（可选）。传入时做线性映射；不传时自动判断量级。
 */
function normalizeScore(raw: number, refMax?: number): number {
  if (refMax !== undefined && refMax !== null) {
    if (refMax <= 0) return 0
    const mapped = Math.round((raw / refMax) * 100)
    return Math.max(0, Math.min(100, mapped))
  }
  if (raw <= 1) return Math.round(raw * 100)
  if (raw <= 100) return Math.max(0, Math.min(100, Math.round(raw)))
  return 100
}

/**
 * 从 chartData.relationshipModel 中提取关系分析维度数据。
 * 优先读取 Python rule_based_v1 规范的字段名：
 *   attraction_score / emotional_score / conflict_score / stability_score
 * 各维度分数已经是 0-100 范围内的百分比（如 28.79），直接四舍五入为整数展示。
 * 兼容旧字段名作为降级。
 */
const realAnalysis = computed(() => {
  const rm = chartData.value?.relationshipModel
  if (!rm) return []
  const items: Array<{ label: string, value: number, color: string, desc: string }> = []

  // 吸引力（0-100 百分比，如 28.79 → 29）
  const attractionRaw = rm.attraction_score ?? rm.attraction ?? rm.magnetic_attraction
  if (attractionRaw != null) {
    const val = normalizeScore(Number(attractionRaw), 100)
    items.push({ label: '吸引力', value: val, color: '#e070a0', desc: rm.attraction_desc || '双方星盘间的吸引强度' })
  }

  // 情绪匹配（0-100 百分比，如 20.11 → 20）
  const emotionalRaw = rm.emotional_score ?? rm.emotional_compatibility ?? rm.emotional_match ?? rm.emotion_score
  if (emotionalRaw != null) {
    const val = normalizeScore(Number(emotionalRaw),  100)
    items.push({ label: '情绪匹配', value: val, color: '#7080f0', desc: rm.emotion_desc || '情感波动的共鸣程度' })
  }

  // 冲突指数（0-100 百分比，如 38.27 → 38）
  const conflictRaw = rm.conflict_score ?? rm.conflict_index ?? rm.tension
  if (conflictRaw != null) {
    const val = normalizeScore(Number(conflictRaw),   100)
    items.push({ label: '冲突指数', value: val, color: '#f09040', desc: rm.conflict_desc || '关系中的摩擦与张力' })
  }

  // 长期稳定（0-100 百分比）
  const stabilityRaw = rm.stability_score ?? rm.long_term_stability ?? rm.stability
  if (stabilityRaw != null) {
    const val = normalizeScore(Number(stabilityRaw),  100)
    items.push({ label: '长期稳定', value: val, color: '#50c878', desc: rm.stability_desc || '长期关系的稳定基础' })
  }

  // 降级：若无上述字段，使用通用 compatibility_score
  if (items.length === 0 && rm.compatibility_score != null) {
    items.push({ label: '综合契合', value: normalizeScore(Number(rm.compatibility_score), 100), color: '#9b87d1', desc: rm.summary || '整体关系契合度' })
  }
  return items
})

// ── 英文关系主题 → 中文标签映射 ──────────────────────────────
const THEME_MAP: Record<string, { title: string; icon: string; type: 'positive' | 'challenge' | 'neutral'; desc: string }> = {
  // 情感类
  'emotional dependency':       { title: '情感依赖',     icon: '🌊', type: 'neutral',   desc: '双方在情感上有较深的相互依赖' },
  'emotional resonance':        { title: '情感共鸣',     icon: '💞', type: 'positive',  desc: '情绪状态高度同频，互相理解' },
  'emotional connection':       { title: '情感纽带',     icon: '💫', type: 'positive',  desc: '建立了深厚的情感连接' },
  'emotional conflict':         { title: '情感冲突',     icon: '⚡', type: 'challenge', desc: '情绪表达方式存在摩擦' },
  // 稳定类
  'long-term stability':        { title: '长期稳定',     icon: '⚓', type: 'positive',  desc: '关系具备长久发展的坚实基础' },
  'stable foundation':          { title: '稳固根基',     icon: '🏛️', type: 'positive',  desc: '价值观一致，关系基础牢固' },
  'commitment potential':       { title: '承诺潜力',     icon: '💍', type: 'positive',  desc: '双方有建立长期承诺的倾向' },
  // 吸引类
  'intense attraction':         { title: '强烈吸引',     icon: '🔥', type: 'positive',  desc: '双方之间存在强烈的相互吸引' },
  'magnetic attraction':        { title: '磁场吸引',     icon: '✨', type: 'positive',  desc: '天然的磁场感应，互相被吸引' },
  'physical attraction':        { title: '肢体吸引',     icon: '💫', type: 'positive',  desc: '肢体层面有较强的吸引力' },
  // 沟通类
  'intellectual connection':    { title: '智识共鸣',     icon: '💡', type: 'positive',  desc: '思维方式相近，交流顺畅' },
  'communication harmony':      { title: '沟通和谐',     icon: '🗣️', type: 'positive',  desc: '表达方式互补，沟通无障碍' },
  'communication challenges':   { title: '沟通挑战',     icon: '⚡', type: 'challenge', desc: '表达风格不同，需要更多耐心' },
  // 成长类
  'transformative relationship': { title: '蜕变关系',    icon: '🦋', type: 'neutral',   desc: '这段关系将带来深刻的自我转化' },
  'growth potential':           { title: '成长潜力',     icon: '🌱', type: 'positive',  desc: '相互促进，共同成长' },
  'karmic connection':          { title: '命运羁绊',     icon: '🔮', type: 'neutral',   desc: '似乎有超越此生的灵魂连接' },
  'soul connection':            { title: '灵魂连接',     icon: '🌟', type: 'positive',  desc: '灵魂层面的深度共鸣' },
  // 挑战类
  'power tension':              { title: '权力张力',     icon: '⚔️', type: 'challenge', desc: '双方存在控制与主导权的博弈' },
  'power struggles':            { title: '权力拉锯',     icon: '⚔️', type: 'challenge', desc: '双方都有较强的主导欲，需要协调' },
  'tension and conflict':       { title: '紧张冲突',     icon: '⚡', type: 'challenge', desc: '关系中存在一定的内在张力' },
  'value differences':          { title: '价值差异',     icon: '🌀', type: 'challenge', desc: '核心价值观上存在分歧需磨合' },
}

function resolveTheme(raw: string): { icon: string; title: string; desc: string; type: 'positive' | 'challenge' | 'neutral' } {
  const lower = raw.toLowerCase().trim()
  const mapped = THEME_MAP[lower]
  if (mapped) return mapped
  // 模糊匹配：遍历 key 看是否包含
  for (const [key, val] of Object.entries(THEME_MAP)) {
    if (lower.includes(key) || key.includes(lower)) return val
  }
  // 未匹配：按关键词推断 type
  const isChallenge = /conflict|struggle|tension|challenge|difficult|opposition|friction/i.test(raw)
  const isPositive  = /harmony|resonance|attraction|connection|stability|growth|soul|love|romantic/i.test(raw)
  return {
    icon: isChallenge ? '⚡' : isPositive ? '✨' : '💫',
    title: raw,
    desc: '',
    type: isChallenge ? 'challenge' : isPositive ? 'positive' : 'neutral'
  }
}

/**
 * 从 chartData.aspects 中提取关系相位并归纳为主题卡片
 * 若无真实数据则返回空数组
 */
const realThemes = computed(() => {
  // 优先使用后端直接提供的 themes 字段
  const themesRaw = chartData.value?.themes
  if (themesRaw && Array.isArray(themesRaw) && themesRaw.length > 0) {
    return themesRaw.slice(0, 4).map((t: any) => {
      if (typeof t === 'string') {
        return resolveTheme(t)
      }
      return {
        icon: t.icon || (t.type === 'positive' ? '✨' : t.type === 'challenge' ? '⚡' : '💫'),
        title: t.title || t.name || t.theme || String(t),
        desc: t.desc || t.description || '',
        type: t.type || 'neutral'
      }
    })
  }

  // 降级：从相位数据中提炼关键主题（取前 3 个强相位）
  const aspects = chartData.value?.aspects
  if (!aspects || !Array.isArray(aspects) || aspects.length === 0) return []
  return aspects.slice(0, 3).map((a: any) => {
    const p1Key = (a.planet_a || a.planet1 || a.body1 || '').toLowerCase()
    const p2Key = (a.planet_b || a.planet2 || a.body2 || '').toLowerCase()
    const aspectType = (a.aspect_type || a.aspect || a.type || '').toLowerCase()
    const p1 = PLANET_DISPLAY_S[p1Key]
    const p2 = PLANET_DISPLAY_S[p2Key]
    const aspectLabel = ASPECT_LABEL_S[aspectType]
    const harmony = aspectLabel?.harmony || 'neutral'
    const icon = harmony === 'positive' ? '✨' : harmony === 'challenge' ? '⚡' : '💫'
    const title = [p1?.name || p1Key, aspectLabel?.label || aspectType, p2?.name || p2Key].filter(Boolean).join(' ')
    const desc = a.description || a.interpretation || ''
    return { icon, title, desc, type: harmony }
  })
})

// 轴点 key 集合，用于判断是否为纯轴点互相相位
const AXIS_KEYS_S = new Set(['ascendant', 'asc', 'as', 'asc.', 'midheaven', 'mc', 'mc.', 'medium coeli', 'descendant', 'dc', 'dc.', 'ic', 'ic.', 'imum coeli'])

/**
 * 从 chart.angles 中提取轴点的星座，供相位列表补充 p1Sign/p2Sign
 * 返回 { ascendant: 'Scorpio', mc: 'Virgo', descendant: 'Taurus', ic: 'Pisces' }
 */
const angleSignMap = computed<Record<string, string>>(() => {
  const angles = (chartData.value?.chart as any)?.angles
  if (!angles) return {}
  const result: Record<string, string> = {}
  for (const key of Object.keys(angles)) {
    const sign: string = angles[key]?.sign || ''
    if (sign) result[key.toLowerCase()] = sign
  }
  // 互相补充别名
  if (result['ascendant'] && !result['asc']) result['asc'] = result['ascendant']
  if (result['midheaven'] && !result['mc']) result['mc'] = result['midheaven']
  if (result['mc'] && !result['midheaven']) result['midheaven'] = result['mc']
  if (result['descendant'] && !result['dc']) result['dc'] = result['descendant']
  if (result['ic']) result['ic.'] = result['ic']
  return result
})

/**
 * 从 chartData.aspects 中提取双人相位列表（完整版，用于相位 Tab）
 * 兼容多种 Python 返回字段名
 * 过滤掉纯轴点互相的恒定相位（如 ascendant-opposition-descendant，数学上必然成立，对解读无意义）
 * tags 字段（如 ["emotional","attraction","conflict"]）参与 harmony 判定：优先级高于 aspect_type 分类
 */
const realAspects = computed(() => {
  const aspects = chartData.value?.aspects
  if (!aspects || !Array.isArray(aspects) || aspects.length === 0) return []

  return aspects.map((a: any) => {
    // ── 行星名兼容 ──────────────────────────────────────────
    const p1Raw = (
      a.planet1 || a.body1 || a.planet_a || a.p1 ||
      a.person1_planet || a.chart1_planet || a.planet || ''
    ).toLowerCase().trim()
    const p2Raw = (
      a.planet2 || a.body2 || a.planet_b || a.p2 ||
      a.person2_planet || a.chart2_planet || a.natal_planet || ''
    ).toLowerCase().trim()

    // ── 过滤纯轴点互相的恒定相位（两者都是轴点则跳过） ──────
    if (AXIS_KEYS_S.has(p1Raw) && AXIS_KEYS_S.has(p2Raw)) return null

    // ── 相位类型兼容 ────────────────────────────────────────
    const aspectType = (
      a.aspect || a.type || a.aspect_type || a.aspect_name || 'conjunction'
    ).toLowerCase().trim()

    // ── 查找 Planet / Aspect 显示配置 ────────────────────────
    const p1Info = PLANET_DISPLAY_S[p1Raw]
    const p2Info = PLANET_DISPLAY_S[p2Raw]
    const aspectInfo = ASPECT_LABEL_S[aspectType]

    // ── 容许度 ──────────────────────────────────────────────
    const orb = a.orb ?? a.orb_value ?? a.exact_orb ?? null
    const orbStr = orb != null ? `${Math.abs(Number(orb)).toFixed(1)}°` : ''

    // ── 力度（strength 0-1，展示为百分比） ──────────────────
    const strength = a.strength != null ? Math.round(Number(a.strength) * 100) : null

    // ── 行星星座：优先取相位数据自带字段，轴点则从 angles 补充 ──
    const rawP1Sign = a.planet1_sign || a.sign1 || a.p1_sign || angleSignMap.value[p1Raw] || ''
    const rawP2Sign = a.planet2_sign || a.sign2 || a.p2_sign || angleSignMap.value[p2Raw] || ''
    const p1Sign = zodiacZhS(rawP1Sign)
    const p2Sign = zodiacZhS(rawP2Sign)

    // ── tags 参与 harmony 判定 ──────────────────────────────
    // Python 返回的 tags 如 ["emotional","attraction","conflict"]，可以覆盖 aspect 默认分类
    const tags: string[] = Array.isArray(a.tags) ? a.tags.map((t: any) => String(t).toLowerCase()) : []
    let harmony: 'positive' | 'challenge' | 'neutral' = aspectInfo?.harmony || 'neutral'
    if (tags.includes('conflict')) harmony = 'challenge'
    else if (tags.includes('attraction') || tags.includes('emotional')) {
      // 情感/吸引类 tag，若 aspect 本身是负面则保持，否则标记为正面
      if (harmony !== 'challenge') harmony = 'positive'
    }

    return {
      p1Symbol: p1Info?.symbol || p1Raw.slice(0, 2).toUpperCase(),
      p1Name:   p1Info?.name   || p1Raw,
      p1Sign,
      p2Symbol: p2Info?.symbol || p2Raw.slice(0, 2).toUpperCase(),
      p2Name:   p2Info?.name   || p2Raw,
      p2Sign,
      aspectSymbol: aspectInfo?.symbol || aspectType.slice(0, 2),
      aspectLabel:  aspectInfo?.label  || aspectType,
      harmony,
      orb: orbStr,
      strength,
      tags,
      description: a.description || a.interpretation || a.impact || '',
      exactness: a.exactness || a.exact || '',
    }
  }).filter(Boolean)
})

/**
 * 相位统计摘要（harmony 分布）
 */
const aspectStats = computed(() => {
  const list = realAspects.value
  if (list.length === 0) return null
  const pos = list.filter(a => a.harmony === 'positive').length
  const neg = list.filter(a => a.harmony === 'challenge').length
  const neu = list.filter(a => a.harmony === 'neutral').length
  return { total: list.length, positive: pos, challenge: neg, neutral: neu }
})

// ── 宫位叠加数据 ──────────────────────────────────────────
/**
 * 从 chartData.chart.house_overlay 提取宫位叠加列表
 * 格式：[{source_chart: "chart_b", planet: "sun", target_house: 7, meaning: null}]
 * chart_a = 我方，chart_b = 对方
 */
const houseOverlay = computed(() => {
  const overlay = (chartData.value?.chart as any)?.house_overlay
  if (!overlay || !Array.isArray(overlay) || overlay.length === 0) return null
  const meItems: Array<{ planet: string; house: number; pInfo: any }> = []
  const partnerItems: Array<{ planet: string; house: number; pInfo: any }> = []
  for (const item of overlay) {
    const planet = String(item.planet || '').toLowerCase()
    const house = Number(item.target_house)
    const pInfo = PLANET_DISPLAY_S[planet]
    if (!house || isNaN(house)) continue
    if (item.source_chart === 'chart_b') {
      partnerItems.push({ planet, house, pInfo })
    } else {
      meItems.push({ planet, house, pInfo })
    }
  }
  if (meItems.length === 0 && partnerItems.length === 0) return null
  return { me: meItems, partner: partnerItems }
})

// ── 宫位含义映射 ──────────────────────────────────────────
/**
 * 从 chartData 中提取契合度分数（0-100）
 * 优先读取后端直接返回的综合分；若无则从四个维度加权计算：
 *   情绪匹配（40%）+ 吸引力（30%）+ 长期稳定（20%）+ 冲突（-10%，高冲突拉低总分）
 */
const compatibilityScore = computed(() => {
  const rm = chartData.value?.relationshipModel
  if (!rm) return null

  // 优先使用后端直接提供的综合分（已是 0-100）
  const directScore = rm.compatibility_score ?? rm.overall_score ?? rm.total_score
  if (directScore != null) return Math.min(100, Math.max(0, Math.round(Number(directScore))))

  // 从四维度加权合成
  const emotional = rm.emotional_score ?? rm.emotional_compatibility ?? rm.emotional_match ?? rm.emotion_score
  const attraction = rm.attraction_score ?? rm.attraction ?? rm.magnetic_attraction
  const stability = rm.stability_score ?? rm.long_term_stability ?? rm.stability
  const conflict = rm.conflict_score ?? rm.conflict_index ?? rm.tension

  // 至少需要有一个维度才合成
  if (emotional == null && attraction == null && stability == null) return null

  const emotionalNorm  = emotional  != null ? normalizeScore(Number(emotional),  100) : 50
  const attractionNorm = attraction != null ? normalizeScore(Number(attraction), 100) : 50
  const stabilityNorm  = stability  != null ? normalizeScore(Number(stability),  100) : 50
  const conflictNorm   = conflict   != null ? normalizeScore(Number(conflict),   100) : 0

  // 加权综合：情绪 40% + 吸引力 30% + 稳定 20% - 冲突影响 10%
  const composite = Math.round(
    emotionalNorm * 0.4 + attractionNorm * 0.3 + stabilityNorm * 0.2 - conflictNorm * 0.1
  )
  return Math.min(100, Math.max(0, composite))
})

// ── 内置城市数据（全国300+城市，支持拼音搜索） ────────────────────────────
const MAJOR_CITIES = [
  // 直辖市
  { name: '北京', lat: 39.9042, lng: 116.4074, address: '北京市', pinyin: 'beijing bj' },
  { name: '上海', lat: 31.2304, lng: 121.4737, address: '上海市', pinyin: 'shanghai sh' },
  { name: '天津', lat: 39.3434, lng: 117.3616, address: '天津市', pinyin: 'tianjin tj' },
  { name: '重庆', lat: 29.5630, lng: 106.5516, address: '重庆市', pinyin: 'chongqing cq' },
  // 广东省
  { name: '广州', lat: 23.1291, lng: 113.2644, address: '广东省广州市', pinyin: 'guangzhou gz' },
  { name: '深圳', lat: 22.5431, lng: 114.0579, address: '广东省深圳市', pinyin: 'shenzhen sz' },
  { name: '东莞', lat: 23.0207, lng: 113.7518, address: '广东省东莞市', pinyin: 'dongguan dg' },
  { name: '佛山', lat: 23.0219, lng: 113.1219, address: '广东省佛山市', pinyin: 'foshan fs' },
  { name: '珠海', lat: 22.2711, lng: 113.5767, address: '广东省珠海市', pinyin: 'zhuhai zh' },
  { name: '惠州', lat: 23.1115, lng: 114.4152, address: '广东省惠州市', pinyin: 'huizhou hz' },
  { name: '中山', lat: 22.5176, lng: 113.3926, address: '广东省中山市', pinyin: 'zhongshan zs' },
  { name: '江门', lat: 22.5788, lng: 113.0819, address: '广东省江门市', pinyin: 'jiangmen jm' },
  { name: '湛江', lat: 21.2707, lng: 110.3594, address: '广东省湛江市', pinyin: 'zhanjiang zj' },
  { name: '汕头', lat: 23.3535, lng: 116.6820, address: '广东省汕头市', pinyin: 'shantou st' },
  { name: '潮州', lat: 23.6567, lng: 116.6226, address: '广东省潮州市', pinyin: 'chaozhou cz' },
  { name: '揭阳', lat: 23.5497, lng: 116.3724, address: '广东省揭阳市', pinyin: 'jieyang jy' },
  { name: '茂名', lat: 21.6631, lng: 110.9253, address: '广东省茂名市', pinyin: 'maoming mm' },
  { name: '肇庆', lat: 23.0470, lng: 112.4653, address: '广东省肇庆市', pinyin: 'zhaoqing zq' },
  { name: '梅州', lat: 24.2882, lng: 116.1225, address: '广东省梅州市', pinyin: 'meizhou mz' },
  { name: '清远', lat: 23.6820, lng: 113.0563, address: '广东省清远市', pinyin: 'qingyuan qy' },
  { name: '河源', lat: 23.7435, lng: 114.6979, address: '广东省河源市', pinyin: 'heyuan hy' },
  { name: '阳江', lat: 21.8581, lng: 111.9822, address: '广东省阳江市', pinyin: 'yangjiang yj' },
  { name: '云浮', lat: 22.9151, lng: 112.0445, address: '广东省云浮市', pinyin: 'yunfu yf' },
  { name: '韶关', lat: 24.8107, lng: 113.5975, address: '广东省韶关市', pinyin: 'shaoguan sg' },
  { name: '汕尾', lat: 22.7748, lng: 115.3756, address: '广东省汕尾市', pinyin: 'shanwei sw' },
  // 浙江省
  { name: '杭州', lat: 30.2741, lng: 120.1551, address: '浙江省杭州市', pinyin: 'hangzhou hz' },
  { name: '宁波', lat: 29.8683, lng: 121.5440, address: '浙江省宁波市', pinyin: 'ningbo nb' },
  { name: '温州', lat: 27.9938, lng: 120.6994, address: '浙江省温州市', pinyin: 'wenzhou wz' },
  { name: '嘉兴', lat: 30.7522, lng: 120.7551, address: '浙江省嘉兴市', pinyin: 'jiaxing jx' },
  { name: '湖州', lat: 30.8703, lng: 120.0869, address: '浙江省湖州市', pinyin: 'huzhou huz' },
  { name: '绍兴', lat: 30.0023, lng: 120.5832, address: '浙江省绍兴市', pinyin: 'shaoxing sx' },
  { name: '金华', lat: 29.0788, lng: 119.6474, address: '浙江省金华市', pinyin: 'jinhua jh' },
  { name: '衢州', lat: 28.9359, lng: 118.8741, address: '浙江省衢州市', pinyin: 'quzhou qz' },
  { name: '舟山', lat: 30.0361, lng: 122.1067, address: '浙江省舟山市', pinyin: 'zhoushan zs2' },
  { name: '台州', lat: 28.6561, lng: 121.4206, address: '浙江省台州市', pinyin: 'taizhou tz' },
  { name: '丽水', lat: 28.4677, lng: 119.9230, address: '浙江省丽水市', pinyin: 'lishui ls' },
  // 江苏省
  { name: '南京', lat: 32.0603, lng: 118.7969, address: '江苏省南京市', pinyin: 'nanjing nj' },
  { name: '苏州', lat: 31.2989, lng: 120.5853, address: '江苏省苏州市', pinyin: 'suzhou sz2' },
  { name: '无锡', lat: 31.4912, lng: 120.3119, address: '江苏省无锡市', pinyin: 'wuxi wx' },
  { name: '常州', lat: 31.7744, lng: 119.9741, address: '江苏省常州市', pinyin: 'changzhou cz2' },
  { name: '南通', lat: 31.9801, lng: 120.8944, address: '江苏省南通市', pinyin: 'nantong nt' },
  { name: '扬州', lat: 32.3936, lng: 119.4127, address: '江苏省扬州市', pinyin: 'yangzhou yz' },
  { name: '徐州', lat: 34.2044, lng: 117.2847, address: '江苏省徐州市', pinyin: 'xuzhou xz' },
  { name: '镇江', lat: 32.1875, lng: 119.4253, address: '江苏省镇江市', pinyin: 'zhenjiang zj2' },
  { name: '泰州', lat: 32.4547, lng: 119.9229, address: '江苏省泰州市', pinyin: 'taizhou2 tz2' },
  { name: '盐城', lat: 33.3480, lng: 120.1631, address: '江苏省盐城市', pinyin: 'yancheng yc' },
  { name: '淮安', lat: 33.5518, lng: 119.0214, address: '江苏省淮安市', pinyin: 'huaian ha' },
  { name: '连云港', lat: 34.5965, lng: 119.2214, address: '江苏省连云港市', pinyin: 'lianyungang lyg' },
  { name: '宿迁', lat: 33.9631, lng: 118.2750, address: '江苏省宿迁市', pinyin: 'suqian sq' },
  // 四川省
  { name: '成都', lat: 30.5728, lng: 104.0668, address: '四川省成都市', pinyin: 'chengdu cd' },
  { name: '绵阳', lat: 31.4678, lng: 104.6796, address: '四川省绵阳市', pinyin: 'mianyang my' },
  { name: '德阳', lat: 31.1270, lng: 104.3976, address: '四川省德阳市', pinyin: 'deyang dy' },
  { name: '宜宾', lat: 28.7514, lng: 104.6426, address: '四川省宜宾市', pinyin: 'yibin yb' },
  { name: '泸州', lat: 28.8718, lng: 105.4425, address: '四川省泸州市', pinyin: 'luzhou lz' },
  { name: '南充', lat: 30.8368, lng: 106.1105, address: '四川省南充市', pinyin: 'nanchong nc' },
  { name: '自贡', lat: 29.3390, lng: 104.7787, address: '四川省自贡市', pinyin: 'zigong zg' },
  { name: '攀枝花', lat: 26.5824, lng: 101.7183, address: '四川省攀枝花市', pinyin: 'panzhihua pzh' },
  { name: '广元', lat: 32.4355, lng: 105.8434, address: '四川省广元市', pinyin: 'guangyuan gy' },
  { name: '遂宁', lat: 30.5331, lng: 105.5927, address: '四川省遂宁市', pinyin: 'suining sn' },
  { name: '内江', lat: 29.5806, lng: 105.0585, address: '四川省内江市', pinyin: 'neijiang nj2' },
  { name: '乐山', lat: 29.5527, lng: 103.7661, address: '四川省乐山市', pinyin: 'leshan ls2' },
  { name: '眉山', lat: 30.0748, lng: 103.8486, address: '四川省眉山市', pinyin: 'meishan ms' },
  { name: '雅安', lat: 29.9997, lng: 103.0015, address: '四川省雅安市', pinyin: 'yaan ya' },
  { name: '巴中', lat: 31.8670, lng: 106.7478, address: '四川省巴中市', pinyin: 'bazhong bz' },
  { name: '资阳', lat: 30.1221, lng: 104.6278, address: '四川省资阳市', pinyin: 'ziyang zy' },
  // 湖北省
  { name: '武汉', lat: 30.5928, lng: 114.3055, address: '湖北省武汉市', pinyin: 'wuhan wh' },
  { name: '宜昌', lat: 30.6918, lng: 111.2864, address: '湖北省宜昌市', pinyin: 'yichang yc2' },
  { name: '襄阳', lat: 32.0084, lng: 112.1223, address: '湖北省襄阳市', pinyin: 'xiangyang xy' },
  { name: '荆州', lat: 30.3354, lng: 112.2396, address: '湖北省荆州市', pinyin: 'jingzhou jz' },
  { name: '十堰', lat: 32.6292, lng: 110.7987, address: '湖北省十堰市', pinyin: 'shiyan sy2' },
  { name: '黄石', lat: 30.2006, lng: 115.0387, address: '湖北省黄石市', pinyin: 'huangshi hs' },
  { name: '孝感', lat: 30.9244, lng: 113.9161, address: '湖北省孝感市', pinyin: 'xiaogan xg' },
  { name: '黄冈', lat: 30.4534, lng: 114.8722, address: '湖北省黄冈市', pinyin: 'huanggang hg' },
  { name: '随州', lat: 31.6899, lng: 113.3826, address: '湖北省随州市', pinyin: 'suizhou sz3' },
  // 湖南省
  { name: '长沙', lat: 28.2278, lng: 112.9388, address: '湖南省长沙市', pinyin: 'changsha cs' },
  { name: '株洲', lat: 27.8274, lng: 113.1340, address: '湖南省株洲市', pinyin: 'zhuzhou zz' },
  { name: '湘潭', lat: 27.8295, lng: 112.9447, address: '湖南省湘潭市', pinyin: 'xiangtan xt' },
  { name: '岳阳', lat: 29.3572, lng: 113.1289, address: '湖南省岳阳市', pinyin: 'yueyang yy' },
  { name: '常德', lat: 29.0322, lng: 111.6986, address: '湖南省常德市', pinyin: 'changde cd2' },
  { name: '衡阳', lat: 26.8933, lng: 112.5719, address: '湖南省衡阳市', pinyin: 'hengyang hy2' },
  { name: '邵阳', lat: 27.2394, lng: 111.4678, address: '湖南省邵阳市', pinyin: 'shaoyang sy3' },
  { name: '益阳', lat: 28.5539, lng: 112.3551, address: '湖南省益阳市', pinyin: 'yiyang yy2' },
  { name: '娄底', lat: 27.7003, lng: 111.9954, address: '湖南省娄底市', pinyin: 'loudi ld' },
  { name: '郴州', lat: 25.7700, lng: 113.0148, address: '湖南省郴州市', pinyin: 'chenzhou cz3' },
  { name: '永州', lat: 26.4202, lng: 111.6148, address: '湖南省永州市', pinyin: 'yongzhou yz2' },
  { name: '怀化', lat: 27.5703, lng: 109.9588, address: '湖南省怀化市', pinyin: 'huaihua hh' },
  { name: '张家界', lat: 29.1248, lng: 110.4791, address: '湖南省张家界市', pinyin: 'zhangjiajie zjj' },
  // 福建省
  { name: '福州', lat: 26.0745, lng: 119.2965, address: '福建省福州市', pinyin: 'fuzhou fz' },
  { name: '厦门', lat: 24.4798, lng: 118.0894, address: '福建省厦门市', pinyin: 'xiamen xm' },
  { name: '泉州', lat: 24.8741, lng: 118.6757, address: '福建省泉州市', pinyin: 'quanzhou qz2' },
  { name: '漳州', lat: 24.5141, lng: 117.6472, address: '福建省漳州市', pinyin: 'zhangzhou zz2' },
  { name: '莆田', lat: 25.4540, lng: 119.0073, address: '福建省莆田市', pinyin: 'putian pt' },
  { name: '三明', lat: 26.2654, lng: 117.6386, address: '福建省三明市', pinyin: 'sanming sm' },
  { name: '南平', lat: 26.6351, lng: 118.1786, address: '福建省南平市', pinyin: 'nanping np' },
  { name: '龙岩', lat: 25.0751, lng: 117.0177, address: '福建省龙岩市', pinyin: 'longyan ly' },
  { name: '宁德', lat: 26.6658, lng: 119.5479, address: '福建省宁德市', pinyin: 'ningde nd' },
  // 山东省
  { name: '济南', lat: 36.6512, lng: 117.1201, address: '山东省济南市', pinyin: 'jinan jn' },
  { name: '青岛', lat: 36.0671, lng: 120.3826, address: '山东省青岛市', pinyin: 'qingdao qd' },
  { name: '烟台', lat: 37.4638, lng: 121.4479, address: '山东省烟台市', pinyin: 'yantai yt' },
  { name: '潍坊', lat: 36.7071, lng: 119.1616, address: '山东省潍坊市', pinyin: 'weifang wf' },
  { name: '威海', lat: 37.5130, lng: 122.1219, address: '山东省威海市', pinyin: 'weihai wh2' },
  { name: '淄博', lat: 36.8132, lng: 118.0549, address: '山东省淄博市', pinyin: 'zibo zb' },
  { name: '临沂', lat: 35.1046, lng: 118.3564, address: '山东省临沂市', pinyin: 'linyi ly2' },
  { name: '济宁', lat: 35.4146, lng: 116.5869, address: '山东省济宁市', pinyin: 'jining jn2' },
  { name: '菏泽', lat: 35.2333, lng: 115.4800, address: '山东省菏泽市', pinyin: 'heze hz2' },
  { name: '泰安', lat: 36.1996, lng: 117.0878, address: '山东省泰安市', pinyin: 'taian ta' },
  { name: '东营', lat: 37.4343, lng: 118.6748, address: '山东省东营市', pinyin: 'dongying dy2' },
  { name: '聊城', lat: 36.4562, lng: 115.9855, address: '山东省聊城市', pinyin: 'liaocheng lc' },
  { name: '滨州', lat: 37.3836, lng: 117.9700, address: '山东省滨州市', pinyin: 'binzhou bz2' },
  { name: '德州', lat: 37.4354, lng: 116.3592, address: '山东省德州市', pinyin: 'dezhou dz' },
  { name: '枣庄', lat: 34.8107, lng: 117.3219, address: '山东省枣庄市', pinyin: 'zaozhuang zz3' },
  { name: '日照', lat: 35.4164, lng: 119.5268, address: '山东省日照市', pinyin: 'rizhao rz' },
  // 河南省
  { name: '郑州', lat: 34.7473, lng: 113.6249, address: '河南省郑州市', pinyin: 'zhengzhou zz4' },
  { name: '洛阳', lat: 34.6197, lng: 112.4540, address: '河南省洛阳市', pinyin: 'luoyang ly3' },
  { name: '开封', lat: 34.7971, lng: 114.3075, address: '河南省开封市', pinyin: 'kaifeng kf' },
  { name: '新乡', lat: 35.3028, lng: 113.9230, address: '河南省新乡市', pinyin: 'xinxiang xx' },
  { name: '安阳', lat: 36.0975, lng: 114.3924, address: '河南省安阳市', pinyin: 'anyang ay' },
  { name: '焦作', lat: 35.2395, lng: 113.2418, address: '河南省焦作市', pinyin: 'jiaozuo jz2' },
  { name: '南阳', lat: 32.9905, lng: 112.5283, address: '河南省南阳市', pinyin: 'nanyang ny' },
  { name: '许昌', lat: 34.0356, lng: 113.8522, address: '河南省许昌市', pinyin: 'xuchang xc' },
  { name: '平顶山', lat: 33.7661, lng: 113.2914, address: '河南省平顶山市', pinyin: 'pingdingshan pds' },
  { name: '信阳', lat: 32.1472, lng: 114.0913, address: '河南省信阳市', pinyin: 'xinyang xy2' },
  { name: '周口', lat: 33.6477, lng: 114.6496, address: '河南省周口市', pinyin: 'zhoukou zk' },
  { name: '驻马店', lat: 32.9826, lng: 114.0221, address: '河南省驻马店市', pinyin: 'zhumadian zmd' },
  { name: '商丘', lat: 34.4143, lng: 115.6561, address: '河南省商丘市', pinyin: 'shangqiu sq2' },
  { name: '濮阳', lat: 35.7620, lng: 115.0290, address: '河南省濮阳市', pinyin: 'puyang py' },
  { name: '漯河', lat: 33.5757, lng: 114.0164, address: '河南省漯河市', pinyin: 'luohe lh' },
  { name: '三门峡', lat: 34.7734, lng: 111.2010, address: '河南省三门峡市', pinyin: 'sanmenxia smx' },
  // 辽宁省
  { name: '沈阳', lat: 41.8057, lng: 123.4315, address: '辽宁省沈阳市', pinyin: 'shenyang sy' },
  { name: '大连', lat: 38.9140, lng: 121.6147, address: '辽宁省大连市', pinyin: 'dalian dl' },
  { name: '鞍山', lat: 41.1085, lng: 122.9958, address: '辽宁省鞍山市', pinyin: 'anshan as' },
  { name: '抚顺', lat: 41.8797, lng: 123.9571, address: '辽宁省抚顺市', pinyin: 'fushun fs2' },
  { name: '锦州', lat: 41.1305, lng: 121.1268, address: '辽宁省锦州市', pinyin: 'jinzhou jz3' },
  { name: '营口', lat: 40.6672, lng: 122.2347, address: '辽宁省营口市', pinyin: 'yingkou yk' },
  { name: '丹东', lat: 40.1292, lng: 124.3545, address: '辽宁省丹东市', pinyin: 'dandong dd' },
  // 陕西省
  { name: '西安', lat: 34.3416, lng: 108.9398, address: '陕西省西安市', pinyin: 'xian xa' },
  { name: '咸阳', lat: 34.3297, lng: 108.7089, address: '陕西省咸阳市', pinyin: 'xianyang xy3' },
  { name: '宝鸡', lat: 34.3617, lng: 107.2373, address: '陕西省宝鸡市', pinyin: 'baoji bj2' },
  { name: '渭南', lat: 34.4997, lng: 109.5095, address: '陕西省渭南市', pinyin: 'weinan wn' },
  { name: '汉中', lat: 33.0667, lng: 107.0282, address: '陕西省汉中市', pinyin: 'hanzhong hz3' },
  { name: '榆林', lat: 38.2856, lng: 109.7342, address: '陕西省榆林市', pinyin: 'yulin yl' },
  { name: '延安', lat: 36.5853, lng: 109.4897, address: '陕西省延安市', pinyin: 'yanan yan' },
  // 安徽省
  { name: '合肥', lat: 31.8206, lng: 117.2272, address: '安徽省合肥市', pinyin: 'hefei hf' },
  { name: '芜湖', lat: 31.3520, lng: 118.4329, address: '安徽省芜湖市', pinyin: 'wuhu wh3' },
  { name: '蚌埠', lat: 32.9162, lng: 117.3795, address: '安徽省蚌埠市', pinyin: 'bengbu bb' },
  { name: '淮南', lat: 32.6252, lng: 116.9993, address: '安徽省淮南市', pinyin: 'huainan hn' },
  { name: '马鞍山', lat: 31.6704, lng: 118.5066, address: '安徽省马鞍山市', pinyin: 'maanshan mas' },
  { name: '安庆', lat: 30.5430, lng: 117.0633, address: '安徽省安庆市', pinyin: 'anqing aq' },
  { name: '黄山', lat: 29.7151, lng: 118.3380, address: '安徽省黄山市', pinyin: 'huangshan hs2' },
  { name: '阜阳', lat: 32.8989, lng: 115.8149, address: '安徽省阜阳市', pinyin: 'fuyang fy' },
  // 河北省
  { name: '石家庄', lat: 38.0428, lng: 114.5149, address: '河北省石家庄市', pinyin: 'shijiazhuang sjz' },
  { name: '唐山', lat: 39.6310, lng: 118.1800, address: '河北省唐山市', pinyin: 'tangshan ts' },
  { name: '秦皇岛', lat: 39.9355, lng: 119.5994, address: '河北省秦皇岛市', pinyin: 'qinhuangdao qhd' },
  { name: '保定', lat: 38.8736, lng: 115.4644, address: '河北省保定市', pinyin: 'baoding bd' },
  { name: '邯郸', lat: 36.6251, lng: 114.5389, address: '河北省邯郸市', pinyin: 'handan hd' },
  { name: '张家口', lat: 40.8114, lng: 114.8796, address: '河北省张家口市', pinyin: 'zhangjiakou zjk' },
  { name: '廊坊', lat: 39.5382, lng: 116.7032, address: '河北省廊坊市', pinyin: 'langfang lf' },
  // 山西省
  { name: '太原', lat: 37.8706, lng: 112.5489, address: '山西省太原市', pinyin: 'taiyuan ty' },
  { name: '大同', lat: 40.0766, lng: 113.2982, address: '山西省大同市', pinyin: 'datong dt' },
  { name: '长治', lat: 36.1956, lng: 113.1164, address: '山西省长治市', pinyin: 'changzhi cz6' },
  { name: '运城', lat: 35.0224, lng: 111.0070, address: '山西省运城市', pinyin: 'yuncheng yc3' },
  // 黑龙江省
  { name: '哈尔滨', lat: 45.8038, lng: 126.5349, address: '黑龙江省哈尔滨市', pinyin: 'haerbin hrb' },
  { name: '齐齐哈尔', lat: 47.3479, lng: 123.9182, address: '黑龙江省齐齐哈尔市', pinyin: 'qiqihaer qqhr' },
  { name: '大庆', lat: 46.5897, lng: 125.1032, address: '黑龙江省大庆市', pinyin: 'daqing dq' },
  { name: '牡丹江', lat: 44.5526, lng: 129.6328, address: '黑龙江省牡丹江市', pinyin: 'mudanjiang mdj' },
  // 吉林省
  { name: '长春', lat: 43.8171, lng: 125.3235, address: '吉林省长春市', pinyin: 'changchun cc' },
  { name: '吉林', lat: 43.8378, lng: 126.5496, address: '吉林省吉林市', pinyin: 'jilin jl' },
  { name: '延吉', lat: 42.9099, lng: 129.5130, address: '吉林省延吉市', pinyin: 'yanji yj2' },
  // 江西省
  { name: '南昌', lat: 28.6820, lng: 115.8582, address: '江西省南昌市', pinyin: 'nanchang nc2' },
  { name: '赣州', lat: 25.8311, lng: 114.9330, address: '江西省赣州市', pinyin: 'ganzhou gz2' },
  { name: '九江', lat: 29.7055, lng: 115.9926, address: '江西省九江市', pinyin: 'jiujiang jj' },
  { name: '景德镇', lat: 29.2687, lng: 117.1786, address: '江西省景德镇市', pinyin: 'jingdezhen jdz' },
  { name: '上饶', lat: 28.4544, lng: 117.9429, address: '江西省上饶市', pinyin: 'shangrao sr' },
  // 云南省
  { name: '昆明', lat: 25.0453, lng: 102.7097, address: '云南省昆明市', pinyin: 'kunming km' },
  { name: '大理', lat: 25.6066, lng: 100.2598, address: '云南省大理市', pinyin: 'dali dl2' },
  { name: '丽江', lat: 26.8721, lng: 100.2331, address: '云南省丽江市', pinyin: 'lijiang lj' },
  { name: '西双版纳', lat: 22.0073, lng: 100.7974, address: '云南省西双版纳傣族自治州', pinyin: 'xishuangbanna xsbn' },
  // 贵州省
  { name: '贵阳', lat: 26.6470, lng: 106.6302, address: '贵州省贵阳市', pinyin: 'guiyang gy2' },
  { name: '遵义', lat: 27.7251, lng: 106.9271, address: '贵州省遵义市', pinyin: 'zunyi zy2' },
  // 广西壮族自治区
  { name: '南宁', lat: 22.8170, lng: 108.3665, address: '广西壮族自治区南宁市', pinyin: 'nanning nn' },
  { name: '柳州', lat: 24.3264, lng: 109.4281, address: '广西壮族自治区柳州市', pinyin: 'liuzhou lz2' },
  { name: '桂林', lat: 25.2736, lng: 110.2907, address: '广西壮族自治区桂林市', pinyin: 'guilin gl' },
  { name: '北海', lat: 21.4808, lng: 109.1196, address: '广西壮族自治区北海市', pinyin: 'beihai bh' },
  // 海南省
  { name: '海口', lat: 20.0440, lng: 110.1991, address: '海南省海口市', pinyin: 'haikou hk' },
  { name: '三亚', lat: 18.2528, lng: 109.5119, address: '海南省三亚市', pinyin: 'sanya sy5' },
  // 内蒙古自治区
  { name: '呼和浩特', lat: 40.8414, lng: 111.7519, address: '内蒙古自治区呼和浩特市', pinyin: 'huhehaote hhht' },
  { name: '包头', lat: 40.6575, lng: 109.8401, address: '内蒙古自治区包头市', pinyin: 'baotou bt' },
  { name: '鄂尔多斯', lat: 39.6086, lng: 109.7814, address: '内蒙古自治区鄂尔多斯市', pinyin: 'eerduosi eeds' },
  { name: '赤峰', lat: 42.2574, lng: 118.8881, address: '内蒙古自治区赤峰市', pinyin: 'chifeng cf' },
  // 新疆维吾尔自治区
  { name: '乌鲁木齐', lat: 43.8256, lng: 87.6168, address: '新疆维吾尔自治区乌鲁木齐市', pinyin: 'wulumuqi wlmq' },
  { name: '喀什', lat: 39.4673, lng: 75.9896, address: '新疆维吾尔自治区喀什地区', pinyin: 'kashi ks' },
  { name: '哈密', lat: 42.8176, lng: 93.5142, address: '新疆维吾尔自治区哈密市', pinyin: 'hami hm' },
  // 西藏自治区
  { name: '拉萨', lat: 29.6500, lng: 91.1000, address: '西藏自治区拉萨市', pinyin: 'lasa ls3' },
  { name: '日喀则', lat: 29.2677, lng: 88.8850, address: '西藏自治区日喀则市', pinyin: 'rikaze rkz' },
  { name: '林芝', lat: 29.6490, lng: 94.3625, address: '西藏自治区林芝市', pinyin: 'linzhi lz3' },
  // 宁夏回族自治区
  { name: '银川', lat: 38.4872, lng: 106.2309, address: '宁夏回族自治区银川市', pinyin: 'yinchuan yc6' },
  // 青海省
  { name: '西宁', lat: 36.6177, lng: 101.7782, address: '青海省西宁市', pinyin: 'xining xn' },
  // 甘肃省
  { name: '兰州', lat: 36.0611, lng: 103.8343, address: '甘肃省兰州市', pinyin: 'lanzhou lz4' },
  { name: '天水', lat: 34.5807, lng: 105.7245, address: '甘肃省天水市', pinyin: 'tianshui ts2' },
  { name: '酒泉', lat: 39.7432, lng: 98.4938, address: '甘肃省酒泉市', pinyin: 'jiuquan jq' },
  // 港澳台
  { name: '香港', lat: 22.3193, lng: 114.1694, address: '香港特别行政区', pinyin: 'xianggang hk2 hong kong' },
  { name: '澳门', lat: 22.1987, lng: 113.5439, address: '澳门特别行政区', pinyin: 'aomen macao macau' },
  { name: '台北', lat: 25.0478, lng: 121.5319, address: '台湾省台北市', pinyin: 'taibei tb' },
  { name: '台中', lat: 24.1477, lng: 120.6736, address: '台湾省台中市', pinyin: 'taizhong tz3' },
  { name: '高雄', lat: 22.6273, lng: 120.3014, address: '台湾省高雄市', pinyin: 'gaoxiong gx' },
  { name: '台南', lat: 22.9908, lng: 120.2133, address: '台湾省台南市', pinyin: 'tainan tn' },
]

// 城市别名映射（简称/别名 → 城市名）
const CITY_ALIASES: Record<string, string> = {
  '京': '北京', '沪': '上海', '渝': '重庆', '津': '天津',
  '穗': '广州', '深': '深圳', '杭': '杭州', '苏': '苏州',
  '宁': '南京', '汉': '武汉', '蓉': '成都',
  '锡': '无锡', '常': '常州', '扬': '扬州',
}

let partnerTimer: any = null

// ── 对方城市搜索 ────────────────────────────────────────
const partnerCityKeyword = ref('')
const partnerCityResults = ref<Array<{name: string, lat: number, lng: number, address?: string}>>([])
const showPartnerSuggestions = ref(false)

/**
 * 从 userStore 回填对方信息（页面初始化时调用）
 * partnerTime 格式：yyyy-MM-dd HH:mm，解析为 year/month/day/hour
 */
function prefillPartnerFromStore() {
  const u = userStore.userInfo
  if (!u) return
  if (u.synastryPartnerCity) {
    partnerForm.city = u.synastryPartnerCity
    partnerForm.lat = u.synastryPartnerLat ?? null
    partnerForm.lng = u.synastryPartnerLng ?? null
    partnerCityKeyword.value = u.synastryPartnerCity
  }
  if (u.synastryPartnerName) {
    partnerName.value = u.synastryPartnerName
  }
  if (u.synastryPartnerTime) {
    // 解析 "yyyy-MM-dd HH:mm" → year/month/day/hour/minute
    try {
      const [datePart, timePart] = u.synastryPartnerTime.split(' ')
      const [y, mo, d] = datePart.split('-').map(Number)
      const [h, min] = timePart.split(':').map(Number)
      if (y) partnerForm.year = y
      if (mo) partnerForm.month = mo
      if (d) partnerForm.day = d
      if (h !== undefined) partnerForm.hour = h
      if (min !== undefined && !isNaN(min)) partnerForm.minute = min
    } catch (_) { /* 解析失败则保留默认值 */ }
  }
}

onMounted(async () => {
  prefillPartnerFromStore()
  // 如果已设置出生信息、对方信息，且有和盘缓存，则自动加载
  const astroInfo = userStore.astrologyInfo
  if (hasBirthInfo() && hasSynastryPartner() && astroInfo?.hasSynastryCache) {
    await calculateSynastry()
  }
})

function onPartnerCityInput(e: any) {
  const kw = e.detail.value as string
  partnerCityKeyword.value = kw
  if (!kw.trim()) {
    showPartnerSuggestions.value = false
    partnerCityResults.value = []
    partnerForm.city = ''
    partnerForm.lat = null
    partnerForm.lng = null
    return
  }
  clearTimeout(partnerTimer)
  partnerTimer = setTimeout(() => {
    const keyword = kw.toLowerCase().trim()
    const aliasTarget = CITY_ALIASES[keyword]
    const matched = MAJOR_CITIES.filter(c => {
      if (c.name.includes(keyword)) return true
      if (c.address?.includes(keyword)) return true
      if (c.pinyin && c.pinyin.toLowerCase().includes(keyword)) return true
      if (aliasTarget && c.name === aliasTarget) return true
      return false
    }).slice(0, 8)
    partnerCityResults.value = matched
    showPartnerSuggestions.value = matched.length > 0
  }, 300)
}

function selectPartnerCity(city: {name: string, lat: number, lng: number, address?: string}) {
  partnerForm.city = city.name
  partnerForm.lat = city.lat
  partnerForm.lng = city.lng
  partnerCityKeyword.value = city.name
  showPartnerSuggestions.value = false
}

function closeAllSuggestions() {
  showPartnerSuggestions.value = false
}

// ── 计算逻辑 ────────────────────────────────────────────

/**
 * 监听出生信息变化：当出生信息发生变更时，清空已有的和盘结果
 * 因为出生信息变了，已有的和盘结果不再有效
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

/** 格式化出生信息展示文本（从 birthTime 解析） */
const selfBirthDisplay = computed(() => {
  const u = userStore.userInfo
  if (!u?.birthTime || !u?.birthCity) return null
  // birthTime 格式: yyyy-MM-dd HH:mm
  const parts = u.birthTime.split(' ')
  const date = parts[0] || ''
  const time = parts[1] || ''
  return `${date} ${time} · ${u.birthCity}`
})

async function calculateSynastry() {
  // 前置检查：用户是否已设置出生信息（未设置则直接在 form 页展示友好占位，不弹窗）
  if (!hasBirthInfo()) {
    step.value = 'form'
    return
  }

  if (!partnerForm.city) {
    uni.showToast({ title: '请填写 Ta 的出生城市', icon: 'none' })
    return
  }

  step.value = 'loading'
  const TEXTS = ['正在解析双人星盘能量...', '计算相位连接...', '正在连接宇宙能量...', '生成关系地图...']
  let idx = 0
  const timer = setInterval(() => { loadingText.value = TEXTS[++idx % TEXTS.length] }, 1500)
  try {
    const pName = partnerName.value || 'Ta'
    const partnerTimeStr = `${String(partnerForm.year).padStart(4, '0')}-${String(partnerForm.month).padStart(2, '0')}-${String(partnerForm.day).padStart(2, '0')} ${String(partnerForm.hour).padStart(2, '0')}:${String(partnerForm.minute).padStart(2, '0')}`

    // 先将对方信息保存到后端 user 表，以满足 calculateSynastry 无参数接口的前置条件
    await updateSynastryPartner({
      partnerName: pName,
      partnerCity: partnerForm.city,
      partnerLat: partnerForm.lat,
      partnerLng: partnerForm.lng,
      partnerTime: partnerTimeStr
    })

    // 对方信息已存入 DB，调用无参数的和盘计算接口，后端自动从 user 表读取
    const result = await getSynastryChart()
    chartData.value = result

    // 同步到前端 store（本地缓存，方便下次回填）
    userStore.updateSynastryPartner(
      pName,
      partnerForm.city,
      partnerForm.lat,
      partnerForm.lng,
      partnerTimeStr
    )
    // 更新缓存标志位
    userStore.updateAstrologyCache({ hasSynastryCache: true })
  } catch (e: any) {
    const errCode = e?.data?.code
    if (errCode === 7001) {
      // 出生信息未设置，直接回到 form 展示友好占位
      clearInterval(timer)
      step.value = 'form'
      userStore.updateAstrologyCache({ hasSynastryCache: false })
      return
    }
    chartData.value = { relationshipModel: null, aspects: [], themes: [], chart: null }
  } finally {
    clearInterval(timer)
    step.value = 'result'
  }
}

async function getInterpretation() {
  // 自增版本号，令所有正在运行的旧实例在下次循环时感知到版本变化并安全退出
  const myGeneration = ++typingGeneration

  isInterpreting.value = true
  interpretation.value = ''
  activeTab.value = 'interpret'
  try {
    // chart 由后端从 DB 读取，前端只传 relationshipType 和 focus
    // 将前端 UI 的关系分类 key 映射为后端期望的 relationshipType 值
    const typeMap: Record<string, 'romantic' | 'family' | 'friendship' | 'colleague'> = {
      love: 'romantic',
      marriage: 'romantic',
      soul: 'friendship',
      emotion: 'romantic'
    }
    const relationshipType = typeMap[selectedInterpretType.value] ?? 'romantic'
    const result = await interpretSynastry({ relationshipType, focus: selectedInterpretType.value, tone: 'gentle' })
    const text = result.interpretation
    for (let i = 0; i <= text.length; i++) {
      if (typingGeneration !== myGeneration) {
        // 有新实例启动，立即将文本设置为完整内容后退出，避免 UI 卡在半截
        interpretation.value = text
        return
      }
      interpretation.value = text.slice(0, i)
      await new Promise(r => setTimeout(r, 25))
    }
  } catch {
    const mock = `💞 你们之间有一种深刻的灵魂契约感。金星与月亮的六分相位意味着你们的情感表达方式天然和谐，彼此能够理解对方在关系中的需求。\n\n这份关系中有强烈的吸引力，但也要注意火星与土星的紧张相位——在某些具体事务的执行和推进上，双方可能会产生节奏不一致的摩擦。\n\n长期来看，你们关系中最稳固的基础来自于相似的价值观和人生方向感，这是一种可以共同成长的连接方式。🌙`
    for (let i = 0; i <= mock.length; i++) {
      if (typingGeneration !== myGeneration) {
        interpretation.value = mock
        return
      }
      interpretation.value = mock.slice(0, i)
      await new Promise(r => setTimeout(r, 20))
    }
  } finally {
    // 仅当前实例仍是最新版本时才清除加载状态，防止旧实例误关闭新实例的 loading
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
  <view class="synastry-page" @click="closeAllSuggestions">
    <view class="bg-gradient-s" />

    <!-- Loading -->
    <view v-if="step === 'loading'" class="loading-screen">
      <view class="loading-dual">
        <view class="dual-orb orb-a">
          <view class="orb-ring r1" /><view class="orb-ring r2" />
          <text class="orb-sym">♀</text>
        </view>
        <view class="dual-connect">
          <view class="connect-line" />
          <view class="connect-dot" />
        </view>
        <view class="dual-orb orb-b">
          <view class="orb-ring r1" /><view class="orb-ring r2" />
          <text class="orb-sym">♂</text>
        </view>
      </view>
      <text class="loading-text">{{ loadingText }}</text>
      <text class="loading-sub">正在感应两颗星球的相遇</text>
    </view>

    <!-- 表单 -->
    <scroll-view v-if="step === 'form'" class="page-scroll" scroll-y>
      <view class="form-header">
        <view class="form-icon-row">
          <view class="ficon-wrap ficon-a"><text class="ficon">♀</text></view>
          <text class="ficon-link">∞</text>
          <view class="ficon-wrap ficon-b"><text class="ficon">♂</text></view>
        </view>
        <text class="form-title">和盘分析</text>
        <text class="form-desc">两颗灵魂的星盘相遇，揭示关系的宇宙密码</text>
      </view>

      <!-- 我的出生信息（只读，从 userStore 读取） -->
      <view class="person-card self-card">
        <view class="person-label-row">
          <view class="person-dot dot-self" />
          <text class="person-label">我</text>
          <view class="goto-natal-btn" @click="goToNatalPage">
            <text class="goto-natal-text">{{ hasBirthInfo() ? '✏️ 修改' : '⚙️ 去设置' }}</text>
          </view>
        </view>

        <!-- 已设置出生信息时展示 -->
        <view v-if="hasBirthInfo()" class="birth-info-display">
          <view class="birth-info-row">
            <text class="birth-info-icon">⏰</text>
            <text class="birth-info-text">{{ selfBirthDisplay }}</text>
          </view>
        </view>

        <!-- 未设置出生信息时提示 -->
        <view v-else class="birth-info-empty" @click="goToNatalPage">
          <text class="birth-empty-icon">⚙️</text>
          <text class="birth-empty-text">请先设置您的出生信息</text>
          <text class="birth-empty-sub">点击前往本命盘页面设置</text>
        </view>
      </view>

      <!-- 连接符号 -->
      <view class="connector-row">
        <view class="connector-line-v" />
        <view class="connector-badge">
          <text class="connector-sym">∞</text>
        </view>
        <view class="connector-line-v" />
      </view>

      <!-- 对方信息 -->
      <view class="person-card partner-card" @click.stop>
        <view class="person-label-row">
          <view class="person-dot dot-partner" />
          <view class="partner-name-row">
            <input class="partner-name-input" v-model="partnerName"
              placeholder="Ta 的名字（可选）" placeholder-style="color:#7a5888" maxlength="10" />
          </view>
        </view>
        <view class="date-row">
          <picker mode="selector" :range="YEAR_OPTIONS" range-key="label"
            :value="YEARS.indexOf(partnerForm.year)" @change="onPartnerYearChange">
            <view class="picker-btn-s picker-partner">{{ partnerForm.year }}年</view>
          </picker>
          <picker mode="selector" :range="MONTH_OPTIONS" range-key="label"
            :value="partnerForm.month - 1" @change="onPartnerMonthChange">
            <view class="picker-btn-s picker-partner">{{ partnerForm.month }}月</view>
          </picker>
          <picker mode="selector" :range="DAY_OPTIONS" range-key="label"
            :value="partnerForm.day - 1" @change="onPartnerDayChange">
            <view class="picker-btn-s picker-partner">{{ partnerForm.day }}日</view>
          </picker>
          <picker mode="selector" :range="HOUR_OPTIONS" range-key="label"
            :value="partnerForm.hour" @change="onPartnerHourChange">
            <view class="picker-btn-s picker-partner">{{ partnerForm.hour }}时</view>
          </picker>
          <picker mode="selector" :range="MINUTE_OPTIONS" range-key="label"
            :value="partnerForm.minute" @change="onPartnerMinuteChange">
            <view class="picker-btn-s picker-partner">{{ String(partnerForm.minute).padStart(2, '0') }}分</view>
          </picker>
        </view>

        <!-- Ta的城市搜索 -->
        <view class="city-search-wrap-s" @click.stop>
          <view class="input-row-s input-row-partner">
            <text class="input-icon-s">📍</text>
            <input
              class="city-input-s city-input-partner"
              :value="partnerCityKeyword"
              @input="onPartnerCityInput"
              placeholder="Ta 的出生城市"
              placeholder-style="color:#9a6080"
              maxlength="30"
            />
            <view v-if="partnerCityKeyword" class="input-clear-s" @click.stop="() => { partnerCityKeyword = ''; partnerForm.city = ''; partnerForm.lat = null; partnerForm.lng = null; showPartnerSuggestions = false }">
              <text class="clear-icon-s">✕</text>
            </view>
          </view>
          <view v-if="showPartnerSuggestions && partnerCityResults.length > 0" class="city-suggestions-s city-suggestions-partner">
            <view
              v-for="city in partnerCityResults"
              :key="city.name"
              class="city-suggestion-item-s"
              @click.stop="selectPartnerCity(city)"
            >
              <text class="suggestion-icon-s">📍</text>
              <view class="suggestion-info-s">
                <text class="suggestion-name-s">{{ city.name }}</text>
                <text v-if="city.address" class="suggestion-addr-s">{{ city.address }}</text>
              </view>
            </view>
          </view>
          <view v-if="partnerForm.lat && partnerForm.lng" class="location-tag-s partner-tag">
            <text class="location-tag-icon-s">✓</text>
            <text class="location-tag-text-s">{{ partnerForm.city }}</text>
          </view>
        </view>
      </view>

      <view class="submit-btn" :class="{ 'btn-disabled': !hasBirthInfo() }" @click="calculateSynastry">
        <text class="submit-text">✦ 解析双人星盘</text>
      </view>
      <view style="height: 120rpx" />
    </scroll-view>

    <!-- 结果页 -->
    <view v-if="step === 'result'" class="result-page">
      <!-- 双人摘要 -->
      <view class="dual-summary">
        <view class="ds-person">
          <view class="ds-avatar ds-a">
            <text class="ds-sym">♀</text>
          </view>
          <text class="ds-name">我</text>
        </view>
        <view class="ds-center">
          <view class="ds-score-wrap">
            <text class="ds-score">{{ compatibilityScore != null ? compatibilityScore : '—' }}</text>
            <text v-if="compatibilityScore != null" class="ds-score-unit">%</text>
          </view>
          <text class="ds-compat">契合度</text>
        </view>
        <view class="ds-person">
          <view class="ds-avatar ds-b">
            <text class="ds-sym">♂</text>
          </view>
          <text class="ds-name">{{ partnerName || 'Ta' }}</text>
        </view>
      </view>

      <!-- Tabs -->
      <view class="tabs-bar">
        <view class="tab-item" :class="{ active: activeTab === 'analysis' }" @click="activeTab = 'analysis'"><text>关系分析</text></view>
        <view class="tab-item" :class="{ active: activeTab === 'aspects' }" @click="activeTab = 'aspects'">
          <text>相位列表</text>
          <view v-if="aspectStats" class="tab-badge">{{ aspectStats.total }}</view>
        </view>
        <view class="tab-item" :class="{ active: activeTab === 'themes' }" @click="activeTab = 'themes'"><text>关系主题</text></view>
        <view class="tab-item" :class="{ active: activeTab === 'interpret' }" @click="activeTab = 'interpret'"><text>AI 解读</text></view>
      </view>

      <scroll-view class="tab-content" scroll-y>
        <!-- 关系分析 -->
        <view v-if="activeTab === 'analysis'">
          <view v-if="realAnalysis.length === 0" class="data-empty-tip-s">
            <text class="data-empty-icon-s">∞</text>
            <text class="data-empty-text-s">暂无关系分析数据</text>
          </view>
          <view v-for="item in realAnalysis" :key="item.label" class="analysis-row">
            <view class="analysis-head">
              <text class="analysis-label">{{ item.label }}</text>
              <text class="analysis-val" :style="{ color: item.color }">{{ item.value }}%</text>
            </view>
            <view class="analysis-track">
              <view class="analysis-fill" :style="{ width: item.value + '%', background: item.color }" />
            </view>
            <text class="analysis-desc">{{ item.desc }}</text>
          </view>

          <!-- 宫位叠加 -->
          <view v-if="houseOverlay" class="house-overlay-section">
            <text class="house-overlay-title">宫位叠加</text>
            <text class="house-overlay-subtitle">两人星盘中行星落入对方宫位的位置，揭示关系中的能量流向</text>

            <!-- 对方行星落在我的宫位 -->
            <view v-if="houseOverlay.partner.length > 0" class="house-overlay-group">
              <text class="house-overlay-group-label">{{ partnerName || 'Ta' }} → 我的宫位</text>
              <view class="house-overlay-chips">
                <view v-for="item in houseOverlay.partner" :key="item.planet" class="house-chip house-chip-partner">
                  <text class="hc-symbol">{{ item.pInfo?.symbol || item.planet.slice(0,2).toUpperCase() }}</text>
                  <text class="hc-planet">{{ item.pInfo?.name || item.planet }}</text>
                  <text class="hc-arrow">→</text>
                  <text class="hc-house">第{{ item.house }}宫</text>
                </view>
              </view>
            </view>

            <!-- 我的行星落在对方宫位 -->
            <view v-if="houseOverlay.me.length > 0" class="house-overlay-group">
              <text class="house-overlay-group-label">我 → {{ partnerName || 'Ta' }} 的宫位</text>
              <view class="house-overlay-chips">
                <view v-for="item in houseOverlay.me" :key="item.planet" class="house-chip house-chip-me">
                  <text class="hc-symbol">{{ item.pInfo?.symbol || item.planet.slice(0,2).toUpperCase() }}</text>
                  <text class="hc-planet">{{ item.pInfo?.name || item.planet }}</text>
                  <text class="hc-arrow">→</text>
                  <text class="hc-house">第{{ item.house }}宫</text>
                </view>
              </view>
            </view>
          </view>

        </view>

        <!-- 相位列表 -->
        <view v-if="activeTab === 'aspects'">
          <!-- 相位统计摘要 -->
          <view v-if="aspectStats" class="aspect-stats-row">
            <view class="ast-item ast-total">
              <text class="ast-val">{{ aspectStats.total }}</text>
              <text class="ast-label">总相位</text>
            </view>
            <view class="ast-divider" />
            <view class="ast-item ast-positive">
              <text class="ast-val">{{ aspectStats.positive }}</text>
              <text class="ast-label">和谐</text>
            </view>
            <view class="ast-divider" />
            <view class="ast-item ast-neutral">
              <text class="ast-val">{{ aspectStats.neutral }}</text>
              <text class="ast-label">中性</text>
            </view>
            <view class="ast-divider" />
            <view class="ast-item ast-challenge">
              <text class="ast-val">{{ aspectStats.challenge }}</text>
              <text class="ast-label">紧张</text>
            </view>
          </view>

          <!-- 空状态 -->
          <view v-if="realAspects.length === 0" class="data-empty-tip-s">
            <text class="data-empty-icon-s">⚯</text>
            <text class="data-empty-text-s">暂无双人相位数据</text>
          </view>

          <!-- 相位卡片列表 -->
          <view v-for="(asp, idx) in realAspects" :key="idx"
            class="aspect-card-s" :class="'asp-' + asp.harmony">
            <!-- 行星A -->
            <view class="asp-planet asp-p1">
              <view class="asp-sym-wrap" :class="'asp-sw-' + asp.harmony">
                <text class="asp-sym-text">{{ asp.p1Symbol }}</text>
              </view>
              <view class="asp-info">
                <text class="asp-planet-name">{{ asp.p1Name }}</text>
                <text v-if="asp.p1Sign" class="asp-planet-sign">{{ asp.p1Sign }}</text>
              </view>
            </view>

            <!-- 相位符号 -->
            <view class="asp-mid">
              <text class="asp-symbol-main" :class="'asym-' + asp.harmony">{{ asp.aspectSymbol }}</text>
              <text class="asp-label-text">{{ asp.aspectLabel }}</text>
              <text v-if="asp.orb" class="asp-orb">{{ asp.orb }}</text>
              <!-- 力度 -->
              <text v-if="asp.strength != null" class="asp-strength">{{ asp.strength }}%</text>
            </view>

            <!-- 行星B -->
            <view class="asp-planet asp-p2">
              <view class="asp-sym-wrap" :class="'asp-sw-' + asp.harmony">
                <text class="asp-sym-text">{{ asp.p2Symbol }}</text>
              </view>
              <view class="asp-info asp-info-right">
                <text class="asp-planet-name">{{ asp.p2Name }}</text>
                <text v-if="asp.p2Sign" class="asp-planet-sign">{{ asp.p2Sign }}</text>
              </view>
            </view>

            <!-- tags 标签 -->
            <view v-if="asp.tags && asp.tags.length > 0" class="asp-tags-row">
              <text v-for="tag in asp.tags" :key="tag" class="asp-tag" :class="'asp-tag-' + tag">
                {{ tag === 'emotional' ? '情感' : tag === 'attraction' ? '吸引' : tag === 'conflict' ? '冲突' : tag }}
              </text>
            </view>

            <!-- 说明（折叠展示） -->
            <view v-if="asp.description" class="asp-desc-wrap">
              <text class="asp-desc-text">{{ asp.description }}</text>
            </view>
          </view>
        </view>

        <!-- 关系主题 -->
        <view v-if="activeTab === 'themes'">
          <view v-if="realThemes.length === 0" class="data-empty-tip-s">
            <text class="data-empty-icon-s">💫</text>
            <text class="data-empty-text-s">暂无关系主题数据</text>
          </view>
          <view v-for="(theme, idx) in realThemes" :key="idx" class="theme-card"
            :class="'theme-' + theme.type">
            <view class="theme-icon-wrap">
              <text class="theme-icon">{{ theme.icon }}</text>
            </view>
            <view class="theme-content">
              <text class="theme-title">{{ theme.title }}</text>
              <text v-if="theme.desc" class="theme-desc">{{ theme.desc }}</text>
            </view>
          </view>
        </view>

        <!-- AI 解读 -->
        <view v-if="activeTab === 'interpret'" class="interpret-panel">
          <view class="interpret-type-list">
            <view v-for="t in INTERPRET_TYPES" :key="t.key"
              class="itype-chip" :class="{ 'itype-active': selectedInterpretType === t.key }"
              @click="selectedInterpretType = t.key">
              <text class="itype-icon">{{ t.icon }}</text>
              <text class="itype-label">{{ t.label }}</text>
            </view>
          </view>
          <view v-if="!interpretation && !isInterpreting" class="interpret-trigger" @click="getInterpretation">
            <view class="trigger-glow-s" />
            <text class="trigger-icon">∞</text>
            <text class="trigger-text">开始解读你们的关系</text>
            <text class="trigger-sub">AI 分析双人星盘能量</text>
          </view>
          <view v-if="interpretation || isInterpreting" class="interpret-content">
            <view class="interpret-header">
              <text class="interpret-focus-label">{{ INTERPRET_TYPES.find(t => t.key === selectedInterpretType)?.label }}</text>
              <view v-if="isInterpreting" class="typing-indicator">
                <view class="typing-dot" /><view class="typing-dot" /><view class="typing-dot" />
              </view>
            </view>
            <text class="interpret-text">{{ interpretation }}</text>
          </view>
          <view v-if="interpretation && !isInterpreting" class="re-interpret-btn" @click="getInterpretation">
            <text>↻ 换个角度解读</text>
          </view>
        </view>
        <view style="height: 160rpx" />
      </scroll-view>

      <view class="result-actions">
        <view class="action-btn-outline" @click="step = 'form'"><text>重新计算</text></view>
        <view class="action-btn-primary-s" @click="goInterpret"><text>∞ AI 解读关系</text></view>
      </view>
    </view>
  </view>
</template>

<style>
/* ═══════════════════════════════════════════
   和盘分析页 · 深色宇宙风格
═══════════════════════════════════════════ */

.synastry-page {
  min-height: 100vh;
  background: #0a0a1a;
  position: relative;
}

.bg-gradient-s {
  position: fixed;
  top: 0; left: 0; right: 0;
  height: 400rpx;
  background: radial-gradient(ellipse at 50% 0%, rgba(155, 135, 209, 0.12) 0%, transparent 70%);
  pointer-events: none;
  z-index: 0;
}

/* Loading */
.loading-screen {
  height: 100vh;
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  gap: 44rpx;
  background: #0a0a1a;
}
.loading-dual { display: flex; align-items: center; gap: 20rpx; }
.dual-orb {
  width: 120rpx; height: 120rpx;
  position: relative;
  display: flex; align-items: center; justify-content: center;
}
.orb-a .orb-ring { border-color: rgba(100, 150, 220, 0.4); }
.orb-b .orb-ring { border-color: rgba(220, 100, 160, 0.4); }
.orb-ring {
  position: absolute; border-radius: 50%;
  border: 1rpx solid;
  animation: spin 4s linear infinite;
}
.r1 { width: 70rpx; height: 70rpx; }
.r2 { width: 110rpx; height: 110rpx; animation-duration: 7s; border-style: dashed; opacity: 0.7; }
@keyframes spin { to { transform: rotate(360deg); } }
.orb-sym { font-size: 38rpx; color: #a0b0d0; z-index: 1; }
.dual-connect { display: flex; flex-direction: column; align-items: center; gap: 8rpx; }
.connect-line { width: 56rpx; height: 1rpx; background: linear-gradient(90deg, rgba(100, 150, 220, 0.5), rgba(220, 100, 160, 0.5)); }
.connect-dot { width: 11rpx; height: 11rpx; border-radius: 50%; background: #9b87d1; box-shadow: 0 0 10rpx rgba(155,135,209,0.8); }
.loading-text { font-size: 30rpx; color: #c4b4f0; letter-spacing: 2rpx; }
.loading-sub { font-size: 23rpx; color: rgba(155, 135, 209, 0.5); }

/* 表单 */
.page-scroll { position: relative; z-index: 1; padding: 0 26rpx; }
.form-header {
  padding-top: 56rpx; padding-bottom: 34rpx;
  display: flex; flex-direction: column; align-items: center; gap: 14rpx;
}
.form-icon-row { display: flex; align-items: center; gap: 18rpx; margin-bottom: 6rpx; }
.ficon-wrap {
  width: 78rpx; height: 78rpx; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
}
.ficon-a { background: rgba(100, 150, 220, 0.12); border: 1.5rpx solid rgba(100, 150, 220, 0.26); }
.ficon-b { background: rgba(220, 100, 160, 0.12); border: 1.5rpx solid rgba(220, 100, 160, 0.26); }
.ficon { font-size: 38rpx; color: #7a8ab0; }
.ficon-link { font-size: 40rpx; color: #c3aaee; }
.form-title { font-size: 44rpx; color: #e8e0ff; font-weight: 700; display: block; }
.form-desc { font-size: 24rpx; color: rgba(155, 135, 209, 0.55); text-align: center; line-height: 1.65; display: block; }

.person-card {
  background: rgba(20, 16, 42, 0.90);
  border-radius: 24rpx;
  padding: 28rpx;
  margin-bottom: 8rpx;
  backdrop-filter: blur(12rpx);
  box-shadow: 0 2rpx 20rpx rgba(0, 0, 0, 0.3);
}
.self-card { border: 1.5rpx solid rgba(100, 150, 220, 0.25); }
.partner-card { border: 1.5rpx solid rgba(220, 100, 160, 0.25); }

.person-label-row { display: flex; align-items: center; gap: 12rpx; margin-bottom: 18rpx; }
.person-dot { width: 14rpx; height: 14rpx; border-radius: 50%; flex-shrink: 0; }
.dot-self { background: rgba(100, 150, 220, 0.85); box-shadow: 0 0 10rpx rgba(100, 150, 220, 0.5); }
.dot-partner { background: rgba(220, 100, 160, 0.85); box-shadow: 0 0 10rpx rgba(220, 100, 160, 0.5); }
.person-label { font-size: 27rpx; color: #e8e0ff; font-weight: 600; flex: 1; }
.partner-name-row { flex: 1; }
.partner-name-input { font-size: 27rpx; color: #d8d0f8; width: 100%; }

/* 跳转到本命盘按钮 */
.goto-natal-btn {
  padding: 8rpx 18rpx;
  background: rgba(100, 150, 220, 0.12);
  border: 1rpx solid rgba(100, 150, 220, 0.3);
  border-radius: 20rpx;
}
.goto-natal-text { font-size: 22rpx; color: rgba(130, 170, 240, 0.85); }

/* 我的出生信息展示 */
.birth-info-display {
  background: rgba(30, 40, 70, 0.60);
  border-radius: 14rpx;
  padding: 16rpx 18rpx;
}
.birth-info-row {
  display: flex; align-items: center; gap: 10rpx;
}
.birth-info-icon { font-size: 26rpx; flex-shrink: 0; }
.birth-info-text { font-size: 25rpx; color: rgba(160, 190, 240, 0.85); line-height: 1.5; flex: 1; }

/* 未设置出生信息提示 */
.birth-info-empty {
  background: rgba(30, 40, 70, 0.50);
  border: 1.5rpx dashed rgba(100, 150, 220, 0.30);
  border-radius: 14rpx;
  padding: 28rpx; text-align: center;
  display: flex; flex-direction: column; align-items: center; gap: 8rpx;
}
.birth-empty-icon { font-size: 38rpx; }
.birth-empty-text { font-size: 26rpx; color: rgba(130, 170, 240, 0.85); font-weight: 500; }
.birth-empty-sub { font-size: 21rpx; color: rgba(155, 135, 209, 0.5); }

.date-row { display: flex; gap: 8rpx; margin-bottom: 14rpx; }
.picker-btn-s {
  background: rgba(30, 36, 70, 0.85);
  border: 1.5rpx solid rgba(100, 150, 220, 0.25);
  border-radius: 13rpx;
  padding: 13rpx 7rpx;
  text-align: center;
  font-size: 23rpx;
  color: rgba(160, 190, 240, 0.85);
}
.picker-partner {
  background: rgba(50, 24, 50, 0.85) !important;
  border-color: rgba(220, 100, 160, 0.25) !important;
  color: rgba(240, 170, 210, 0.85) !important;
}

/* 城市搜索（和盘） */
.city-search-wrap-s { position: relative; z-index: 10; }

.input-row-s {
  display: flex; align-items: center; gap: 12rpx;
  background: rgba(30, 36, 70, 0.85);
  border: 1.5rpx solid rgba(100, 150, 220, 0.25);
  border-radius: 13rpx;
  padding: 13rpx 15rpx;
}
.input-row-partner {
  background: rgba(50, 24, 50, 0.85) !important;
  border-color: rgba(220, 100, 160, 0.25) !important;
}
.input-icon-s { font-size: 26rpx; flex-shrink: 0; }
.city-input-s { flex: 1; font-size: 27rpx; color: rgba(160, 190, 240, 0.85); min-width: 0; }
.city-input-partner { color: rgba(240, 170, 210, 0.85) !important; }
.input-clear-s {
  width: 36rpx; height: 36rpx;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.clear-icon-s { font-size: 22rpx; color: rgba(155, 135, 209, 0.4); }

.city-suggestions-s {
  position: absolute;
  top: 100%; left: 0; right: 0;
  margin-top: 8rpx;
  background: rgba(16, 12, 36, 0.97);
  border: 1.5rpx solid rgba(100, 150, 220, 0.25);
  border-radius: 16rpx; overflow: hidden; z-index: 100;
  box-shadow: 0 8rpx 32rpx rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(20rpx);
}
.city-suggestions-partner {
  border-color: rgba(220, 100, 160, 0.25) !important;
}
.city-suggestion-item-s {
  display: flex; align-items: center; gap: 14rpx;
  padding: 20rpx 22rpx;
  border-bottom: 1rpx solid rgba(155, 135, 209, 0.1);
}
.city-suggestion-item-s:last-child { border-bottom: none; }
.suggestion-icon-s { font-size: 26rpx; flex-shrink: 0; }
.suggestion-info-s { flex: 1; }
.suggestion-name-s { font-size: 29rpx; color: #d8d0f8; display: block; }
.suggestion-addr-s { font-size: 22rpx; color: rgba(155, 135, 209, 0.55); display: block; margin-top: 3rpx; }

.location-tag-s {
  display: flex; align-items: center; gap: 9rpx;
  margin-top: 10rpx;
  border-radius: 10rpx; padding: 8rpx 14rpx;
}
.self-tag {
  background: rgba(60, 90, 160, 0.15);
  border: 1rpx solid rgba(100, 150, 220, 0.3);
}
.partner-tag {
  background: rgba(160, 50, 100, 0.15);
  border: 1rpx solid rgba(220, 100, 160, 0.3);
}
.location-tag-icon-s { font-size: 22rpx; color: #70e0a0; flex-shrink: 0; }
.location-tag-text-s { font-size: 21rpx; color: rgba(200, 220, 250, 0.75); line-height: 1.4; }

.connector-row {
  display: flex; flex-direction: column;
  align-items: center; gap: 0;
  margin: 0;
  padding: 10rpx 0;
}
.connector-line-v { width: 1rpx; height: 24rpx; background: rgba(155, 135, 209, 0.25); }
.connector-badge {
  width: 56rpx; height: 56rpx;
  border-radius: 50%;
  background: rgba(155, 135, 209, 0.12);
  border: 1.5rpx solid rgba(155, 135, 209, 0.3);
  display: flex; align-items: center; justify-content: center;
}
.connector-sym { font-size: 26rpx; color: #c4b4f0; }

.submit-btn {
  background: linear-gradient(135deg, #8a70c8, #9b87d1);
  border-radius: 20rpx; padding: 30rpx; text-align: center;
  box-shadow: 0 8rpx 32rpx rgba(155, 135, 209, 0.4);
  margin-top: 22rpx;
}
.submit-btn.btn-disabled {
  background: rgba(60, 50, 90, 0.6);
  box-shadow: none;
  opacity: 0.6;
}
.submit-text { font-size: 32rpx; color: white; font-weight: 600; letter-spacing: 4rpx; }

/* 结果页 */
.result-page { height: 100vh; display: flex; flex-direction: column; position: relative; z-index: 1; }

.dual-summary {
  background: rgba(16, 12, 36, 0.97);
  backdrop-filter: blur(20rpx);
  padding: 28rpx;
  display: flex; align-items: center; justify-content: space-between;
  flex-shrink: 0;
  border-bottom: 1rpx solid rgba(155, 135, 209, 0.15);
}
.ds-person { display: flex; flex-direction: column; align-items: center; gap: 10rpx; }
.ds-avatar {
  width: 84rpx; height: 84rpx; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
}
.ds-a { background: rgba(100, 150, 220, 0.15); border: 2rpx solid rgba(100, 150, 220, 0.4); box-shadow: 0 0 20rpx rgba(100,150,220,0.2); }
.ds-b { background: rgba(220, 100, 160, 0.15); border: 2rpx solid rgba(220, 100, 160, 0.4); box-shadow: 0 0 20rpx rgba(220,100,160,0.2); }
.ds-sym { font-size: 40rpx; color: #a0b8e0; }
.ds-name { font-size: 22rpx; color: rgba(155, 135, 209, 0.6); }
.ds-center { display: flex; flex-direction: column; align-items: center; gap: 7rpx; }
.ds-score-wrap { display: flex; align-items: baseline; gap: 4rpx; }
.ds-score { font-size: 68rpx; color: #e8e0ff; font-weight: bold; line-height: 1; text-shadow: 0 0 30rpx rgba(155,135,209,0.5); }
.ds-score-unit { font-size: 28rpx; color: #c4b4f0; }
.ds-compat { font-size: 22rpx; color: rgba(155, 135, 209, 0.55); }

.tabs-bar {
  display: flex;
  background: rgba(16, 12, 36, 0.95);
  border-bottom: 1rpx solid rgba(155, 135, 209, 0.12);
  flex-shrink: 0;
}
.tab-item {
  flex: 1; padding: 22rpx; text-align: center;
  font-size: 27rpx; color: rgba(155, 135, 209, 0.4); position: relative;
}
.tab-item.active { color: #c4b4f0; font-weight: 600; }
.tab-item.active::after {
  content: ''; position: absolute; bottom: 0; left: 20%; right: 20%;
  height: 3rpx;
  background: linear-gradient(90deg, #8a70c8, #9b87d1);
  border-radius: 2rpx;
}
.tab-content { flex: 1; padding: 22rpx 24rpx; background: rgba(10, 8, 24, 0.98); }

/* 关系分析 */
.analysis-row { margin-bottom: 26rpx; }
.analysis-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 9rpx; }
.analysis-label { font-size: 26rpx; color: #e8e0ff; font-weight: 600; }
.analysis-val { font-size: 30rpx; font-weight: bold; }
.analysis-track { height: 9rpx; background: rgba(155, 135, 209, 0.12); border-radius: 5rpx; overflow: hidden; margin-bottom: 7rpx; }
.analysis-fill { height: 100%; border-radius: 5rpx; opacity: 0.85; }
.analysis-desc { font-size: 22rpx; color: rgba(155, 135, 209, 0.55); }

/* 关系主题 */
.theme-card {
  display: flex; gap: 18rpx; align-items: flex-start;
  padding: 22rpx; border-radius: 20rpx; margin-bottom: 14rpx;
  border: 1rpx solid;
  background: rgba(20, 16, 42, 0.85);
}
.theme-positive { border-color: rgba(80, 200, 120, 0.25); }
.theme-neutral { border-color: rgba(100, 150, 220, 0.22); }
.theme-challenge { border-color: rgba(240, 100, 80, 0.22); }
.theme-icon-wrap { flex-shrink: 0; font-size: 38rpx; }
.theme-icon { font-size: 38rpx; }
.theme-title { font-size: 28rpx; color: #e8e0ff; font-weight: 600; display: block; margin-bottom: 7rpx; }
.theme-desc { font-size: 23rpx; color: rgba(155, 135, 209, 0.6); line-height: 1.73; }

/* AI 解读 */
.interpret-panel { padding: 6rpx 0; }
.interpret-type-list { display: flex; gap: 12rpx; flex-wrap: wrap; margin-bottom: 22rpx; }
.itype-chip {
  display: flex; align-items: center; gap: 8rpx;
  padding: 12rpx 20rpx; border-radius: 30rpx;
  background: rgba(30, 24, 60, 0.85); border: 1.5rpx solid rgba(155, 135, 209, 0.18);
}
.itype-active { background: rgba(155, 135, 209, 0.15); border-color: rgba(155, 135, 209, 0.5); }
.itype-icon { font-size: 24rpx; }
.itype-label { font-size: 24rpx; color: rgba(155, 135, 209, 0.55); }
.itype-active .itype-label { color: #c4b4f0; }

.interpret-trigger {
  position: relative;
  background: rgba(20, 16, 42, 0.90);
  border: 1.5rpx solid rgba(155, 135, 209, 0.25);
  border-radius: 24rpx; padding: 44rpx 28rpx; text-align: center; overflow: hidden;
}
.trigger-glow-s {
  position: absolute; top: -40rpx; left: 50%;
  width: 200rpx; height: 200rpx; border-radius: 50%;
  background: radial-gradient(circle, rgba(155, 135, 209, 0.2), transparent);
  transform: translateX(-50%);
}
.trigger-icon { font-size: 44rpx; color: #c4b4f0; display: block; margin-bottom: 14rpx; text-shadow: 0 0 20rpx rgba(155,135,209,0.8); }
.trigger-text { font-size: 34rpx; color: #e8e0ff; font-weight: 600; display: block; margin-bottom: 9rpx; }
.trigger-sub { font-size: 23rpx; color: rgba(155, 135, 209, 0.5); display: block; }

.interpret-content {
  background: rgba(20, 16, 42, 0.90); border: 1.5rpx solid rgba(155, 135, 209, 0.18);
  border-radius: 20rpx; padding: 26rpx 28rpx;
}
.interpret-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18rpx; }
.interpret-focus-label { font-size: 25rpx; color: #c4b4f0; font-weight: 600; }

.typing-indicator { display: flex; gap: 7rpx; align-items: center; }
.typing-dot {
  width: 9rpx; height: 9rpx; border-radius: 50%;
  background: #9b87d1; animation: bounce 1.2s ease-in-out infinite;
}
.typing-dot:nth-child(2) { animation-delay: 0.2s; }
.typing-dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes bounce {
  0%, 80%, 100% { transform: translateY(0); opacity: 0.4; }
  40% { transform: translateY(-7rpx); opacity: 1; }
}
.interpret-text { font-size: 27rpx; color: rgba(220, 210, 250, 0.85); line-height: 2; white-space: pre-wrap; }

.re-interpret-btn {
  text-align: center; padding: 22rpx; color: #c4b4f0; font-size: 25rpx;
  border: 1.5rpx solid rgba(155, 135, 209, 0.25); border-radius: 16rpx; margin-top: 14rpx;
  background: rgba(30, 24, 60, 0.85);
}

/* 空数据提示 */
.data-empty-tip-s {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  padding: 56rpx 0;
  opacity: 0.7;
}
.data-empty-icon-s { font-size: 40rpx; color: rgba(155, 135, 209, 0.35); }
.data-empty-text-s { font-size: 25rpx; color: rgba(155, 135, 209, 0.45); }

.result-actions {
  padding: 18rpx 24rpx;
  padding-bottom: calc(18rpx + env(safe-area-inset-bottom));
  display: flex; gap: 14rpx;
  background: rgba(16, 12, 36, 0.97);
  backdrop-filter: blur(20rpx);
  border-top: 1rpx solid rgba(155, 135, 209, 0.15);
  flex-shrink: 0;
}
.action-btn-outline {
  flex: 1; padding: 24rpx; text-align: center;
  border: 1.5rpx solid rgba(155, 135, 209, 0.28); border-radius: 16rpx;
  font-size: 28rpx; color: rgba(155, 135, 209, 0.7);
  background: rgba(30, 24, 60, 0.8);
}
.action-btn-primary-s {
  flex: 2; padding: 24rpx; text-align: center;
  background: linear-gradient(135deg, #8a70c8, #9b87d1);
  border-radius: 16rpx; font-size: 28rpx; color: white; font-weight: 600;
  box-shadow: 0 4rpx 24rpx rgba(155, 135, 209, 0.4);
}

/* ── Tab Badge（相位数量气泡） ───────────────────────────── */
.tab-item {
  position: relative;
  display: flex; align-items: center; justify-content: center; gap: 6rpx;
}
.tab-badge {
  background: rgba(155, 135, 209, 0.4);
  border-radius: 20rpx;
  padding: 2rpx 10rpx;
  font-size: 18rpx;
  color: #e8e0ff;
  line-height: 1.4;
}
.tab-item.active .tab-badge {
  background: rgba(155, 135, 209, 0.7);
}

/* ── 相位统计行 ──────────────────────────────────────────── */
.aspect-stats-row {
  display: flex;
  align-items: center;
  justify-content: space-around;
  background: rgba(20, 16, 42, 0.85);
  border: 1rpx solid rgba(155, 135, 209, 0.15);
  border-radius: 20rpx;
  padding: 18rpx 12rpx;
  margin-bottom: 18rpx;
}
.ast-item {
  display: flex; flex-direction: column; align-items: center; gap: 6rpx;
  flex: 1;
}
.ast-divider {
  width: 1rpx; height: 40rpx;
  background: rgba(155, 135, 209, 0.15);
  flex-shrink: 0;
}
.ast-val {
  font-size: 36rpx;
  font-weight: bold;
  line-height: 1;
}
.ast-label {
  font-size: 20rpx;
  color: rgba(155, 135, 209, 0.55);
}
.ast-total .ast-val  { color: #e8e0ff; }
.ast-positive .ast-val { color: #70d890; }
.ast-neutral .ast-val  { color: #a0b8e0; }
.ast-challenge .ast-val { color: #f07870; }

/* ── 相位卡片 ────────────────────────────────────────────── */
.aspect-card-s {
  background: rgba(20, 16, 42, 0.85);
  border-radius: 18rpx;
  margin-bottom: 12rpx;
  padding: 16rpx 18rpx 12rpx;
  border: 1rpx solid;
  display: flex;
  flex-direction: column;
  gap: 0;
}
.asp-positive  { border-color: rgba(80, 200, 120, 0.25); }
.asp-neutral   { border-color: rgba(100, 140, 210, 0.22); }
.asp-challenge { border-color: rgba(220, 80, 80, 0.22); }

/* 主行（行星A ＋ 相位符号 ＋ 行星B） */
.aspect-card-s .asp-p1 {
  display: flex; align-items: center; gap: 12rpx; width: 100%;
}

/* 统一用 flex row 排列三个区域 */
.aspect-card-s {
  flex-direction: row;
  flex-wrap: wrap;
  align-items: center;
}

.asp-planet {
  display: flex;
  align-items: center;
  gap: 10rpx;
  flex: 1;
  min-width: 0;
}
.asp-p2 { flex-direction: row-reverse; }

.asp-sym-wrap {
  width: 52rpx; height: 52rpx;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.asp-sw-positive  { background: rgba(64, 200, 120, 0.12);  border: 1rpx solid rgba(64, 200, 120, 0.3); }
.asp-sw-neutral   { background: rgba(100, 140, 210, 0.12); border: 1rpx solid rgba(100, 140, 210, 0.3); }
.asp-sw-challenge { background: rgba(220, 80, 80, 0.10);   border: 1rpx solid rgba(220, 80, 80, 0.25); }

.asp-sym-text {
  font-size: 26rpx;
  color: #e0d8ff;
}

.asp-info {
  flex: 1; min-width: 0;
  display: flex; flex-direction: column; gap: 3rpx;
}
.asp-info-right {
  align-items: flex-end;
  text-align: right;
}
.asp-planet-name {
  font-size: 26rpx;
  color: #e8e0ff;
  font-weight: 600;
}
.asp-planet-sign {
  font-size: 20rpx;
  color: rgba(155, 135, 209, 0.55);
}

/* 中间相位区 */
.asp-mid {
  display: flex; flex-direction: column; align-items: center; gap: 3rpx;
  padding: 0 8rpx;
  flex-shrink: 0;
  min-width: 80rpx;
}
.asp-symbol-main {
  font-size: 32rpx;
  font-weight: bold;
  line-height: 1;
}
.asym-positive  { color: #70d890; text-shadow: 0 0 10rpx rgba(70,200,120,0.5); }
.asym-neutral   { color: #a0b8e0; text-shadow: 0 0 10rpx rgba(100,140,210,0.5); }
.asym-challenge { color: #f07870; text-shadow: 0 0 10rpx rgba(220,80,80,0.5); }
.asp-label-text {
  font-size: 18rpx;
  color: rgba(155, 135, 209, 0.6);
  text-align: center;
}
.asp-orb {
  font-size: 17rpx;
  color: rgba(155, 135, 209, 0.4);
  text-align: center;
}

/* 相位说明（下方展开） */
.asp-desc-wrap {
  width: 100%;
  margin-top: 10rpx;
  padding: 10rpx 12rpx;
  background: rgba(155, 135, 209, 0.05);
  border-radius: 10rpx;
  border-top: 1rpx solid rgba(155, 135, 209, 0.1);
}
.asp-desc-text {
  font-size: 22rpx;
  color: rgba(155, 135, 209, 0.65);
  line-height: 1.7;
}

/* 相位力度 */
.asp-strength {
  font-size: 15rpx;
  color: rgba(155, 135, 209, 0.38);
  text-align: center;
}

/* 相位 tags */
.asp-tags-row {
  width: 100%;
  display: flex; flex-wrap: wrap; gap: 8rpx;
  margin-top: 8rpx;
  padding-top: 8rpx;
  border-top: 1rpx solid rgba(155, 135, 209, 0.08);
}
.asp-tag {
  font-size: 18rpx;
  padding: 3rpx 12rpx;
  border-radius: 20rpx;
  background: rgba(155, 135, 209, 0.08);
  color: rgba(155, 135, 209, 0.55);
  border: 1rpx solid rgba(155, 135, 209, 0.15);
}
.asp-tag-emotional { background: rgba(100, 140, 220, 0.1); color: rgba(130, 170, 240, 0.7); border-color: rgba(100, 140, 220, 0.2); }
.asp-tag-attraction { background: rgba(220, 100, 160, 0.1); color: rgba(240, 140, 200, 0.7); border-color: rgba(220, 100, 160, 0.2); }
.asp-tag-conflict { background: rgba(220, 80, 80, 0.1); color: rgba(240, 130, 110, 0.7); border-color: rgba(220, 80, 80, 0.2); }

/* ── 宫位叠加 ───────────────────────────────────────────── */
.house-overlay-section {
  margin-top: 28rpx;
  padding-top: 24rpx;
  border-top: 1rpx solid rgba(155, 135, 209, 0.12);
}
.house-overlay-title {
  display: block;
  font-size: 27rpx; color: #e8e0ff; font-weight: 600; margin-bottom: 6rpx;
}
.house-overlay-subtitle {
  display: block;
  font-size: 21rpx; color: rgba(155, 135, 209, 0.5); margin-bottom: 20rpx; line-height: 1.6;
}
.house-overlay-group {
  margin-bottom: 18rpx;
}
.house-overlay-group-label {
  display: block;
  font-size: 22rpx; color: rgba(155, 135, 209, 0.65); margin-bottom: 12rpx; font-weight: 500;
}
.house-overlay-chips {
  display: flex; flex-wrap: wrap; gap: 10rpx;
}
.house-chip {
  display: flex; align-items: center; gap: 7rpx;
  padding: 8rpx 14rpx; border-radius: 14rpx;
  background: rgba(20, 16, 42, 0.85); border: 1rpx solid;
}
.house-chip-partner {
  border-color: rgba(220, 100, 160, 0.3);
  background: rgba(60, 20, 50, 0.6);
}
.house-chip-me {
  border-color: rgba(100, 150, 220, 0.3);
  background: rgba(20, 40, 70, 0.6);
}
.hc-symbol { font-size: 22rpx; color: #c4b4f0; flex-shrink: 0; }
.hc-planet { font-size: 21rpx; color: rgba(200, 190, 240, 0.8); }
.hc-arrow { font-size: 18rpx; color: rgba(155, 135, 209, 0.4); }
.hc-house { font-size: 21rpx; color: rgba(200, 215, 245, 0.75); }

</style>

