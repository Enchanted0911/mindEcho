<script setup lang="ts">
import {ref} from 'vue'
import {wxLogin} from '../../api/auth'
import {useUserStore} from '../../store/user'

const userStore = useUserStore()
const isLoading = ref(false)

async function handleLogin() {
  if (isLoading.value) return
  isLoading.value = true

  try {
    // 1. 获取微信登录 code
    const loginResult = await new Promise<{ code: string }>((resolve, reject) => {
      uni.login({
        provider: 'weixin',
        success: (res) => resolve({ code: res.code }),
        fail: (err) => reject(err)
      })
    })

    // 2. 请求后端登录
    const response = await wxLogin(loginResult.code)

    // 3. 保存登录状态
    userStore.setToken(response.token)
    userStore.setUserInfo(response.userInfo)

    // 4. 跳转聊天页
    uni.switchTab({ url: '/pages/chat/index' })
  } catch (error) {
    uni.showToast({
      title: '登录失败，请重试',
      icon: 'none',
      duration: 2000
    })
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <view class="login-page">
    <!-- 背景星空效果 -->
    <view class="stars-bg">
      <view v-for="i in 30" :key="i" class="star" :style="{
        left: Math.random() * 100 + '%',
        top: Math.random() * 100 + '%',
        animationDelay: Math.random() * 3 + 's',
        width: (Math.random() * 2 + 1) + 'px',
        height: (Math.random() * 2 + 1) + 'px'
      }" />
    </view>

    <!-- Logo 区域 -->
    <view class="logo-area">
      <view class="logo-icon">🌙</view>
      <text class="logo-title">心屿</text>
      <text class="logo-subtitle">MindEcho</text>
      <text class="logo-desc">你的 AI 情绪陪伴</text>
    </view>

    <!-- 特性介绍 -->
    <view class="features">
      <view class="feature-item">
        <text class="feature-icon">💬</text>
        <text class="feature-text">倾听你的心声</text>
      </view>
      <view class="feature-item">
        <text class="feature-icon">🧠</text>
        <text class="feature-text">记住你的故事</text>
      </view>
      <view class="feature-item">
        <text class="feature-icon">✨</text>
        <text class="feature-text">陪你走过每一夜</text>
      </view>
    </view>

    <!-- 登录按钮 -->
    <view class="login-area">
      <button
        class="login-btn"
        :loading="isLoading"
        :disabled="isLoading"
        open-type="getPhoneNumber"
        @click="handleLogin"
      >
        <text v-if="!isLoading">微信一键登录</text>
        <text v-else>登录中...</text>
      </button>
      <text class="login-tips">登录即代表你同意《用户协议》和《隐私政策》</text>
      <text class="safety-tips">心屿不提供医疗建议，如有心理危机请拨打专业热线</text>
    </view>
  </view>
</template>

<style>
.login-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #0a0a1a 0%, #1a0a2e 50%, #0f0f1a 100%);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: space-between;
  padding: 80rpx 60rpx 120rpx;
  position: relative;
  overflow: hidden;
}

.stars-bg {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
}

.star {
  position: absolute;
  background: #fff;
  border-radius: 50%;
  opacity: 0.6;
  animation: twinkle 3s infinite alternate;
}

@keyframes twinkle {
  0% { opacity: 0.2; }
  100% { opacity: 0.8; }
}

.logo-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-top: 60rpx;
}

.logo-icon {
  font-size: 120rpx;
  margin-bottom: 20rpx;
  animation: float 4s ease-in-out infinite;
}

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-20rpx); }
}

.logo-title {
  font-size: 72rpx;
  font-weight: bold;
  color: #e8d5ff;
  letter-spacing: 8rpx;
}

.logo-subtitle {
  font-size: 32rpx;
  color: #b89ee8;
  letter-spacing: 4rpx;
  margin-top: 8rpx;
}

.logo-desc {
  font-size: 28rpx;
  color: #7a6b9a;
  margin-top: 16rpx;
}

.features {
  display: flex;
  flex-direction: column;
  gap: 32rpx;
  width: 100%;
  padding: 0 40rpx;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 24rpx;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 20rpx;
  padding: 28rpx 36rpx;
  border: 1rpx solid rgba(184, 158, 232, 0.15);
}

.feature-icon {
  font-size: 44rpx;
}

.feature-text {
  font-size: 30rpx;
  color: #c4a8f0;
  font-weight: 500;
}

.login-area {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20rpx;
}

.login-btn {
  width: 100%;
  height: 96rpx;
  background: linear-gradient(135deg, #b89ee8 0%, #8b6fd1 100%);
  border-radius: 48rpx;
  color: white;
  font-size: 34rpx;
  font-weight: bold;
  border: none;
  box-shadow: 0 8rpx 32rpx rgba(139, 111, 209, 0.4);
  letter-spacing: 2rpx;
}

.login-btn[disabled] {
  opacity: 0.6;
}

.login-tips {
  font-size: 22rpx;
  color: #5a5070;
  text-align: center;
}

.safety-tips {
  font-size: 22rpx;
  color: #4a4060;
  text-align: center;
  line-height: 1.5;
}
</style>

