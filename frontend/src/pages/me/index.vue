<script setup lang="ts">
import {useUserStore} from '../../store/user'
import {usePersonalityStore} from '../../store/personality'
import {getPersonalityInfo} from '../../utils/emotion'
import {computed, onMounted} from 'vue'

const userStore = useUserStore()
const personalityStore = usePersonalityStore()

onMounted(async () => {
  await personalityStore.ensureLoaded()
})

/**
 * 优先使用接口数据（name 字段），接口未加载时降级使用 PERSONALITY_MAP（label 字段）
 */
const personality = computed(() => {
  const found = personalityStore.findByCode(userStore.currentPersonality)
  if (found) {
    return { label: found.name, desc: found.description, emoji: found.emoji }
  }
  return getPersonalityInfo(userStore.currentPersonality)
})

function goToVip() {
  uni.navigateTo({ url: '/pages/vip/index' })
}

function handleLogout() {
  uni.showModal({
    title: '退出登录',
    content: '确认退出登录吗？',
    success: (res) => {
      if (res.confirm) {
        userStore.logout()
      }
    }
  })
}
</script>

<template>
  <view class="me-page">
    <view class="me-header">
      <text class="header-title">我的</text>
    </view>

    <scroll-view class="me-scroll" scroll-y>
      <!-- 用户信息卡片 -->
      <view class="user-card">
        <view class="user-avatar">
          <text class="avatar-placeholder">{{ userStore.userInfo?.nickname?.[0] || '🌙' }}</text>
        </view>
        <view class="user-info">
          <text class="user-name">{{ userStore.userInfo?.nickname || '用户' }}</text>
          <view class="vip-tag" v-if="userStore.isVip">
            <text>👑 会员</text>
          </view>
          <view class="free-tag" v-else @click="goToVip">
            <text>升级会员 →</text>
          </view>
        </view>
      </view>

      <!-- 当前人格 -->
      <view class="section">
        <text class="section-label">当前 AI 人格</text>
        <view class="current-personality" @click="uni.switchTab({ url: '/pages/chat/index' })">
          <text class="p-emoji">{{ personality.emoji }}</text>
          <view class="p-info">
            <text class="p-name">{{ personality.label }}</text>
            <text class="p-desc">{{ personality.desc }}</text>
          </view>
          <text class="p-chevron">›</text>
        </view>
      </view>

      <!-- 设置 -->
      <view class="section">
        <text class="section-label">设置</text>
        <view class="menu-list">
          <view class="menu-item" @click="goToVip">
            <text class="menu-icon">👑</text>
            <text class="menu-text">开通会员</text>
            <text class="menu-arrow">›</text>
          </view>
          <view class="menu-item">
            <text class="menu-icon">🔔</text>
            <text class="menu-text">消息通知</text>
            <text class="menu-arrow">›</text>
          </view>
          <view class="menu-item">
            <text class="menu-icon">🛡️</text>
            <text class="menu-text">隐私设置</text>
            <text class="menu-arrow">›</text>
          </view>
          <view class="menu-item">
            <text class="menu-icon">❓</text>
            <text class="menu-text">帮助与反馈</text>
            <text class="menu-arrow">›</text>
          </view>
          <view class="menu-item">
            <text class="menu-icon">📄</text>
            <text class="menu-text">用户协议</text>
            <text class="menu-arrow">›</text>
          </view>
        </view>
      </view>

      <!-- 危机热线 -->
      <view class="crisis-card">
        <text class="crisis-icon">💙</text>
        <view class="crisis-info">
          <text class="crisis-title">心理援助热线</text>
          <text class="crisis-line">北京：010-82951332</text>
          <text class="crisis-line">全国：400-161-9995</text>
        </view>
      </view>

      <!-- 退出登录 -->
      <view class="logout-btn" @click="handleLogout">
        <text>退出登录</text>
      </view>

      <view class="version-info">
        <text>MindEcho 心屿 v1.0.0</text>
      </view>

      <view style="height: 80rpx" />
    </scroll-view>
  </view>
</template>

<style>
.me-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #0f0f1a;
}

.me-header {
  padding: 100rpx 32rpx 24rpx;
  background: rgba(15,15,26,0.95);
}

.header-title { font-size: 40rpx; color: #e8d5ff; font-weight: bold; }

.me-scroll { flex: 1; padding: 24rpx; }

.user-card {
  background: rgba(255,255,255,0.04);
  border: 1rpx solid rgba(184,158,232,0.1);
  border-radius: 24rpx;
  padding: 32rpx;
  display: flex;
  align-items: center;
  gap: 24rpx;
  margin-bottom: 32rpx;
}

.user-avatar {
  width: 100rpx;
  height: 100rpx;
  border-radius: 50rpx;
  background: linear-gradient(135deg, #b89ee8, #8b6fd1);
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-placeholder { font-size: 44rpx; color: white; }

.user-name { font-size: 36rpx; color: #e8d5ff; font-weight: bold; display: block; margin-bottom: 12rpx; }

.vip-tag {
  display: inline-flex;
  background: rgba(240,208,96,0.15);
  border: 1rpx solid rgba(240,208,96,0.3);
  padding: 6rpx 20rpx;
  border-radius: 30rpx;
}

.vip-tag text { font-size: 24rpx; color: #f0d060; }

.free-tag {
  display: inline-flex;
  background: rgba(184,158,232,0.12);
  border: 1rpx solid rgba(184,158,232,0.2);
  padding: 6rpx 20rpx;
  border-radius: 30rpx;
}

.free-tag text { font-size: 24rpx; color: #b89ee8; }

.section { margin-bottom: 24rpx; }
.section-label { font-size: 26rpx; color: #5a5070; display: block; margin-bottom: 16rpx; margin-left: 4rpx; }

.current-personality {
  background: rgba(255,255,255,0.04);
  border: 1rpx solid rgba(184,158,232,0.1);
  border-radius: 20rpx;
  padding: 24rpx 28rpx;
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.p-emoji { font-size: 48rpx; }
.p-info { flex: 1; }
.p-name { font-size: 30rpx; color: #e8d5ff; font-weight: 600; display: block; }
.p-desc { font-size: 24rpx; color: #7a6b9a; }
.p-chevron { font-size: 36rpx; color: #5a5070; }

.menu-list {
  background: rgba(255,255,255,0.04);
  border: 1rpx solid rgba(184,158,232,0.1);
  border-radius: 20rpx;
  overflow: hidden;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 28rpx;
  border-bottom: 1rpx solid rgba(255,255,255,0.04);
}

.menu-item:last-child { border-bottom: none; }

.menu-icon { font-size: 36rpx; }
.menu-text { flex: 1; font-size: 30rpx; color: #c4a8f0; }
.menu-arrow { font-size: 32rpx; color: #5a5070; }

.crisis-card {
  background: rgba(100,180,255,0.05);
  border: 1rpx solid rgba(100,180,255,0.15);
  border-radius: 20rpx;
  padding: 28rpx;
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-bottom: 24rpx;
}

.crisis-icon { font-size: 44rpx; }
.crisis-title { font-size: 28rpx; color: #80c0ff; font-weight: 600; display: block; margin-bottom: 8rpx; }
.crisis-line { font-size: 24rpx; color: #5a8aaa; display: block; }

.logout-btn {
  text-align: center;
  padding: 28rpx;
  color: #5a5070;
  font-size: 28rpx;
  background: rgba(255,255,255,0.02);
  border-radius: 20rpx;
  margin-bottom: 16rpx;
}

.version-info {
  text-align: center;
  padding: 16rpx;
}

.version-info text { font-size: 22rpx; color: #3a3050; }
</style>

