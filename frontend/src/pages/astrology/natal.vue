<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue'
import {getNatalChart, interpretNatal, type NatalChartResponse, saveBirthInfoToProfile} from '../../api/astrology'
import {useUserStore} from '../../store/user'

const userStore = useUserStore()

// ── 状态 ────────────────────────────────────────────────
const step = ref<'form' | 'loading' | 'result'>('form')
const loadingText = ref('正在解析星体轨迹...')
const chartData = ref<NatalChartResponse | null>(null)
const interpretation = ref('')
const isInterpreting = ref(false)
const selectedFocus = ref('personality')
const activeTab = ref<'planets' | 'aspects' | 'interpret'>('planets')
/**
 * 打字机版本计数器：每次发起新的解读时递增
 */
let typingGeneration = 0

// ── 出生信息表单 ─────────────────────────────────────────
const form = reactive({
  year: new Date().getFullYear() - 25,
  month: 1,
  day: 1,
  hour: 8,
  minute: 0,
  city: '',
  lat: null as number | null,
  lng: null as number | null,
})

// 城市搜索状态
const citySearchKeyword = ref('')
const citySearchResults = ref<Array<{name: string, lat: number, lng: number, address?: string}>>([])
const showCitySuggestions = ref(false)
const isSearchingCity = ref(false)

// ── 从用户档案自动填充出生信息 ─────────────────────────────
onMounted(() => {
  const info = userStore.userInfo
  if (info?.birthCity) {
    citySearchKeyword.value = info.birthCity
    form.city = info.birthCity
    form.lat = info.birthLat ?? null
    form.lng = info.birthLng ?? null
  }
  if (info?.birthTime) {
    // 解析 "yyyy-MM-dd HH:mm"
    try {
      const [datePart, timePart] = info.birthTime.split(' ')
      const [y, m, d] = datePart.split('-').map(Number)
      const [h, min] = timePart.split(':').map(Number)
      if (y && m && d) {
        form.year = y
        form.month = m
        form.day = d
      }
      if (!isNaN(h)) form.hour = h
      if (!isNaN(min)) form.minute = min
    } catch {
      // 解析失败保持默认值
    }
  }
})

// 解读焦点选项
const FOCUS_OPTIONS = [
  { key: 'personality', label: '性格人格', icon: '✨' },
  { key: 'emotion', label: '情感模式', icon: '💫' },
  { key: 'career', label: '天赋事业', icon: '⚡' },
  { key: 'growth', label: '成长课题', icon: '🌱' },
  { key: 'shadow', label: '阴影人格', icon: '🌑' }
]

// 模拟行星数据（真实数据从 chartData 解析）
const MOCK_PLANETS = [
  { symbol: '☉', name: '太阳', sign: '狮子座', house: '7宫', color: '#FFD700', strength: 85 },
  { symbol: '☽', name: '月亮', sign: '双鱼座', house: '2宫', color: '#C8C8FF', strength: 72 },
  { symbol: '☿', name: '水星', sign: '处女座', house: '8宫', color: '#90EE90', strength: 68 },
  { symbol: '♀', name: '金星', sign: '天蝎座', house: '10宫', color: '#FFB6C1', strength: 78 },
  { symbol: '♂', name: '火星', sign: '射手座', house: '11宫', color: '#FF6B6B', strength: 62 },
  { symbol: '♃', name: '木星', sign: '天秤座', house: '9宫', color: '#DEB887', strength: 55 },
  { symbol: '♄', name: '土星', sign: '摩羯座', house: '12宫', color: '#8B9DC3', strength: 48 },
]

const MOCK_ASPECTS = [
  { p1: '☉ 太阳', aspect: '△ 三分', p2: '☽ 月亮', harmony: 'positive' },
  { p1: '☽ 月亮', aspect: '□ 四分', p2: '♄ 土星', harmony: 'challenge' },
  { p1: '♀ 金星', aspect: '☌ 合', p2: '♂ 火星', harmony: 'neutral' },
  { p1: '☿ 水星', aspect: '⚹ 六分', p2: '♃ 木星', harmony: 'positive' },
]

// ── 表单日期选项 ─────────────────────────────────────────
const YEARS = Array.from({ length: 80 }, (_, i) => 1950 + i)
const MONTHS = Array.from({ length: 12 }, (_, i) => i + 1)
const DAYS = Array.from({ length: 31 }, (_, i) => i + 1)
const HOURS = Array.from({ length: 24 }, (_, i) => i)
const MINUTES = [0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55]

const YEAR_OPTIONS = YEARS.map(y => ({ label: y + '年', value: y }))
const MONTH_OPTIONS = MONTHS.map(m => ({ label: m + '月', value: m }))
const DAY_OPTIONS = DAYS.map(d => ({ label: d + '日', value: d }))
const HOUR_OPTIONS = HOURS.map(h => ({ label: h + '时', value: h }))
const MINUTE_OPTIONS = MINUTES.map(m => ({ label: String(m).padStart(2, '0') + '分', value: m }))

function onYearChange(e: any) { form.year = Number(YEAR_OPTIONS[e.detail.value].value) }
function onMonthChange(e: any) { form.month = Number(MONTH_OPTIONS[e.detail.value].value) }
function onDayChange(e: any) { form.day = Number(DAY_OPTIONS[e.detail.value].value) }
function onHourChange(e: any) { form.hour = Number(HOUR_OPTIONS[e.detail.value].value) }
function onMinuteChange(e: any) { form.minute = Number(MINUTE_OPTIONS[e.detail.value].value) }

// ── 城市搜索逻辑 ──────────────────────────────────────────

/** 内置主要城市数据（离线可用） */
const MAJOR_CITIES = [
  { name: '北京', lat: 39.9042, lng: 116.4074, address: '北京市' },
  { name: '上海', lat: 31.2304, lng: 121.4737, address: '上海市' },
  { name: '广州', lat: 23.1291, lng: 113.2644, address: '广东省广州市' },
  { name: '深圳', lat: 22.5431, lng: 114.0579, address: '广东省深圳市' },
  { name: '杭州', lat: 30.2741, lng: 120.1551, address: '浙江省杭州市' },
  { name: '成都', lat: 30.5728, lng: 104.0668, address: '四川省成都市' },
  { name: '重庆', lat: 29.5630, lng: 106.5516, address: '重庆市' },
  { name: '武汉', lat: 30.5928, lng: 114.3055, address: '湖北省武汉市' },
  { name: '西安', lat: 34.3416, lng: 108.9398, address: '陕西省西安市' },
  { name: '南京', lat: 32.0603, lng: 118.7969, address: '江苏省南京市' },
  { name: '天津', lat: 39.3434, lng: 117.3616, address: '天津市' },
  { name: '苏州', lat: 31.2989, lng: 120.5853, address: '江苏省苏州市' },
  { name: '郑州', lat: 34.7473, lng: 113.6249, address: '河南省郑州市' },
  { name: '长沙', lat: 28.2278, lng: 112.9388, address: '湖南省长沙市' },
  { name: '沈阳', lat: 41.8057, lng: 123.4315, address: '辽宁省沈阳市' },
  { name: '青岛', lat: 36.0671, lng: 120.3826, address: '山东省青岛市' },
  { name: '济南', lat: 36.6512, lng: 117.1201, address: '山东省济南市' },
  { name: '大连', lat: 38.9140, lng: 121.6147, address: '辽宁省大连市' },
  { name: '厦门', lat: 24.4798, lng: 118.0894, address: '福建省厦门市' },
  { name: '福州', lat: 26.0745, lng: 119.2965, address: '福建省福州市' },
  { name: '宁波', lat: 29.8683, lng: 121.5440, address: '浙江省宁波市' },
  { name: '无锡', lat: 31.4912, lng: 120.3119, address: '江苏省无锡市' },
  { name: '合肥', lat: 31.8206, lng: 117.2272, address: '安徽省合肥市' },
  { name: '昆明', lat: 25.0453, lng: 102.7097, address: '云南省昆明市' },
  { name: '哈尔滨', lat: 45.8038, lng: 126.5349, address: '黑龙江省哈尔滨市' },
  { name: '长春', lat: 43.8171, lng: 125.3235, address: '吉林省长春市' },
  { name: '南昌', lat: 28.6820, lng: 115.8582, address: '江西省南昌市' },
  { name: '贵阳', lat: 26.6470, lng: 106.6302, address: '贵州省贵阳市' },
  { name: '南宁', lat: 22.8170, lng: 108.3665, address: '广西壮族自治区南宁市' },
  { name: '呼和浩特', lat: 40.8414, lng: 111.7519, address: '内蒙古自治区呼和浩特市' },
  { name: '乌鲁木齐', lat: 43.8256, lng: 87.6168, address: '新疆维吾尔自治区乌鲁木齐市' },
  { name: '拉萨', lat: 29.6500, lng: 91.1000, address: '西藏自治区拉萨市' },
  { name: '银川', lat: 38.4872, lng: 106.2309, address: '宁夏回族自治区银川市' },
  { name: '西宁', lat: 36.6177, lng: 101.7782, address: '青海省西宁市' },
  { name: '兰州', lat: 36.0611, lng: 103.8343, address: '甘肃省兰州市' },
  { name: '太原', lat: 37.8706, lng: 112.5489, address: '山西省太原市' },
  { name: '石家庄', lat: 38.0428, lng: 114.5149, address: '河北省石家庄市' },
  { name: '海口', lat: 20.0440, lng: 110.1991, address: '海南省海口市' },
]

let searchTimer: any = null

function onCityInput(e: any) {
  const kw = e.detail.value as string
  citySearchKeyword.value = kw

  if (!kw.trim()) {
    showCitySuggestions.value = false
    citySearchResults.value = []
    form.city = ''
    form.lat = null
    form.lng = null
    return
  }

  // 防抖搜索
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    searchCities(kw.trim())
  }, 300)
}

function searchCities(keyword: string) {
  // 先从内置城市中模糊匹配
  const lower = keyword.toLowerCase()
  const matched = MAJOR_CITIES.filter(c =>
    c.name.includes(keyword) || c.address?.includes(keyword)
  ).slice(0, 6)

  if (matched.length > 0) {
    citySearchResults.value = matched
    showCitySuggestions.value = true
  } else {
    citySearchResults.value = []
    showCitySuggestions.value = false
  }
}

function selectCity(city: {name: string, lat: number, lng: number, address?: string}) {
  form.city = city.name
  form.lat = city.lat
  form.lng = city.lng
  citySearchKeyword.value = city.name
  showCitySuggestions.value = false
}

/** 调用微信地图选点 */
function chooseLocationOnMap() {
  uni.chooseLocation({
    success: (res) => {
      const cityName = res.name || res.address || ''
      form.city = cityName
      form.lat = res.latitude
      form.lng = res.longitude
      citySearchKeyword.value = cityName
      showCitySuggestions.value = false
    },
    fail: (err) => {
      // 用户取消或无权限
      if (err.errMsg && !err.errMsg.includes('cancel')) {
        uni.showToast({ title: '无法打开地图，请手动输入城市', icon: 'none' })
      }
    }
  })
}

function closeSuggestions() {
  showCitySuggestions.value = false
}

// ── 构造出生时间字符串 ────────────────────────────────────
function getBirthTimeStr(): string {
  const m = String(form.month).padStart(2, '0')
  const d = String(form.day).padStart(2, '0')
  const h = String(form.hour).padStart(2, '0')
  const min = String(form.minute).padStart(2, '0')
  return `${form.year}-${m}-${d} ${h}:${min}`
}

// ── 提交计算 ─────────────────────────────────────────────
async function calculateChart() {
  if (!form.city.trim()) {
    uni.showToast({ title: '请输入或选择出生城市', icon: 'none' })
    return
  }

  step.value = 'loading'
  const LOADING_TEXTS = [
    '正在解析星体轨迹...',
    '正在连接宇宙能量...',
    '计算行星精确位置...',
    '生成你的星盘地图...',
  ]
  let textIdx = 0
  const textTimer = setInterval(() => {
    textIdx = (textIdx + 1) % LOADING_TEXTS.length
    loadingText.value = LOADING_TEXTS[textIdx]
  }, 1500)

  try {
    const result = await getNatalChart({
      birthInfo: {
        year: form.year, month: form.month, day: form.day,
        hour: form.hour, minute: form.minute, city: form.city,
        latitude: form.lat ?? undefined,
        longitude: form.lng ?? undefined,
      },
      saveToProfile: true
    })
    chartData.value = result

    // 成功后保存出生信息到用户档案
    await saveBirthProfile()

    step.value = 'result'
  } catch (e) {
    // 使用 mock 数据展示（开发阶段 Python 服务未启动时）
    // 仍然保存出生信息
    try { await saveBirthProfile() } catch (_) {}
    chartData.value = { chart: null, summary: null, savedToProfile: false }
    step.value = 'result'
  } finally {
    clearInterval(textTimer)
  }
}

/** 保存出生信息到后端 + userStore */
async function saveBirthProfile() {
  const birthTime = getBirthTimeStr()
  try {
    const updatedInfo = await saveBirthInfoToProfile({
      birthCity: form.city,
      birthLat: form.lat,
      birthLng: form.lng,
      birthTime,
    })
    // 同步到 userStore 本地缓存
    userStore.updateBirthInfo(form.city, form.lat, form.lng, birthTime)
  } catch (e) {
    // 保存失败不影响星盘展示，仅本地更新
    userStore.updateBirthInfo(form.city, form.lat, form.lng, birthTime)
  }
}

// ── AI 解读 ──────────────────────────────────────────────
async function getInterpretation() {
  if (!chartData.value) return

  const myGeneration = ++typingGeneration

  isInterpreting.value = true
  interpretation.value = ''
  activeTab.value = 'interpret'

  const TYPING_DELAY = 30

  try {
    const result = await interpretNatal({
      chart: chartData.value.chart,
      focus: selectedFocus.value,
      tone: 'gentle'
    })
    const text = result.interpretation
    for (let i = 0; i <= text.length; i++) {
      if (typingGeneration !== myGeneration) {
        interpretation.value = text
        return
      }
      await new Promise(r => setTimeout(r, TYPING_DELAY))
      if (typingGeneration !== myGeneration) {
        interpretation.value = text
        return
      }
      interpretation.value = text.slice(0, i)
    }
  } catch (e) {
    const mockText = `✨ 你的太阳位于狮子座，赋予你天然的光芒与表达欲望，你渴望被看见、被认可，同时也有着深沉的创造力。\n\n月亮落在双鱼座的你，内心世界如海洋般深邃而敏感，情绪细腻，容易与他人的感受产生共鸣。这份敏感既是礼物，也需要学会为自己设立边界。\n\n太阳与月亮形成柔和的三分相位，意味着你的意识与潜意识之间流动顺畅，自我认知相对清晰，内心的光与柔可以和谐共存。\n\n最需要关注的成长课题在于：学会在展示自我的同时，也给自己静默的空间。🌙`
    for (let i = 0; i <= mockText.length; i++) {
      if (typingGeneration !== myGeneration) {
        interpretation.value = mockText
        return
      }
      await new Promise(r => setTimeout(r, 20))
      if (typingGeneration !== myGeneration) {
        interpretation.value = mockText
        return
      }
      interpretation.value = mockText.slice(0, i)
    }
  } finally {
    if (typingGeneration === myGeneration) {
      isInterpreting.value = false
    }
  }
}

function backToForm() {
  step.value = 'form'
  chartData.value = null
  interpretation.value = ''
}

function goInterpret() {
  activeTab.value = 'interpret'
  if (!interpretation.value) {
    getInterpretation()
  }
}
</script>

<template>
  <view class="natal-page" @click="closeSuggestions">
    <!-- 背景 -->
    <view class="bg-gradient" />

    <!-- Loading -->
    <view v-if="step === 'loading'" class="loading-screen">
      <view class="loading-orb">
        <view class="orb-ring r1" /><view class="orb-ring r2" /><view class="orb-ring r3" />
        <text class="orb-symbol">☉</text>
      </view>
      <text class="loading-text">{{ loadingText }}</text>
      <text class="loading-sub">正在绘制你的宇宙地图</text>
    </view>

    <!-- 表单 -->
    <scroll-view v-if="step === 'form'" class="page-scroll" scroll-y>
      <view class="form-header">
        <view class="form-icon-wrap">
          <text class="form-icon">☉</text>
        </view>
        <text class="form-title">本命盘</text>
        <text class="form-desc">输入你的出生信息，探索属于你的星盘宇宙</text>
      </view>

      <view class="form-card">
        <text class="field-label">出生日期</text>
        <view class="date-row">
          <picker class="date-picker" mode="selector" :range="YEAR_OPTIONS" range-key="label"
            :value="YEARS.indexOf(form.year)" @change="onYearChange">
            <view class="picker-btn">{{ form.year }}年</view>
          </picker>
          <picker class="date-picker" mode="selector" :range="MONTH_OPTIONS" range-key="label"
            :value="form.month - 1" @change="onMonthChange">
            <view class="picker-btn">{{ form.month }}月</view>
          </picker>
          <picker class="date-picker" mode="selector" :range="DAY_OPTIONS" range-key="label"
            :value="form.day - 1" @change="onDayChange">
            <view class="picker-btn">{{ form.day }}日</view>
          </picker>
        </view>

        <text class="field-label" style="margin-top:28rpx">出生时间</text>
        <view class="time-row">
          <picker class="time-picker" mode="selector" :range="HOUR_OPTIONS" range-key="label"
            :value="form.hour" @change="onHourChange">
            <view class="picker-btn">{{ form.hour }}时</view>
          </picker>
          <picker class="time-picker" mode="selector" :range="MINUTE_OPTIONS" range-key="label"
            :value="MINUTES.indexOf(form.minute)" @change="onMinuteChange">
            <view class="picker-btn">{{ String(form.minute).padStart(2,'0') }}分</view>
          </picker>
        </view>

        <text class="field-label" style="margin-top:28rpx">出生城市</text>

        <!-- 城市搜索区域 -->
        <view class="city-search-wrap" @click.stop>
          <view class="city-input-row">
            <view class="input-wrap" style="flex:1">
              <text class="input-icon">📍</text>
              <input
                class="city-input"
                :value="citySearchKeyword"
                @input="onCityInput"
                placeholder="搜索城市，如：北京、成都"
                placeholder-style="color:#4a3868"
                maxlength="30"
              />
              <view v-if="citySearchKeyword" class="input-clear" @click.stop="() => { citySearchKeyword = ''; form.city = ''; form.lat = null; form.lng = null; showCitySuggestions = false }">
                <text class="clear-icon">✕</text>
              </view>
            </view>
            <!-- 地图点选按钮 -->
            <view class="map-btn" @click.stop="chooseLocationOnMap">
              <text class="map-btn-icon">🗺️</text>
              <text class="map-btn-text">地图</text>
            </view>
          </view>

          <!-- 城市搜索建议下拉 -->
          <view v-if="showCitySuggestions && citySearchResults.length > 0" class="city-suggestions">
            <view
              v-for="city in citySearchResults"
              :key="city.name"
              class="city-suggestion-item"
              @click.stop="selectCity(city)"
            >
              <text class="suggestion-icon">📍</text>
              <view class="suggestion-info">
                <text class="suggestion-name">{{ city.name }}</text>
                <text v-if="city.address" class="suggestion-addr">{{ city.address }}</text>
              </view>
            </view>
          </view>

          <!-- 已选位置经纬度展示 -->
          <view v-if="form.lat && form.lng" class="location-tag">
            <text class="location-tag-icon">✓</text>
            <text class="location-tag-text">{{ form.city }}（{{ form.lat.toFixed(4) }}°N, {{ form.lng.toFixed(4) }}°E）</text>
          </view>
        </view>
      </view>

      <!-- 提示 -->
      <view class="tip-card">
        <text class="tip-icon">🌙</text>
        <text class="tip-text">出生时间越精确，星盘越准确。若不确定具体时间，可使用中午12时作为参考。出生地点将用于计算精确的行星宫位。</text>
      </view>

      <view class="submit-btn" @click="calculateChart">
        <text class="submit-text">✦ 绘制我的星盘</text>
      </view>

      <view style="height: 120rpx" />
    </scroll-view>

    <!-- 结果页 -->
    <view v-if="step === 'result'" class="result-page">
      <!-- 顶部星盘可视化区域 -->
      <view class="chart-visual">
        <view class="chart-bg-glow" />
        <view class="chart-circle outer-circle" />
        <view class="chart-circle mid-circle" />
        <view class="chart-circle inner-circle" />
        <!-- 12宫位分割线 -->
        <view v-for="i in 12" :key="i" class="house-line"
          :style="{ transform: `rotate(${i * 30}deg)` }" />
        <!-- 行星点 -->
        <view v-for="(p, idx) in MOCK_PLANETS" :key="idx" class="planet-marker"
          :style="{
            transform: `rotate(${idx * 51.4}deg) translateX(110rpx) rotate(-${idx * 51.4}deg)`,
            color: p.color
          }">
          <text class="planet-symbol-marker">{{ p.symbol }}</text>
        </view>
        <!-- 中心 -->
        <view class="chart-center">
          <text class="chart-center-text">☉</text>
        </view>
      </view>

      <!-- 摘要栏 -->
      <view class="summary-row">
        <view class="summary-item">
          <text class="summary-label">☉ 太阳</text>
          <text class="summary-val">狮子座</text>
        </view>
        <view class="summary-divider" />
        <view class="summary-item">
          <text class="summary-label">☽ 月亮</text>
          <text class="summary-val">双鱼座</text>
        </view>
        <view class="summary-divider" />
        <view class="summary-item">
          <text class="summary-label">↑ 上升</text>
          <text class="summary-val">天蝎座</text>
        </view>
      </view>

      <!-- Tabs -->
      <view class="tabs-bar">
        <view class="tab-item" :class="{ active: activeTab === 'planets' }" @click="activeTab = 'planets'">
          <text>行星</text>
        </view>
        <view class="tab-item" :class="{ active: activeTab === 'aspects' }" @click="activeTab = 'aspects'">
          <text>相位</text>
        </view>
        <view class="tab-item" :class="{ active: activeTab === 'interpret' }" @click="activeTab = 'interpret'">
          <text>AI 解读</text>
        </view>
      </view>

      <scroll-view class="tab-content" scroll-y>
        <!-- 行星列表 -->
        <view v-if="activeTab === 'planets'">
          <view v-for="planet in MOCK_PLANETS" :key="planet.name" class="planet-row">
            <view class="planet-symbol-wrap" :style="{ background: planet.color + '22', borderColor: planet.color + '44' }">
              <text class="planet-symbol" :style="{ color: planet.color }">{{ planet.symbol }}</text>
            </view>
            <view class="planet-info">
              <text class="planet-name">{{ planet.name }}</text>
              <text class="planet-sign">{{ planet.sign }} · {{ planet.house }}</text>
            </view>
              <view class="planet-bar-wrap">
              <view class="planet-bar" :style="{ width: planet.strength + '%', background: planet.color }" />
            </view>
          </view>
        </view>

        <!-- 相位列表 -->
        <view v-if="activeTab === 'aspects'">
          <view v-for="asp in MOCK_ASPECTS" :key="asp.p1 + asp.p2" class="aspect-row"
            :class="'aspect-' + asp.harmony">
            <text class="asp-p1">{{ asp.p1 }}</text>
            <view class="asp-badge" :class="'badge-' + asp.harmony">
              <text class="asp-symbol">{{ asp.aspect }}</text>
            </view>
            <text class="asp-p2">{{ asp.p2 }}</text>
          </view>
        </view>

        <!-- AI 解读 -->
        <view v-if="activeTab === 'interpret'" class="interpret-panel">
          <!-- 焦点选择 -->
          <view class="focus-scroll-wrap">
            <scroll-view class="focus-scroll" scroll-x>
              <view class="focus-list">
                <view v-for="opt in FOCUS_OPTIONS" :key="opt.key"
                  class="focus-chip" :class="{ 'focus-active': selectedFocus === opt.key }"
                  @click="selectedFocus = opt.key">
                  <text class="focus-chip-icon">{{ opt.icon }}</text>
                  <text class="focus-chip-label">{{ opt.label }}</text>
                </view>
              </view>
            </scroll-view>
          </view>

          <!-- 解读触发按钮 -->
          <view v-if="!interpretation && !isInterpreting" class="interpret-trigger" @click="getInterpretation">
            <view class="trigger-glow" />
            <text class="trigger-icon">✦</text>
            <text class="trigger-text">开始 AI 解读</text>
            <text class="trigger-sub">由星屿 AI 为你解析</text>
          </view>

          <!-- 解读内容（打字机效果） -->
          <view v-if="interpretation || isInterpreting" class="interpret-content">
            <view class="interpret-header">
              <text class="interpret-focus-label">{{ FOCUS_OPTIONS.find(o => o.key === selectedFocus)?.label }} 解读</text>
              <view v-if="isInterpreting" class="typing-indicator">
                <view class="typing-dot" /><view class="typing-dot" /><view class="typing-dot" />
              </view>
            </view>
            <text class="interpret-text">{{ interpretation }}</text>
          </view>

          <!-- 切换焦点重新解读 -->
          <view v-if="interpretation && !isInterpreting" class="re-interpret-btn" @click="getInterpretation">
            <text>↻ 换个角度解读</text>
          </view>
        </view>

        <view style="height: 160rpx" />
      </scroll-view>

      <!-- 底部操作栏 -->
      <view class="result-actions">
        <view class="action-btn-outline" @click="backToForm">
          <text>重新计算</text>
        </view>
        <view class="action-btn-primary" @click="goInterpret">
          <text>✦ AI 解读</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style>
.natal-page {
  min-height: 100vh;
  background: #0a0a18;
  position: relative;
}
.bg-gradient {
  position: fixed;
  top: 0; left: 0; right: 0;
  height: 400rpx;
  background: radial-gradient(ellipse at 50% 0%, rgba(139,111,209,0.15) 0%, transparent 70%);
  pointer-events: none;
  z-index: 0;
}

/* Loading */
.loading-screen {
  height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 48rpx;
}
.loading-orb {
  width: 200rpx; height: 200rpx;
  position: relative;
  display: flex; align-items: center; justify-content: center;
}
.orb-ring {
  position: absolute;
  border: 1rpx solid rgba(139,111,209,0.3);
  border-radius: 50%;
  animation: spin 4s linear infinite;
}
.r1 { width: 100rpx; height: 100rpx; }
.r2 { width: 150rpx; height: 150rpx; animation-duration: 6s; border-style: dashed; }
.r3 { width: 200rpx; height: 200rpx; animation-duration: 10s; opacity: 0.5; }
@keyframes spin { to { transform: rotate(360deg); } }
.orb-symbol { font-size: 60rpx; color: #c4a8f0; z-index: 1; }
.loading-text { font-size: 32rpx; color: #c4a8f0; letter-spacing: 2rpx; }
.loading-sub { font-size: 24rpx; color: #5a4878; }

/* 表单 */
.page-scroll { position: relative; z-index: 1; padding: 0 28rpx; }
.form-header {
  padding-top: 60rpx;
  padding-bottom: 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
}
.form-icon-wrap {
  width: 100rpx; height: 100rpx;
  border-radius: 30rpx;
  background: rgba(139,111,209,0.15);
  border: 1rpx solid rgba(139,111,209,0.3);
  display: flex; align-items: center; justify-content: center;
  margin-bottom: 8rpx;
}
.form-icon { font-size: 52rpx; color: #c4a8f0; }
.form-title { font-size: 48rpx; color: #f0e8ff; font-weight: bold; display: block; }
.form-desc { font-size: 26rpx; color: #7a6a9a; text-align: center; line-height: 1.6; display: block; }

.form-card {
  background: rgba(255,255,255,0.03);
  border: 1rpx solid rgba(184,158,232,0.12);
  border-radius: 24rpx;
  padding: 36rpx;
  margin-bottom: 20rpx;
}
.field-label { font-size: 24rpx; color: #7a6a9a; display: block; margin-bottom: 16rpx; letter-spacing: 1rpx; }

.date-row { display: flex; gap: 12rpx; }
.time-row { display: flex; gap: 16rpx; }
.date-picker { flex: 1; }
.time-picker { flex: 1; }
.picker-btn {
  background: rgba(139,111,209,0.08);
  border: 1rpx solid rgba(139,111,209,0.2);
  border-radius: 14rpx;
  padding: 18rpx;
  text-align: center;
  font-size: 28rpx;
  color: #d4b8f8;
}

/* 城市搜索 */
.city-search-wrap {
  position: relative;
  z-index: 10;
}
.city-input-row {
  display: flex;
  gap: 16rpx;
  align-items: stretch;
}
.input-wrap {
  background: rgba(139,111,209,0.08);
  border: 1rpx solid rgba(139,111,209,0.2);
  border-radius: 14rpx;
  padding: 18rpx 20rpx;
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.input-icon { font-size: 30rpx; flex-shrink: 0; }
.city-input { flex: 1; font-size: 30rpx; color: #d4b8f8; min-width: 0; }
.input-clear {
  width: 40rpx; height: 40rpx;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.clear-icon { font-size: 24rpx; color: #6a5a88; }

/* 地图按钮 */
.map-btn {
  background: rgba(139,111,209,0.15);
  border: 1rpx solid rgba(139,111,209,0.35);
  border-radius: 14rpx;
  padding: 16rpx 20rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6rpx;
  flex-shrink: 0;
  min-width: 100rpx;
}
.map-btn-icon { font-size: 32rpx; line-height: 1; }
.map-btn-text { font-size: 22rpx; color: #b89ee8; }

/* 城市建议下拉 */
.city-suggestions {
  position: absolute;
  top: 100%;
  left: 0; right: 0;
  margin-top: 8rpx;
  background: #1a1530;
  border: 1rpx solid rgba(139,111,209,0.25);
  border-radius: 16rpx;
  overflow: hidden;
  z-index: 100;
  box-shadow: 0 8rpx 32rpx rgba(0,0,0,0.5);
}
.city-suggestion-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 22rpx 24rpx;
  border-bottom: 1rpx solid rgba(255,255,255,0.04);
}
.city-suggestion-item:last-child { border-bottom: none; }
.suggestion-icon { font-size: 28rpx; flex-shrink: 0; }
.suggestion-info { flex: 1; }
.suggestion-name { font-size: 30rpx; color: #d4b8f8; display: block; }
.suggestion-addr { font-size: 22rpx; color: #5a4878; display: block; margin-top: 4rpx; }

/* 已选位置标签 */
.location-tag {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-top: 14rpx;
  background: rgba(80,200,120,0.08);
  border: 1rpx solid rgba(80,200,120,0.2);
  border-radius: 10rpx;
  padding: 10rpx 16rpx;
}
.location-tag-icon { font-size: 24rpx; color: #50c878; flex-shrink: 0; }
.location-tag-text { font-size: 22rpx; color: #70d890; line-height: 1.4; }

.tip-card {
  background: rgba(100,180,255,0.04);
  border: 1rpx solid rgba(100,180,255,0.1);
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
  margin-bottom: 32rpx;
  display: flex;
  gap: 14rpx;
  align-items: flex-start;
}
.tip-icon { font-size: 28rpx; flex-shrink: 0; }
.tip-text { font-size: 24rpx; color: #5a8aaa; line-height: 1.7; }

.submit-btn {
  background: linear-gradient(135deg, #8b6fd1, #6040b0);
  border-radius: 20rpx;
  padding: 32rpx;
  text-align: center;
  box-shadow: 0 8rpx 32rpx rgba(139,111,209,0.3);
}
.submit-text { font-size: 34rpx; color: white; font-weight: 600; letter-spacing: 4rpx; }

/* ── 结果页 ── */
.result-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  position: relative;
  z-index: 1;
}

/* 星盘可视化 */
.chart-visual {
  background: linear-gradient(180deg, #12102a 0%, #0a0a18 100%);
  height: 380rpx;
  position: relative;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.chart-bg-glow {
  position: absolute;
  width: 300rpx; height: 300rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(139,111,209,0.2), transparent);
  top: 50%; left: 50%;
  transform: translate(-50%, -50%);
}
.chart-circle {
  position: absolute;
  border-radius: 50%;
  border: 1rpx solid rgba(139,111,209,0.2);
  top: 50%; left: 50%;
  transform: translate(-50%, -50%);
}
.outer-circle { width: 320rpx; height: 320rpx; }
.mid-circle { width: 240rpx; height: 240rpx; border-style: dashed; opacity: 0.5; }
.inner-circle { width: 160rpx; height: 160rpx; }

.house-line {
  position: absolute;
  top: 50%; left: 50%;
  width: 160rpx; height: 1rpx;
  background: rgba(139,111,209,0.1);
  transform-origin: left center;
}

.planet-marker {
  position: absolute;
  top: 50%; left: 50%;
  font-size: 24rpx;
}
.planet-symbol-marker { font-size: 24rpx; }

.chart-center {
  position: absolute;
  width: 60rpx; height: 60rpx;
  border-radius: 50%;
  background: rgba(139,111,209,0.15);
  display: flex; align-items: center; justify-content: center;
  z-index: 2;
}
.chart-center-text { font-size: 28rpx; color: #c4a8f0; }

/* 摘要行 */
.summary-row {
  background: rgba(255,255,255,0.03);
  border-bottom: 1rpx solid rgba(255,255,255,0.05);
  padding: 20rpx 32rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0;
  flex-shrink: 0;
}
.summary-item { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 6rpx; }
.summary-label { font-size: 22rpx; color: #7a6a9a; }
.summary-val { font-size: 28rpx; color: #d4b8f8; font-weight: 600; }
.summary-divider { width: 1rpx; height: 48rpx; background: rgba(255,255,255,0.06); }

/* Tabs */
.tabs-bar {
  display: flex;
  background: rgba(255,255,255,0.02);
  border-bottom: 1rpx solid rgba(255,255,255,0.05);
  flex-shrink: 0;
}
.tab-item {
  flex: 1; padding: 24rpx;
  text-align: center;
  font-size: 28rpx;
  color: #5a4878;
  position: relative;
}
.tab-item.active { color: #c4a8f0; }
.tab-item.active::after {
  content: '';
  position: absolute;
  bottom: 0; left: 20%; right: 20%;
  height: 3rpx;
  background: linear-gradient(90deg, #8b6fd1, #b89ee8);
  border-radius: 2rpx;
}

.tab-content { flex: 1; padding: 24rpx 28rpx; }

/* 行星列表 */
.planet-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 20rpx 0;
  border-bottom: 1rpx solid rgba(255,255,255,0.04);
}
.planet-symbol-wrap {
  width: 60rpx; height: 60rpx;
  border-radius: 16rpx;
  border: 1rpx solid;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.planet-symbol { font-size: 28rpx; }
.planet-info { flex: 1; }
.planet-name { font-size: 28rpx; color: #d4b8f8; display: block; font-weight: 600; }
.planet-sign { font-size: 24rpx; color: #7a6a9a; }
.planet-bar-wrap { width: 120rpx; height: 6rpx; background: rgba(255,255,255,0.05); border-radius: 3rpx; overflow: hidden; }
.planet-bar { height: 100%; border-radius: 3rpx; opacity: 0.7; }

/* 相位列表 */
.aspect-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 22rpx 0;
  border-bottom: 1rpx solid rgba(255,255,255,0.04);
}
.asp-p1, .asp-p2 { flex: 1; font-size: 26rpx; color: #c4a8f0; }
.asp-p2 { text-align: right; }
.asp-badge {
  padding: 8rpx 20rpx;
  border-radius: 20rpx;
  flex-shrink: 0;
}
.badge-positive { background: rgba(80,200,120,0.15); border: 1rpx solid rgba(80,200,120,0.25); }
.badge-positive .asp-symbol { color: #50c878; font-size: 24rpx; }
.badge-challenge { background: rgba(255,100,100,0.12); border: 1rpx solid rgba(255,100,100,0.2); }
.badge-challenge .asp-symbol { color: #ff8888; font-size: 24rpx; }
.badge-neutral { background: rgba(200,200,100,0.12); border: 1rpx solid rgba(200,200,100,0.2); }
.badge-neutral .asp-symbol { color: #d4cc60; font-size: 24rpx; }

/* AI 解读区 */
.interpret-panel { padding: 8rpx 0; }

.focus-scroll-wrap { margin-bottom: 28rpx; }
.focus-scroll { white-space: nowrap; }
.focus-list { display: flex; gap: 16rpx; padding-right: 28rpx; }
.focus-chip {
  display: inline-flex;
  align-items: center;
  gap: 10rpx;
  padding: 14rpx 24rpx;
  border-radius: 30rpx;
  background: rgba(255,255,255,0.04);
  border: 1rpx solid rgba(255,255,255,0.08);
  white-space: nowrap;
  flex-shrink: 0;
}
.focus-active {
  background: rgba(139,111,209,0.2);
  border-color: rgba(139,111,209,0.4);
}
.focus-chip-icon { font-size: 26rpx; }
.focus-chip-label { font-size: 26rpx; color: #8a7aaa; }
.focus-active .focus-chip-label { color: #c4a8f0; }

.interpret-trigger {
  position: relative;
  background: linear-gradient(135deg, rgba(139,111,209,0.15), rgba(80,50,140,0.1));
  border: 1rpx solid rgba(139,111,209,0.3);
  border-radius: 24rpx;
  padding: 48rpx 32rpx;
  text-align: center;
  overflow: hidden;
}
.trigger-glow {
  position: absolute;
  top: -40rpx; left: 50%;
  width: 200rpx; height: 200rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(139,111,209,0.2), transparent);
  transform: translateX(-50%);
}
.trigger-icon { font-size: 48rpx; color: #c4a8f0; display: block; margin-bottom: 16rpx; }
.trigger-text { font-size: 36rpx; color: #e0d0ff; font-weight: 600; display: block; margin-bottom: 10rpx; }
.trigger-sub { font-size: 24rpx; color: #7a6a9a; display: block; }

.interpret-content {
  background: rgba(255,255,255,0.02);
  border: 1rpx solid rgba(139,111,209,0.12);
  border-radius: 20rpx;
  padding: 28rpx 32rpx;
}
.interpret-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}
.interpret-focus-label { font-size: 26rpx; color: #a888e8; font-weight: 600; }

.typing-indicator { display: flex; gap: 8rpx; align-items: center; }
.typing-dot {
  width: 10rpx; height: 10rpx;
  border-radius: 50%;
  background: #8b6fd1;
  animation: bounce 1.2s ease-in-out infinite;
}
.typing-dot:nth-child(2) { animation-delay: 0.2s; }
.typing-dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes bounce {
  0%, 80%, 100% { transform: translateY(0); opacity: 0.4; }
  40% { transform: translateY(-8rpx); opacity: 1; }
}

.interpret-text { font-size: 28rpx; color: #c4a8f0; line-height: 2; white-space: pre-wrap; }

.re-interpret-btn {
  text-align: center;
  padding: 24rpx;
  color: #8b6fd1;
  font-size: 26rpx;
  border: 1rpx solid rgba(139,111,209,0.2);
  border-radius: 16rpx;
  margin-top: 16rpx;
}

/* 底部操作栏 */
.result-actions {
  padding: 20rpx 28rpx;
  padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
  display: flex;
  gap: 16rpx;
  background: rgba(10,10,24,0.95);
  border-top: 1rpx solid rgba(255,255,255,0.06);
  flex-shrink: 0;
}
.action-btn-outline {
  flex: 1; padding: 26rpx;
  text-align: center;
  border: 1rpx solid rgba(139,111,209,0.3);
  border-radius: 16rpx;
  font-size: 30rpx;
  color: #8b6fd1;
}
.action-btn-primary {
  flex: 2; padding: 26rpx;
  text-align: center;
  background: linear-gradient(135deg, #8b6fd1, #6040b0);
  border-radius: 16rpx;
  font-size: 30rpx;
  color: white;
  font-weight: 600;
}
</style>

