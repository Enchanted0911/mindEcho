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
    updateBirthInfo
  }
})

