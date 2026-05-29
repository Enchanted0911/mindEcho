<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {checkNatalChart} from '../../api/astrology'

const hasNatal = ref(false)
const isChecking = ref(true)

// 每日星语（轮播文案）
const STAR_QUOTES = [
  '星盘是灵魂的地图，而你才是旅者。',
  '行星不决定命运，只描绘倾向。',
  '了解自己，是所有关系的起点。',
  '流运是风，你决定如何扬帆。',
  '内心的宇宙，比星空更辽阔。'
]
const quoteIndex = ref(Math.floor(Math.random() * STAR_QUOTES.length))

onMounted(async () => {
  try {
    hasNatal.value = await checkNatalChart()
  } catch (e) {
    hasNatal.value = false
  } finally {
    isChecking.value = false
  }
})

function goNatal() {
  uni.navigateTo({ url: '/pages/astrology/natal' })
}
function goSynastry() {
  uni.navigateTo({ url: '/pages/astrology/synastry' })
}
function goTransit() {
  uni.navigateTo({ url: '/pages/astrology/transit' })
}
</script>

<template>
  <view class="astro-home">
    <!-- 星空背景 -->
    <view class="stars-bg">
      <view v-for="i in 30" :key="i" class="star" :style="{
        left: (Math.sin(i * 137.5) * 50 + 50) + '%',
        top: (Math.cos(i * 97.3) * 50 + 50) + '%',
        width: (i % 3 === 0 ? 3 : i % 3 === 1 ? 2 : 1.5) + 'rpx',
        height: (i % 3 === 0 ? 3 : i % 3 === 1 ? 2 : 1.5) + 'rpx',
        animationDelay: (i * 0.3) + 's',
        opacity: 0.3 + (i % 5) * 0.1
      }" />
    </view>

    <scroll-view class="home-scroll" scroll-y>
      <!-- Header -->
      <view class="page-header">
        <view class="header-badge">✦ AI 星盘</view>
        <text class="header-title">星屿占星</text>
        <text class="header-subtitle">AI Emotional Astrology</text>
      </view>

      <!-- 每日星语 -->
      <view class="quote-card">
        <text class="quote-star">✦</text>
        <text class="quote-text">{{ STAR_QUOTES[quoteIndex] }}</text>
      </view>

      <!-- 本命盘状态提示 -->
      <view v-if="!isChecking && !hasNatal" class="natal-hint" @click="goNatal">
        <text class="hint-icon">🌟</text>
        <view class="hint-content">
          <text class="hint-title">建立你的星盘档案</text>
          <text class="hint-desc">录入出生信息，解锁个性化占星体验</text>
        </view>
        <text class="hint-arrow">›</text>
      </view>

      <!-- 三大功能入口 -->
      <view class="section-title">选择解读模式</view>

      <!-- 单星盘 -->
      <view class="feature-card natal-card" @click="goNatal">
        <view class="card-glow natal-glow" />
        <view class="card-content">
          <view class="card-header-row">
            <view class="card-icon-wrap natal-icon-wrap">
              <text class="card-icon">☉</text>
            </view>
            <view class="card-tag">核心</view>
          </view>
          <text class="card-title">本命盘</text>
          <text class="card-subtitle">Natal Chart</text>
          <text class="card-desc">探索你的星盘结构、行星能量与人生底色，了解性格深层动力。</text>
          <view class="card-tags-row">
            <view class="tag">性格</view>
            <view class="tag">情感</view>
            <view class="tag">天赋</view>
            <view class="tag">成长</view>
          </view>
        </view>
        <view class="card-planet-deco">
          <view class="orbit-ring ring-1" />
          <view class="orbit-ring ring-2" />
          <view class="planet-dot" />
        </view>
      </view>

      <!-- 和盘 -->
      <view class="feature-card synastry-card" @click="goSynastry">
        <view class="card-glow synastry-glow" />
        <view class="card-content">
          <view class="card-header-row">
            <view class="card-icon-wrap synastry-icon-wrap">
              <text class="card-icon">♀</text>
            </view>
            <view class="card-tag synastry-tag">关系</view>
          </view>
          <text class="card-title">和盘分析</text>
          <text class="card-subtitle">Synastry Chart</text>
          <text class="card-desc">与另一个人的星盘相遇，揭示关系动力、吸引力与挑战所在。</text>
          <view class="card-tags-row">
            <view class="tag">吸引力</view>
            <view class="tag">情绪共鸣</view>
            <view class="tag">长期稳定</view>
          </view>
        </view>
        <view class="card-planet-deco synastry-deco">
          <view class="orbit-ring ring-1" />
          <view class="planet-dot dot-a" />
          <view class="planet-dot dot-b" />
        </view>
      </view>

      <!-- 流运 -->
      <view class="feature-card transit-card" @click="goTransit">
        <view class="card-glow transit-glow" />
        <view class="card-content">
          <view class="card-header-row">
            <view class="card-icon-wrap transit-icon-wrap">
              <text class="card-icon">♄</text>
            </view>
            <view class="card-tag transit-tag">当下</view>
          </view>
          <text class="card-title">流运解读</text>
          <text class="card-subtitle">Transit Reading</text>
          <text class="card-desc">当前天空的行星正与你的星盘发生共鸣，了解近期能量流动与变化。</text>
          <view class="card-tags-row">
            <view class="tag">今日能量</view>
            <view class="tag">情感变化</view>
            <view class="tag">近期建议</view>
          </view>
        </view>
        <view class="card-planet-deco transit-deco">
          <view class="orbit-ring ring-1" />
          <view class="orbit-ring ring-2 ring-fast" />
          <view class="planet-dot" />
        </view>
      </view>

      <!-- 底部说明 -->
      <view class="footer-note">
        <text class="footer-text">✦ 占星是了解自我的工具，而非命运的裁决 ✦</text>
      </view>

      <view style="height: 120rpx" />
    </scroll-view>
  </view>
</template>

<style>
.astro-home {
  min-height: 100vh;
  background: #0a0a18;
  position: relative;
  overflow: hidden;
}

/* 星空背景 */
.stars-bg {
  position: fixed;
  top: 0; left: 0;
  width: 100%; height: 100%;
  pointer-events: none;
  z-index: 0;
}
.star {
  position: absolute;
  border-radius: 50%;
  background: #ffffff;
  animation: twinkle 3s ease-in-out infinite;
}
@keyframes twinkle {
  0%, 100% { opacity: 0.2; transform: scale(1); }
  50% { opacity: 0.8; transform: scale(1.4); }
}

.home-scroll {
  position: relative;
  z-index: 1;
  padding: 0 28rpx;
}

/* Header */
.page-header {
  padding-top: 80rpx;
  padding-bottom: 32rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
}
.header-badge {
  background: rgba(139,111,209,0.15);
  border: 1rpx solid rgba(139,111,209,0.35);
  border-radius: 30rpx;
  padding: 8rpx 24rpx;
  font-size: 22rpx;
  color: #a888e8;
  letter-spacing: 2rpx;
}
.header-title {
  font-size: 56rpx;
  font-weight: bold;
  color: #f0e8ff;
  letter-spacing: 4rpx;
  display: block;
  text-align: center;
}
.header-subtitle {
  font-size: 24rpx;
  color: #5a4878;
  letter-spacing: 6rpx;
  display: block;
  text-align: center;
}

/* 每日星语 */
.quote-card {
  background: linear-gradient(135deg, rgba(139,111,209,0.08), rgba(80,50,140,0.05));
  border: 1rpx solid rgba(139,111,209,0.18);
  border-radius: 20rpx;
  padding: 28rpx 32rpx;
  margin-bottom: 32rpx;
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
}
.quote-star { font-size: 28rpx; color: #8b6fd1; flex-shrink: 0; margin-top: 4rpx; }
.quote-text { font-size: 28rpx; color: #c4a8f0; line-height: 1.7; font-style: italic; }

/* 本命盘提示 */
.natal-hint {
  background: linear-gradient(135deg, rgba(240,200,80,0.08), rgba(200,150,50,0.05));
  border: 1rpx solid rgba(240,200,80,0.2);
  border-radius: 20rpx;
  padding: 24rpx 28rpx;
  margin-bottom: 32rpx;
  display: flex;
  align-items: center;
  gap: 20rpx;
}
.hint-icon { font-size: 44rpx; flex-shrink: 0; }
.hint-content { flex: 1; }
.hint-title { font-size: 30rpx; color: #f0d060; font-weight: 600; display: block; margin-bottom: 6rpx; }
.hint-desc { font-size: 24rpx; color: #a09040; }
.hint-arrow { font-size: 40rpx; color: #806a20; }

.section-title {
  font-size: 24rpx;
  color: #5a4878;
  letter-spacing: 3rpx;
  margin-bottom: 20rpx;
  padding-left: 4rpx;
  display: block;
}

/* 功能卡片 */
.feature-card {
  position: relative;
  border-radius: 28rpx;
  margin-bottom: 24rpx;
  overflow: hidden;
  min-height: 320rpx;
}
.natal-card { background: linear-gradient(145deg, #12102a 0%, #1a1035 60%, #0f0d20 100%); border: 1rpx solid rgba(184,158,232,0.2); }
.synastry-card { background: linear-gradient(145deg, #0f1a2a 0%, #0d1535 60%, #0a1020 100%); border: 1rpx solid rgba(100,180,255,0.2); }
.transit-card { background: linear-gradient(145deg, #1a100a 0%, #231430 60%, #150f0a 100%); border: 1rpx solid rgba(255,160,80,0.2); }

.card-glow {
  position: absolute;
  top: -80rpx; right: -80rpx;
  width: 300rpx; height: 300rpx;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.25;
  pointer-events: none;
}
.natal-glow { background: radial-gradient(circle, #8b6fd1, transparent); }
.synastry-glow { background: radial-gradient(circle, #4a90e2, transparent); }
.transit-glow { background: radial-gradient(circle, #e07820, transparent); }

.card-content {
  position: relative;
  z-index: 2;
  padding: 36rpx 36rpx 32rpx;
}

.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}

.card-icon-wrap {
  width: 72rpx; height: 72rpx;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.natal-icon-wrap { background: rgba(139,111,209,0.2); border: 1rpx solid rgba(139,111,209,0.3); }
.synastry-icon-wrap { background: rgba(74,144,226,0.2); border: 1rpx solid rgba(74,144,226,0.3); }
.transit-icon-wrap { background: rgba(224,120,32,0.2); border: 1rpx solid rgba(224,120,32,0.3); }

.card-icon { font-size: 36rpx; }

.card-tag {
  font-size: 22rpx;
  padding: 6rpx 18rpx;
  border-radius: 20rpx;
  background: rgba(139,111,209,0.15);
  border: 1rpx solid rgba(139,111,209,0.25);
  color: #a888e8;
}
.synastry-tag { background: rgba(74,144,226,0.15); border-color: rgba(74,144,226,0.25); color: #64b4ff; }
.transit-tag { background: rgba(224,120,32,0.15); border-color: rgba(224,120,32,0.25); color: #ffaa44; }

.card-title {
  font-size: 44rpx;
  font-weight: bold;
  color: #f0e8ff;
  display: block;
  margin-bottom: 6rpx;
}
.card-subtitle {
  font-size: 22rpx;
  color: #5a4878;
  letter-spacing: 4rpx;
  display: block;
  margin-bottom: 20rpx;
}
.card-desc {
  font-size: 26rpx;
  color: #8a7aaa;
  line-height: 1.75;
  display: block;
  margin-bottom: 24rpx;
}

.card-tags-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}
.tag {
  font-size: 22rpx;
  color: #7a6a9a;
  background: rgba(255,255,255,0.04);
  border: 1rpx solid rgba(255,255,255,0.07);
  padding: 6rpx 18rpx;
  border-radius: 20rpx;
}

/* 行星装饰 */
.card-planet-deco {
  position: absolute;
  bottom: -20rpx;
  right: -20rpx;
  width: 200rpx;
  height: 200rpx;
  z-index: 1;
  pointer-events: none;
}
.orbit-ring {
  position: absolute;
  border: 1rpx solid rgba(139,111,209,0.15);
  border-radius: 50%;
  top: 50%; left: 50%;
  transform: translate(-50%, -50%);
  animation: orbit-spin 12s linear infinite;
}
.ring-1 { width: 120rpx; height: 120rpx; }
.ring-2 { width: 180rpx; height: 180rpx; animation-duration: 20s; }
.ring-fast { animation-duration: 7s !important; }

.synastry-deco .orbit-ring { border-color: rgba(74,144,226,0.15); }
.transit-deco .orbit-ring { border-color: rgba(224,120,32,0.15); }

.planet-dot {
  position: absolute;
  width: 16rpx; height: 16rpx;
  border-radius: 50%;
  background: radial-gradient(circle, #c4a8f0, #8b6fd1);
  top: 50%; left: 50%;
  transform: translate(-50%, -50%);
  box-shadow: 0 0 12rpx rgba(139,111,209,0.5);
}
.synastry-deco .planet-dot { background: radial-gradient(circle, #80c0ff, #4a90e2); box-shadow: 0 0 12rpx rgba(74,144,226,0.5); }
.transit-deco .planet-dot { background: radial-gradient(circle, #ffcc80, #e07820); box-shadow: 0 0 12rpx rgba(224,120,32,0.5); }

.dot-a {
  top: 30%; left: 30%;
  width: 12rpx; height: 12rpx;
}
.dot-b {
  top: 65%; left: 65%;
  width: 10rpx; height: 10rpx;
  background: radial-gradient(circle, #ffb8d8, #e060a0) !important;
  box-shadow: 0 0 10rpx rgba(224,96,160,0.4) !important;
}

@keyframes orbit-spin {
  from { transform: translate(-50%, -50%) rotate(0deg); }
  to { transform: translate(-50%, -50%) rotate(360deg); }
}

/* 底部 */
.footer-note {
  text-align: center;
  padding: 32rpx 0 16rpx;
}
.footer-text {
  font-size: 22rpx;
  color: #3a2860;
  letter-spacing: 2rpx;
}
</style>

