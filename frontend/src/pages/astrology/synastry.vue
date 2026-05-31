<script setup lang="ts">
import {computed, onMounted, reactive, ref, watch} from 'vue'
import {getSynastryChart, interpretSynastry, type SynastryResponse} from '../../api/astrology'
import {updateSynastryPartner} from '../../api/auth'
import {useUserStore} from '../../store/user'

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
 * 将原始分数按各维度的参考上限线性映射到 0-100 的显示值。
 *
 * @param raw    原始分数（如 11.47）
 * @param refMax 该维度的参考上限，用于做线性映射：
 *               - attraction_score / conflict_score / stability_score 参考上限 5
 *               - emotional_score 参考上限 15
 *               - 已知是 0-100 的综合分（compatibility_score）传 100
 */
function normalizeScore(raw: number, refMax: number): number {
  if (refMax <= 0) return 0
  const mapped = Math.round((raw / refMax) * 100)
  return Math.max(0, Math.min(100, mapped))
}

/**
 * 从 chartData.relationshipModel 中提取关系分析维度数据。
 * 优先读取 Python rule_based_v1 规范的字段名：
 *   attraction_score / emotional_score / conflict_score / stability_score
 * 兼容旧字段名作为降级。
 * 原始分数不在 0-100 范围时，按各维度参考上限做规范化。
 */
const realAnalysis = computed(() => {
  const rm = chartData.value?.relationshipModel
  if (!rm) return []
  const items: Array<{ label: string, value: number, color: string, desc: string }> = []

  // 吸引力（参考上限 5，rule_based_v1 中 attraction 强度通常 0~5）
  const attractionRaw = rm.attraction_score ?? rm.attraction ?? rm.magnetic_attraction
  if (attractionRaw != null) {
    const val = normalizeScore(Number(attractionRaw), 5)
    items.push({ label: '吸引力', value: val, color: '#e070a0', desc: rm.attraction_desc || '双方星盘间的吸引强度' })
  }

  // 情绪匹配（参考上限 15，emotional_score 通常 0~15）
  const emotionalRaw = rm.emotional_score ?? rm.emotional_compatibility ?? rm.emotional_match ?? rm.emotion_score
  if (emotionalRaw != null) {
    const val = normalizeScore(Number(emotionalRaw), 15)
    items.push({ label: '情绪匹配', value: val, color: '#7080f0', desc: rm.emotion_desc || '情感波动的共鸣程度' })
  }

  // 冲突指数（参考上限 5，较高冲突通常不超过 5）
  const conflictRaw = rm.conflict_score ?? rm.conflict_index ?? rm.tension
  if (conflictRaw != null) {
    const val = normalizeScore(Number(conflictRaw), 5)
    items.push({ label: '冲突指数', value: val, color: '#f09040', desc: rm.conflict_desc || '关系中的摩擦与张力' })
  }

  // 长期稳定（参考上限 5）
  const stabilityRaw = rm.stability_score ?? rm.long_term_stability ?? rm.stability
  if (stabilityRaw != null) {
    const val = normalizeScore(Number(stabilityRaw), 5)
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

    // ── 行星星座：优先取相位数据自带字段，轴点则从 angles 补充 ──
    const rawP1Sign = a.planet1_sign || a.sign1 || a.p1_sign || angleSignMap.value[p1Raw] || ''
    const rawP2Sign = a.planet2_sign || a.sign2 || a.p2_sign || angleSignMap.value[p2Raw] || ''
    const p1Sign = zodiacZhS(rawP1Sign)
    const p2Sign = zodiacZhS(rawP2Sign)

    return {
      p1Symbol: p1Info?.symbol || p1Raw.slice(0, 2).toUpperCase(),
      p1Name:   p1Info?.name   || p1Raw,
      p1Sign,
      p2Symbol: p2Info?.symbol || p2Raw.slice(0, 2).toUpperCase(),
      p2Name:   p2Info?.name   || p2Raw,
      p2Sign,
      aspectSymbol: aspectInfo?.symbol || aspectType.slice(0, 2),
      aspectLabel:  aspectInfo?.label  || aspectType,
      harmony:      aspectInfo?.harmony || 'neutral',
      orb: orbStr,
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

  const emotionalNorm = emotional != null ? normalizeScore(Number(emotional), 15) : 50
  const attractionNorm = attraction != null ? normalizeScore(Number(attraction), 5) : 50
  const stabilityNorm = stability != null ? normalizeScore(Number(stability), 5) : 50
  const conflictNorm = conflict != null ? normalizeScore(Number(conflict), 5) : 0

  // 加权综合：情绪 40% + 吸引力 30% + 稳定 20% - 冲突影响 10%
  const composite = Math.round(
    emotionalNorm * 0.4 + attractionNorm * 0.3 + stabilityNorm * 0.2 - conflictNorm * 0.1
  )
  return Math.min(100, Math.max(0, composite))
})

// ── 内置城市数据 ────────────────────────────────────────
const MAJOR_CITIES = [
  { name: '北京', lat: 39.9042, lng: 116.4074, address: '北京市' },
  { name: '上海', lat: 31.2304, lng: 121.4737, address: '上海市' },
  { name: '广州', lat: 23.1291, lng: 113.2644, address: '广东省广州市' },
  { name: '深圳', lat: 22.5431, lng: 114.0579, address: '广东省深圳市' },
  { name: '杭州', lat: 30.2741, lng: 120.1551, address: '浙江省杭州市' },
  { name: '成都', lat: 30.5728, lng: 104.0668, address: '四川省成都市' },
  { name: '重庆', lat: 29.5630, lng: 106.5516, address: '重庆市' },
  { name: '武汉', lat: 30.5928, lng: 114.3055, address: '湖北省武汉市' },
  { name: '西安', lat: 34.3416, lng: 108.9398, address: '陕西省西安市' },
  { name: '南京', lat: 32.0603, lng: 118.7969, address: '江苏省南京市' },
  { name: '天津', lat: 39.3434, lng: 117.3616, address: '天津市' },
  { name: '苏州', lat: 31.2989, lng: 120.5853, address: '江苏省苏州市' },
  { name: '郑州', lat: 34.7473, lng: 113.6249, address: '河南省郑州市' },
  { name: '长沙', lat: 28.2278, lng: 112.9388, address: '湖南省长沙市' },
  { name: '沈阳', lat: 41.8057, lng: 123.4315, address: '辽宁省沈阳市' },
  { name: '青岛', lat: 36.0671, lng: 120.3826, address: '山东省青岛市' },
  { name: '济南', lat: 36.6512, lng: 117.1201, address: '山东省济南市' },
  { name: '大连', lat: 38.9140, lng: 121.6147, address: '辽宁省大连市' },
  { name: '厦门', lat: 24.4798, lng: 118.0894, address: '福建省厦门市' },
  { name: '福州', lat: 26.0745, lng: 119.2965, address: '福建省福州市' },
  { name: '宁波', lat: 29.8683, lng: 121.5440, address: '浙江省宁波市' },
  { name: '无锡', lat: 31.4912, lng: 120.3119, address: '江苏省无锡市' },
  { name: '合肥', lat: 31.8206, lng: 117.2272, address: '安徽省合肥市' },
  { name: '昆明', lat: 25.0453, lng: 102.7097, address: '云南省昆明市' },
  { name: '哈尔滨', lat: 45.8038, lng: 126.5349, address: '黑龙江省哈尔滨市' },
  { name: '长春', lat: 43.8171, lng: 125.3235, address: '吉林省长春市' },
  { name: '南昌', lat: 28.6820, lng: 115.8582, address: '江西省南昌市' },
  { name: '贵阳', lat: 26.6470, lng: 106.6302, address: '贵州省贵阳市' },
  { name: '南宁', lat: 22.8170, lng: 108.3665, address: '广西壮族自治区南宁市' },
  { name: '乌鲁木齐', lat: 43.8256, lng: 87.6168, address: '新疆维吾尔自治区乌鲁木齐市' },
  { name: '拉萨', lat: 29.6500, lng: 91.1000, address: '西藏自治区拉萨市' },
  { name: '兰州', lat: 36.0611, lng: 103.8343, address: '甘肃省兰州市' },
  { name: '太原', lat: 37.8706, lng: 112.5489, address: '山西省太原市' },
  { name: '石家庄', lat: 38.0428, lng: 114.5149, address: '河北省石家庄市' },
  { name: '海口', lat: 20.0440, lng: 110.1991, address: '海南省海口市' },
]

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

onMounted(() => {
  prefillPartnerFromStore()
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
    const matched = MAJOR_CITIES.filter(c => c.name.includes(kw) || c.address?.includes(kw)).slice(0, 6)
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
  // 前置检查：用户是否已设置出生信息
  if (!hasBirthInfo()) {
    uni.showModal({
      title: '需要设置出生信息',
      content: '计算和盘需要先设置您的出生信息，去本命盘页面设置吗？',
      confirmText: '去设置',
      cancelText: '取消',
      success: (res) => {
        if (res.confirm) goToNatalPage()
      }
    })
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
  } catch (e: any) {
    const errCode = e?.data?.code
    if (errCode === 7001) {
      // 出生信息未设置，引导用户设置
      clearInterval(timer)
      step.value = 'form'
      uni.showModal({
        title: '需要设置出生信息',
        content: '请先在本命盘页面设置您的出生信息',
        confirmText: '去设置',
        cancelText: '取消',
        success: (res) => { if (res.confirm) goToNatalPage() }
      })
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
</style>

