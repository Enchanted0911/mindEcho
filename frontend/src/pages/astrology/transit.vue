<script setup lang="ts">
import {computed, reactive, ref} from 'vue'
import {getTransitChart, interpretTransit, type TransitResponse} from '../../api/astrology'

const step = ref<'form' | 'loading' | 'result'>('form')
const loadingText = ref('正在解析当前星体轨迹...')
const chartData = ref<TransitResponse | null>(null)
const interpretation = ref('')
const isInterpreting = ref(false)
const selectedInterpretType = ref('today_emotion')
const activeTab = ref<'today' | 'events' | 'interpret'>('today')
/**
 * 打字机版本计数器：每次发起新的解读时递增
 * 每个解读实例记住自己的 generation，循环时检查是否仍是最新版本
 */
let typingGeneration = 0

// 今日日期
const today = new Date()
const todayStr = computed(() => `${today.getFullYear()}年${today.getMonth() + 1}月${today.getDate()}日`)

const YEARS = Array.from({ length: 80 }, (_, i) => 1950 + i)
const MONTHS = Array.from({ length: 12 }, (_, i) => i + 1)
const DAYS = Array.from({ length: 31 }, (_, i) => i + 1)
const HOURS = Array.from({ length: 24 }, (_, i) => i)
const YEAR_OPTIONS = YEARS.map(y => ({ label: y + '年', value: y }))
const MONTH_OPTIONS = MONTHS.map(m => ({ label: m + '月', value: m }))
const DAY_OPTIONS = DAYS.map(d => ({ label: d + '日', value: d }))
const HOUR_OPTIONS = HOURS.map(h => ({ label: h + '时', value: h }))

const form = reactive({ year: 1998, month: 6, day: 15, hour: 8, city: '北京' })

function onYearChange(e: any) { form.year = YEAR_OPTIONS[e.detail.value].value }
function onMonthChange(e: any) { form.month = MONTH_OPTIONS[e.detail.value].value }
function onDayChange(e: any) { form.day = DAY_OPTIONS[e.detail.value].value }
function onHourChange(e: any) { form.hour = HOUR_OPTIONS[e.detail.value].value }

const INTERPRET_TYPES = [
  { key: 'today_emotion', label: '今日情绪', icon: '💭' },
  { key: 'relationship', label: '近期关系', icon: '💫' },
  { key: 'stress', label: '压力感知', icon: '⚡' },
  { key: 'growth', label: '成长方向', icon: '🌱' }
]

// 今日重点行星 mock
const TODAY_HIGHLIGHTS = [
  { symbol: '☿', name: '水星', aspect: '逆行', impact: '沟通与出行可能受阻，适合反思而非推进', energy: 'caution' },
  { symbol: '♃', name: '木星', aspect: '拱本命太阳', impact: '带来扩展性机遇，直觉准确，适合冒险', energy: 'positive' },
  { symbol: '☽', name: '月亮', aspect: '位于天蝎座', impact: '情绪深沉而敏感，容易洞察事物的深层含义', energy: 'deep' }
]

const TODAY_ENERGY = {
  overall: 72,
  emotion: 58,
  action: 80,
  social: 65
}

// 流运事件 mock
const TRANSIT_EVENTS = [
  {
    planets: '♄ 土星', aspect: '合', natal: '☽ 月亮',
    date: '近 3 周', duration: '持续约 6 周',
    desc: '你可能感受到情感上的压力与责任感加重，这是一段需要沉淀内心的时期。',
    intensity: 'strong', type: 'challenge'
  },
  {
    planets: '♃ 木星', aspect: '拱', natal: '☉ 太阳',
    date: '近 2 个月', duration: '持续约 3 个月',
    desc: '能量充沛，有利于事业推进和自我展示，机会值得主动把握。',
    intensity: 'strong', type: 'positive'
  },
  {
    planets: '♀ 金星', aspect: '六分', natal: '♂ 火星',
    date: '近 2 周', duration: '持续约 2 周',
    desc: '人际关系和谐，情感表达流畅，恋爱运势良好的时期。',
    intensity: 'medium', type: 'positive'
  },
  {
    planets: '♂ 火星', aspect: '四分', natal: '♄ 土星',
    date: '近 1 个月', duration: '持续约 1 个月',
    desc: '执行力遇到阻碍，需要避免冲动和对抗，耐心比速度更重要。',
    intensity: 'medium', type: 'challenge'
  }
]

async function calculateTransit() {
  if (!form.city) {
    uni.showToast({ title: '请填写出生城市', icon: 'none' }); return
  }
  step.value = 'loading'
  const TEXTS = ['正在解析当前星体轨迹...', '连接今日宇宙能量...', '计算流运相位...', '生成能量地图...']
  let idx = 0
  const timer = setInterval(() => { loadingText.value = TEXTS[++idx % TEXTS.length] }, 1400)
  try {
    const result = await getTransitChart({
      birthInfo: { year: form.year, month: form.month, day: form.day, hour: form.hour, minute: 0, city: form.city }
    })
    chartData.value = result
  } catch {
    chartData.value = { events: [], summary: null, chart: null }
  } finally {
    clearInterval(timer)
    step.value = 'result'
  }
}

async function getInterpretation() {
  // 递增版本号，使所有之前的打字机实例在各自的循环中检测到已过期并退出
  const myGeneration = ++typingGeneration

  isInterpreting.value = true
  interpretation.value = ''
  activeTab.value = 'interpret'
  try {
    const result = await interpretTransit({ chart: chartData.value?.chart, focus: 'current', interpretType: selectedInterpretType.value, tone: 'gentle' })
    const text = result.interpretation
    for (let i = 0; i <= text.length; i++) {
      if (typingGeneration !== myGeneration) {
        interpretation.value = text
        return
      }
      await new Promise(r => setTimeout(r, 25))
      if (typingGeneration !== myGeneration) {
        interpretation.value = text
        return
      }
      interpretation.value = text.slice(0, i)
    }
  } catch {
    const mock = `💭 今天水星逆行的影响让你的思维可能转向内省，这不是推进新计划的最佳时机，但非常适合回顾与整理。\n\n木星正在与你的本命太阳形成柔和的拱相，这是一段内在力量被加强的时期，你的直觉和判断力比平时更加准确，可以信任自己的感受。\n\n今日情绪状态：较为内敛，但内心有稳定的支撑感。适合进行深度思考、写日记或与亲近的人进行真实的对话。避免重要决策的公开展示，但私下的沟通和整理效果会很好。🌙`
    for (let i = 0; i <= mock.length; i++) {
      if (typingGeneration !== myGeneration) {
        interpretation.value = mock
        return
      }
      await new Promise(r => setTimeout(r, 20))
      if (typingGeneration !== myGeneration) {
        interpretation.value = mock
        return
      }
      interpretation.value = mock.slice(0, i)
    }
  } finally {
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
  <view class="transit-page">
    <view class="bg-gradient-t" />

    <!-- Loading -->
    <view v-if="step === 'loading'" class="loading-screen">
      <view class="loading-orbit">
        <view class="orbit-sun">
          <view class="sun-ring r1" /><view class="sun-ring r2" /><view class="sun-ring r3" />
          <text class="sun-sym">♄</text>
        </view>
      </view>
      <text class="loading-text">{{ loadingText }}</text>
      <text class="loading-sub">感应今日宇宙能量流动</text>
    </view>

    <!-- 表单 -->
    <scroll-view v-if="step === 'form'" class="page-scroll" scroll-y>
      <view class="form-header">
        <view class="form-icon-wrap-t">
          <text class="form-icon-t">♄</text>
        </view>
        <text class="form-title">流运解读</text>
        <text class="form-subtitle">{{ todayStr }}</text>
        <text class="form-desc">当前天空的行星如何与你的星盘共鸣</text>
      </view>

      <view class="form-card">
        <text class="field-label">你的出生信息</text>
        <view class="date-row">
          <picker mode="selector" :range="YEAR_OPTIONS" range-key="label"
            :value="YEARS.indexOf(form.year)" @change="onYearChange">
            <view class="picker-btn-t">{{ form.year }}年</view>
          </picker>
          <picker mode="selector" :range="MONTH_OPTIONS" range-key="label"
            :value="form.month - 1" @change="onMonthChange">
            <view class="picker-btn-t">{{ form.month }}月</view>
          </picker>
          <picker mode="selector" :range="DAY_OPTIONS" range-key="label"
            :value="form.day - 1" @change="onDayChange">
            <view class="picker-btn-t">{{ form.day }}日</view>
          </picker>
          <picker mode="selector" :range="HOUR_OPTIONS" range-key="label"
            :value="form.hour" @change="onHourChange">
            <view class="picker-btn-t">{{ form.hour }}时</view>
          </picker>
        </view>
        <view class="input-wrap-t">
          <text class="input-icon-t">📍</text>
          <input class="city-input-t" v-model="form.city"
            placeholder="出生城市" placeholder-style="color:#4a3828" maxlength="20" />
        </view>
      </view>

      <view class="submit-btn-t" @click="calculateTransit">
        <text class="submit-text">♄ 感应今日流运</text>
      </view>
      <view style="height: 120rpx" />
    </scroll-view>

    <!-- 结果页 -->
    <view v-if="step === 'result'" class="result-page">
      <!-- 今日日期头 -->
      <view class="today-header">
        <view class="today-badge">今日</view>
        <text class="today-date">{{ todayStr }}</text>
        <view class="today-energy-badge">
          <text class="te-label">综合能量</text>
          <text class="te-val">{{ TODAY_ENERGY.overall }}%</text>
        </view>
      </view>

      <!-- Tabs -->
      <view class="tabs-bar">
        <view class="tab-item" :class="{ active: activeTab === 'today' }" @click="activeTab = 'today'"><text>今日流运</text></view>
        <view class="tab-item" :class="{ active: activeTab === 'events' }" @click="activeTab = 'events'"><text>流运事件</text></view>
        <view class="tab-item" :class="{ active: activeTab === 'interpret' }" @click="activeTab = 'interpret'"><text>AI 解读</text></view>
      </view>

      <scroll-view class="tab-content" scroll-y>
        <!-- 今日流运 -->
        <view v-if="activeTab === 'today'">
          <!-- 能量仪表盘 -->
          <text class="section-sub-title">能量分布</text>
          <view class="energy-grid">
            <view v-for="(val, key) in TODAY_ENERGY" :key="key" class="energy-cell">
              <text class="energy-key">{{ key === 'overall' ? '综合' : key === 'emotion' ? '情感' : key === 'action' ? '行动' : '社交' }}</text>
              <view class="energy-ring-wrap">
                <view class="energy-ring" :style="{ background: `conic-gradient(#f09040 ${val * 3.6}deg, rgba(255,255,255,0.05) 0deg)` }">
                  <view class="energy-ring-inner">
                    <text class="energy-pct">{{ val }}</text>
                  </view>
                </view>
              </view>
            </view>
          </view>

          <!-- 重点行星 -->
          <text class="section-sub-title" style="margin-top:32rpx">当前重点行星</text>
          <view v-for="h in TODAY_HIGHLIGHTS" :key="h.name" class="highlight-card"
            :class="'hc-' + h.energy">
            <view class="hc-symbol-wrap">
              <text class="hc-symbol">{{ h.symbol }}</text>
            </view>
            <view class="hc-info">
              <view class="hc-title-row">
                <text class="hc-name">{{ h.name }}</text>
                <view class="hc-aspect-badge" :class="'hab-' + h.energy">
                  <text class="hc-aspect">{{ h.aspect }}</text>
                </view>
              </view>
              <text class="hc-impact">{{ h.impact }}</text>
            </view>
          </view>
        </view>

        <!-- 流运事件 -->
        <view v-if="activeTab === 'events'">
          <view v-for="ev in TRANSIT_EVENTS" :key="ev.planets + ev.natal" class="event-card"
            :class="'ec-' + ev.type">
            <view class="event-head">
              <view class="event-planets-row">
                <text class="event-planet">{{ ev.planets }}</text>
                <view class="event-aspect-badge" :class="'eab-' + ev.type">
                  <text class="event-aspect">{{ ev.aspect }}</text>
                </view>
                <text class="event-planet">{{ ev.natal }}</text>
              </view>
              <view class="event-intensity" :class="'ei-' + ev.intensity">
                <text class="ei-text">{{ ev.intensity === 'strong' ? '强' : '中' }}</text>
              </view>
            </view>
            <view class="event-meta">
              <text class="event-date">📅 {{ ev.date }}</text>
              <text class="event-duration">⏱ {{ ev.duration }}</text>
            </view>
            <text class="event-desc">{{ ev.desc }}</text>
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
            <view class="trigger-glow-t" />
            <text class="trigger-icon">♄</text>
            <text class="trigger-text">感应今日流运能量</text>
            <text class="trigger-sub">AI 分析当前宇宙影响</text>
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
        <view class="action-btn-outline-t" @click="step = 'form'"><text>重新计算</text></view>
        <view class="action-btn-primary-t" @click="goInterpret"><text>♄ AI 解读流运</text></view>
      </view>
    </view>
  </view>
</template>

<style>
.transit-page {
  min-height: 100vh;
  background: #0e0a08;
  position: relative;
}
.bg-gradient-t {
  position: fixed; top: 0; left: 0; right: 0; height: 400rpx;
  background: radial-gradient(ellipse at 50% 0%, rgba(224,120,32,0.1) 0%, transparent 70%);
  pointer-events: none; z-index: 0;
}

/* Loading */
.loading-screen {
  height: 100vh; display: flex; flex-direction: column;
  align-items: center; justify-content: center; gap: 48rpx;
}
.loading-orbit { position: relative; width: 200rpx; height: 200rpx; display: flex; align-items: center; justify-content: center; }
.orbit-sun { position: relative; display: flex; align-items: center; justify-content: center; width: 100%; height: 100%; }
.sun-ring {
  position: absolute; border-radius: 50%;
  border: 1rpx solid rgba(224,120,32,0.3);
  animation: spin 4s linear infinite;
}
.r1 { width: 80rpx; height: 80rpx; }
.r2 { width: 140rpx; height: 140rpx; animation-duration: 8s; border-style: dashed; }
.r3 { width: 200rpx; height: 200rpx; animation-duration: 14s; opacity: 0.4; }
@keyframes spin { to { transform: rotate(360deg); } }
.sun-sym { font-size: 60rpx; color: #f0a860; z-index: 1; }
.loading-text { font-size: 32rpx; color: #f0a860; letter-spacing: 2rpx; }
.loading-sub { font-size: 24rpx; color: #604830; }

/* 表单 */
.page-scroll { position: relative; z-index: 1; padding: 0 28rpx; }
.form-header {
  padding-top: 60rpx; padding-bottom: 36rpx;
  display: flex; flex-direction: column; align-items: center; gap: 12rpx;
}
.form-icon-wrap-t {
  width: 100rpx; height: 100rpx; border-radius: 30rpx;
  background: rgba(224,120,32,0.15); border: 1rpx solid rgba(224,120,32,0.3);
  display: flex; align-items: center; justify-content: center; margin-bottom: 8rpx;
}
.form-icon-t { font-size: 52rpx; color: #f0a860; }
.form-title { font-size: 48rpx; color: #fff0e8; font-weight: bold; display: block; }
.form-subtitle { font-size: 26rpx; color: #f0a860; display: block; }
.form-desc { font-size: 26rpx; color: #7a6050; text-align: center; line-height: 1.6; display: block; }

.form-card {
  background: rgba(255,255,255,0.03); border: 1rpx solid rgba(224,120,32,0.15);
  border-radius: 24rpx; padding: 36rpx; margin-bottom: 20rpx;
}
.field-label { font-size: 24rpx; color: #7a6050; display: block; margin-bottom: 16rpx; }

.date-row { display: flex; gap: 8rpx; margin-bottom: 20rpx; }
.picker-btn-t {
  background: rgba(224,120,32,0.08); border: 1rpx solid rgba(224,120,32,0.2);
  border-radius: 12rpx; padding: 14rpx 8rpx; text-align: center;
  font-size: 24rpx; color: #f0a860;
}
.input-wrap-t {
  display: flex; align-items: center; gap: 12rpx;
  background: rgba(224,120,32,0.06); border: 1rpx solid rgba(224,120,32,0.15);
  border-radius: 12rpx; padding: 14rpx 16rpx;
}
.input-icon-t { font-size: 28rpx; }
.city-input-t { flex: 1; font-size: 28rpx; color: #f0a860; }

.submit-btn-t {
  background: linear-gradient(135deg, #e07820, #b04010);
  border-radius: 20rpx; padding: 32rpx; text-align: center; margin-top: 8rpx;
  box-shadow: 0 8rpx 32rpx rgba(224,120,32,0.25);
}
.submit-text { font-size: 34rpx; color: white; font-weight: 600; letter-spacing: 4rpx; }

/* 结果页 */
.result-page { height: 100vh; display: flex; flex-direction: column; position: relative; z-index: 1; }

.today-header {
  background: linear-gradient(180deg, #1a100a 0%, #0e0a08 100%);
  padding: 24rpx 32rpx;
  display: flex; align-items: center; gap: 20rpx; flex-shrink: 0;
}
.today-badge {
  background: rgba(224,120,32,0.2); border: 1rpx solid rgba(224,120,32,0.4);
  border-radius: 16rpx; padding: 8rpx 20rpx;
  font-size: 24rpx; color: #f0a860;
}
.today-date { flex: 1; font-size: 28rpx; color: #c8a090; }
.today-energy-badge { display: flex; flex-direction: column; align-items: flex-end; gap: 4rpx; }
.te-label { font-size: 20rpx; color: #7a6050; }
.te-val { font-size: 28rpx; color: #f0a860; font-weight: 600; }

.tabs-bar {
  display: flex; background: rgba(255,255,255,0.02);
  border-bottom: 1rpx solid rgba(255,255,255,0.05); flex-shrink: 0;
}
.tab-item {
  flex: 1; padding: 24rpx; text-align: center;
  font-size: 28rpx; color: #604830; position: relative;
}
.tab-item.active { color: #f0a860; }
.tab-item.active::after {
  content: ''; position: absolute; bottom: 0; left: 20%; right: 20%;
  height: 3rpx; background: linear-gradient(90deg, #e07820, #f0a860); border-radius: 2rpx;
}
.tab-content { flex: 1; padding: 24rpx 28rpx; }

/* 能量仪表 */
.section-sub-title { font-size: 24rpx; color: #7a6050; display: block; margin-bottom: 16rpx; }
.energy-grid { display: flex; gap: 16rpx; flex-wrap: wrap; margin-bottom: 8rpx; }
.energy-cell { flex: 1; min-width: 130rpx; display: flex; flex-direction: column; align-items: center; gap: 12rpx; }
.energy-key { font-size: 24rpx; color: #a07860; }
.energy-ring-wrap { position: relative; width: 100rpx; height: 100rpx; }
.energy-ring {
  width: 100rpx; height: 100rpx; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
}
.energy-ring-inner {
  width: 72rpx; height: 72rpx; border-radius: 50%;
  background: #0e0a08;
  display: flex; align-items: center; justify-content: center;
}
.energy-pct { font-size: 24rpx; color: #f0a860; font-weight: 600; }

/* 重点行星 */
.highlight-card {
  display: flex; gap: 20rpx; align-items: flex-start;
  padding: 24rpx; border-radius: 20rpx; margin-bottom: 16rpx; border: 1rpx solid;
}
.hc-positive { background: rgba(80,200,120,0.04); border-color: rgba(80,200,120,0.12); }
.hc-caution { background: rgba(255,160,60,0.04); border-color: rgba(255,160,60,0.12); }
.hc-deep { background: rgba(139,111,209,0.04); border-color: rgba(139,111,209,0.12); }
.hc-symbol-wrap {
  width: 70rpx; height: 70rpx; border-radius: 50%;
  background: rgba(224,120,32,0.12); border: 1rpx solid rgba(224,120,32,0.2);
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.hc-symbol { font-size: 34rpx; color: #f0a860; }
.hc-info { flex: 1; }
.hc-title-row { display: flex; align-items: center; gap: 12rpx; margin-bottom: 8rpx; }
.hc-name { font-size: 30rpx; color: #f0e8d8; font-weight: 600; }
.hc-aspect-badge {
  padding: 4rpx 14rpx; border-radius: 20rpx; font-size: 22rpx;
}
.hab-positive { background: rgba(80,200,120,0.15); color: #50c878; }
.hab-caution { background: rgba(255,160,60,0.15); color: #ffa040; }
.hab-deep { background: rgba(139,111,209,0.15); color: #c4a8f0; }
.hc-aspect { font-size: 22rpx; }
.hc-impact { font-size: 24rpx; color: #8a7060; line-height: 1.7; }

/* 流运事件 */
.event-card {
  padding: 24rpx; border-radius: 20rpx; margin-bottom: 16rpx; border: 1rpx solid;
}
.ec-positive { background: rgba(80,200,120,0.04); border-color: rgba(80,200,120,0.12); }
.ec-challenge { background: rgba(255,100,60,0.04); border-color: rgba(255,100,60,0.12); }
.event-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12rpx; }
.event-planets-row { display: flex; align-items: center; gap: 10rpx; }
.event-planet { font-size: 28rpx; color: #f0e8d8; font-weight: 600; }
.event-aspect-badge { padding: 6rpx 16rpx; border-radius: 20rpx; }
.eab-positive { background: rgba(80,200,120,0.15); }
.eab-challenge { background: rgba(255,100,60,0.12); }
.event-aspect { font-size: 22rpx; color: #a0a080; }
.event-intensity { width: 40rpx; height: 40rpx; border-radius: 50%; display: flex; align-items: center; justify-content: center; }
.ei-strong { background: rgba(255,100,60,0.2); }
.ei-medium { background: rgba(255,200,60,0.15); }
.ei-text { font-size: 20rpx; color: #f0c060; }
.event-meta { display: flex; gap: 24rpx; margin-bottom: 12rpx; }
.event-date, .event-duration { font-size: 22rpx; color: #7a6050; }
.event-desc { font-size: 26rpx; color: #8a7060; line-height: 1.75; }

/* AI 解读 */
.interpret-panel { padding: 8rpx 0; }
.interpret-type-list { display: flex; gap: 12rpx; flex-wrap: wrap; margin-bottom: 24rpx; }
.itype-chip {
  display: flex; align-items: center; gap: 8rpx;
  padding: 14rpx 22rpx; border-radius: 30rpx;
  background: rgba(255,255,255,0.04); border: 1rpx solid rgba(255,255,255,0.08);
}
.itype-active { background: rgba(224,120,32,0.18); border-color: rgba(224,120,32,0.35); }
.itype-icon { font-size: 26rpx; }
.itype-label { font-size: 26rpx; color: #7a6050; }
.itype-active .itype-label { color: #f0a860; }

.interpret-trigger {
  position: relative;
  background: linear-gradient(135deg, rgba(224,120,32,0.1), rgba(139,80,20,0.06));
  border: 1rpx solid rgba(224,120,32,0.22); border-radius: 24rpx;
  padding: 48rpx 32rpx; text-align: center; overflow: hidden;
}
.trigger-glow-t {
  position: absolute; top: -40rpx; left: 50%;
  width: 200rpx; height: 200rpx; border-radius: 50%;
  background: radial-gradient(circle, rgba(224,120,32,0.12), transparent);
  transform: translateX(-50%);
}
.trigger-icon { font-size: 48rpx; color: #f0a860; display: block; margin-bottom: 16rpx; }
.trigger-text { font-size: 36rpx; color: #fff0e8; font-weight: 600; display: block; margin-bottom: 10rpx; }
.trigger-sub { font-size: 24rpx; color: #7a6050; display: block; }

.interpret-content {
  background: rgba(255,255,255,0.02); border: 1rpx solid rgba(224,120,32,0.1);
  border-radius: 20rpx; padding: 28rpx 32rpx;
}
.interpret-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20rpx; }
.interpret-focus-label { font-size: 26rpx; color: #f0a860; font-weight: 600; }
.typing-indicator { display: flex; gap: 8rpx; align-items: center; }
.typing-dot {
  width: 10rpx; height: 10rpx; border-radius: 50%;
  background: #e07820; animation: bounce 1.2s ease-in-out infinite;
}
.typing-dot:nth-child(2) { animation-delay: 0.2s; }
.typing-dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes bounce {
  0%, 80%, 100% { transform: translateY(0); opacity: 0.4; }
  40% { transform: translateY(-8rpx); opacity: 1; }
}
.interpret-text { font-size: 28rpx; color: #f0c8a0; line-height: 2; white-space: pre-wrap; }
.re-interpret-btn {
  text-align: center; padding: 24rpx; color: #e07820; font-size: 26rpx;
  border: 1rpx solid rgba(224,120,32,0.2); border-radius: 16rpx; margin-top: 16rpx;
}

.result-actions {
  padding: 20rpx 28rpx;
  padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
  display: flex; gap: 16rpx;
  background: rgba(14,10,8,0.95);
  border-top: 1rpx solid rgba(255,255,255,0.06); flex-shrink: 0;
}
.action-btn-outline-t {
  flex: 1; padding: 26rpx; text-align: center;
  border: 1rpx solid rgba(224,120,32,0.3); border-radius: 16rpx;
  font-size: 30rpx; color: #e07820;
}
.action-btn-primary-t {
  flex: 2; padding: 26rpx; text-align: center;
  background: linear-gradient(135deg, #e07820, #b04010);
  border-radius: 16rpx; font-size: 30rpx; color: white; font-weight: 600;
}
</style>

