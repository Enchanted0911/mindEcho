import {get, post, put} from '../utils/request'

export interface WxLoginResponse {
  token: string
  userInfo: {
    id: string   // 后端 UUID 序列化为字符串
    nickname: string
    avatar: string | null
    isVip: boolean
    vipExpireTime: string | null
    aiPersonality: string
    /** 出生城市 */
    birthCity?: string | null
    /** 出生地纬度 */
    birthLat?: number | null
    /** 出生地经度 */
    birthLng?: number | null
    /** 出生时间 yyyy-MM-dd HH:mm */
    birthTime?: string | null

    // ── 和盘：最近一次对方出生信息（前端回填用） ────────────────────
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

    // ── 流运：最近一次查询的目标日期 ────────────────────────────────
    /** 最近一次流运查询的目标日期 yyyy-MM-dd */
    transitTargetDate?: string | null
  }
}

// ─────────────────────── 出生信息 & 占星档案请求类型 ───────────────────────

export interface UpdateBirthInfoRequest {
  birthCity: string
  birthLat: number | null
  birthLng: number | null
  birthTime: string  // yyyy-MM-dd HH:mm
}

/**
 * 保存和盘对方出生信息请求
 * 每次提交和盘计算时调用，方便前端下次回填
 */
export interface UpdateSynastryPartnerRequest {
  partnerName?: string
  partnerCity: string
  partnerLat?: number | null
  partnerLng?: number | null
  partnerTime: string  // yyyy-MM-dd HH:mm
}

/**
 * 保存流运目标日期请求
 * 每次提交流运计算时调用，方便前端下次回填
 */
export interface UpdateTransitDateRequest {
  targetDate: string  // yyyy-MM-dd
}

// ─────────────────────── API 方法 ───────────────────────

/**
 * 微信小程序登录
 */
export function wxLogin(code: string): Promise<WxLoginResponse> {
  return post<WxLoginResponse>('/auth/wx-login', { code })
}

/**
 * 获取当前登录用户的最新信息
 * 用于页面挂载时从服务端同步最新 VIP 状态，避免仅依赖本地缓存（可能已过期）
 */
export function getProfile(): Promise<WxLoginResponse['userInfo']> {
  return get<WxLoginResponse['userInfo']>('/auth/profile')
}

/** 保存/更新用户出生信息到用户档案 */
export function updateBirthInfo(data: UpdateBirthInfoRequest): Promise<WxLoginResponse['userInfo']> {
  return put<WxLoginResponse['userInfo']>('/auth/profile/birth', data)
}

/**
 * 保存和盘对方出生信息到用户档案（前端回填用）
 * 每次提交和盘计算后调用，将对方信息持久化，方便下次自动回填
 */
export function updateSynastryPartner(data: UpdateSynastryPartnerRequest): Promise<WxLoginResponse['userInfo']> {
  return put<WxLoginResponse['userInfo']>('/auth/profile/synastry-partner', data)
}

/**
 * 保存流运目标日期到用户档案（前端回填用）
 * 每次提交流运计算后调用，将目标日期持久化，方便下次自动回填
 */
export function updateTransitDate(data: UpdateTransitDateRequest): Promise<WxLoginResponse['userInfo']> {
  return put<WxLoginResponse['userInfo']>('/auth/profile/transit-date', data)
}

