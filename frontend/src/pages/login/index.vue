<script setup lang="ts">
import {ref} from 'vue'
import {wxLogin} from '../../api/auth'
import {getUserAstrologyInfo} from '../../api/astrology'
import {useUserStore} from '../../store/user'

const userStore = useUserStore()
const isLoading = ref(false)

const STARS = Array.from({ length: 60 }, () => ({
  left: Math.random() * 100 + '%',
  top: Math.random() * 100 + '%',
  animationDelay: Math.random() * 5 + 's',
  animationDuration: (Math.random() * 4 + 3) + 's',
  size: (Math.random() * 2 + 0.5) + 'px',
  opacity: Math.random() * 0.6 + 0.2
}))

async function handleLogin() {
  if (isLoading.value) return
  isLoading.value = true

  try {
    const loginResult = await new Promise<{ code: string }>((resolve, reject) => {
      uni.login({
        provider: 'weixin',
        success: (res) => resolve({ code: res.code }),
        fail: (err) => reject(err)
      })
    })

    const response = await wxLogin(loginResult.code)
    userStore.setToken(response.token)
    userStore.setUserInfo(response.userInfo)
    // 异步拉取星盘信息（不阻塞跳转，在后台静默获取）
    getUserAstrologyInfo().then(info => {
      userStore.setAstrologyInfo(info)
    }).catch(err => {
      console.warn('[login] Failed to fetch astrology info:', err)
    })
    uni.switchTab({ url: '/pages/chat/index' })
  } catch (error: any) {
    console.error('Login error:', error)
    const msg = error?.errMsg || ''
    if (msg.includes('cancel') || msg.includes('deny')) {
      isLoading.value = false
      return
    }
    uni.showToast({
      title: error?.message || '登录失败，请重试',
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
    <!-- 深邃背景 -->
    <view class="bg-layer" />

    <!-- 星空背景 -->
    <view class="stars-bg">
      <view
        v-for="(star, i) in STARS"
        :key="i"
        class="star"
        :style="{
          left: star.left,
          top: star.top,
          width: star.size,
          height: star.size,
          animationDelay: star.animationDelay,
          animationDuration: star.animationDuration,
          opacity: star.opacity
        }"
      />
    </view>

    <!-- 光晕装饰 -->
    <view class="glow-top" />
    <view class="glow-bottom" />

    <!-- 主内容 -->
    <view class="content">
      <!-- Logo 区域 -->
      <view class="logo-area">
        <view class="logo-orb">
          <view class="orb-inner">
            <text class="orb-symbol">🌙</text>
          </view>
          <view class="orb-ring ring-1" />
          <view class="orb-ring ring-2" />
        </view>
        <text class="app-name">心屿</text>
        <text class="app-name-en">MindEcho</text>
        <view class="tagline-wrap">
          <text class="tagline">你的 AI 情绪陪伴</text>
        </view>
      </view>

      <!-- 特性卡片 -->
      <view class="features">
        <view class="feature-item" v-for="(f, i) in [
          { icon: '💬', text: '倾听你的心声' },
          { icon: '🧠', text: '记住你的故事' },
          { icon: '✨', text: '陪你走过每一夜' }
        ]" :key="i">
          <view class="feature-icon-wrap">
            <text class="feature-icon">{{ f.icon }}</text>
          </view>
          <text class="feature-text">{{ f.text }}</text>
        </view>
      </view>

      <!-- 登录区域 -->
      <view class="login-area">
        <view
          class="login-btn"
          :class="{ 'loading': isLoading }"
          @click="handleLogin"
        >
          <view class="btn-bg" />
          <view class="btn-content">
            <text v-if="!isLoading" class="wechat-icon">微</text>
            <text class="btn-text">{{ isLoading ? '登录中...' : '微信一键登录' }}</text>
          </view>
        </view>
        <text class="tips">登录即代表你同意《用户协议》和《隐私政策》</text>
        <text class="safety-tip">心屿不提供医疗建议，如有心理危机请拨打专业热线</text>
      </view>
    </view>

    <!-- 底部装饰线 -->
    <view class="bottom-line" />
  </view>
</template>

<style>
.login-page {
  min-height: 100vh;
  background: #0a0a0f;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 0 48rpx;
  position: relative;
  overflow: hidden;
}

/* 背景层 */
.bg-layer {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse 80% 60% at 50% -10%, rgba(99, 69, 168, 0.35) 0%, transparent 60%),
    radial-gradient(ellipse 60% 40% at 80% 100%, rgba(49, 49, 120, 0.20) 0%, transparent 50%),
    linear-gradient(180deg, #0d0b1a 0%, #070611 50%, #0a0a16 100%);
  pointer-events: none;
}

/* 星星 */
.stars-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 1;
}

.star {
  position: absolute;
  background: #fff;
  border-radius: 50%;
  animation: twinkle 4s ease-in-out infinite alternate;
}

@keyframes twinkle {
  0% { opacity: 0.1; transform: scale(0.8); }
  100% { opacity: 0.8; transform: scale(1.2); }
}

/* 顶部光晕 */
.glow-top {
  position: absolute;
  top: -200rpx;
  left: 50%;
  transform: translateX(-50%);
  width: 600rpx;
  height: 600rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(120, 80, 220, 0.25) 0%, transparent 65%);
  pointer-events: none;
  z-index: 1;
}

.glow-bottom {
  position: absolute;
  bottom: -300rpx;
  right: -100rpx;
  width: 500rpx;
  height: 500rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(60, 100, 200, 0.15) 0%, transparent 65%);
  pointer-events: none;
  z-index: 1;
}

/* 主内容 */
.content {
  position: relative;
  z-index: 2;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 64rpx;
  padding: 80rpx 0 40rpx;
}

/* Logo 区域 */
.logo-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
}

.logo-orb {
  position: relative;
  width: 140rpx;
  height: 140rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12rpx;
}

.orb-inner {
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(150, 100, 240, 0.3), rgba(80, 50, 180, 0.5));
  border: 1.5rpx solid rgba(180, 140, 255, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 0 40rpx rgba(120, 80, 220, 0.4), inset 0 1rpx 0 rgba(255, 255, 255, 0.1);
  animation: float 4s ease-in-out infinite;
}

.orb-symbol { font-size: 44rpx; }

.orb-ring {
  position: absolute;
  border-radius: 50%;
  border: 1rpx solid rgba(150, 100, 250, 0.2);
  animation: spin-slow linear infinite;
}

.ring-1 {
  width: 120rpx;
  height: 120rpx;
  animation-duration: 8s;
  border-style: dashed;
}

.ring-2 {
  width: 140rpx;
  height: 140rpx;
  animation-duration: 14s;
  animation-direction: reverse;
  opacity: 0.5;
}

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-10rpx); }
}

@keyframes spin-slow {
  to { transform: rotate(360deg); }
}

.app-name {
  font-size: 72rpx;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: 10rpx;
  text-shadow: 0 0 40rpx rgba(160, 120, 255, 0.5);
}

.app-name-en {
  font-size: 26rpx;
  color: rgba(180, 160, 220, 0.7);
  letter-spacing: 8rpx;
  font-weight: 300;
}

.tagline-wrap {
  margin-top: 8rpx;
  background: rgba(255, 255, 255, 0.05);
  border: 1rpx solid rgba(255, 255, 255, 0.1);
  border-radius: 30rpx;
  padding: 10rpx 28rpx;
}

.tagline {
  font-size: 24rpx;
  color: rgba(200, 180, 255, 0.8);
  letter-spacing: 2rpx;
}

/* 特性列表 */
.features {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 24rpx;
  background: rgba(255, 255, 255, 0.04);
  border: 1rpx solid rgba(255, 255, 255, 0.08);
  border-radius: 20rpx;
  padding: 24rpx 28rpx;
  backdrop-filter: blur(10rpx);
}

.feature-icon-wrap {
  width: 52rpx;
  height: 52rpx;
  border-radius: 14rpx;
  background: rgba(130, 90, 220, 0.2);
  border: 1rpx solid rgba(150, 100, 250, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.feature-icon { font-size: 28rpx; }

.feature-text {
  font-size: 28rpx;
  color: rgba(220, 210, 240, 0.85);
  font-weight: 400;
  letter-spacing: 1rpx;
}

/* 登录区域 */
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
  border-radius: 20rpx;
  position: relative;
  overflow: hidden;
}

.btn-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #7c4dff 0%, #5c35cc 50%, #4527a0 100%);
  border-radius: 20rpx;
  box-shadow: 0 8rpx 32rpx rgba(100, 60, 220, 0.45), inset 0 1rpx 0 rgba(255, 255, 255, 0.15);
}

.login-btn::before {
  content: '';
  position: absolute;
  inset: 0;
  border: 1rpx solid rgba(180, 140, 255, 0.4);
  border-radius: 20rpx;
  z-index: 1;
}

.btn-content {
  position: relative;
  z-index: 2;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14rpx;
}

.wechat-icon {
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
  color: white;
  font-weight: bold;
  line-height: 44rpx;
  text-align: center;
}

.btn-text {
  font-size: 32rpx;
  font-weight: 600;
  color: #ffffff;
  letter-spacing: 2rpx;
}

.login-btn.loading .btn-bg {
  opacity: 0.6;
}

.tips {
  font-size: 21rpx;
  color: rgba(180, 170, 200, 0.45);
  text-align: center;
  line-height: 1.6;
}

.safety-tip {
  font-size: 20rpx;
  color: rgba(180, 170, 200, 0.35);
  text-align: center;
  line-height: 1.6;
}

/* 底部装饰 */
.bottom-line {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 1rpx;
  background: linear-gradient(90deg, transparent, rgba(120, 80, 220, 0.3), transparent);
  z-index: 2;
}
</style>

