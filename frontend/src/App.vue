<script setup lang="ts">
import {onLaunch, onShow} from '@dcloudio/uni-app'
import {useUserStore} from './store/user'
import {usePersonalityStore} from './store/personality'

onLaunch(() => {
  const userStore = useUserStore()
  // 检查登录状态
  const token = uni.getStorageSync('token')
  if (token) {
    userStore.setToken(token)
    const userInfo = uni.getStorageSync('userInfo')
    if (userInfo) {
      userStore.setUserInfo(JSON.parse(userInfo))
    }
    // 登录态有效时，预加载人格列表（异步，不阻塞启动流程）
    // ensureLoaded() 内部已做幂等处理，多次调用只请求一次
    const personalityStore = usePersonalityStore()
    personalityStore.ensureLoaded().catch(err => {
      console.warn('App.vue: personality preload failed', err)
    })
  } else {
    // 未登录跳转登录页
    uni.reLaunch({ url: '/pages/login/index' })
  }
})

onShow(() => {
  // App 显示时的操作
})
</script>

<template>
  <page-meta :page-style="'overflow:hidden'" />
</template>

