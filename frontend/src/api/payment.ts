import {get, post} from '../utils/request'

// ─────────────────────── 接口类型 ───────────────────────

/** 微信支付唤起参数（由后端统一下单后生成，直接透传给 uni.requestPayment） */
export interface WxPayParams {
  /** 时间戳（秒，字符串格式） */
  timeStamp: string
  /** 随机字符串 */
  nonceStr: string
  /** "prepay_id=xxx" */
  pkg: string
  /** 签名类型，固定 "RSA" */
  signType: 'RSA' | 'MD5' | 'HMAC-SHA256'
  /** 支付签名（RSA-SHA256） */
  paySign: string
}

/** 会员订单 DTO */
export interface VipOrderDTO {
  /** 订单数据库 ID（雪花ID，字符串防精度丢失） */
  id: string
  /** 商户订单号（ME + 时间戳 + 随机串） */
  orderNo: string
  /** 订单金额（元） */
  amount: number
  /**
   * 订单状态
   * - pending: 待支付
   * - paid: 已支付（会员已激活）
   * - cancelled: 已取消
   * - refunded: 已退款
   */
  status: 'pending' | 'paid' | 'cancelled' | 'refunded'
  /** 套餐类型 */
  vipType: 'monthly' | 'quarterly' | 'yearly'
  /** VIP 到期时间（仅 paid 状态时有值） */
  expireTime?: string
  /** 订单创建时间 */
  createdTime: string
  /**
   * 微信支付唤起参数
   * - 调用成功时：包含完整签名参数，可直接传给 uni.requestPayment
   * - 开发环境未接入时：为 null/undefined，前端走 mock 流程
   */
  wxPayParams?: WxPayParams
}

// ─────────────────────── API 方法 ───────────────────────

/**
 * 创建会员订单
 *
 * 后端会：
 * 1. 复用已有 pending 订单（同用户同套餐）或新建订单
 * 2. 调用微信统一下单，生成 prepay_id
 * 3. 用私钥签名，返回前端唤起支付所需的 wxPayParams
 *
 * @param vipType 套餐类型: monthly / quarterly / yearly
 */
export function createOrder(vipType: string): Promise<VipOrderDTO> {
  return post<VipOrderDTO>('/payment/order', {vipType})
}

/**
 * 查询订单状态
 *
 * 前端在微信支付 success 回调后调用此接口轮询，
 * 直到 status === 'paid' 才确认支付成功（避免仅依赖客户端回调判断）。
 *
 * @param orderNo 商户订单号（createOrder 返回的 orderNo）
 */
export function getOrder(orderNo: string): Promise<VipOrderDTO> {
  return get<VipOrderDTO>(`/payment/order/${orderNo}`)
}

/**
 * 调起微信小程序支付
 *
 * 封装 uni.requestPayment，将后端返回的 wxPayParams 透传给微信 SDK。
 *
 * ⚠️ 注意：
 * - success 回调仅表示用户完成了支付操作，不代表服务端确认到账
 * - 支付成功后需调用 getOrder 轮询，status === 'paid' 才是真正到账
 * - 用户取消时 errMsg 包含 "cancel"，需单独处理
 *
 * @param params 后端返回的 wxPayParams（来自 createOrder 响应）
 */
export function wxPay(params: WxPayParams | undefined): Promise<void> {
  return new Promise((resolve, reject) => {
    if (!params) {
      reject(new Error('支付参数缺失，请重新下单'))
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

