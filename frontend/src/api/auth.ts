import {get, post} from '../utils/request'

export interface WxLoginResponse {
  token: string
  userInfo: {
    id: string   // 后端 Long 序列化为字符串，防止 JS 精度丢失
    nickname: string
    avatar: string | null
    isVip: boolean
    vipExpireTime: string | null
    aiPersonality: string
    birthCity?: string | null
    birthLat?: number | null
    birthLng?: number | null
    birthTime?: string | null
  }
}

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

