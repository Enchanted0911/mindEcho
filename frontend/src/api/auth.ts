import {post} from '../utils/request'

export interface WxLoginResponse {
  token: string
  userInfo: {
    id: number
    nickname: string
    avatar: string | null
    isVip: boolean
    vipExpireTime: string | null
    aiPersonality: string
  }
}

/**
 * 微信小程序登录
 */
export function wxLogin(code: string): Promise<WxLoginResponse> {
  return post<WxLoginResponse>('/auth/wx-login', { code })
}

