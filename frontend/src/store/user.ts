import {defineStore} from 'pinia'
import {computed, ref} from 'vue'

export interface UserInfo {
  id: string   // 后端 Long 序列化为字符串，防止 JS 精度丢失
  nickname: string
  avatar: string | null
  isVip: boolean
  vipExpireTime: string | null
  personality: string
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>('')
  const userInfo = ref<UserInfo | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const isVip = computed(() => userInfo.value?.isVip ?? false)
  const currentPersonality = computed(() => userInfo.value?.personality ?? 'gentle_sister')

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

  function updatePersonality(personality: string) {
    if (userInfo.value) {
      userInfo.value.personality = personality
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
    updatePersonality
  }
})

