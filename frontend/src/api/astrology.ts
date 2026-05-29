import {get, post, put} from '../utils/request'

// ─────────────────────── 请求类型 ───────────────────────

export interface UpdateBirthInfoRequest {
  birthCity: string
  birthLat: number | null
  birthLng: number | null
  birthTime: string  // yyyy-MM-dd HH:mm
}

export interface BirthInfo {
  year: number
  month: number
  day: number
  hour: number
  minute: number
  city: string
  latitude?: number
  longitude?: number
  timezone?: number
}

export interface NatalRequest {
  birthInfo: BirthInfo
  saveToProfile?: boolean
}

export interface SynastryRequest {
  selfBirthInfo: BirthInfo
  partnerBirthInfo: BirthInfo
  partnerName: string
  relationshipType?: 'romantic' | 'family' | 'friendship' | 'colleague'
}

export interface TransitRequest {
  birthInfo: BirthInfo
  targetDate?: string
  windowDays?: number
}

export interface InterpretRequest {
  chart: any
  focus?: string
  tone?: string
  interpretType?: string
  extraContext?: string
}

// ─────────────────────── 响应类型 ───────────────────────

export interface NatalChartResponse {
  chart: any
  summary: any
  savedToProfile: boolean
}

export interface SynastryResponse {
  relationshipModel: any
  aspects: any[]
  themes: string[]
  chart: any
}

export interface TransitResponse {
  events: any[]
  summary: any
  chart: any
}

export interface InterpretResponse {
  interpretation: string
  focus: string
  interpretType: string
  memoryFused: boolean
  ragFused: boolean
}

// ─────────────────────── API 方法 ───────────────────────

/** 计算本命盘 */
export function getNatalChart(data: NatalRequest): Promise<NatalChartResponse> {
  return post<NatalChartResponse>('/astrology/natal', data)
}

/** 单盘 AI 解读 */
export function interpretNatal(data: InterpretRequest): Promise<InterpretResponse> {
  return post<InterpretResponse>('/astrology/natal/interpret', data)
}

/** 检查用户是否有缓存本命盘 */
export function checkNatalChart(): Promise<boolean> {
  return get<boolean>('/astrology/natal/check')
}

/** 计算和盘 */
export function getSynastryChart(data: SynastryRequest): Promise<SynastryResponse> {
  return post<SynastryResponse>('/astrology/synastry', data)
}

/** 和盘 AI 解读 */
export function interpretSynastry(data: InterpretRequest): Promise<InterpretResponse> {
  return post<InterpretResponse>('/astrology/synastry/interpret', data)
}

/** 计算流运 */
export function getTransitChart(data: TransitRequest): Promise<TransitResponse> {
  return post<TransitResponse>('/astrology/transit', data)
}

/** 流运 AI 解读 */
export function interpretTransit(data: InterpretRequest): Promise<InterpretResponse> {
  return post<InterpretResponse>('/astrology/transit/interpret', data)
}

/** 保存/更新用户出生信息到用户档案 */
export function saveBirthInfoToProfile(data: UpdateBirthInfoRequest): Promise<any> {
  return put<any>('/auth/profile/birth', data)
}

