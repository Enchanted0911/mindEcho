<script setup lang="ts">
import {onMounted, onUnmounted, ref} from 'vue'
import {checkNatalChart} from '../../api/astrology'
import {useUserStore} from '../../store/user'

const userStore = useUserStore()
const hasNatal = ref(false)
const isChecking = ref(true)

// ── 太阳系行星数据 ─────────────────────────────
interface Planet {
  id: string
  name: string
  symbol: string
  color: string
  size: number       // 行星显示半径 rpx
  orbitR: number     // 轨道半径（相对中心，rpx单位，0 = 太阳）
  speed: number      // 公转速度（度/秒）
  angle: number      // 当前角度（度）
  desc: string       // 描述
  sign?: string      // 星座（从后端数据获取）
  house?: string
  degree?: string
}

const PLANETS: Planet[] = [
  {
    id: 'sun',   name: '太阳', symbol: '☉', color: '#FFD060',
    size: 26,    orbitR: 0,    speed: 0,      angle: 0,
    desc: '生命力与自我核心'
  },
  {
    id: 'mercury', name: '水星', symbol: '☿', color: '#B0C8E0',
    size: 9,     orbitR: 80,   speed: 1.8,    angle: 30,
    desc: '思维、沟通与学习'
  },
  {
    id: 'venus', name: '金星', symbol: '♀', color: '#FFB8A8',
    size: 13,    orbitR: 120,  speed: 1.2,    angle: 80,
    desc: '爱、美与价值观'
  },
  {
    id: 'earth', name: '地球', symbol: '🌍', color: '#60B8E0',
    size: 13,    orbitR: 165,  speed: 0.9,    angle: 150,
    desc: '你所在的世界'
  },
  {
    id: 'moon',  name: '月亮', symbol: '☽', color: '#D8D0FF',
    size: 9,     orbitR: 190,  speed: 2.5,    angle: 60,
    desc: '情感、直觉与潜意识'
  },
  {
    id: 'mars',  name: '火星', symbol: '♂', color: '#FF8070',
    size: 11,    orbitR: 230,  speed: 0.55,   angle: 220,
    desc: '行动力、欲望与冲突'
  },
  {
    id: 'jupiter', name: '木星', symbol: '♃', color: '#E0C090',
    size: 20,    orbitR: 285,  speed: 0.22,   angle: 310,
    desc: '幸运、扩展与哲学'
  },
  {
    id: 'saturn', name: '土星', symbol: '♄', color: '#C8B890',
    size: 17,    orbitR: 335,  speed: 0.12,   angle: 180,
    desc: '规则、纪律与命运课题'
  },
]

// 动画状态
const planets = ref<Planet[]>(PLANETS.map(p => ({...p})))
let animTimer: any = null

function startAnimation() {
  animTimer = setInterval(() => {
    planets.value.forEach(p => {
      if (p.orbitR > 0) {
        p.angle = (p.angle + p.speed * 0.12) % 360
      }
    })
  }, 50)
}

function stopAnimation() {
  if (animTimer) clearInterval(animTimer)
}

// ── 点击行星弹出信息 ──────────────────────────
const selectedPlanet = ref<Planet | null>(null)
const showPlanetInfo = ref(false)

function onPlanetTap(planet: Planet) {
  selectedPlanet.value = planet
  showPlanetInfo.value = true
}

function closePlanetInfo() {
  showPlanetInfo.value = false
  selectedPlanet.value = null
}

// ── 四角功能弹层 ──────────────────────────────
type FeatureKey = 'natal' | 'synastry' | 'transit' | 'interpret'
const showFeature = ref<FeatureKey | null>(null)

function openFeature(key: FeatureKey) {
  showFeature.value = key
}

function closeFeature() {
  showFeature.value = null
}

const FEATURE_INFO = {
  natal: {
    title: '本命盘',
    subtitle: 'Natal Chart',
    icon: '☉',
    color: '#9b87d1',
    desc: '探索你的星盘结构、行星能量与人生底色，了解性格深层动力。',
    action: '开始分析',
    actionFn: () => { closeFeature(); uni.navigateTo({ url: '/pages/astrology/natal' }) }
  },
  synastry: {
    title: '和盘',
    subtitle: 'Synastry Chart',
    icon: '♀',
    color: '#60b8e0',
    desc: '与另一个人的星盘相遇，揭示关系动力、吸引力与挑战所在。',
    action: '开始分析',
    actionFn: () => { closeFeature(); uni.navigateTo({ url: '/pages/astrology/synastry' }) }
  },
  transit: {
    title: '流运',
    subtitle: 'Transit Reading',
    icon: '♄',
    color: '#ffb060',
    desc: '当前天空行星正与你的星盘共鸣，了解近期能量流动与变化机遇。',
    action: '开始分析',
    actionFn: () => { closeFeature(); uni.navigateTo({ url: '/pages/astrology/transit' }) }
  },
  interpret: {
    title: '解读',
    subtitle: 'AI Interpretation',
    icon: '✦',
    color: '#70c890',
    desc: '由 AI 深度解读你的星盘，涵盖性格、情感、天赋、成长课题。',
    action: '开始解读',
    actionFn: () => { closeFeature(); uni.navigateTo({ url: '/pages/astrology/natal' }) }
  }
}

onMounted(async () => {
  // ── 登录状态检查 ─────────────────────────────────
  // 小程序冷启动后 Pinia 内存中的 token 为空，先尝试从 Storage 恢复。
  // 若 Storage 中也没有 token，说明用户未登录，跳转到登录页。
  const loggedIn = userStore.restoreFromStorage()
  if (!loggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }

  // ── 检查本命盘是否已建立 ─────────────────────────
  try {
    hasNatal.value = await checkNatalChart()
  } catch (e) {
    // token 有效但 API 失败（如网络错误），不影响页面展示，hasNatal 默认 false
    hasNatal.value = false
  } finally {
    isChecking.value = false
  }
  startAnimation()
})

onUnmounted(() => {
  stopAnimation()
})

// 计算行星位置（画布中心 = 375, 375rpx）
function getPlanetPos(p: Planet) {
  const cx = 375, cy = 375
  if (p.orbitR === 0) return { x: cx, y: cy }
  const rad = (p.angle - 90) * Math.PI / 180
  return {
    x: cx + p.orbitR * Math.cos(rad),
    y: cy + p.orbitR * Math.sin(rad)
  }
}
</script>

<template>
  <view class="solar-page">

    <!-- ░░ 星空背景装饰粒子 ░░ -->
    <view class="star-bg">
      <view class="star-dot s1" />
      <view class="star-dot s2" />
      <view class="star-dot s3" />
      <view class="star-dot s4" />
      <view class="star-dot s5" />
      <view class="star-dot s6" />
      <view class="star-dot s7" />
      <view class="star-dot s8" />
      <view class="nebula-glow ng1" />
      <view class="nebula-glow ng2" />
    </view>

    <!-- ░░ 顶部标题栏 ░░ -->
    <view class="top-bar">
      <view class="top-bar-inner">
        <view class="top-badge">AI 星盘</view>
        <text class="top-title">星屿占星</text>
        <text class="top-sub">探索宇宙，认识自己</text>
      </view>
    </view>

    <!-- ░░ 太阳系画布 ░░ -->
    <view class="solar-canvas-wrap">
      <!-- 轨道圈 -->
      <view
        v-for="p in planets.filter(x => x.orbitR > 0)"
        :key="'orbit_' + p.id"
        class="orbit-ring"
        :style="{
          width: p.orbitR * 2 + 'rpx',
          height: p.orbitR * 2 + 'rpx',
          marginLeft: -(p.orbitR) + 'rpx',
          marginTop: -(p.orbitR) + 'rpx',
        }"
      />

      <!-- 行星 -->
      <view
        v-for="p in planets"
        :key="'planet_' + p.id"
        class="planet-node"
        :style="{
          width: p.size * 2 + 'rpx',
          height: p.size * 2 + 'rpx',
          left: getPlanetPos(p).x - p.size + 'rpx',
          top: getPlanetPos(p).y - p.size + 'rpx',
          background: p.id === 'sun'
            ? 'radial-gradient(circle at 38% 38%, #fff8d0, ' + p.color + ' 60%, #c07010)'
            : p.id === 'earth'
            ? 'radial-gradient(circle at 38% 38%, #a8dcf8, #3890c0 60%, #1a5070)'
            : 'radial-gradient(circle at 38% 38%, ' + p.color + 'ee, ' + p.color + '88)',
          boxShadow: p.id === 'sun'
            ? '0 0 28rpx ' + p.color + ', 0 0 60rpx ' + p.color + '66, 0 0 100rpx ' + p.color + '22'
            : '0 0 12rpx ' + p.color + '88, 0 0 24rpx ' + p.color + '33'
        }"
        @tap="onPlanetTap(p)"
      >
        <!-- 土星环 -->
        <view v-if="p.id === 'saturn'" class="saturn-ring" />
        <!-- 行星符号 -->
        <text v-if="p.id !== 'earth'" class="planet-label" :style="{ fontSize: Math.max(p.size * 0.9, 9) + 'rpx', color: p.id === 'sun' ? '#7a4a00' : 'rgba(255,255,255,0.9)' }">
          {{ p.symbol }}
        </text>
      </view>

      <!-- 四角功能按钮 -->
      <!-- 左上: 本命 -->
      <view class="corner-btn btn-tl" @tap="openFeature('natal')">
        <view class="corner-icon-wrap" style="background: rgba(155,135,209,0.12); border-color: rgba(155,135,209,0.4)">
          <text class="corner-icon" style="color:#c4b4f0">☉</text>
        </view>
        <text class="corner-label">本命</text>
      </view>

      <!-- 右上: 和盘 -->
      <view class="corner-btn btn-tr" @tap="openFeature('synastry')">
        <view class="corner-icon-wrap" style="background: rgba(96,184,224,0.12); border-color: rgba(96,184,224,0.4)">
          <text class="corner-icon" style="color:#80d0f4">♀</text>
        </view>
        <text class="corner-label">和盘</text>
      </view>

      <!-- 左下: 流运 -->
      <view class="corner-btn btn-bl" @tap="openFeature('transit')">
        <view class="corner-icon-wrap" style="background: rgba(255,176,96,0.12); border-color: rgba(255,176,96,0.4)">
          <text class="corner-icon" style="color:#ffc878">♄</text>
        </view>
        <text class="corner-label">流运</text>
      </view>

      <!-- 右下: 解读 -->
      <view class="corner-btn btn-br" @tap="openFeature('interpret')">
        <view class="corner-icon-wrap" style="background: rgba(112,200,144,0.12); border-color: rgba(112,200,144,0.4)">
          <text class="corner-icon" style="color:#90e0b4">✦</text>
        </view>
        <text class="corner-label">解读</text>
      </view>
    </view>

    <!-- ░░ 底部提示语 ░░ -->
    <view class="bottom-hint">
      <text class="hint-text">✦ 点击行星查看星位 · 点击角落开始解读 ✦</text>
    </view>

    <!-- ░░ 建立星盘提示（未建立本命盘时） ░░ -->
    <view v-if="!isChecking && !hasNatal" class="natal-prompt" @tap="openFeature('natal')">
      <view class="prompt-glow" />
      <text class="prompt-icon">🌟</text>
      <view class="prompt-content">
        <text class="prompt-title">建立你的星盘</text>
        <text class="prompt-desc">录入出生信息，解锁个性化占星</text>
      </view>
      <text class="prompt-arrow">›</text>
    </view>

    <!-- ══════════════════════════════════════
         行星信息弹窗
    ══════════════════════════════════════ -->
    <view v-if="showPlanetInfo && selectedPlanet" class="planet-popup-overlay" @tap="closePlanetInfo">
      <view class="planet-popup" @tap.stop>
        <view class="popup-drag-bar" />
        <view class="planet-popup-header">
          <view class="planet-popup-icon" :style="{ background: selectedPlanet.color + '15', borderColor: selectedPlanet.color + '50' }">
            <text class="planet-popup-symbol" :style="{ color: selectedPlanet.id === 'earth' ? '#3890c0' : selectedPlanet.color }">
              {{ selectedPlanet.id === 'earth' ? '🌍' : selectedPlanet.symbol }}
            </text>
          </view>
          <view class="planet-popup-meta">
            <text class="planet-popup-name">{{ selectedPlanet.name }}</text>
            <text class="planet-popup-desc">{{ selectedPlanet.desc }}</text>
          </view>
          <view class="popup-close" @tap="closePlanetInfo">✕</view>
        </view>

        <view v-if="selectedPlanet.sign" class="planet-pos-info">
          <view class="pos-item">
            <text class="pos-label">星座</text>
            <text class="pos-val">{{ selectedPlanet.sign }}</text>
          </view>
          <view v-if="selectedPlanet.house" class="pos-item">
            <text class="pos-label">宫位</text>
            <text class="pos-val">{{ selectedPlanet.house }}</text>
          </view>
          <view v-if="selectedPlanet.degree" class="pos-item">
            <text class="pos-label">度数</text>
            <text class="pos-val">{{ selectedPlanet.degree }}</text>
          </view>
        </view>
        <view v-else class="planet-no-data">
          <text class="no-data-text">建立本命盘后可查看你的{{ selectedPlanet.name }}星位</text>
          <view class="no-data-btn" @tap="() => { closePlanetInfo(); openFeature('natal') }">
            <text>去建立本命盘 →</text>
          </view>
        </view>
      </view>
    </view>

    <!-- ══════════════════════════════════════
         四角功能弹窗
    ══════════════════════════════════════ -->
    <view v-if="showFeature" class="feature-overlay" @tap="closeFeature">
      <view class="feature-popup" @tap.stop>
        <view class="popup-drag-bar" />
        <!-- 关闭 -->
        <view class="feature-close" @tap="closeFeature">✕</view>

        <template v-if="showFeature && FEATURE_INFO[showFeature]">
          <view class="feature-icon-wrap" :style="{ background: FEATURE_INFO[showFeature].color + '15', borderColor: FEATURE_INFO[showFeature].color + '40' }">
            <text class="feature-icon" :style="{ color: FEATURE_INFO[showFeature].color }">
              {{ FEATURE_INFO[showFeature].icon }}
            </text>
          </view>

          <text class="feature-title">{{ FEATURE_INFO[showFeature].title }}</text>
          <text class="feature-subtitle">{{ FEATURE_INFO[showFeature].subtitle }}</text>
          <text class="feature-desc">{{ FEATURE_INFO[showFeature].desc }}</text>

          <view class="feature-action-btn"
            :style="{ background: 'linear-gradient(135deg, ' + FEATURE_INFO[showFeature].color + 'bb, ' + FEATURE_INFO[showFeature].color + ')' }"
            @tap="FEATURE_INFO[showFeature].actionFn">
            <text class="feature-action-text">{{ FEATURE_INFO[showFeature].action }}</text>
          </view>
        </template>
      </view>
    </view>

  </view>
</template>

<style>
/* ═══════════════════════════════════════════
   太阳系星盘页 · 深色宇宙风格
═══════════════════════════════════════════ */

.solar-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #0a0a1a;
  position: relative;
  overflow: hidden;
}

/* ── 星空背景 ─────────────────────────────── */
.star-bg {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  pointer-events: none;
  z-index: 0;
}

.star-dot {
  position: absolute;
  border-radius: 50%;
  background: white;
  animation: star-twinkle 3s ease-in-out infinite;
}

.s1  { width: 3rpx; height: 3rpx; top: 8%;  left: 12%; opacity: 0.8; animation-delay: 0s; }
.s2  { width: 2rpx; height: 2rpx; top: 15%; left: 75%; opacity: 0.6; animation-delay: 0.5s; }
.s3  { width: 4rpx; height: 4rpx; top: 25%; left: 35%; opacity: 0.9; animation-delay: 1s; }
.s4  { width: 2rpx; height: 2rpx; top: 40%; left: 90%; opacity: 0.7; animation-delay: 1.5s; }
.s5  { width: 3rpx; height: 3rpx; top: 60%; left: 5%;  opacity: 0.8; animation-delay: 0.8s; }
.s6  { width: 2rpx; height: 2rpx; top: 70%; left: 55%; opacity: 0.6; animation-delay: 2s; }
.s7  { width: 4rpx; height: 4rpx; top: 85%; left: 80%; opacity: 0.9; animation-delay: 0.3s; }
.s8  { width: 3rpx; height: 3rpx; top: 90%; left: 25%; opacity: 0.7; animation-delay: 1.2s; }

.nebula-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(60rpx);
  opacity: 0.06;
}

.ng1 { width: 400rpx; height: 400rpx; background: #9b87d1; top: -100rpx; left: -100rpx; }
.ng2 { width: 300rpx; height: 300rpx; background: #60b8e0; bottom: -80rpx; right: -80rpx; }

@keyframes star-twinkle {
  0%, 100% { opacity: 0.8; transform: scale(1); }
  50%       { opacity: 0.2; transform: scale(0.5); }
}

/* ── 顶部标题栏 ─────────────────────────────── */
.top-bar {
  padding: 80rpx 32rpx 20rpx;
  background: rgba(10, 10, 26, 0.7);
  backdrop-filter: blur(20rpx);
  border-bottom: 1rpx solid rgba(155, 135, 209, 0.15);
  position: relative;
  z-index: 10;
}

.top-bar-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.top-badge {
  background: rgba(155, 135, 209, 0.12);
  border: 1rpx solid rgba(155, 135, 209, 0.35);
  border-radius: 30rpx;
  padding: 6rpx 22rpx;
  font-size: 22rpx;
  color: #9b87d1;
  letter-spacing: 2rpx;
}

.top-title {
  font-size: 40rpx;
  font-weight: 700;
  color: #f0eaff;
  letter-spacing: 3rpx;
  display: block;
  text-shadow: 0 0 30rpx rgba(155, 135, 209, 0.5);
}

.top-sub {
  font-size: 22rpx;
  color: rgba(155, 135, 209, 0.6);
  letter-spacing: 2rpx;
  display: block;
}

/* ── 太阳系画布 ─────────────────────────────── */
.solar-canvas-wrap {
  flex: 1;
  position: relative;
  overflow: hidden;
}

/* 轨道圈 */
.orbit-ring {
  position: absolute;
  border-radius: 50%;
  border: 1rpx solid rgba(155, 135, 209, 0.12);
  left: 375rpx;
  top: 375rpx;
  pointer-events: none;
}

/* 行星节点 */
.planet-node {
  position: absolute;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 5;
}

.planet-label {
  font-weight: bold;
  line-height: 1;
  z-index: 1;
}

/* 土星环效果 */
.saturn-ring {
  position: absolute;
  width: 200%;
  height: 40%;
  border: 2rpx solid rgba(200, 184, 144, 0.6);
  border-radius: 50%;
  left: -50%;
  top: 30%;
  transform: rotateX(70deg);
  pointer-events: none;
}

/* ── 四角功能按钮 ─────────────────────────────── */
.corner-btn {
  position: absolute;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  z-index: 20;
}

.btn-tl { top: 28rpx; left: 24rpx; }
.btn-tr { top: 28rpx; right: 24rpx; }
.btn-bl { bottom: 90rpx; left: 24rpx; }
.btn-br { bottom: 90rpx; right: 24rpx; }

.corner-icon-wrap {
  width: 80rpx;
  height: 80rpx;
  border-radius: 24rpx;
  border: 1.5rpx solid;
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(12rpx);
}

.corner-icon {
  font-size: 32rpx;
  font-weight: bold;
}

.corner-label {
  font-size: 22rpx;
  color: rgba(200, 190, 230, 0.7);
  font-weight: 500;
}

/* ── 底部提示 ─────────────────────────────── */
.bottom-hint {
  padding: 10rpx 0 16rpx;
  text-align: center;
  z-index: 10;
}

.hint-text {
  font-size: 22rpx;
  color: rgba(155, 135, 209, 0.45);
  letter-spacing: 1rpx;
}

/* ── 建立星盘提示条 ─────────────────────────────── */
.natal-prompt {
  position: absolute;
  bottom: 160rpx;
  left: 24rpx;
  right: 24rpx;
  background: rgba(20, 18, 40, 0.92);
  backdrop-filter: blur(20rpx);
  border: 1.5rpx solid rgba(155, 135, 209, 0.3);
  border-radius: 20rpx;
  padding: 20rpx 24rpx;
  display: flex;
  align-items: center;
  gap: 18rpx;
  z-index: 15;
  overflow: hidden;
}

.prompt-glow {
  position: absolute;
  left: -30rpx; top: -30rpx;
  width: 120rpx; height: 120rpx;
  background: radial-gradient(circle, rgba(155, 135, 209, 0.2), transparent 70%);
  pointer-events: none;
}

.prompt-icon { font-size: 38rpx; flex-shrink: 0; }
.prompt-content { flex: 1; }
.prompt-title { font-size: 28rpx; color: #e8e0ff; font-weight: 600; display: block; margin-bottom: 4rpx; }
.prompt-desc { font-size: 22rpx; color: rgba(155, 135, 209, 0.6); }
.prompt-arrow { font-size: 36rpx; color: rgba(155, 135, 209, 0.5); }

/* ══════════════════════════════════════
   行星信息弹窗
══════════════════════════════════════ */
.planet-popup-overlay {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 15, 0.65);
  backdrop-filter: blur(6rpx);
  display: flex;
  align-items: flex-end;
  z-index: 200;
}

.planet-popup {
  background: rgba(16, 14, 34, 0.97);
  backdrop-filter: blur(24rpx);
  border-top-left-radius: 36rpx;
  border-top-right-radius: 36rpx;
  padding: 8rpx 28rpx 60rpx;
  width: 100%;
  border-top: 1rpx solid rgba(155, 135, 209, 0.2);
  box-shadow: 0 -8rpx 48rpx rgba(155, 135, 209, 0.12);
}

.popup-drag-bar {
  width: 60rpx;
  height: 6rpx;
  background: rgba(155, 135, 209, 0.25);
  border-radius: 3rpx;
  margin: 12rpx auto 28rpx;
}

.planet-popup-header {
  display: flex;
  align-items: center;
  gap: 18rpx;
  margin-bottom: 28rpx;
}

.planet-popup-icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: 20rpx;
  border: 1.5rpx solid;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.planet-popup-symbol { font-size: 34rpx; }

.planet-popup-meta { flex: 1; }
.planet-popup-name { font-size: 30rpx; color: #e8e0ff; font-weight: 700; display: block; margin-bottom: 5rpx; }
.planet-popup-desc { font-size: 23rpx; color: rgba(155, 135, 209, 0.6); }

.popup-close {
  width: 48rpx; height: 48rpx;
  display: flex; align-items: center; justify-content: center;
  font-size: 28rpx; color: rgba(155, 135, 209, 0.4);
  background: rgba(155, 135, 209, 0.08);
  border-radius: 14rpx;
}

.planet-pos-info {
  display: flex;
  gap: 16rpx;
  flex-wrap: wrap;
}

.pos-item {
  flex: 1;
  min-width: 140rpx;
  background: rgba(30, 24, 60, 0.8);
  border: 1.5rpx solid rgba(155, 135, 209, 0.18);
  border-radius: 16rpx;
  padding: 18rpx 16rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
}

.pos-label { font-size: 22rpx; color: rgba(155, 135, 209, 0.55); }
.pos-val { font-size: 28rpx; color: #e8e0ff; font-weight: 600; }

.planet-no-data {
  text-align: center;
  padding: 8rpx 0;
}

.no-data-text { font-size: 25rpx; color: rgba(155, 135, 209, 0.5); display: block; margin-bottom: 20rpx; line-height: 1.6; }

.no-data-btn {
  display: inline-flex;
  background: rgba(155, 135, 209, 0.1);
  border: 1rpx solid rgba(155, 135, 209, 0.3);
  border-radius: 30rpx;
  padding: 12rpx 32rpx;
}

.no-data-btn text { font-size: 26rpx; color: #9b87d1; }

/* ══════════════════════════════════════
   四角功能弹窗
══════════════════════════════════════ */
.feature-overlay {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 15, 0.7);
  backdrop-filter: blur(8rpx);
  display: flex;
  align-items: flex-end;
  z-index: 200;
}

.feature-popup {
  background: rgba(16, 14, 34, 0.98);
  backdrop-filter: blur(28rpx);
  border-top-left-radius: 40rpx;
  border-top-right-radius: 40rpx;
  padding: 8rpx 32rpx 80rpx;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14rpx;
  border-top: 1rpx solid rgba(155, 135, 209, 0.2);
  box-shadow: 0 -8rpx 48rpx rgba(155, 135, 209, 0.12);
  position: relative;
}

.feature-close {
  position: absolute;
  top: 32rpx;
  right: 28rpx;
  width: 48rpx; height: 48rpx;
  display: flex; align-items: center; justify-content: center;
  font-size: 28rpx; color: rgba(155, 135, 209, 0.4);
  background: rgba(155, 135, 209, 0.08);
  border-radius: 14rpx;
}

.feature-icon-wrap {
  width: 96rpx;
  height: 96rpx;
  border-radius: 28rpx;
  border: 1.5rpx solid;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 6rpx;
  margin-top: 10rpx;
}

.feature-icon { font-size: 44rpx; font-weight: bold; }

.feature-title {
  font-size: 40rpx;
  font-weight: 700;
  color: #e8e0ff;
  display: block;
  text-align: center;
}

.feature-subtitle {
  font-size: 22rpx;
  color: rgba(155, 135, 209, 0.5);
  letter-spacing: 4rpx;
  display: block;
  text-align: center;
}

.feature-desc {
  font-size: 27rpx;
  color: rgba(200, 190, 230, 0.65);
  line-height: 1.75;
  text-align: center;
  display: block;
  margin: 8rpx 0 16rpx;
  padding: 0 12rpx;
}

.feature-action-btn {
  width: 100%;
  padding: 30rpx;
  border-radius: 20rpx;
  text-align: center;
  box-shadow: 0 6rpx 30rpx rgba(0,0,0,0.3);
}

.feature-action-text {
  font-size: 32rpx;
  color: white;
  font-weight: 600;
  letter-spacing: 2rpx;
}
</style>

