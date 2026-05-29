<script setup lang="ts">
import {reactive, ref} from 'vue'
import {getSynastryChart, interpretSynastry, type SynastryResponse} from '../../api/astrology'

const step = ref<'form' | 'loading' | 'result'>('form')
const loadingText = ref('正在解析双人星盘能量...')
const chartData = ref<SynastryResponse | null>(null)
const interpretation = ref('')
const isInterpreting = ref(false)
const selectedInterpretType = ref('love')
const activeTab = ref<'analysis' | 'themes' | 'interpret'>('analysis')
/** 版本计数器：每次发起新请求时自增，旧实例通过比较版本号安全退出，彻底解决竞态问题 */
let typingGeneration = 0

const YEARS = Array.from({ length: 80 }, (_, i) => 1950 + i)
const MONTHS = Array.from({ length: 12 }, (_, i) => i + 1)
const DAYS = Array.from({ length: 31 }, (_, i) => i + 1)
const HOURS = Array.from({ length: 24 }, (_, i) => i)
const YEAR_OPTIONS = YEARS.map(y => ({ label: y + '年', value: y }))
const MONTH_OPTIONS = MONTHS.map(m => ({ label: m + '月', value: m }))
const DAY_OPTIONS = DAYS.map(d => ({ label: d + '日', value: d }))
const HOUR_OPTIONS = HOURS.map(h => ({ label: h + '时', value: h }))

const selfForm = reactive({ year: 1998, month: 6, day: 15, hour: 8, city: '北京' })
const partnerForm = reactive({ year: 1997, month: 3, day: 22, hour: 10, city: '上海' })
const partnerName = ref('Ta')

function onSelfYearChange(e: any) { selfForm.year = YEAR_OPTIONS[e.detail.value].value }
function onSelfMonthChange(e: any) { selfForm.month = MONTH_OPTIONS[e.detail.value].value }
function onSelfDayChange(e: any) { selfForm.day = DAY_OPTIONS[e.detail.value].value }
function onSelfHourChange(e: any) { selfForm.hour = HOUR_OPTIONS[e.detail.value].value }
function onPartnerYearChange(e: any) { partnerForm.year = YEAR_OPTIONS[e.detail.value].value }
function onPartnerMonthChange(e: any) { partnerForm.month = MONTH_OPTIONS[e.detail.value].value }
function onPartnerDayChange(e: any) { partnerForm.day = DAY_OPTIONS[e.detail.value].value }
function onPartnerHourChange(e: any) { partnerForm.hour = HOUR_OPTIONS[e.detail.value].value }

const INTERPRET_TYPES = [
  { key: 'love', label: '爱情关系', icon: '💞' },
  { key: 'marriage', label: '婚姻契合', icon: '💍' },
  { key: 'soul', label: '灵魂连接', icon: '✨' },
  { key: 'emotion', label: '情绪共鸣', icon: '🌊' }
]

// 模拟分析数据
const MOCK_ANALYSIS = [
  { label: '吸引力', value: 82, color: '#e070a0', desc: '强烈的磁场吸引' },
  { label: '情绪匹配', value: 68, color: '#7080f0', desc: '情感波动相似' },
  { label: '冲突指数', value: 34, color: '#f09040', desc: '少量摩擦' },
  { label: '长期稳定', value: 75, color: '#50c878', desc: '基础稳固' }
]

const MOCK_THEMES = [
  { icon: '🔥', title: '强烈吸引', desc: '金星与火星的连接带来无法忽视的磁场感应，初次相遇时便有明确的化学反应。', type: 'positive' },
  { icon: '🌊', title: '情绪依赖', desc: '月亮与海王星的相位创造了深层情感联系，你们之间容易产生强烈的情感依附感。', type: 'neutral' },
  { icon: '⚡', title: '权力拉扯', desc: '火星与冥王星的四分相提示双方在某些议题上可能产生控制欲的冲突，需要有意识地协商。', type: 'challenge' }
]

async function calculateSynastry() {
  if (!selfForm.city || !partnerForm.city) {
    uni.showToast({ title: '请填写出生城市', icon: 'none' }); return
  }
  step.value = 'loading'
  const TEXTS = ['正在解析双人星盘能量...', '计算相位连接...', '正在连接宇宙能量...', '生成关系地图...']
  let idx = 0
  const timer = setInterval(() => { loadingText.value = TEXTS[++idx % TEXTS.length] }, 1500)
  try {
    const result = await getSynastryChart({
      selfBirthInfo: { year: selfForm.year, month: selfForm.month, day: selfForm.day, hour: selfForm.hour, minute: 0, city: selfForm.city },
      partnerBirthInfo: { year: partnerForm.year, month: partnerForm.month, day: partnerForm.day, hour: partnerForm.hour, minute: 0, city: partnerForm.city },
      partnerName: partnerName.value
    })
    chartData.value = result
  } catch {
    chartData.value = { relationshipModel: null, aspects: [], themes: [], chart: null }
  } finally {
    clearInterval(timer)
    step.value = 'result'
  }
}

async function getInterpretation() {
  // 自增版本号，令所有正在运行的旧实例在下次循环时感知到版本变化并安全退出
  const myGeneration = ++typingGeneration

  isInterpreting.value = true
  interpretation.value = ''
  activeTab.value = 'interpret'
  try {
    const result = await interpretSynastry({ chart: chartData.value?.chart, focus: 'compatibility', interpretType: selectedInterpretType.value, tone: 'gentle' })
    const text = result.interpretation
    for (let i = 0; i <= text.length; i++) {
      if (typingGeneration !== myGeneration) {
        // 有新实例启动，立即将文本设置为完整内容后退出，避免 UI 卡在半截
        interpretation.value = text
        return
      }
      interpretation.value = text.slice(0, i)
      await new Promise(r => setTimeout(r, 25))
    }
  } catch {
    const mock = `💞 你们之间有一种深刻的灵魂契约感。金星与月亮的六分相位意味着你们的情感表达方式天然和谐，彼此能够理解对方在关系中的需求。\n\n这份关系中有强烈的吸引力，但也要注意火星与土星的紧张相位——在某些具体事务的执行和推进上，双方可能会产生节奏不一致的摩擦。\n\n长期来看，你们关系中最稳固的基础来自于相似的价值观和人生方向感，这是一种可以共同成长的连接方式。🌙`
    for (let i = 0; i <= mock.length; i++) {
      if (typingGeneration !== myGeneration) {
        interpretation.value = mock
        return
      }
      interpretation.value = mock.slice(0, i)
      await new Promise(r => setTimeout(r, 20))
    }
  } finally {
    // 仅当前实例仍是最新版本时才清除加载状态，防止旧实例误关闭新实例的 loading
    if (typingGeneration === myGeneration) {
      isInterpreting.value = false
    }
  }
}

function goInterpret() {
  activeTab.value = 'interpret'
  if (!interpretation.value) getInterpretation()
}
</script>

<template>
  <view class="synastry-page">
    <view class="bg-gradient-s" />

    <!-- Loading -->
    <view v-if="step === 'loading'" class="loading-screen">
      <view class="loading-dual">
        <view class="dual-orb orb-a">
          <view class="orb-ring r1" /><view class="orb-ring r2" />
          <text class="orb-sym">♀</text>
        </view>
        <view class="dual-connect">
          <view class="connect-line" />
          <view class="connect-dot" />
        </view>
        <view class="dual-orb orb-b">
          <view class="orb-ring r1" /><view class="orb-ring r2" />
          <text class="orb-sym">♂</text>
        </view>
      </view>
      <text class="loading-text">{{ loadingText }}</text>
      <text class="loading-sub">正在感应两颗星球的相遇</text>
    </view>

    <!-- 表单 -->
    <scroll-view v-if="step === 'form'" class="page-scroll" scroll-y>
      <view class="form-header">
        <view class="form-icon-row">
          <view class="ficon-wrap ficon-a"><text class="ficon">♀</text></view>
          <text class="ficon-link">∞</text>
          <view class="ficon-wrap ficon-b"><text class="ficon">♂</text></view>
        </view>
        <text class="form-title">和盘分析</text>
        <text class="form-desc">两颗灵魂的星盘相遇，揭示关系的宇宙密码</text>
      </view>

      <!-- 自己信息 -->
      <view class="person-card self-card">
        <view class="person-label-row">
          <view class="person-dot dot-self" />
          <text class="person-label">我</text>
        </view>
        <view class="date-row">
          <picker mode="selector" :range="YEAR_OPTIONS" range-key="label"
            :value="YEARS.indexOf(selfForm.year)" @change="onSelfYearChange">
            <view class="picker-btn-s">{{ selfForm.year }}年</view>
          </picker>
          <picker mode="selector" :range="MONTH_OPTIONS" range-key="label"
            :value="selfForm.month - 1" @change="onSelfMonthChange">
            <view class="picker-btn-s">{{ selfForm.month }}月</view>
          </picker>
          <picker mode="selector" :range="DAY_OPTIONS" range-key="label"
            :value="selfForm.day - 1" @change="onSelfDayChange">
            <view class="picker-btn-s">{{ selfForm.day }}日</view>
          </picker>
          <picker mode="selector" :range="HOUR_OPTIONS" range-key="label"
            :value="selfForm.hour" @change="onSelfHourChange">
            <view class="picker-btn-s">{{ selfForm.hour }}时</view>
          </picker>
        </view>
        <view class="input-row">
          <text class="input-icon-s">📍</text>
          <input class="city-input" v-model="selfForm.city"
            placeholder="出生城市" placeholder-style="color:#4a3868" maxlength="20" />
        </view>
      </view>

      <!-- 连接符号 -->
      <view class="connector-row">
        <view class="connector-line-v" />
        <view class="connector-badge">
          <text class="connector-sym">∞</text>
        </view>
        <view class="connector-line-v" />
      </view>

      <!-- 对方信息 -->
      <view class="person-card partner-card">
        <view class="person-label-row">
          <view class="person-dot dot-partner" />
          <view class="partner-name-row">
            <input class="partner-name-input" v-model="partnerName"
              placeholder="Ta 的名字（可选）" placeholder-style="color:#4a3868" maxlength="10" />
          </view>
        </view>
        <view class="date-row">
          <picker mode="selector" :range="YEAR_OPTIONS" range-key="label"
            :value="YEARS.indexOf(partnerForm.year)" @change="onPartnerYearChange">
            <view class="picker-btn-s picker-partner">{{ partnerForm.year }}年</view>
          </picker>
          <picker mode="selector" :range="MONTH_OPTIONS" range-key="label"
            :value="partnerForm.month - 1" @change="onPartnerMonthChange">
            <view class="picker-btn-s picker-partner">{{ partnerForm.month }}月</view>
          </picker>
          <picker mode="selector" :range="DAY_OPTIONS" range-key="label"
            :value="partnerForm.day - 1" @change="onPartnerDayChange">
            <view class="picker-btn-s picker-partner">{{ partnerForm.day }}日</view>
          </picker>
          <picker mode="selector" :range="HOUR_OPTIONS" range-key="label"
            :value="partnerForm.hour" @change="onPartnerHourChange">
            <view class="picker-btn-s picker-partner">{{ partnerForm.hour }}时</view>
          </picker>
        </view>
        <view class="input-row">
          <text class="input-icon-s">📍</text>
          <input class="city-input city-partner" v-model="partnerForm.city"
            placeholder="出生城市" placeholder-style="color:#4a3868" maxlength="20" />
        </view>
      </view>

      <view class="submit-btn" @click="calculateSynastry">
        <text class="submit-text">✦ 解析双人星盘</text>
      </view>
      <view style="height: 120rpx" />
    </scroll-view>

    <!-- 结果页 -->
    <view v-if="step === 'result'" class="result-page">
      <!-- 双人摘要 -->
      <view class="dual-summary">
        <view class="ds-person">
          <view class="ds-avatar ds-a">
            <text class="ds-sym">♀</text>
          </view>
          <text class="ds-name">我</text>
        </view>
        <view class="ds-center">
          <view class="ds-score-wrap">
            <text class="ds-score">82</text>
            <text class="ds-score-unit">%</text>
          </view>
          <text class="ds-compat">契合度</text>
        </view>
        <view class="ds-person">
          <view class="ds-avatar ds-b">
            <text class="ds-sym">♂</text>
          </view>
          <text class="ds-name">{{ partnerName || 'Ta' }}</text>
        </view>
      </view>

      <!-- Tabs -->
      <view class="tabs-bar">
        <view class="tab-item" :class="{ active: activeTab === 'analysis' }" @click="activeTab = 'analysis'"><text>关系分析</text></view>
        <view class="tab-item" :class="{ active: activeTab === 'themes' }" @click="activeTab = 'themes'"><text>关系主题</text></view>
        <view class="tab-item" :class="{ active: activeTab === 'interpret' }" @click="activeTab = 'interpret'"><text>AI 解读</text></view>
      </view>

      <scroll-view class="tab-content" scroll-y>
        <!-- 关系分析 -->
        <view v-if="activeTab === 'analysis'">
          <view v-for="item in MOCK_ANALYSIS" :key="item.label" class="analysis-row">
            <view class="analysis-head">
              <text class="analysis-label">{{ item.label }}</text>
              <text class="analysis-val" :style="{ color: item.color }">{{ item.value }}%</text>
            </view>
            <view class="analysis-track">
              <view class="analysis-fill" :style="{ width: item.value + '%', background: item.color }" />
            </view>
            <text class="analysis-desc">{{ item.desc }}</text>
          </view>
        </view>

        <!-- 关系主题 -->
        <view v-if="activeTab === 'themes'">
          <view v-for="theme in MOCK_THEMES" :key="theme.title" class="theme-card"
            :class="'theme-' + theme.type">
            <view class="theme-icon-wrap">
              <text class="theme-icon">{{ theme.icon }}</text>
            </view>
            <view class="theme-content">
              <text class="theme-title">{{ theme.title }}</text>
              <text class="theme-desc">{{ theme.desc }}</text>
            </view>
          </view>
        </view>

        <!-- AI 解读 -->
        <view v-if="activeTab === 'interpret'" class="interpret-panel">
          <view class="interpret-type-list">
            <view v-for="t in INTERPRET_TYPES" :key="t.key"
              class="itype-chip" :class="{ 'itype-active': selectedInterpretType === t.key }"
              @click="selectedInterpretType = t.key">
              <text class="itype-icon">{{ t.icon }}</text>
              <text class="itype-label">{{ t.label }}</text>
            </view>
          </view>
          <view v-if="!interpretation && !isInterpreting" class="interpret-trigger" @click="getInterpretation">
            <view class="trigger-glow-s" />
            <text class="trigger-icon">∞</text>
            <text class="trigger-text">开始解读你们的关系</text>
            <text class="trigger-sub">AI 分析双人星盘能量</text>
          </view>
          <view v-if="interpretation || isInterpreting" class="interpret-content">
            <view class="interpret-header">
              <text class="interpret-focus-label">{{ INTERPRET_TYPES.find(t => t.key === selectedInterpretType)?.label }}</text>
              <view v-if="isInterpreting" class="typing-indicator">
                <view class="typing-dot" /><view class="typing-dot" /><view class="typing-dot" />
              </view>
            </view>
            <text class="interpret-text">{{ interpretation }}</text>
          </view>
          <view v-if="interpretation && !isInterpreting" class="re-interpret-btn" @click="getInterpretation">
            <text>↻ 换个角度解读</text>
          </view>
        </view>
        <view style="height: 160rpx" />
      </scroll-view>

      <view class="result-actions">
        <view class="action-btn-outline" @click="step = 'form'"><text>重新计算</text></view>
        <view class="action-btn-primary-s" @click="goInterpret"><text>∞ AI 解读关系</text></view>
      </view>
    </view>
  </view>
</template>

<style>
.synastry-page {
  min-height: 100vh;
  background: #080e1a;
  position: relative;
}
.bg-gradient-s {
  position: fixed;
  top: 0; left: 0; right: 0;
  height: 400rpx;
  background: radial-gradient(ellipse at 50% 0%, rgba(74,144,226,0.12) 0%, transparent 70%);
  pointer-events: none;
  z-index: 0;
}

/* Loading */
.loading-screen {
  height: 100vh;
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  gap: 48rpx;
}
.loading-dual { display: flex; align-items: center; gap: 20rpx; }
.dual-orb {
  width: 120rpx; height: 120rpx;
  position: relative;
  display: flex; align-items: center; justify-content: center;
}
.orb-a .orb-ring { border-color: rgba(74,144,226,0.3); }
.orb-b .orb-ring { border-color: rgba(224,96,160,0.3); }
.orb-ring {
  position: absolute; border-radius: 50%;
  border: 1rpx solid;
  animation: spin 4s linear infinite;
}
.r1 { width: 70rpx; height: 70rpx; }
.r2 { width: 110rpx; height: 110rpx; animation-duration: 7s; border-style: dashed; }
@keyframes spin { to { transform: rotate(360deg); } }
.orb-sym { font-size: 40rpx; color: #c8d8ff; z-index: 1; }
.dual-connect { display: flex; flex-direction: column; align-items: center; gap: 8rpx; }
.connect-line { width: 60rpx; height: 1rpx; background: linear-gradient(90deg, rgba(74,144,226,0.4), rgba(224,96,160,0.4)); }
.connect-dot { width: 12rpx; height: 12rpx; border-radius: 50%; background: #c4a8f0; }
.loading-text { font-size: 32rpx; color: #b0c4ff; letter-spacing: 2rpx; }
.loading-sub { font-size: 24rpx; color: #4a5878; }

/* 表单 */
.page-scroll { position: relative; z-index: 1; padding: 0 28rpx; }
.form-header {
  padding-top: 60rpx; padding-bottom: 36rpx;
  display: flex; flex-direction: column; align-items: center; gap: 16rpx;
}
.form-icon-row { display: flex; align-items: center; gap: 20rpx; margin-bottom: 8rpx; }
.ficon-wrap {
  width: 80rpx; height: 80rpx; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
}
.ficon-a { background: rgba(74,144,226,0.15); border: 1rpx solid rgba(74,144,226,0.3); }
.ficon-b { background: rgba(224,96,160,0.15); border: 1rpx solid rgba(224,96,160,0.3); }
.ficon { font-size: 40rpx; color: #c8d8ff; }
.ficon-link { font-size: 44rpx; color: #5a6898; }
.form-title { font-size: 48rpx; color: #e8f0ff; font-weight: bold; display: block; }
.form-desc { font-size: 26rpx; color: #6a7898; text-align: center; line-height: 1.6; display: block; }

.person-card {
  background: rgba(255,255,255,0.03);
  border-radius: 24rpx;
  padding: 32rpx;
  margin-bottom: 8rpx;
}
.self-card { border: 1rpx solid rgba(74,144,226,0.2); }
.partner-card { border: 1rpx solid rgba(224,96,160,0.2); }

.person-label-row { display: flex; align-items: center; gap: 14rpx; margin-bottom: 20rpx; }
.person-dot { width: 16rpx; height: 16rpx; border-radius: 50%; }
.dot-self { background: #4a90e2; box-shadow: 0 0 8rpx rgba(74,144,226,0.5); }
.dot-partner { background: #e060a0; box-shadow: 0 0 8rpx rgba(224,96,160,0.5); }
.person-label { font-size: 28rpx; color: #c8d8ff; font-weight: 600; }
.partner-name-row { flex: 1; }
.partner-name-input { font-size: 28rpx; color: #f0d8ff; width: 100%; }

.date-row { display: flex; gap: 8rpx; margin-bottom: 16rpx; }
.picker-btn-s {
  background: rgba(74,144,226,0.08);
  border: 1rpx solid rgba(74,144,226,0.2);
  border-radius: 12rpx;
  padding: 14rpx 8rpx;
  text-align: center;
  font-size: 24rpx;
  color: #a8c8f0;
}
.picker-partner {
  background: rgba(224,96,160,0.08) !important;
  border-color: rgba(224,96,160,0.2) !important;
  color: #f0a8d0 !important;
}
.input-row {
  display: flex; align-items: center; gap: 12rpx;
  background: rgba(74,144,226,0.06);
  border: 1rpx solid rgba(74,144,226,0.15);
  border-radius: 12rpx;
  padding: 14rpx 16rpx;
}
.input-icon-s { font-size: 28rpx; }
.city-input { flex: 1; font-size: 28rpx; color: #a8c8f0; }
.city-partner { color: #f0a8d0 !important; }

.connector-row {
  display: flex; flex-direction: column;
  align-items: center; gap: 0;
  margin: 0;
  padding: 12rpx 0;
}
.connector-line-v { width: 1rpx; height: 28rpx; background: rgba(139,111,209,0.2); }
.connector-badge {
  width: 60rpx; height: 60rpx;
  border-radius: 50%;
  background: rgba(139,111,209,0.12);
  border: 1rpx solid rgba(139,111,209,0.25);
  display: flex; align-items: center; justify-content: center;
}
.connector-sym { font-size: 28rpx; color: #a888e8; }

.submit-btn {
  background: linear-gradient(135deg, #4a90e2, #7060d0);
  border-radius: 20rpx; padding: 32rpx; text-align: center;
  box-shadow: 0 8rpx 32rpx rgba(74,144,226,0.25);
  margin-top: 24rpx;
}
.submit-text { font-size: 34rpx; color: white; font-weight: 600; letter-spacing: 4rpx; }

/* 结果页 */
.result-page { height: 100vh; display: flex; flex-direction: column; position: relative; z-index: 1; }

.dual-summary {
  background: linear-gradient(180deg, #0d1530 0%, #080e1a 100%);
  padding: 32rpx;
  display: flex; align-items: center; justify-content: space-between;
  flex-shrink: 0;
}
.ds-person { display: flex; flex-direction: column; align-items: center; gap: 12rpx; }
.ds-avatar {
  width: 90rpx; height: 90rpx; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
}
.ds-a { background: rgba(74,144,226,0.2); border: 2rpx solid rgba(74,144,226,0.4); }
.ds-b { background: rgba(224,96,160,0.2); border: 2rpx solid rgba(224,96,160,0.4); }
.ds-sym { font-size: 44rpx; color: #c8d8ff; }
.ds-name { font-size: 24rpx; color: #7a8aaa; }
.ds-center { display: flex; flex-direction: column; align-items: center; gap: 8rpx; }
.ds-score-wrap { display: flex; align-items: baseline; gap: 4rpx; }
.ds-score { font-size: 72rpx; color: #e8f0ff; font-weight: bold; line-height: 1; }
.ds-score-unit { font-size: 32rpx; color: #a8b8d8; }
.ds-compat { font-size: 24rpx; color: #5a6888; }

.tabs-bar {
  display: flex;
  background: rgba(255,255,255,0.02);
  border-bottom: 1rpx solid rgba(255,255,255,0.05);
  flex-shrink: 0;
}
.tab-item {
  flex: 1; padding: 24rpx; text-align: center;
  font-size: 28rpx; color: #4a5878; position: relative;
}
.tab-item.active { color: #90b8ff; }
.tab-item.active::after {
  content: ''; position: absolute; bottom: 0; left: 20%; right: 20%;
  height: 3rpx;
  background: linear-gradient(90deg, #4a90e2, #90b8ff);
  border-radius: 2rpx;
}
.tab-content { flex: 1; padding: 24rpx 28rpx; }

/* 关系分析 */
.analysis-row { margin-bottom: 28rpx; }
.analysis-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10rpx; }
.analysis-label { font-size: 28rpx; color: #c8d8ff; font-weight: 600; }
.analysis-val { font-size: 32rpx; font-weight: bold; }
.analysis-track { height: 10rpx; background: rgba(255,255,255,0.06); border-radius: 5rpx; overflow: hidden; margin-bottom: 8rpx; }
.analysis-fill { height: 100%; border-radius: 5rpx; opacity: 0.8; }
.analysis-desc { font-size: 24rpx; color: #5a6888; }

/* 关系主题 */
.theme-card {
  display: flex; gap: 20rpx; align-items: flex-start;
  padding: 24rpx; border-radius: 20rpx; margin-bottom: 16rpx;
  border: 1rpx solid;
}
.theme-positive { background: rgba(80,200,120,0.05); border-color: rgba(80,200,120,0.15); }
.theme-neutral { background: rgba(74,144,226,0.05); border-color: rgba(74,144,226,0.15); }
.theme-challenge { background: rgba(255,120,60,0.05); border-color: rgba(255,120,60,0.15); }
.theme-icon-wrap { flex-shrink: 0; font-size: 40rpx; }
.theme-icon { font-size: 40rpx; }
.theme-title { font-size: 30rpx; color: #c8d8ff; font-weight: 600; display: block; margin-bottom: 8rpx; }
.theme-desc { font-size: 24rpx; color: #6a7898; line-height: 1.7; }

/* AI 解读 */
.interpret-panel { padding: 8rpx 0; }
.interpret-type-list { display: flex; gap: 12rpx; flex-wrap: wrap; margin-bottom: 24rpx; }
.itype-chip {
  display: flex; align-items: center; gap: 8rpx;
  padding: 14rpx 22rpx; border-radius: 30rpx;
  background: rgba(255,255,255,0.04); border: 1rpx solid rgba(255,255,255,0.08);
}
.itype-active { background: rgba(74,144,226,0.2); border-color: rgba(74,144,226,0.4); }
.itype-icon { font-size: 26rpx; }
.itype-label { font-size: 26rpx; color: #7a8aaa; }
.itype-active .itype-label { color: #90b8ff; }

.interpret-trigger {
  position: relative;
  background: linear-gradient(135deg, rgba(74,144,226,0.12), rgba(139,111,209,0.08));
  border: 1rpx solid rgba(74,144,226,0.25);
  border-radius: 24rpx; padding: 48rpx 32rpx; text-align: center; overflow: hidden;
}
.trigger-glow-s {
  position: absolute; top: -40rpx; left: 50%;
  width: 200rpx; height: 200rpx; border-radius: 50%;
  background: radial-gradient(circle, rgba(74,144,226,0.15), transparent);
  transform: translateX(-50%);
}
.trigger-icon { font-size: 48rpx; color: #90b8ff; display: block; margin-bottom: 16rpx; }
.trigger-text { font-size: 36rpx; color: #d8e8ff; font-weight: 600; display: block; margin-bottom: 10rpx; }
.trigger-sub { font-size: 24rpx; color: #5a6888; display: block; }

.interpret-content {
  background: rgba(255,255,255,0.02); border: 1rpx solid rgba(74,144,226,0.12);
  border-radius: 20rpx; padding: 28rpx 32rpx;
}
.interpret-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20rpx; }
.interpret-focus-label { font-size: 26rpx; color: #90b8ff; font-weight: 600; }

.typing-indicator { display: flex; gap: 8rpx; align-items: center; }
.typing-dot {
  width: 10rpx; height: 10rpx; border-radius: 50%;
  background: #4a90e2; animation: bounce 1.2s ease-in-out infinite;
}
.typing-dot:nth-child(2) { animation-delay: 0.2s; }
.typing-dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes bounce {
  0%, 80%, 100% { transform: translateY(0); opacity: 0.4; }
  40% { transform: translateY(-8rpx); opacity: 1; }
}
.interpret-text { font-size: 28rpx; color: #b0c4ff; line-height: 2; white-space: pre-wrap; }

.re-interpret-btn {
  text-align: center; padding: 24rpx; color: #4a90e2; font-size: 26rpx;
  border: 1rpx solid rgba(74,144,226,0.2); border-radius: 16rpx; margin-top: 16rpx;
}

.result-actions {
  padding: 20rpx 28rpx;
  padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
  display: flex; gap: 16rpx;
  background: rgba(8,14,26,0.95);
  border-top: 1rpx solid rgba(255,255,255,0.06);
  flex-shrink: 0;
}
.action-btn-outline {
  flex: 1; padding: 26rpx; text-align: center;
  border: 1rpx solid rgba(74,144,226,0.3); border-radius: 16rpx;
  font-size: 30rpx; color: #4a90e2;
}
.action-btn-primary-s {
  flex: 2; padding: 26rpx; text-align: center;
  background: linear-gradient(135deg, #4a90e2, #7060d0);
  border-radius: 16rpx; font-size: 30rpx; color: white; font-weight: 600;
}
</style>

