<script setup lang="ts">
import {onLaunch, onShow} from '@dcloudio/uni-app'
import {useUserStore} from './store/user'

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

