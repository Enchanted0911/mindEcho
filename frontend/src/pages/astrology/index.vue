<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {getUserAstrologyInfo} from '@/api/astrology'
import {useUserStore} from '@/store/user'

const userStore = useUserStore()

// ── 今日日期 ─────────────────────────────────
const today = new Date()
const monthDay = `${today.getMonth() + 1}月${today.getDate()}日`

// ── 刷新中状态 ────────────────────────────────
const isRefreshing = ref(false)

// ── 今日运势示例数据 ──────────────────────────
const todayFortune = ref({
  planet: '太阳',
  sign: '天蝎座',
  icon: '🔥',
  iconBg: '#E07428',
  summary: '今天能量转向深层探索，适合进行内省或处理复杂的财务问题。直觉力增强，相信你的第一感...',
})

// ── 用户出生信息摘要（从 astrologyInfo 读取） ──
const birthInfo = computed(() => userStore.astrologyInfo)
const hasBirth = computed(() => !!(birthInfo.value?.birthCity && birthInfo.value?.birthTime))
const birthDisplayTime = computed(() => {
  const t = birthInfo.value?.birthTime
  if (!t) return null
  // yyyy-MM-dd HH:mm → yyyy年M月d日 HH:mm
  try {
    const [date, time] = t.split(' ')
    const [y, m, d] = date.split('-').map(Number)
    return `${y}年${m}月${d}日 ${time}`
  } catch { return t }
})

onMounted(async () => {
  const loggedIn = userStore.restoreFromStorage()
  if (!loggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  // 如果没有 astrologyInfo（首次进入或 store 过期），从后端刷新
  if (!userStore.astrologyInfo) {
    try {
      const info = await getUserAstrologyInfo()
      userStore.setAstrologyInfo(info)
    } catch (e) {
      console.warn('[astrology/index] Failed to fetch astrology info:', e)
    }
  }
})

/** 刷新星盘信息（手动拉取最新状态） */
async function refreshAstrologyInfo() {
  if (isRefreshing.value) return
  isRefreshing.value = true
  try {
    const info = await getUserAstrologyInfo()
    userStore.setAstrologyInfo(info)
    uni.showToast({ title: '信息已刷新', icon: 'success', duration: 1500 })
  } catch {
    uni.showToast({ title: '刷新失败', icon: 'none', duration: 1500 })
  } finally {
    isRefreshing.value = false
  }
}

// ── 功能卡片导航 ──────────────────────────────
function goToNatal()    { uni.navigateTo({ url: '/pages/astrology/natal' }) }
function goToSynastry() { uni.navigateTo({ url: '/pages/astrology/synastry' }) }
function goToTransit()  { uni.navigateTo({ url: '/pages/astrology/transit' }) }
function goToInterpret(){ uni.navigateTo({ url: '/pages/astrology/natal' }) }
function goToDetail()   { uni.navigateTo({ url: '/pages/astrology/transit' }) }
/** 跳转到本命盘页设置出生信息 */
function goSetBirthInfo() { uni.navigateTo({ url: '/pages/astrology/natal' }) }
</script>

<template>
  <scroll-view class="astro-page" scroll-y>

    <!-- ░░ 太阳系动画区 ░░ -->
    <view class="solar-hero">
      <!-- 星空背景粒子 -->
      <view class="starfield">
        <view class="star star-1" />
        <view class="star star-2" />
        <view class="star star-3" />
        <view class="star star-4" />
        <view class="star star-5" />
        <view class="star star-6" />
        <view class="star star-7" />
        <view class="star star-8" />
        <view class="star star-9" />
        <view class="star star-10" />
        <view class="star star-11" />
        <view class="star star-12" />
      </view>

      <!-- 轨道圈 -->
      <view class="orbit orbit-1" />
      <view class="orbit orbit-2" />
      <view class="orbit orbit-3" />
      <view class="orbit orbit-4" />

      <!-- 太阳（中心，带光晕脉冲） -->
      <view class="sun-wrap">
        <view class="sun-glow" />
        <view class="sun-core">
          <text class="sun-emoji">☀️</text>
        </view>
      </view>

      <!-- 水星 -->
      <view class="planet-track track-mercury">
        <view class="planet planet-mercury">
          <view class="planet-dot mercury-dot" />
        </view>
      </view>

      <!-- 金星 -->
      <view class="planet-track track-venus">
        <view class="planet planet-venus">
          <view class="planet-dot venus-dot" />
        </view>
      </view>

      <!-- 地球（带月亮） -->
      <view class="planet-track track-earth">
        <view class="planet planet-earth">
          <view class="planet-dot earth-dot">
            <view class="moon-track">
              <view class="moon-dot" />
            </view>
          </view>
        </view>
      </view>

      <!-- 火星 -->
      <view class="planet-track track-mars">
        <view class="planet planet-mars">
          <view class="planet-dot mars-dot" />
        </view>
      </view>

      <!-- 标题叠加 -->
      <view class="hero-overlay">
        <text class="hero-title">探索你的星图</text>
        <text class="hero-sub">连接宇宙能量，解读生命密码</text>
      </view>
    </view>

    <!-- ░░ 用户星盘状态卡片（统一出生信息设置入口） ░░ -->
    <view class="user-astro-card" @tap="hasBirth ? null : goSetBirthInfo()">
      <!-- 已设置出生信息 -->
      <template v-if="hasBirth">
        <view class="uac-header">
          <view class="uac-left">
            <text class="uac-icon">🌟</text>
            <text class="uac-title">我的星盘信息</text>
          </view>
          <view class="uac-actions">
            <view class="uac-refresh-btn" :class="{ spinning: isRefreshing }" @tap.stop="refreshAstrologyInfo">
              <text class="uac-refresh-icon">↻</text>
            </view>
            <view class="uac-edit-btn" @tap.stop="goSetBirthInfo">
              <text class="uac-edit-text">修改</text>
            </view>
          </view>
        </view>
        <view class="uac-birth-info">
          <view class="uac-info-row">
            <text class="uac-info-key">🕐 出生时间</text>
            <text class="uac-info-val">{{ birthDisplayTime }}</text>
          </view>
          <view class="uac-info-row">
            <text class="uac-info-key">📍 出生城市</text>
            <text class="uac-info-val">{{ birthInfo?.birthCity }}</text>
          </view>
        </view>
        <!-- 星盘缓存状态徽章 -->
        <view class="uac-cache-row">
          <view class="uac-cache-badge" :class="birthInfo?.hasNatalCache ? 'badge-ready' : 'badge-empty'">
            <text class="badge-dot">●</text>
            <text class="badge-text">本命盘</text>
            <text class="badge-status">{{ birthInfo?.hasNatalCache ? '已计算' : '未计算' }}</text>
          </view>
          <view class="uac-cache-badge" :class="birthInfo?.hasSynastryCache ? 'badge-ready' : 'badge-empty'">
            <text class="badge-dot">●</text>
            <text class="badge-text">合盘</text>
            <text class="badge-status">{{ birthInfo?.hasSynastryCache ? '已计算' : '未计算' }}</text>
          </view>
          <view class="uac-cache-badge" :class="birthInfo?.hasTransitCache ? 'badge-ready' : 'badge-empty'">
            <text class="badge-dot">●</text>
            <text class="badge-text">流运</text>
            <text class="badge-status">{{ birthInfo?.hasTransitCache ? '已计算' : '未计算' }}</text>
          </view>
        </view>
      </template>

      <!-- 未设置出生信息 -->
      <template v-else>
        <view class="uac-empty">
          <view class="uac-empty-icon-wrap">
            <text class="uac-empty-icon">🌙</text>
          </view>
          <view class="uac-empty-content">
            <text class="uac-empty-title">设置你的出生信息</text>
            <text class="uac-empty-desc">解锁本命盘、合盘、流运全部功能</text>
          </view>
          <view class="uac-setup-btn" @tap.stop="goSetBirthInfo">
            <text class="uac-setup-text">立即设置 →</text>
          </view>
        </view>
      </template>
    </view>

    <!-- ░░ 2×2 功能卡片 ░░ -->
    <view class="feature-grid">

      <!-- 本命盘 -->
      <view class="feature-card" @tap="goToNatal">
        <view class="fc-icon-wrap fc-purple">
          <text class="fc-icon">👤</text>
        </view>
        <text class="fc-label">本命盘 Natal</text>
      </view>

      <!-- 合盘 -->
      <view class="feature-card" @tap="goToSynastry">
        <view class="fc-icon-wrap fc-rose">
          <text class="fc-icon">∞</text>
        </view>
        <text class="fc-label">合盘 Synastry</text>
      </view>

      <!-- 流运 -->
      <view class="feature-card" @tap="goToTransit">
        <view class="fc-icon-wrap fc-blue">
          <text class="fc-icon">↺</text>
        </view>
        <text class="fc-label">流运 Transit</text>
      </view>

      <!-- AI 解读（高亮卡片） -->
      <view class="feature-card feature-card--active" @tap="goToInterpret">
        <view class="fc-icon-wrap fc-active">
          <text class="fc-icon">🤖</text>
        </view>
        <text class="fc-label fc-label--active">AI 解读</text>
      </view>

    </view>

    <!-- ░░ 今日运势 ░░ -->
    <view class="section-block">
      <view class="section-header">
        <view class="section-title-row">
          <text class="section-title-icon">🌟</text>
          <text class="section-title">今日运势</text>
        </view>
        <text class="section-date">{{ monthDay }}</text>
      </view>

      <view class="fortune-card" @tap="goToDetail">
        <view class="fortune-planet-icon" :style="{ background: todayFortune.iconBg }">
          <text class="fortune-planet-emoji">{{ todayFortune.icon }}</text>
        </view>
        <view class="fortune-content">
          <text class="fortune-headline">{{ todayFortune.planet }}进入{{ todayFortune.sign }}</text>
          <text class="fortune-summary">{{ todayFortune.summary }}</text>
          <view class="fortune-detail-row">
            <text class="fortune-detail-link">查看详情</text>
            <text class="fortune-detail-arrow"> ›</text>
          </view>
        </view>
      </view>
    </view>

    <!-- ░░ Astro AI 洞察 + 解锁专属报告 合并卡片 ░░ -->
    <view class="section-block ai-insight-block">
      <view class="ai-insight-header">
        <view class="ai-header-left">
          <text class="ai-insight-icon">✨</text>
          <text class="ai-insight-title">Astro AI 洞察</text>
        </view>
        <view class="ai-badge">
          <text class="ai-badge-text">NEW</text>
        </view>
      </view>

      <text class="ai-insight-desc">基于你近期的星象行进，AI为你生成了一份专属的下周能量指引报告，涵盖情感、事业、财运三大维度深度分析。</text>

      <!-- 预览列表 -->
      <view class="ai-preview-list">
        <view class="ai-preview-item">
          <text class="ai-preview-dot" style="color: #e07428;">●</text>
          <text class="ai-preview-text">情感运势：水星逆行影响沟通，需主动表达...</text>
        </view>
        <view class="ai-preview-item">
          <text class="ai-preview-dot" style="color: #9b7fe8;">●</text>
          <text class="ai-preview-text">事业运势：木星加持创新思维，适合启动新项目...</text>
        </view>
        <view class="ai-preview-item ai-preview-blur">
          <text class="ai-preview-dot" style="color: #60b8e0;">●</text>
          <text class="ai-preview-text">财运分析：土星稳健保守，建议规避高风险...</text>
        </view>
      </view>

      <!-- 解锁按钮（内嵌在卡片内） -->
      <view class="unlock-btn-inner" @tap="goToInterpret">
        <text class="unlock-btn-icon">🔮</text>
        <text class="unlock-btn-text">解锁专属完整报告</text>
        <text class="unlock-btn-arrow">→</text>
      </view>
    </view>

    <!-- 底部安全距离 -->
    <view style="height: 60rpx;" />

  </scroll-view>
</template>

<style>
/* ══════════════════════════════════════════════
   AstroMind 星盘首页  ·  深宇宙风格（现代动效版）
══════════════════════════════════════════════ */

.astro-page {
  min-height: 100vh;
  background: #0a0818;
  color: #e8e0ff;
}

/* ══════════════════════════════════════════════
   太阳系动画区  ·  纯 CSS 驱动
══════════════════════════════════════════════ */
.solar-hero {
  position: relative;
  width: 750rpx;
  height: 420rpx;
  overflow: hidden;
  background: radial-gradient(ellipse at 50% 60%, #1a1260 0%, #0a0818 70%);
}

/* ── 星空粒子 ─────────────────────── */
.starfield { position: absolute; inset: 0; }

.star {
  position: absolute;
  border-radius: 50%;
  background: #fff;
  animation: twinkle 3s ease-in-out infinite alternate;
}
.star-1  { width: 3rpx; height: 3rpx; top: 8%;  left: 12%; animation-delay: 0s;    opacity: 0.8; }
.star-2  { width: 4rpx; height: 4rpx; top: 15%; left: 80%; animation-delay: 0.5s;  opacity: 0.6; }
.star-3  { width: 3rpx; height: 3rpx; top: 25%; left: 35%; animation-delay: 1s;    opacity: 0.9; }
.star-4  { width: 2rpx; height: 2rpx; top: 30%; left: 60%; animation-delay: 1.5s;  opacity: 0.7; }
.star-5  { width: 4rpx; height: 4rpx; top: 5%;  left: 55%; animation-delay: 0.3s;  opacity: 0.5; }
.star-6  { width: 3rpx; height: 3rpx; top: 50%; left: 88%; animation-delay: 2s;    opacity: 0.8; }
.star-7  { width: 2rpx; height: 2rpx; top: 70%; left: 10%; animation-delay: 0.7s;  opacity: 0.6; }
.star-8  { width: 4rpx; height: 4rpx; top: 60%; left: 72%; animation-delay: 1.2s;  opacity: 0.9; }
.star-9  { width: 3rpx; height: 3rpx; top: 80%; left: 45%; animation-delay: 0.9s;  opacity: 0.7; }
.star-10 { width: 2rpx; height: 2rpx; top: 20%; left: 20%; animation-delay: 2.5s;  opacity: 0.5; }
.star-11 { width: 3rpx; height: 3rpx; top: 12%; left: 68%; animation-delay: 1.8s;  opacity: 0.8; }
.star-12 { width: 4rpx; height: 4rpx; top: 88%; left: 25%; animation-delay: 0.4s;  opacity: 0.6; }

@keyframes twinkle {
  0%   { opacity: 0.2; transform: scale(0.8); }
  100% { opacity: 1;   transform: scale(1.2); }
}

/* ── 轨道圈 ─────────────────────────── */
.orbit {
  position: absolute;
  border-radius: 50%;
  border: 1rpx solid rgba(155, 135, 209, 0.15);
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}
.orbit-1 { width: 136rpx; height: 136rpx; border-color: rgba(176, 200, 224, 0.2); }
.orbit-2 { width: 192rpx; height: 192rpx; border-color: rgba(76, 214, 160, 0.2); }
.orbit-3 { width: 256rpx; height: 256rpx; border-color: rgba(96, 184, 224, 0.2); }
.orbit-4 { width: 324rpx; height: 324rpx; border-color: rgba(255, 128, 112, 0.15); }

/* ── 太阳 ───────────────────────────── */
.sun-wrap {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
}

.sun-glow {
  position: absolute;
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(245, 166, 35, 0.4) 0%, transparent 70%);
  animation: pulse-glow 2.5s ease-in-out infinite;
}

@keyframes pulse-glow {
  0%, 100% { transform: scale(1);   opacity: 0.6; }
  50%       { transform: scale(1.5); opacity: 1;   }
}

.sun-core {
  width: 52rpx;
  height: 52rpx;
  border-radius: 50%;
  background: radial-gradient(circle at 35% 35%, #fff3b0, #F5A623 60%, #b06010);
  box-shadow: 0 0 16rpx #F5A623, 0 0 40rpx rgba(245,166,35,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1;
}

.sun-emoji {
  font-size: 30rpx;
  line-height: 1;
}

/* ── 行星公转轨道包裹层 ────────────────
   原理：track 旋转 → 行星始终处于轨道边缘
   通过 rotate 反方向让行星本体不随之旋转
─────────────────────────────────────── */
.planet-track {
  position: absolute;
  top: 50%;
  left: 50%;
  border-radius: 50%;
  transform-origin: 0 0;
  animation: orbit-spin linear infinite;
}

/* 水星 */
.track-mercury {
  width: 136rpx;
  height: 136rpx;
  margin-left: -68rpx;
  margin-top: -68rpx;
  animation-duration: 5s;
}
/* 金星 */
.track-venus {
  width: 192rpx;
  height: 192rpx;
  margin-left: -96rpx;
  margin-top: -96rpx;
  animation-duration: 9s;
}
/* 地球 */
.track-earth {
  width: 256rpx;
  height: 256rpx;
  margin-left: -128rpx;
  margin-top: -128rpx;
  animation-duration: 14s;
}
/* 火星 */
.track-mars {
  width: 324rpx;
  height: 324rpx;
  margin-left: -162rpx;
  margin-top: -162rpx;
  animation-duration: 22s;
}

@keyframes orbit-spin {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}

/* 行星容器：偏移到轨道顶部中心点 */
.planet {
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
}

/* 行星点本体（反向旋转，保持朝向） */
.planet-dot {
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 水星 */
.mercury-dot {
  width: 12rpx; height: 12rpx;
  background: radial-gradient(circle at 35% 35%, #d0dff0, #8090a8);
  box-shadow: 0 0 8rpx rgba(176, 200, 224, 0.8);
}

/* 金星 */
.venus-dot {
  width: 16rpx; height: 16rpx;
  background: radial-gradient(circle at 35% 35%, #e0f8ec, #4CD6A0);
  box-shadow: 0 0 10rpx rgba(76, 214, 160, 0.8);
}

/* 地球 */
.earth-dot {
  width: 20rpx; height: 20rpx;
  background: radial-gradient(circle at 35% 35%, #a8dff8, #1e7fc8);
  box-shadow: 0 0 12rpx rgba(96, 184, 224, 0.8);
  position: relative;
  overflow: visible;
}

/* 月亮 */
.moon-track {
  position: absolute;
  width: 36rpx;
  height: 36rpx;
  top: 50%;
  left: 50%;
  margin-top: -18rpx;
  margin-left: -18rpx;
  animation: orbit-spin 2.5s linear infinite;
}
.moon-dot {
  position: absolute;
  width: 6rpx;
  height: 6rpx;
  background: #c8c0e0;
  border-radius: 50%;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  box-shadow: 0 0 4rpx rgba(200, 192, 224, 0.9);
}

/* 火星 */
.mars-dot {
  width: 16rpx; height: 16rpx;
  background: radial-gradient(circle at 35% 35%, #ffc0b0, #e05040);
  box-shadow: 0 0 10rpx rgba(255, 128, 112, 0.8);
}

/* ── 标题叠加在动画区上 ─────────────── */
.hero-overlay {
  position: absolute;
  bottom: 24rpx;
  left: 0;
  right: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  z-index: 20;
}

.hero-title {
  font-size: 48rpx;
  font-weight: 700;
  color: #f0ebff;
  letter-spacing: 3rpx;
  text-align: center;
  display: block;
  text-shadow: 0 0 20rpx rgba(155, 135, 209, 0.5);
}

.hero-sub {
  font-size: 24rpx;
  color: rgba(180, 160, 240, 0.65);
  letter-spacing: 2rpx;
  text-align: center;
  display: block;
}

/* ══════════════════════════════════════════════
   用户星盘状态卡片（user-astro-card / uac-*）
══════════════════════════════════════════════ */
.user-astro-card {
  margin: 20rpx 28rpx 0;
  background: rgba(22, 18, 48, 0.85);
  border: 1.5rpx solid rgba(155, 135, 209, 0.2);
  border-radius: 28rpx;
  padding: 28rpx;
  backdrop-filter: blur(10rpx);
}

/* ── 已设置出生信息 Header ───────── */
.uac-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}

.uac-left {
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.uac-icon { font-size: 30rpx; }

.uac-title {
  font-size: 28rpx;
  font-weight: 700;
  color: #e8e0ff;
}

.uac-actions {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.uac-refresh-btn {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: rgba(155, 127, 232, 0.15);
  border: 1rpx solid rgba(155, 127, 232, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
}

.uac-refresh-btn:active { opacity: 0.7; }

.uac-refresh-icon {
  font-size: 28rpx;
  color: #9b7fe8;
  display: block;
}

.uac-refresh-btn.spinning .uac-refresh-icon {
  animation: spin360 1s linear infinite;
}

@keyframes spin360 {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}

.uac-edit-btn {
  background: rgba(155, 127, 232, 0.15);
  border: 1rpx solid rgba(155, 127, 232, 0.3);
  border-radius: 20rpx;
  padding: 8rpx 24rpx;
}

.uac-edit-btn:active { opacity: 0.7; }

.uac-edit-text {
  font-size: 24rpx;
  color: #9b7fe8;
  font-weight: 500;
}

/* ── 出生信息两行 ──────────────────── */
.uac-birth-info {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
  margin-bottom: 20rpx;
}

.uac-info-row {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.uac-info-key {
  font-size: 24rpx;
  color: rgba(155, 135, 209, 0.7);
  flex-shrink: 0;
  width: 180rpx;
}

.uac-info-val {
  font-size: 24rpx;
  color: #d8d0f0;
  font-weight: 500;
  flex: 1;
}

/* ── 缓存徽章行 ────────────────────── */
.uac-cache-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.uac-cache-badge {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
  padding: 14rpx 8rpx;
  border-radius: 16rpx;
  border: 1rpx solid rgba(155, 135, 209, 0.12);
}

.uac-cache-badge.badge-ready {
  background: rgba(80, 200, 120, 0.1);
  border-color: rgba(80, 200, 120, 0.25);
}

.uac-cache-badge.badge-empty {
  background: rgba(155, 135, 209, 0.07);
}

.badge-dot {
  font-size: 16rpx;
  line-height: 1;
}

.badge-ready .badge-dot { color: #50c878; }
.badge-empty .badge-dot { color: rgba(155, 135, 209, 0.4); }

.badge-text {
  font-size: 22rpx;
  color: rgba(200, 190, 235, 0.8);
  font-weight: 500;
  display: block;
}

.badge-status {
  font-size: 20rpx;
  display: block;
}

.badge-ready .badge-status { color: #50c878; }
.badge-empty .badge-status { color: rgba(155, 135, 209, 0.45); }

/* ── 未设置出生信息 空态 ─────────── */
.uac-empty {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.uac-empty-icon-wrap {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: rgba(155, 127, 232, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.uac-empty-icon { font-size: 36rpx; }

.uac-empty-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.uac-empty-title {
  font-size: 28rpx;
  font-weight: 700;
  color: #e8e0ff;
  display: block;
}

.uac-empty-desc {
  font-size: 22rpx;
  color: rgba(155, 135, 209, 0.6);
  display: block;
  line-height: 1.5;
}

.uac-setup-btn {
  background: linear-gradient(135deg, #7b5cf0, #9b7fe8);
  border-radius: 20rpx;
  padding: 14rpx 22rpx;
  flex-shrink: 0;
}

.uac-setup-btn:active { opacity: 0.85; }

.uac-setup-text {
  font-size: 24rpx;
  color: #fff;
  font-weight: 600;
  white-space: nowrap;
}

/* ══════════════════════════════════════════════
   2×2 功能卡片
══════════════════════════════════════════════ */
.feature-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20rpx;
  padding: 24rpx 28rpx 8rpx;
}

.feature-card {
  background: rgba(22, 18, 48, 0.85);
  border: 1.5rpx solid rgba(155, 135, 209, 0.15);
  border-radius: 28rpx;
  padding: 36rpx 24rpx 32rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 18rpx;
  backdrop-filter: blur(10rpx);
}

.feature-card:active { opacity: 0.85; transform: scale(0.97); }

.feature-card--active {
  background: linear-gradient(135deg, rgba(100, 70, 200, 0.35), rgba(140, 90, 220, 0.25));
  border-color: rgba(160, 120, 240, 0.45);
  box-shadow: 0 0 32rpx rgba(130, 90, 220, 0.2);
}

.fc-icon-wrap {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.fc-purple { background: rgba(130, 90, 210, 0.25); }
.fc-rose   { background: rgba(200, 70, 110, 0.25); }
.fc-blue   { background: rgba(70, 150, 220, 0.25); }
.fc-active { background: rgba(130, 90, 210, 0.3); }

.fc-icon { font-size: 38rpx; line-height: 1; }

.fc-label {
  font-size: 26rpx;
  color: rgba(210, 200, 240, 0.8);
  font-weight: 500;
  text-align: center;
  display: block;
}

.fc-label--active {
  color: #c8b0ff;
  font-weight: 600;
}

/* ══════════════════════════════════════════════
   通用 section 块
══════════════════════════════════════════════ */
.section-block {
  margin: 24rpx 28rpx 0;
  background: rgba(22, 18, 48, 0.75);
  border: 1.5rpx solid rgba(155, 135, 209, 0.15);
  border-radius: 28rpx;
  padding: 28rpx 28rpx 24rpx;
  backdrop-filter: blur(10rpx);
}

/* ══════════════════════════════════════════════
   今日运势
══════════════════════════════════════════════ */
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
}

.section-title-row {
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.section-title-icon { font-size: 30rpx; }

.section-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #e8e0ff;
}

.section-date {
  font-size: 24rpx;
  color: rgba(155, 135, 209, 0.6);
}

.fortune-card {
  display: flex;
  align-items: flex-start;
  gap: 22rpx;
  background: rgba(30, 24, 58, 0.7);
  border-radius: 20rpx;
  padding: 24rpx;
  border: 1rpx solid rgba(155, 135, 209, 0.1);
}

.fortune-card:active { opacity: 0.85; }

.fortune-planet-icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.fortune-planet-emoji { font-size: 34rpx; line-height: 1; }

.fortune-content { flex: 1; }

.fortune-headline {
  font-size: 30rpx;
  font-weight: 700;
  color: #f0ebff;
  display: block;
  margin-bottom: 10rpx;
}

.fortune-summary {
  font-size: 24rpx;
  color: rgba(180, 165, 220, 0.7);
  line-height: 1.7;
  display: block;
  margin-bottom: 14rpx;
}

.fortune-detail-row {
  display: flex;
  align-items: center;
}

.fortune-detail-link {
  font-size: 24rpx;
  color: #9b7fe8;
  font-weight: 500;
}

.fortune-detail-arrow {
  font-size: 28rpx;
  color: #9b7fe8;
}

/* ══════════════════════════════════════════════
   AI 洞察 + 解锁报告 合并卡片
══════════════════════════════════════════════ */
.ai-insight-block {
  margin-top: 20rpx;
  background: linear-gradient(145deg, rgba(28, 16, 64, 0.9), rgba(16, 12, 42, 0.9));
  border-color: rgba(155, 127, 232, 0.25);
  box-shadow: 0 4rpx 32rpx rgba(100, 60, 200, 0.15);
}

.ai-insight-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16rpx;
}

.ai-header-left {
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.ai-insight-icon { font-size: 30rpx; }

.ai-insight-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #e8e0ff;
}

.ai-badge {
  background: linear-gradient(135deg, #7b5cf0, #a855f7);
  border-radius: 20rpx;
  padding: 4rpx 14rpx;
}
.ai-badge-text {
  font-size: 20rpx;
  font-weight: 700;
  color: #fff;
  letter-spacing: 1rpx;
}

.ai-insight-desc {
  font-size: 26rpx;
  color: rgba(180, 165, 220, 0.7);
  line-height: 1.75;
  display: block;
  margin-bottom: 20rpx;
}

/* 预览列表 */
.ai-preview-list {
  background: rgba(255, 255, 255, 0.04);
  border-radius: 16rpx;
  padding: 20rpx;
  margin-bottom: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 14rpx;
  border: 1rpx solid rgba(155, 135, 209, 0.1);
}

.ai-preview-item {
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
}

.ai-preview-dot {
  font-size: 18rpx;
  line-height: 1.8;
  flex-shrink: 0;
}

.ai-preview-text {
  font-size: 24rpx;
  color: rgba(200, 190, 235, 0.75);
  line-height: 1.6;
  display: block;
  flex: 1;
}

/* 最后一条模糊处理（引导解锁） */
.ai-preview-blur .ai-preview-text {
  color: rgba(200, 190, 235, 0.3);
  -webkit-filter: blur(3px);
  filter: blur(3px);
  user-select: none;
}

/* 解锁按钮（内嵌卡片） */
.unlock-btn-inner {
  background: linear-gradient(135deg, #7b5cf0, #9b7fe8);
  border-radius: 20rpx;
  padding: 28rpx 32rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14rpx;
  box-shadow: 0 6rpx 24rpx rgba(123, 92, 240, 0.4);
}

.unlock-btn-inner:active {
  opacity: 0.9;
  transform: scale(0.98);
}

.unlock-btn-icon { font-size: 30rpx; line-height: 1; }

.unlock-btn-text {
  font-size: 30rpx;
  font-weight: 700;
  color: #fff;
  letter-spacing: 1rpx;
}

.unlock-btn-arrow {
  font-size: 28rpx;
  color: rgba(255, 255, 255, 0.8);
  font-weight: 700;
}
</style>

