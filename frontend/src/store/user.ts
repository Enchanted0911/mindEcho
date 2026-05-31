import {defineStore} from 'pinia'
import {computed, ref} from 'vue'

export interface UserInfo {
  id: string   // 后端 Long 序列化为字符串，防止 JS 精度丢失
  nickname: string
  avatar: string | null
  isVip: boolean
  vipExpireTime: string | null
  aiPersonality: string
  /** 出生城市名称 */
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

export const useUserStore = defineStore('user', () => {
  const token = ref<string>('')
  const userInfo = ref<UserInfo | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const isVip = computed(() => userInfo.value?.isVip ?? false)
  const currentPersonality = computed(() => userInfo.value?.aiPersonality ?? 'gentle_female')

  function setToken(newToken: string) {
    token.value = newToken
    uni.setStorageSync('token', newToken)
  }

  function setUserInfo(info: UserInfo) {
    userInfo.value = info
    uni.setStorageSync('userInfo', JSON.stringify(info))
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    uni.removeStorageSync('token')
    uni.removeStorageSync('userInfo')
    uni.reLaunch({ url: '/pages/login/index' })
  }

  function updatePersonality(aiPersonality: string) {
    if (userInfo.value) {
      userInfo.value.aiPersonality = aiPersonality
      uni.setStorageSync('userInfo', JSON.stringify(userInfo.value))
    }
  }

  function updateBirthInfo(birthCity: string, birthLat: number | null, birthLng: number | null, birthTime: string) {
    if (userInfo.value) {
      userInfo.value.birthCity = birthCity
      userInfo.value.birthLat = birthLat
      userInfo.value.birthLng = birthLng
      userInfo.value.birthTime = birthTime
      uni.setStorageSync('userInfo', JSON.stringify(userInfo.value))
    }
  }

  /**
   * 更新和盘对方信息（前端回填用），同步到本地缓存
   */
  function updateSynastryPartner(
    partnerName: string | null,
    partnerCity: string,
    partnerLat: number | null,
    partnerLng: number | null,
    partnerTime: string
  ) {
    if (userInfo.value) {
      userInfo.value.synastryPartnerName = partnerName
      userInfo.value.synastryPartnerCity = partnerCity
      userInfo.value.synastryPartnerLat = partnerLat
      userInfo.value.synastryPartnerLng = partnerLng
      userInfo.value.synastryPartnerTime = partnerTime
      uni.setStorageSync('userInfo', JSON.stringify(userInfo.value))
    }
  }

  /**
   * 更新流运目标日期（前端回填用），同步到本地缓存
   */
  function updateTransitDate(targetDate: string) {
    if (userInfo.value) {
      userInfo.value.transitTargetDate = targetDate
      uni.setStorageSync('userInfo', JSON.stringify(userInfo.value))
    }
  }

  /**
   * 从本地 Storage 恢复登录状态（用于小程序冷启动时内存 store 为空的场景）。
   *
   * <p>小程序每次冷启动后，Pinia 内存中的 token 会被重置为空字符串，
   * 但 uni.setStorageSync 持久化的数据仍然存在。调用此方法可将 storage
   * 中的 token/userInfo 重新填入 store，避免因内存状态丢失而误判为未登录。
   *
   * @returns 是否成功恢复（true = 已登录状态，false = 未登录）
   */
  function restoreFromStorage(): boolean {
    if (token.value) return true   // 内存中已有 token，无需恢复
    try {
      const savedToken = uni.getStorageSync('token')
      const savedUserInfoStr = uni.getStorageSync('userInfo')
      if (savedToken) {
        token.value = savedToken
        if (savedUserInfoStr) {
          userInfo.value = JSON.parse(savedUserInfoStr) as UserInfo
        }
        return true
      }
    } catch (e) {
      console.warn('[userStore] restoreFromStorage failed:', e)
    }
    return false
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    isVip,
    currentPersonality,
    setToken,
    setUserInfo,
    logout,
    updatePersonality,
    updateBirthInfo,
    updateSynastryPartner,
    updateTransitDate,
    restoreFromStorage
  }
})

