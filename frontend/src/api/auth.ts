import {post} from '../utils/request'

export interface WxLoginResponse {
  token: string
  userInfo: {
    id: string   // 后端 Long 序列化为字符串，防止 JS 精度丢失
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

