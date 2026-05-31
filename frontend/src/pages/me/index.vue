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

function handleNotification() {
  uni.showToast({ title: '通知设置即将上线', icon: 'none' })
}

function handlePrivacy() {
  uni.showToast({ title: '隐私设置即将上线', icon: 'none' })
}

function handleHelp() {
  uni.showToast({ title: '如有问题请联系客服', icon: 'none' })
}

function handleAgreement() {
  uni.showToast({ title: '正在加载用户协议...', icon: 'none' })
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
    <!-- 顶部标题栏 -->
    <view class="me-header">
      <text class="header-title">我的</text>
    </view>

    <scroll-view class="me-scroll" scroll-y>
      <!-- 用户信息卡片 -->
      <view class="user-card">
        <view class="user-avatar-wrap">
          <view class="user-avatar">
            <text class="avatar-letter">{{ userStore.userInfo?.nickname?.[0] || '🌙' }}</text>
          </view>
          <view v-if="userStore.isVip" class="vip-crown">
            <text class="crown-icon">👑</text>
          </view>
        </view>
        <view class="user-info">
          <text class="user-name">{{ userStore.userInfo?.nickname || '用户' }}</text>
          <view v-if="userStore.isVip" class="vip-badge">
            <text class="vip-badge-text">会员</text>
          </view>
          <view v-else class="upgrade-btn" @click="goToVip">
            <text class="upgrade-text">升级会员 →</text>
          </view>
        </view>
        <!-- 进入 VIP 箭头 -->
        <view class="user-card-arrow" @click="goToVip">
          <text class="arrow-icon">›</text>
        </view>
      </view>

      <!-- 当前 AI 人格 -->
      <view class="section">
        <text class="section-label">当前 AI 人格</text>
        <view class="personality-card" @click="uni.switchTab({ url: '/pages/chat/index' })">
          <view class="p-avatar">
            <text class="p-emoji">{{ personality.emoji }}</text>
          </view>
          <view class="p-info">
            <text class="p-name">{{ personality.label }}</text>
            <text class="p-desc">{{ personality.desc }}</text>
          </view>
          <view class="card-arrow">
            <text class="arrow-text">›</text>
          </view>
        </view>
      </view>

      <!-- AI 占星快捷入口 -->
      <view class="section">
        <text class="section-label">AI 占星</text>
        <view class="astro-main-card" @click="uni.switchTab({ url: '/pages/astrology/index' })">
          <view class="astro-left">
            <view class="astro-icon-bg">
              <text class="astro-icon">✦</text>
            </view>
            <view class="astro-text">
              <text class="astro-title">星屿占星</text>
              <text class="astro-sub">本命盘 · 和盘 · 流运 · AI 解读</text>
            </view>
          </view>
          <text class="card-arrow-text">›</text>
        </view>
        <view class="astro-quick-row">
          <view class="quick-item" @click="uni.navigateTo({ url: '/pages/astrology/natal' })">
            <text class="quick-symbol">☉</text>
            <text class="quick-label">本命盘</text>
          </view>
          <view class="quick-item" @click="uni.navigateTo({ url: '/pages/astrology/synastry' })">
            <text class="quick-symbol">∞</text>
            <text class="quick-label">和盘</text>
          </view>
          <view class="quick-item" @click="uni.navigateTo({ url: '/pages/astrology/transit' })">
            <text class="quick-symbol">♄</text>
            <text class="quick-label">流运</text>
          </view>
        </view>
      </view>

      <!-- 设置菜单 -->
      <view class="section">
        <text class="section-label">设置</text>
        <view class="menu-group">
          <view class="menu-item" @click="goToVip">
            <view class="menu-icon-bg menu-icon-gold">
              <text class="menu-icon">👑</text>
            </view>
            <text class="menu-text">开通会员</text>
            <text class="menu-arrow">›</text>
          </view>
          <view class="menu-divider" />
          <view class="menu-item" @click="handleNotification">
            <view class="menu-icon-bg menu-icon-blue">
              <text class="menu-icon">🔔</text>
            </view>
            <text class="menu-text">消息通知</text>
            <text class="menu-arrow">›</text>
          </view>
          <view class="menu-divider" />
          <view class="menu-item" @click="handlePrivacy">
            <view class="menu-icon-bg menu-icon-green">
              <text class="menu-icon">🛡</text>
            </view>
            <text class="menu-text">隐私设置</text>
            <text class="menu-arrow">›</text>
          </view>
          <view class="menu-divider" />
          <view class="menu-item" @click="handleHelp">
            <view class="menu-icon-bg menu-icon-orange">
              <text class="menu-icon">💬</text>
            </view>
            <text class="menu-text">帮助与反馈</text>
            <text class="menu-arrow">›</text>
          </view>
          <view class="menu-divider" />
          <view class="menu-item" @click="handleAgreement">
            <view class="menu-icon-bg menu-icon-gray">
              <text class="menu-icon">📄</text>
            </view>
            <text class="menu-text">用户协议</text>
            <text class="menu-arrow">›</text>
          </view>
        </view>
      </view>

      <!-- 危机热线 -->
      <view class="crisis-card">
        <view class="crisis-left">
          <text class="crisis-icon">💙</text>
          <view class="crisis-info">
            <text class="crisis-title">心理援助热线</text>
            <text class="crisis-line">北京：010-82951332</text>
            <text class="crisis-line">全国：400-161-9995</text>
          </view>
        </view>
      </view>

      <!-- 退出登录 -->
      <view class="logout-row" @click="handleLogout">
        <text class="logout-text">退出登录</text>
      </view>

      <view class="version-row">
        <text class="version-text">MindEcho 心屿 v1.0.0</text>
      </view>

      <view style="height: 80rpx" />
    </scroll-view>
  </view>
</template>

<style>
.me-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #0d0b1a;
}

/* ── 顶部栏 ── */
.me-header {
  padding: 96rpx 32rpx 20rpx;
  background: rgba(15, 12, 28, 0.95);
  backdrop-filter: blur(20rpx);
  border-bottom: 1rpx solid rgba(255, 255, 255, 0.06);
}

.header-title {
  font-size: 40rpx;
  color: rgba(230, 225, 255, 0.95);
  font-weight: 700;
}

.me-scroll { flex: 1; padding: 24rpx; }

/* ── 用户信息卡片 ── */
.user-card {
  display: flex;
  align-items: center;
  gap: 20rpx;
  background: rgba(255, 255, 255, 0.04);
  border: 1rpx solid rgba(255, 255, 255, 0.08);
  border-radius: 24rpx;
  padding: 28rpx;
  margin-bottom: 28rpx;
}

.user-avatar-wrap {
  position: relative;
  flex-shrink: 0;
}

.user-avatar {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(150, 100, 240, 0.4), rgba(80, 50, 180, 0.6));
  border: 2rpx solid rgba(150, 100, 250, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4rpx 20rpx rgba(100, 60, 220, 0.25);
}

.avatar-letter {
  font-size: 38rpx;
  color: white;
  font-weight: 600;
}

.vip-crown {
  position: absolute;
  bottom: -6rpx;
  right: -6rpx;
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  background: #1a1530;
  display: flex;
  align-items: center;
  justify-content: center;
}

.crown-icon { font-size: 20rpx; }

.user-info { flex: 1; }

.user-name {
  font-size: 34rpx;
  color: rgba(230, 225, 255, 0.95);
  font-weight: 700;
  display: block;
  margin-bottom: 10rpx;
}

.vip-badge {
  display: inline-flex;
  background: rgba(240, 185, 30, 0.12);
  border: 1rpx solid rgba(240, 190, 60, 0.3);
  border-radius: 20rpx;
  padding: 5rpx 16rpx;
}

.vip-badge-text { font-size: 22rpx; color: #d4a520; font-weight: 600; }

.upgrade-btn {
  display: inline-flex;
  background: rgba(120, 80, 200, 0.12);
  border: 1rpx solid rgba(150, 100, 250, 0.25);
  border-radius: 20rpx;
  padding: 5rpx 16rpx;
}

.upgrade-text { font-size: 22rpx; color: rgba(160, 120, 240, 0.85); }

.user-card-arrow { flex-shrink: 0; }
.arrow-icon { font-size: 34rpx; color: rgba(180, 170, 210, 0.3); }

/* ── 分区 ── */
.section { margin-bottom: 24rpx; }

.section-label {
  font-size: 21rpx;
  color: rgba(180, 160, 220, 0.4);
  display: block;
  margin-bottom: 12rpx;
  margin-left: 4rpx;
  letter-spacing: 2rpx;
  text-transform: uppercase;
}

/* ── 人格卡片 ── */
.personality-card {
  display: flex;
  align-items: center;
  gap: 18rpx;
  background: rgba(255, 255, 255, 0.04);
  border: 1rpx solid rgba(255, 255, 255, 0.08);
  border-radius: 20rpx;
  padding: 22rpx 24rpx;
}

.p-avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 18rpx;
  background: rgba(120, 80, 200, 0.2);
  border: 1rpx solid rgba(150, 100, 250, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.p-emoji { font-size: 34rpx; }
.p-info { flex: 1; }
.p-name { font-size: 28rpx; color: rgba(225, 218, 245, 0.92); font-weight: 600; display: block; margin-bottom: 5rpx; }
.p-desc { font-size: 22rpx; color: rgba(180, 170, 210, 0.5); }

.card-arrow { flex-shrink: 0; }
.arrow-text { font-size: 32rpx; color: rgba(180, 170, 210, 0.3); }

/* ── 占星入口 ── */
.astro-main-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.04);
  border: 1rpx solid rgba(255, 255, 255, 0.08);
  border-radius: 20rpx;
  padding: 22rpx 24rpx;
  margin-bottom: 12rpx;
}

.astro-left { display: flex; align-items: center; gap: 18rpx; }

.astro-icon-bg {
  width: 64rpx;
  height: 64rpx;
  border-radius: 18rpx;
  background: rgba(150, 100, 220, 0.15);
  border: 1rpx solid rgba(180, 130, 255, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.astro-icon { font-size: 30rpx; color: rgba(180, 130, 255, 0.9); }
.astro-text { display: flex; flex-direction: column; gap: 5rpx; }
.astro-title { font-size: 28rpx; color: rgba(225, 218, 245, 0.9); font-weight: 600; }
.astro-sub { font-size: 21rpx; color: rgba(180, 170, 210, 0.5); }
.card-arrow-text { font-size: 32rpx; color: rgba(180, 170, 210, 0.3); }

.astro-quick-row { display: flex; gap: 12rpx; }

.quick-item {
  flex: 1;
  background: rgba(255, 255, 255, 0.03);
  border: 1rpx solid rgba(255, 255, 255, 0.07);
  border-radius: 16rpx;
  padding: 18rpx 10rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.quick-symbol { font-size: 26rpx; color: rgba(180, 130, 255, 0.8); font-weight: bold; }
.quick-label { font-size: 21rpx; color: rgba(180, 170, 210, 0.55); }

/* ── 菜单 ── */
.menu-group {
  background: rgba(255, 255, 255, 0.04);
  border: 1rpx solid rgba(255, 255, 255, 0.07);
  border-radius: 20rpx;
  overflow: hidden;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 24rpx 24rpx;
}

.menu-divider {
  height: 1rpx;
  background: rgba(255, 255, 255, 0.04);
  margin: 0 24rpx;
}

.menu-icon-bg {
  width: 56rpx;
  height: 56rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.menu-icon-gold { background: rgba(240, 185, 30, 0.15); }
.menu-icon-blue { background: rgba(60, 120, 240, 0.15); }
.menu-icon-green { background: rgba(40, 180, 100, 0.15); }
.menu-icon-orange { background: rgba(240, 130, 40, 0.15); }
.menu-icon-gray { background: rgba(180, 170, 210, 0.1); }

.menu-icon { font-size: 30rpx; }
.menu-text { flex: 1; font-size: 28rpx; color: rgba(215, 210, 240, 0.85); }
.menu-arrow { font-size: 28rpx; color: rgba(180, 170, 210, 0.25); }

/* ── 危机热线 ── */
.crisis-card {
  background: rgba(60, 100, 200, 0.08);
  border: 1rpx solid rgba(80, 130, 240, 0.2);
  border-radius: 20rpx;
  padding: 22rpx 24rpx;
  margin-bottom: 20rpx;
}

.crisis-left { display: flex; align-items: center; gap: 18rpx; }
.crisis-icon { font-size: 40rpx; }
.crisis-info { display: flex; flex-direction: column; gap: 6rpx; }
.crisis-title { font-size: 26rpx; color: rgba(100, 160, 255, 0.85); font-weight: 600; }
.crisis-line { font-size: 22rpx; color: rgba(130, 170, 240, 0.65); }

/* ── 退出 & 版本 ── */
.logout-row {
  text-align: center;
  padding: 26rpx;
  background: rgba(255, 255, 255, 0.03);
  border: 1rpx solid rgba(255, 255, 255, 0.06);
  border-radius: 16rpx;
  margin-bottom: 16rpx;
}

.logout-text { font-size: 26rpx; color: rgba(220, 100, 100, 0.6); }

.version-row { text-align: center; padding: 14rpx; }
.version-text { font-size: 21rpx; color: rgba(180, 170, 210, 0.25); }
</style>

