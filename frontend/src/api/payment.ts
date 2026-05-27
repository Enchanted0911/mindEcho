import {get, post} from '../utils/request'

export interface VipOrderDTO {
  id: number
  orderNo: string
  amount: number
  status: string
  vipType: string
  expireTime?: string
  createdTime: string
  wxPayParams?: {
    timeStamp: string
    nonceStr: string
    pkg: string
    signType: string
    paySign: string
  }
}

/**
 * 创建会员订单
 */
export function createOrder(vipType: string): Promise<VipOrderDTO> {
  return post<VipOrderDTO>('/payment/order', { vipType })
}

/**
 * 查询订单
 */
export function getOrder(orderNo: string): Promise<VipOrderDTO> {
  return get<VipOrderDTO>(`/payment/order/${orderNo}`)
}

/**
 * 调起微信支付
 */
export function wxPay(params: VipOrderDTO['wxPayParams']): Promise<void> {
  return new Promise((resolve, reject) => {
    if (!params) {
      reject(new Error('支付参数错误'))
      return
    }
    uni.requestPayment({
      provider: 'wxpay',
      timeStamp: params.timeStamp,
      nonceStr: params.nonceStr,
      package: params.pkg,
      signType: params.signType as 'MD5' | 'HMAC-SHA256' | 'RSA',
      paySign: params.paySign,
      success: () => resolve(),
      fail: (err) => reject(err)
    })
  })
}

