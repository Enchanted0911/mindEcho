import {del, get, post} from '../utils/request'

// ─────────────────────── 请求类型 ───────────────────────

export interface BirthInfo {
  year: number
  month: number
  day: number
  hour: number
  minute: number
  city: string
  latitude?: number
  longitude?: number
  timezone?: string
}

/** 流运计算请求（出生信息由后端从 user_astrology 表读取，前端只传日期） */
export interface TransitRequest {
  targetDate?: string
  windowDays?: number
}

/**
 * 本命盘解读请求
 * chart 由后端从 user_astrology 表读取，前端只传 focus 和 tone
 */
export interface NatalInterpretRequest {
  focus?: string
  tone?: string
  extraContext?: string
}

/**
 * 和盘解读请求
 * chart 由后端从 user_astrology 表读取，前端只传 relationshipType 和 focus
 * 前置条件：已计算过和盘
 */
export interface SynastryInterpretRequest {
  relationshipType?: 'romantic' | 'family' | 'friendship' | 'colleague'
  focus?: string
  tone?: string
}

/**
 * 流运解读请求
 * chart 由后端从 user_astrology 表读取，前端只传 windowDays 和 focus
 * 前置条件：已计算过流运
 */
export interface TransitInterpretRequest {
  windowDays?: number
  focus?: string
  tone?: string
}

// ─────────────────────── 响应类型 ───────────────────────

/** 单个行星数据（Python Python flatdict 键名可能不同，统一用 any 兼容） */
export interface PlanetData {
  planet?: string
  name?: string
  symbol?: string
  degree?: number          // 黄道经度 0-360
  sign?: string            // 英文星座名
  house?: number           // 宫位
  retrograde?: boolean     // 是否逆行
  [key: string]: any
}

/** 单个宫位数据 */
export interface HouseData {
  house?: number
  cusp?: number
  sign?: string
  [key: string]: any
}

/** 单个相位数据 */
export interface AspectData {
  // Python 返回字段名可能不统一，以下列出常见字段
  planet1?: string; planet_a?: string; body1?: string; p1?: string
  planet2?: string; planet_b?: string; body2?: string; p2?: string
  aspect?: string; aspect_type?: string; type?: string; aspect_name?: string
  orb?: number; orb_value?: number; exact_orb?: number
  description?: string; interpretation?: string; impact?: string
  planet1_sign?: string; planet2_sign?: string
  sign1?: string; sign2?: string
  [key: string]: any
}

/** 本命盘 chart 对象（Python 返回，结构灵活） */
export interface NatalChartData {
  planets?: Record<string, PlanetData> | PlanetData[]
  houses?: Record<string, HouseData> | HouseData[]
  aspects?: AspectData[]
  ascendant?: number | PlanetData
  midheaven?: number | PlanetData
  [key: string]: any
}

/** 本命盘 summary 中单个天体/角点数据 */
export interface NatalSummaryBody {
  sign?: string
  degree?: number
  absolute_degree?: number
  house?: number
  retrograde?: boolean
  speed?: number
  [key: string]: any
}

/**
 * 本命盘 summary（Python 返回，包含在 natal_chart_data 中）
 *
 * 实际数据结构示例：
 * ```json
 * {
 *   "sun":       { "sign": "Libra",       "degree": 20.57, "house": 7, ... },
 *   "moon":      { "sign": "Sagittarius", "degree": 15.28, "house": 9, ... },
 *   "ascendant": { "sign": "Aries",       "degree": 1.85,  ... },
 *   "metadata":  { "timezone": "Asia/Shanghai", ... }
 * }
 * ```
 */
export interface NatalSummary {
  sun?: NatalSummaryBody
  moon?: NatalSummaryBody
  ascendant?: NatalSummaryBody
  metadata?: {
    julian_day?: number
    timezone?: string
    lat?: number
    lng?: number
    zodiac?: string
    house_system?: string
    node_type?: string
    [key: string]: any
  }
  [key: string]: any
}

export interface NatalChartResponse {
  chart: NatalChartData | null
  summary: NatalSummary | null
  savedToProfile: boolean
}

/** 和盘 relationshipModel（Python 返回） */
export interface RelationshipModel {
  // ── Python rule_based_v1 规范化字段（实际返回字段名）──
  attraction_score?: number      // 吸引力分（原始值，需规范化）
  emotional_score?: number       // 情绪匹配分（原始值）
  conflict_score?: number        // 冲突指数分（原始值）
  stability_score?: number       // 长期稳定分（原始值）
  normalization_basis?: string   // 规范化依据（如 rule_based_v1）
  // ── 兼容字段（旧版/其他版本可能返回）──
  compatibility_score?: number
  overall_score?: number
  total_score?: number
  attraction?: number
  magnetic_attraction?: number
  attraction_desc?: string
  emotional_compatibility?: number
  emotional_match?: number
  emotion_score?: number
  emotion_desc?: string
  conflict_index?: number
  tension?: number
  conflict_desc?: string
  long_term_stability?: number
  stability?: number
  stability_desc?: string
  summary?: string
  [key: string]: any
}

/** 和盘相位数据（继承自 AspectData，额外增加双人字段） */
export interface SynastryAspectData extends AspectData {
  person1_planet?: string; chart1_planet?: string
  person2_planet?: string; chart2_planet?: string
  natal_planet?: string
}

export interface SynastryResponse {
  relationshipModel: RelationshipModel | null
  aspects: SynastryAspectData[]
  themes: Array<string | { title?: string; icon?: string; desc?: string; type?: string }>
  chart: any | null
}

/** 流运事件数据 */
export interface TransitEventData {
  transit_planet?: string; transiting_planet?: string; planet?: string
  t_planet?: string; tp?: string; body?: string
  natal_planet?: string; natal_body?: string; native_planet?: string
  n_planet?: string; np?: string
  aspect_type?: string; aspect?: string; type?: string; aspect_name?: string
  orb?: number; orb_value?: number; strength?: number; exact_orb?: number
  intensity?: string
  transit_sign?: string; t_sign?: string; sign?: string
  date_range?: string; date?: string; period?: string
  duration?: string; period_length?: string
  description?: string; interpretation?: string; impact?: string; meaning?: string
  [key: string]: any
}

/** 流运 summary（Python 返回） */
export interface TransitSummary {
  overall_energy?: number; overall_score?: number
  /** energy_level 可以是数值（0-100）或字符串（"high" / "medium" / "low" 等） */
  energy_level?: number | string
  emotion_energy?: number; emotional_energy?: number
  action_energy?: number; action_score?: number
  social_energy?: number; social_score?: number
  /** emotional_state：当前情绪状态描述（"positive" / "negative" / "neutral" 等） */
  emotional_state?: string
  /** life_focus：当前生活焦点描述（"inner world" / "relationships" / "career" 等） */
  life_focus?: string
  highlights?: any[]; key_planets?: any[]; featured_planets?: any[]
  key_themes?: any[]; themes?: any[]; focus_areas?: any[]
  description?: string; overview?: string; summary_text?: string
  [key: string]: any
}

export interface TransitResponse {
  events: TransitEventData[]
  summary: TransitSummary | null
}

export interface InterpretResponse {
  interpretation: string
  focus: string
  interpretType: string
  memoryFused: boolean
  ragFused: boolean
}

/**
 * 用户星盘信息汇总（对应后端 UserAstrologyInfoDTO）
 * 登录后通过 GET /astrology/info 一次性获取，缓存到 store
 */
export interface UserAstrologyInfo {
  /** 出生城市名称（null 表示未设置） */
  birthCity?: string | null
  /** 出生地纬度 */
  birthLat?: number | null
  /** 出生地经度 */
  birthLng?: number | null
  /** 出生时间 yyyy-MM-dd HH:mm（null 表示未设置） */
  birthTime?: string | null
  /** 是否存在本命盘缓存 */
  hasNatalCache: boolean
  /** 是否存在和盘缓存 */
  hasSynastryCache: boolean
  /** 是否存在流运缓存 */
  hasTransitCache: boolean
  /** 最近一次和盘对方昵称 */
  synastryPartnerName?: string | null
  /** 最近一次和盘对方出生城市 */
  synastryPartnerCity?: string | null
  /** 最近一次和盘对方出生地纬度 */
  synastryPartnerLat?: number | null
  /** 最近一次和盘对方出生地经度 */
  synastryPartnerLng?: number | null
  /** 最近一次和盘对方出生时间 yyyy-MM-dd HH:mm */
  synastryPartnerTime?: string | null
  /** 最近一次流运查询的目标日期 yyyy-MM-dd */
  transitTargetDate?: string | null
}

// ─────────────────────── API 方法 ───────────────────────

/**
 * 一次获取完整用户星盘信息（登录后/进入星盘页时调用）
 * 返回出生信息、缓存标志位、对方信息、流运日期等
 */
export function getUserAstrologyInfo(): Promise<UserAstrologyInfo> {
  return get<UserAstrologyInfo>('/astrology/info')
}

/**
 * 计算本命盘
 * 后端从 user_astrology 表读取出生信息，前端无需传任何参数
 * 若未设置出生信息，后端返回 7001 错误
 */
export function getNatalChart(): Promise<NatalChartResponse> {
  return post<NatalChartResponse>('/astrology/natal', {})
}

/**
 * 单盘 AI 解读
 * chart 由后端从 DB 读取，前端只传 focus 和 tone
 * 前置条件：已计算过本命盘（若未计算，后端返回 7005 错误）
 */
export function interpretNatal(data?: NatalInterpretRequest): Promise<InterpretResponse> {
  return post<InterpretResponse>('/astrology/natal/interpret', data ?? {})
}

/** 检查用户是否有缓存本命盘（Redis 或 DB） */
export function checkNatalChart(): Promise<boolean> {
  return get<boolean>('/astrology/natal/check')
}

/** 强制刷新本命盘（清除缓存后重新计算，后端接口为 DELETE） */
export function refreshNatalChart(): Promise<NatalChartResponse> {
  return del<NatalChartResponse>('/astrology/natal/cache')
}

/**
 * 计算和盘
 * 无需传任何参数，后端从 user_astrology 表读取自己和对方的出生信息
 * 若未设置出生信息，返回 7001；若未设置对方信息，返回 7002
 */
export function getSynastryChart(): Promise<SynastryResponse> {
  return post<SynastryResponse>('/astrology/synastry', {})
}

/**
 * 和盘 AI 解读
 * chart 由后端从 DB 读取，前端只传 relationshipType 和 focus
 * 前置条件：已计算过和盘（若未计算，后端返回 7003 错误）
 */
export function interpretSynastry(data?: SynastryInterpretRequest): Promise<InterpretResponse> {
  return post<InterpretResponse>('/astrology/synastry/interpret', data ?? {})
}

/**
 * 计算流运
 * 出生信息由后端从 user_astrology 表读取，前端只传日期参数（可选）
 */
export function getTransitChart(data?: TransitRequest): Promise<TransitResponse> {
  return post<TransitResponse>('/astrology/transit', data ?? {})
}

/**
 * 流运 AI 解读
 * chart 由后端从 DB 读取，前端只传 windowDays 和 focus
 * 前置条件：已计算过流运（若未计算，后端返回 7004 错误）
 */
export function interpretTransit(data?: TransitInterpretRequest): Promise<InterpretResponse> {
  return post<InterpretResponse>('/astrology/transit/interpret', data ?? {})
}

