<script setup lang="ts">
import {computed, onMounted, reactive, ref} from 'vue'
import {getNatalChart, interpretNatal, type NatalChartResponse} from '../../api/astrology'
import {updateBirthInfo} from '../../api/auth'
import {useUserStore} from '../../store/user'

const userStore = useUserStore()

// ── 出生信息表单（编辑模式）─────────────────────────────
const editForm = reactive({
  year: new Date().getFullYear() - 25,
  month: 1,
  day: 1,
  hour: 8,
  minute: 0,
  city: '',
  lat: null as number | null,
  lng: null as number | null,
})

// ── 页面步骤 ──────────────────────────────────────────────
// birthEdit: 设置/修改出生信息
// form: 本命盘主页（展示当前出生信息，可计算本命盘）
// loading: 计算中
// result: 计算结果
const step = ref<'birthEdit' | 'form' | 'loading' | 'result'>('form')
const loadingText = ref('正在解析星体轨迹...')
const chartData = ref<NatalChartResponse | null>(null)
const interpretation = ref('')
const isInterpreting = ref(false)
const selectedFocus = ref('personality')
const activeTab = ref<'planets' | 'aspects' | 'interpret'>('planets')
let typingGeneration = 0

// 城市搜索状态
const citySearchKeyword = ref('')
const citySearchResults = ref<Array<{name: string, lat: number, lng: number, address?: string}>>([])
const showCitySuggestions = ref(false)

// 地图状态
const showMapModal = ref(false)
const mapLat = ref(39.9042)
const mapLng = ref(116.4074)
const mapScale = ref(10)
const pendingLat = ref<number | null>(null)
const pendingLng = ref<number | null>(null)
const showMapConfirm = ref(false)
const mapMarkers = ref<Array<{id: number, latitude: number, longitude: number, iconPath?: string, width?: number, height?: number}>>([])
const mapAddress = ref('')

// 是否正在保存出生信息
const isSavingBirthInfo = ref(false)

// ── 判断是否有出生信息 ──────────────────────────────────
function hasBirthInfo(): boolean {
  const info = userStore.userInfo
  return !!(info?.birthCity && info?.birthTime)
}

// ── 构建出生信息展示文本 ─────────────────────────────────
function getBirthInfoDisplay(): string {
  const info = userStore.userInfo
  if (!info?.birthCity || !info?.birthTime) return '未设置'
  return `${info.birthTime} · ${info.birthCity}`
}

// ── onMounted：初始化 ────────────────────────────────────
onMounted(() => {
  // 如果有出生信息，默认展示本命盘主页；否则进入设置页
  if (hasBirthInfo()) {
    step.value = 'form'
  } else {
    // 没有出生信息，先进入设置页
    initEditFormFromStore()
    step.value = 'birthEdit'
  }
})

/** 从 userStore 初始化编辑表单 */
function initEditFormFromStore() {
  const info = userStore.userInfo
  if (info?.birthCity) {
    citySearchKeyword.value = info.birthCity
    editForm.city = info.birthCity
    editForm.lat = info.birthLat ?? null
    editForm.lng = info.birthLng ?? null
  }
  if (info?.birthTime) {
    try {
      const [datePart, timePart] = info.birthTime.split(' ')
      const [y, m, d] = datePart.split('-').map(Number)
      const [h, min] = timePart.split(':').map(Number)
      if (y && m && d) {
        editForm.year = y
        editForm.month = m
        editForm.day = d
      }
      if (!isNaN(h)) editForm.hour = h
      if (!isNaN(min)) editForm.minute = min
    } catch {
      // 解析失败保持默认值
    }
  }
}

/** 打开出生信息编辑页 */
function openBirthEdit() {
  initEditFormFromStore()
  citySearchKeyword.value = editForm.city
  showCitySuggestions.value = false
  step.value = 'birthEdit'
}

// 解读焦点选项
const FOCUS_OPTIONS = [
  { key: 'personality', label: '性格人格', icon: '✨' },
  { key: 'emotion', label: '情感模式', icon: '💫' },
  { key: 'career', label: '天赋事业', icon: '⚡' },
  { key: 'growth', label: '成长课题', icon: '🌱' },
  { key: 'shadow', label: '阴影人格', icon: '🌑' }
]

// ── 行星名称和符号映射 ───────────────────────────────────
const PLANET_DISPLAY: Record<string, { symbol: string, name: string, color: string }> = {
  sun:     { symbol: '☉', name: '太阳',   color: '#FFD700' },
  moon:    { symbol: '☽', name: '月亮',   color: '#C8C8FF' },
  mercury: { symbol: '☿', name: '水星',   color: '#90EE90' },
  venus:   { symbol: '♀', name: '金星',   color: '#FFB6C1' },
  mars:    { symbol: '♂', name: '火星',   color: '#FF6B6B' },
  jupiter: { symbol: '♃', name: '木星',   color: '#DEB887' },
  saturn:  { symbol: '♄', name: '土星',   color: '#8B9DC3' },
  uranus:  { symbol: '⛢', name: '天王星', color: '#7FDBFF' },
  neptune: { symbol: '♆', name: '海王星', color: '#4169E1' },
  pluto:   { symbol: '♇', name: '冥王星', color: '#9B59B6' },
  // 轴点：Python 库(kerykeion/flatlib)可能以多种名称返回
  ascendant:      { symbol: 'AC', name: '上升点', color: '#E8D5B7' },
  asc:            { symbol: 'AC', name: '上升点', color: '#E8D5B7' },
  'asc.':         { symbol: 'AC', name: '上升点', color: '#E8D5B7' },
  midheaven:      { symbol: 'MC', name: '天顶',   color: '#B7D5E8' },
  mc:             { symbol: 'MC', name: '天顶',   color: '#B7D5E8' },
  'mc.':          { symbol: 'MC', name: '天顶',   color: '#B7D5E8' },
  'medium coeli': { symbol: 'MC', name: '天顶',   color: '#B7D5E8' },
  ic:             { symbol: 'IC', name: '天底',   color: '#D5B7E8' },
  'ic.':          { symbol: 'IC', name: '天底',   color: '#D5B7E8' },
  'imum coeli':   { symbol: 'IC', name: '天底',   color: '#D5B7E8' },
  descendant:     { symbol: 'DC', name: '下降点', color: '#E8B7D5' },
  dc:             { symbol: 'DC', name: '下降点', color: '#E8B7D5' },
  'dc.':          { symbol: 'DC', name: '下降点', color: '#E8B7D5' },
  // 节点
  'north node':   { symbol: '☊', name: '北交点', color: '#C8E8B7' },
  north_node:     { symbol: '☊', name: '北交点', color: '#C8E8B7' },
  'south node':   { symbol: '☋', name: '南交点', color: '#E8C8B7' },
  south_node:     { symbol: '☋', name: '南交点', color: '#E8C8B7' },
  chiron:         { symbol: '⚷', name: '凯龙星', color: '#B7E8C8' },
}

// 相位名称映射（兼容英文小写 key，包含常见别名）
const ASPECT_NAME_MAP: Record<string, { label: string, harmony: 'positive' | 'challenge' | 'neutral' }> = {
  conjunction:    { label: '☌ 合相',   harmony: 'neutral' },
  sextile:        { label: '⚹ 六分',   harmony: 'positive' },
  square:         { label: '□ 四分',   harmony: 'challenge' },
  trine:          { label: '△ 三分',   harmony: 'positive' },
  opposition:     { label: '☍ 对分',   harmony: 'challenge' },
  quincunx:       { label: '⚻ 梅花',  harmony: 'neutral' },
  semisextile:    { label: '⚺ 二十分', harmony: 'neutral' },
  sesquiquadrate: { label: '⚼ 倍半方', harmony: 'challenge' },
  semisquare:     { label: '∠ 半方',   harmony: 'challenge' },
  quintile:       { label: '五分',      harmony: 'positive' },
  biquintile:     { label: '双五分',    harmony: 'positive' },
}

// 星座中文名映射
const ZODIAC_ZH: Record<string, string> = {
  aries:       '白羊座',
  taurus:      '金牛座',
  gemini:      '双子座',
  cancer:      '巨蟹座',
  leo:         '狮子座',
  virgo:       '处女座',
  libra:       '天秤座',
  scorpio:     '天蝎座',
  sagittarius: '射手座',
  capricorn:   '摩羯座',
  aquarius:    '水瓶座',
  pisces:      '双鱼座',
}

/** 将星座英文名转换为中文，若已是中文则直接返回 */
function zodiacZh(sign: string): string {
  if (!sign) return ''
  const key = sign.toLowerCase().trim()
  return ZODIAC_ZH[key] || sign
}

/** 从 chartData.chart.planets 中提取行星列表（真实数据） */
const realPlanets = computed(() => {
  const planets = chartData.value?.chart?.planets
  if (!planets) return []
  const result: Array<{ symbol: string, name: string, sign: string, house: string, color: string, strength: number, degree: number | null }> = []
  const planetOrder = ['sun', 'moon', 'mercury', 'venus', 'mars', 'jupiter', 'saturn', 'uranus', 'neptune', 'pluto']
  for (const key of planetOrder) {
    const p = planets[key]
    const display = PLANET_DISPLAY[key]
    if (!p || !display) continue
    // 兼容 sign / zodiac_sign 字段名，并转换为中文
    const signRaw = p.sign || p.zodiac_sign || p.zodiac || ''
    const sign = zodiacZh(signRaw)
    // 宫位兼容
    const houseNum = p.house ?? p.house_number ?? null
    const house = houseNum != null ? `第${houseNum}宫` : ''
    // 经度/度数（用于可视化定位）
    const degree = p.longitude ?? p.degree ?? p.lon ?? null
    // 用速度/距离推算活力（简单映射），若无则默认 60
    let strength = 60
    if (p.speed != null) {
      strength = Math.min(100, Math.max(20, Math.round(Math.abs(Number(p.speed)) * 200 + 40)))
    } else if (p.strength != null) {
      strength = Math.min(100, Math.max(20, Math.round(Number(p.strength))))
    }
    result.push({ symbol: display.symbol, name: display.name, sign, house, color: display.color, strength, degree })
  }
  return result
})

// 轴点 key 集合，用于判断是否为纯轴点互相相位
const AXIS_KEYS = new Set(['ascendant', 'asc', 'asc.', 'midheaven', 'mc', 'mc.', 'medium coeli', 'descendant', 'dc', 'dc.', 'ic', 'ic.', 'imum coeli'])

/** 从 chartData.chart.aspects 中提取相位列表（真实数据） */
const realAspects = computed(() => {
  const aspects = chartData.value?.chart?.aspects
  if (!aspects || !Array.isArray(aspects)) return []
  // 从 chart.angles 构建轴点星座查找表
  const angles = chartData.value?.chart?.angles as Record<string, any> | undefined
  const angleSignMap: Record<string, string> = {}
  if (angles) {
    for (const key of Object.keys(angles)) {
      const sign: string = angles[key]?.sign || ''
      if (sign) angleSignMap[key.toLowerCase()] = sign
    }
    if (angleSignMap['ascendant'] && !angleSignMap['asc']) angleSignMap['asc'] = angleSignMap['ascendant']
    if (angleSignMap['midheaven'] && !angleSignMap['mc']) angleSignMap['mc'] = angleSignMap['midheaven']
    if (angleSignMap['mc'] && !angleSignMap['midheaven']) angleSignMap['midheaven'] = angleSignMap['mc']
  }

  return aspects.slice(0, 20).map((a: any) => {
    // 兼容多种字段名：planet1/planet2、body1/body2、planet_a/planet_b、p1/p2
    const p1Key: string = (a.planet1 || a.body1 || a.planet_a || a.p1 || a.planet || '').toLowerCase().trim()
    const p2Key: string = (a.planet2 || a.body2 || a.planet_b || a.p2 || a.other_planet || '').toLowerCase().trim()

    // 过滤纯轴点互相的恒定相位（如 ascendant-opposition-descendant，数学上必然成立，对解读无意义）
    if (AXIS_KEYS.has(p1Key) && AXIS_KEYS.has(p2Key)) return null

    const p1Display = PLANET_DISPLAY[p1Key]
    const p2Display = PLANET_DISPLAY[p2Key]

    // 容错：两者都未识别且名称为空，跳过
    if (!p1Display && !p2Display && (!p1Key || !p2Key)) return null

    // 兼容多种相位字段名：aspect/type/aspect_type
    const aspectType: string = (a.aspect || a.type || a.aspect_type || 'conjunction').toLowerCase().trim()
    const aspectInfo = ASPECT_NAME_MAP[aspectType] ?? { label: aspectType, harmony: 'neutral' as const }

    // 星座：优先取相位数据字段，轴点从 angles 补充
    const p1SignRaw = a.planet1_sign || a.sign1 || a.p1_sign || angleSignMap[p1Key] || ''
    const p2SignRaw = a.planet2_sign || a.sign2 || a.p2_sign || angleSignMap[p2Key] || ''
    const p1SignZh = zodiacZh(p1SignRaw)
    const p2SignZh = zodiacZh(p2SignRaw)

    const p1Label = p1Display
      ? `${p1Display.symbol} ${p1Display.name}${p1SignZh ? ' · ' + p1SignZh : ''}`
      : (p1Key || '?')
    const p2Label = p2Display
      ? `${p2Display.symbol} ${p2Display.name}${p2SignZh ? ' · ' + p2SignZh : ''}`
      : (p2Key || '?')

    // 轨道/容许度
    const orb = a.orb ?? a.angle_diff ?? null
    const orbStr = orb != null ? ` (${Math.abs(Number(orb)).toFixed(1)}°)` : ''
    return {
      p1: p1Label,
      aspect: aspectInfo.label,
      p2: p2Label,
      harmony: aspectInfo.harmony,
      orbStr
    }
  }).filter(Boolean)
})

/** 从 chartData.summary 提取太阳/月亮/上升摘要 */
const chartSummary = computed(() => {
  const s = chartData.value?.summary
  if (!s) {
    // 降级从 chart.planets 和 chart.angles 读取
    const planets = chartData.value?.chart?.planets
    const angles = chartData.value?.chart?.angles
    return {
      sunSign: zodiacZh(planets?.sun?.sign || planets?.sun?.zodiac_sign || ''),
      moonSign: zodiacZh(planets?.moon?.sign || planets?.moon?.zodiac_sign || ''),
      ascSign: zodiacZh(angles?.ascendant?.sign || angles?.ascendant?.zodiac_sign || ''),
    }
  }
  return {
    sunSign: zodiacZh(s.sun?.sign || s.sun?.zodiac_sign || ''),
    moonSign: zodiacZh(s.moon?.sign || s.moon?.zodiac_sign || ''),
    ascSign: zodiacZh(s.ascendant?.sign || s.ascendant?.zodiac_sign || ''),
  }
})

// ── 表单日期选项 ─────────────────────────────────────────
const YEARS = Array.from({ length: 80 }, (_, i) => 1950 + i)
const MONTHS = Array.from({ length: 12 }, (_, i) => i + 1)
const DAYS = Array.from({ length: 31 }, (_, i) => i + 1)
const HOURS = Array.from({ length: 24 }, (_, i) => i)
const MINUTES = Array.from({ length: 60 }, (_, i) => i)

const YEAR_OPTIONS = YEARS.map(y => ({ label: y + '年', value: y }))
const MONTH_OPTIONS = MONTHS.map(m => ({ label: m + '月', value: m }))
const DAY_OPTIONS = DAYS.map(d => ({ label: d + '日', value: d }))
const HOUR_OPTIONS = HOURS.map(h => ({ label: h + '时', value: h }))
const MINUTE_OPTIONS = MINUTES.map(m => ({ label: String(m).padStart(2, '0') + '分', value: m }))

function onYearChange(e: any) { editForm.year = Number(YEAR_OPTIONS[e.detail.value].value) }
function onMonthChange(e: any) { editForm.month = Number(MONTH_OPTIONS[e.detail.value].value) }
function onDayChange(e: any) { editForm.day = Number(DAY_OPTIONS[e.detail.value].value) }
function onHourChange(e: any) { editForm.hour = Number(HOUR_OPTIONS[e.detail.value].value) }
function onMinuteChange(e: any) { editForm.minute = Number(MINUTE_OPTIONS[e.detail.value].value) }

// ── 城市搜索逻辑 ──────────────────────────────────────────

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
    editForm.city = ''
    editForm.lat = null
    editForm.lng = null
    return
  }
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    searchCities(kw.trim())
  }, 300)
}

function searchCities(keyword: string) {
  const matched = MAJOR_CITIES.filter(c =>
    c.name.includes(keyword) || c.address?.includes(keyword)
  ).slice(0, 6)
  citySearchResults.value = matched
  showCitySuggestions.value = matched.length > 0
}

function selectCity(city: {name: string, lat: number, lng: number, address?: string}) {
  editForm.city = city.name
  editForm.lat = city.lat
  editForm.lng = city.lng
  citySearchKeyword.value = city.name
  showCitySuggestions.value = false
}

function openMapModal() {
  showCitySuggestions.value = false
  if (editForm.lat && editForm.lng) {
    mapLat.value = editForm.lat
    mapLng.value = editForm.lng
    mapMarkers.value = [{ id: 1, latitude: editForm.lat, longitude: editForm.lng, width: 32, height: 32 }]
  } else {
    mapLat.value = 39.9042
    mapLng.value = 116.4074
    mapMarkers.value = []
  }
  pendingLat.value = null
  pendingLng.value = null
  showMapConfirm.value = false
  mapAddress.value = ''
  showMapModal.value = true
}

function onMapTap(e: any) {
  const lat: number = e.detail?.latitude ?? e.detail?.lat
  const lng: number = e.detail?.longitude ?? e.detail?.lng
  if (!lat || !lng) return
  pendingLat.value = lat
  pendingLng.value = lng
  mapLat.value = lat
  mapLng.value = lng
  mapMarkers.value = [{ id: 1, latitude: lat, longitude: lng, width: 32, height: 32 }]
  mapAddress.value = `${lat.toFixed(4)}°N, ${lng.toFixed(4)}°E`
  showMapConfirm.value = true
}

function confirmMapLocation() {
  if (pendingLat.value === null || pendingLng.value === null) return
  editForm.lat = pendingLat.value
  editForm.lng = pendingLng.value
  const nearest = findNearestCity(pendingLat.value, pendingLng.value)
  if (nearest) {
    editForm.city = nearest.name
    citySearchKeyword.value = nearest.name
  } else {
    const label = `${pendingLat.value.toFixed(2)}°N,${pendingLng.value.toFixed(2)}°E`
    editForm.city = label
    citySearchKeyword.value = label
  }
  showMapConfirm.value = false
  showMapModal.value = false
}

function cancelMapLocation() {
  showMapConfirm.value = false
  pendingLat.value = null
  pendingLng.value = null
  if (editForm.lat && editForm.lng) {
    mapMarkers.value = [{ id: 1, latitude: editForm.lat, longitude: editForm.lng, width: 32, height: 32 }]
  } else {
    mapMarkers.value = []
  }
}

function closeMapModal() {
  showMapModal.value = false
  showMapConfirm.value = false
}

function findNearestCity(lat: number, lng: number) {
  let minDist = Infinity
  let nearest: typeof MAJOR_CITIES[0] | null = null
  for (const c of MAJOR_CITIES) {
    const d = Math.sqrt((c.lat - lat) ** 2 + (c.lng - lng) ** 2)
    if (d < minDist) {
      minDist = d
      nearest = c
    }
  }
  return minDist <= 2 ? nearest : null
}

function closeSuggestions() {
  showCitySuggestions.value = false
}

// ── 构造出生时间字符串 ────────────────────────────────────
function getBirthTimeStr(): string {
  const m = String(editForm.month).padStart(2, '0')
  const d = String(editForm.day).padStart(2, '0')
  const h = String(editForm.hour).padStart(2, '0')
  const min = String(editForm.minute).padStart(2, '0')
  return `${editForm.year}-${m}-${d} ${h}:${min}`
}

// ── 保存出生信息 ──────────────────────────────────────────
async function saveBirthInfo() {
  if (!editForm.city.trim()) {
    uni.showToast({ title: '请输入或选择出生城市', icon: 'none' })
    return
  }
  isSavingBirthInfo.value = true
  const birthTime = getBirthTimeStr()
  try {
    await updateBirthInfo({
      birthCity: editForm.city,
      birthLat: editForm.lat,
      birthLng: editForm.lng,
      birthTime,
    })
    // 同步到 userStore（后端已清空本命盘缓存）
    userStore.updateBirthInfo(editForm.city, editForm.lat, editForm.lng, birthTime)
    // 清空本地已有的本命盘结果（因为出生信息变了）
    chartData.value = null
    interpretation.value = ''
    uni.showToast({ title: '出生信息已保存', icon: 'success' })
    // 返回本命盘主页
    step.value = 'form'
  } catch (e) {
    // 本地更新（降级）
    userStore.updateBirthInfo(editForm.city, editForm.lat, editForm.lng, birthTime)
    chartData.value = null
    interpretation.value = ''
    uni.showToast({ title: '保存失败，已本地更新', icon: 'none' })
    step.value = 'form'
  } finally {
    isSavingBirthInfo.value = false
  }
}

// ── 计算本命盘 ─────────────────────────────────────────
async function calculateChart() {
  // 检查是否设置了出生信息
  if (!hasBirthInfo()) {
    uni.showModal({
      title: '未设置出生信息',
      content: '计算本命盘需要先设置出生信息，是否现在设置？',
      confirmText: '去设置',
      cancelText: '取消',
      success: (res) => {
        if (res.confirm) {
          openBirthEdit()
        }
      }
    })
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
    // 后端从 user 表读取出生信息，无需传参
    const result = await getNatalChart()
    chartData.value = result
    step.value = 'result'
  } catch (e: any) {
    // 如果是出生信息未设置的错误（7001），跳转到设置页
    if (e?.code === 7001 || e?.message?.includes('出生信息')) {
      step.value = 'form'
      uni.showModal({
        title: '未设置出生信息',
        content: '请先设置出生信息再计算本命盘',
        confirmText: '去设置',
        cancelText: '取消',
        success: (res) => {
          if (res.confirm) openBirthEdit()
        }
      })
    } else {
      // 使用 mock 数据展示（开发阶段 Python 服务未启动时）
      chartData.value = { chart: null, summary: null, savedToProfile: false }
      step.value = 'result'
    }
  } finally {
    clearInterval(textTimer)
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
    // chart 由后端从 DB 读取，只传 focus 和 tone
    // 前置条件：用户已计算过本命盘（chartData 不为 null）
    const result = await interpretNatal({
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

    <!-- ══════════════════════════════════════
         出生信息设置/编辑页
    ══════════════════════════════════════ -->
    <scroll-view v-if="step === 'birthEdit'" class="page-scroll" scroll-y>
      <!-- 顶部导航 -->
      <view class="edit-nav">
        <view class="back-btn" @click="hasBirthInfo() ? step = 'form' : uni.navigateBack()">
          <text class="back-icon">‹</text>
          <text class="back-text">{{ hasBirthInfo() ? '返回' : '取消' }}</text>
        </view>
        <text class="edit-nav-title">出生信息</text>
        <view style="width: 80rpx" />
      </view>

      <view class="form-header">
        <view class="form-icon-wrap">
          <text class="form-icon">🌟</text>
        </view>
        <text class="form-title">设置出生信息</text>
        <text class="form-desc">准确的出生信息是精确计算星盘的基础</text>
      </view>

      <view class="form-card">
        <text class="field-label">出生日期</text>
        <view class="date-row">
          <picker class="date-picker" mode="selector" :range="YEAR_OPTIONS" range-key="label"
            :value="YEARS.indexOf(editForm.year)" @change="onYearChange">
            <view class="picker-btn">{{ editForm.year }}年</view>
          </picker>
          <picker class="date-picker" mode="selector" :range="MONTH_OPTIONS" range-key="label"
            :value="editForm.month - 1" @change="onMonthChange">
            <view class="picker-btn">{{ editForm.month }}月</view>
          </picker>
          <picker class="date-picker" mode="selector" :range="DAY_OPTIONS" range-key="label"
            :value="editForm.day - 1" @change="onDayChange">
            <view class="picker-btn">{{ editForm.day }}日</view>
          </picker>
        </view>

        <text class="field-label" style="margin-top:28rpx">出生时间</text>
        <view class="time-row">
          <picker class="time-picker" mode="selector" :range="HOUR_OPTIONS" range-key="label"
            :value="editForm.hour" @change="onHourChange">
            <view class="picker-btn">{{ editForm.hour }}时</view>
          </picker>
          <picker class="time-picker" mode="selector" :range="MINUTE_OPTIONS" range-key="label"
            :value="MINUTES.indexOf(editForm.minute)" @change="onMinuteChange">
            <view class="picker-btn">{{ String(editForm.minute).padStart(2,'0') }}分</view>
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
              <view v-if="citySearchKeyword" class="input-clear" @click.stop="() => { citySearchKeyword = ''; editForm.city = ''; editForm.lat = null; editForm.lng = null; showCitySuggestions = false }">
                <text class="clear-icon">✕</text>
              </view>
            </view>
            <view class="map-btn" @click.stop="openMapModal">
              <text class="map-btn-icon">🗺️</text>
              <text class="map-btn-text">地图</text>
            </view>
          </view>

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

          <view v-if="editForm.lat && editForm.lng" class="location-tag">
            <text class="location-tag-icon">✓</text>
            <text class="location-tag-text">{{ editForm.city }}（{{ editForm.lat.toFixed(4) }}°N, {{ editForm.lng.toFixed(4) }}°E）</text>
          </view>
        </view>
      </view>

      <view class="tip-card">
        <text class="tip-icon">🌙</text>
        <text class="tip-text">出生时间越精确，星盘越准确。若不确定具体时间，可使用中午12时作为参考。出生地点将用于计算精确的行星宫位。</text>
      </view>

      <!-- 修改出生信息提示 -->
      <view v-if="hasBirthInfo()" class="warning-card">
        <text class="warning-icon">⚠️</text>
        <text class="warning-text">修改出生信息后，已计算的本命盘和流运将被清空，需要重新计算。</text>
      </view>

      <view class="submit-btn" :class="{ 'btn-loading': isSavingBirthInfo }" @click="saveBirthInfo">
        <text class="submit-text">{{ isSavingBirthInfo ? '保存中...' : '✦ 保存出生信息' }}</text>
      </view>

      <view style="height: 120rpx" />
    </scroll-view>

    <!-- Loading -->
    <view v-if="step === 'loading'" class="loading-screen">
      <view class="loading-orb">
        <view class="orb-ring r1" /><view class="orb-ring r2" /><view class="orb-ring r3" />
        <text class="orb-symbol">☉</text>
      </view>
      <text class="loading-text">{{ loadingText }}</text>
      <text class="loading-sub">正在绘制你的宇宙地图</text>
    </view>

    <!-- ══════════════════════════════════════
         本命盘主页（展示出生信息 + 计算按钮）
    ══════════════════════════════════════ -->
    <scroll-view v-if="step === 'form'" class="page-scroll" scroll-y>
      <view class="form-header">
        <view class="form-icon-wrap">
          <text class="form-icon">☉</text>
        </view>
        <text class="form-title">本命盘</text>
        <text class="form-desc">探索属于你的星盘宇宙，了解行星如何塑造你的性格与命运</text>
      </view>

      <!-- 出生信息卡片 -->
      <view class="birth-info-card">
        <view class="birth-info-header">
          <text class="birth-info-label">出生信息</text>
          <view class="edit-birth-btn" @click="openBirthEdit">
            <text class="edit-birth-icon">✏️</text>
            <text class="edit-birth-text">修改</text>
          </view>
        </view>

        <view v-if="hasBirthInfo()" class="birth-info-content">
          <view class="birth-info-row">
            <text class="birth-info-key">🕐 出生时间</text>
            <text class="birth-info-val">{{ userStore.userInfo?.birthTime }}</text>
          </view>
          <view class="birth-info-row">
            <text class="birth-info-key">📍 出生城市</text>
            <text class="birth-info-val">{{ userStore.userInfo?.birthCity }}</text>
          </view>
          <view v-if="userStore.userInfo?.birthLat" class="birth-info-row">
            <text class="birth-info-key">🌐 坐标</text>
            <text class="birth-info-val">{{ userStore.userInfo?.birthLat?.toFixed(4) }}°N, {{ userStore.userInfo?.birthLng?.toFixed(4) }}°E</text>
          </view>
        </view>

        <view v-else class="birth-info-empty" @click="openBirthEdit">
          <text class="birth-info-empty-icon">🌟</text>
          <text class="birth-info-empty-text">点击设置出生信息</text>
          <text class="birth-info-empty-sub">设置后才能计算本命盘</text>
        </view>
      </view>

      <!-- 提示 -->
      <view class="tip-card">
        <text class="tip-icon">🌙</text>
        <text class="tip-text">本命盘计算完成后将永久保存，无需重复计算。如需更新，请修改出生信息后重新计算。</text>
      </view>

      <view class="submit-btn" :class="{ 'btn-disabled': !hasBirthInfo() }" @click="calculateChart">
        <text class="submit-text">✦ 绘制我的星盘</text>
      </view>

      <view style="height: 120rpx" />
    </scroll-view>

    <!-- ═══ 地图选点弹窗 ═══ -->
    <view v-if="showMapModal" class="map-modal-mask" @click.stop="closeMapModal">
      <view class="map-modal-panel" @click.stop>
        <view class="map-modal-header">
          <text class="map-modal-title">📍 点击地图选择出生地点</text>
          <view class="map-modal-close" @click="closeMapModal">
            <text class="map-close-icon">✕</text>
          </view>
        </view>

        <map
          class="map-component"
          :latitude="mapLat"
          :longitude="mapLng"
          :scale="mapScale"
          :markers="mapMarkers"
          show-compass
          enable-zoom
          enable-scroll
          @tap="onMapTap"
        />

        <view v-if="showMapConfirm" class="map-confirm-bar">
          <view class="map-confirm-info">
            <text class="map-confirm-icon">📍</text>
            <view>
              <text class="map-confirm-label">已选位置</text>
              <text class="map-confirm-addr">{{ mapAddress }}</text>
            </view>
          </view>
          <view class="map-confirm-actions">
            <view class="map-cancel-btn" @click="cancelMapLocation">
              <text>取消</text>
            </view>
            <view class="map-ok-btn" @click="confirmMapLocation">
              <text>确认</text>
            </view>
          </view>
        </view>

        <view v-else class="map-tip-bar">
          <text class="map-tip-text">👆 在地图上点击选择出生城市坐标</text>
        </view>
      </view>
    </view>

    <!-- 结果页 -->
    <view v-if="step === 'result'" class="result-page">
      <!-- 顶部星盘可视化区域 -->
      <view class="chart-visual">
        <view class="chart-bg-glow" />
        <view class="chart-circle outer-circle" />
        <view class="chart-circle mid-circle" />
        <view class="chart-circle inner-circle" />
        <!-- 十二宫分割线 -->
        <view v-for="i in 12" :key="i" class="house-line"
          :style="{ transform: `rotate(${i * 30}deg)` }" />
        <!-- 行星标记：若有真实经度则按经度定位，否则均匀分布 -->
        <view v-for="(p, idx) in realPlanets" :key="idx" class="planet-marker"
          :style="{
            transform: p.degree != null
              ? `rotate(${p.degree - 90}deg) translateY(-110rpx) rotate(-${p.degree - 90}deg)`
              : `rotate(${idx * 36}deg) translateY(-110rpx) rotate(-${idx * 36}deg)`,
            color: p.color
          }">
          <text class="planet-symbol-marker">{{ p.symbol }}</text>
        </view>
        <!-- 上升点标记 -->
        <view v-if="chartData?.chart?.angles?.ascendant" class="asc-marker">
          <text class="asc-text">ASC</text>
        </view>
        <view class="chart-center">
          <text class="chart-center-text">☉</text>
        </view>
      </view>

      <!-- 摘要栏 -->
      <view class="summary-row">
        <view class="summary-item">
          <text class="summary-label">☉ 太阳</text>
          <text class="summary-val">{{ chartSummary.sunSign || '—' }}</text>
        </view>
        <view class="summary-divider" />
        <view class="summary-item">
          <text class="summary-label">☽ 月亮</text>
          <text class="summary-val">{{ chartSummary.moonSign || '—' }}</text>
        </view>
        <view class="summary-divider" />
        <view class="summary-item">
          <text class="summary-label">↑ 上升</text>
          <text class="summary-val">{{ chartSummary.ascSign || '—' }}</text>
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
          <view v-if="realPlanets.length === 0" class="data-empty-tip">
            <text class="data-empty-icon">✦</text>
            <text class="data-empty-text">暂无行星数据，请先计算本命盘</text>
          </view>
          <view v-for="planet in realPlanets" :key="planet.name" class="planet-row">
            <view class="planet-symbol-wrap" :style="{ background: planet.color + '22', borderColor: planet.color + '44' }">
              <text class="planet-symbol" :style="{ color: planet.color }">{{ planet.symbol }}</text>
            </view>
            <view class="planet-info">
              <text class="planet-name">{{ planet.name }}</text>
              <text class="planet-sign">{{ planet.sign || '—' }}{{ planet.house ? ' · ' + planet.house : '' }}</text>
            </view>
            <view class="planet-bar-wrap">
              <view class="planet-bar" :style="{ width: planet.strength + '%', background: planet.color }" />
            </view>
          </view>
        </view>

        <!-- 相位列表 -->
        <view v-if="activeTab === 'aspects'">
          <view v-if="realAspects.length === 0" class="data-empty-tip">
            <text class="data-empty-icon">∅</text>
            <text class="data-empty-text">暂无相位数据，请先计算本命盘</text>
          </view>
          <!-- 相位统计摘要 -->
          <view v-if="realAspects.length > 0" class="aspects-summary">
            <view class="asp-stat">
              <text class="asp-stat-num" style="color:#60e0a0">{{ realAspects.filter(a => a?.harmony === 'positive').length }}</text>
              <text class="asp-stat-label">和谐</text>
            </view>
            <view class="asp-stat">
              <text class="asp-stat-num" style="color:#f08070">{{ realAspects.filter(a => a?.harmony === 'challenge').length }}</text>
              <text class="asp-stat-label">张力</text>
            </view>
            <view class="asp-stat">
              <text class="asp-stat-num" style="color:#e0c060">{{ realAspects.filter(a => a?.harmony === 'neutral').length }}</text>
              <text class="asp-stat-label">中性</text>
            </view>
            <view class="asp-stat">
              <text class="asp-stat-num" style="color:#c4b4f0">{{ realAspects.length }}</text>
              <text class="asp-stat-label">总计</text>
            </view>
          </view>
          <view v-for="(asp, idx) in realAspects" :key="idx" class="aspect-row"
            :class="'aspect-' + asp?.harmony">
            <text class="asp-p1">{{ asp?.p1 }}</text>
            <view class="asp-badge-wrap">
              <view class="asp-badge" :class="'badge-' + asp?.harmony">
                <text class="asp-symbol">{{ asp?.aspect }}</text>
              </view>
              <text v-if="asp?.orbStr" class="asp-orb">{{ asp.orbStr }}</text>
            </view>
            <text class="asp-p2">{{ asp?.p2 }}</text>
          </view>
        </view>

        <!-- AI 解读 -->
        <view v-if="activeTab === 'interpret'" class="interpret-panel">
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

          <view v-if="!interpretation && !isInterpreting" class="interpret-trigger" @click="getInterpretation">
            <view class="trigger-glow" />
            <text class="trigger-icon">✦</text>
            <text class="trigger-text">开始 AI 解读</text>
            <text class="trigger-sub">由星屿 AI 为你解析</text>
          </view>

          <view v-if="interpretation || isInterpreting" class="interpret-content">
            <view class="interpret-header">
              <text class="interpret-focus-label">{{ FOCUS_OPTIONS.find(o => o.key === selectedFocus)?.label }} 解读</text>
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

      <!-- 底部操作栏 -->
      <view class="result-actions">
        <view class="action-btn-outline" @click="backToForm">
          <text>返回主页</text>
        </view>
        <view class="action-btn-secondary" @click="openBirthEdit">
          <text>✏️ 修改出生信息</text>
        </view>
        <view class="action-btn-primary" @click="goInterpret">
          <text>✦ AI 解读</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style>
/* ═══════════════════════════════════════════
   本命盘页 · 深色宇宙风格
═══════════════════════════════════════════ */

.natal-page {
  min-height: 100vh;
  background: #0a0a1a;
  position: relative;
}

.bg-gradient {
  position: fixed;
  top: 0; left: 0; right: 0;
  height: 400rpx;
  background: radial-gradient(ellipse at 50% 0%, rgba(155,135,209,0.12) 0%, transparent 70%);
  pointer-events: none;
  z-index: 0;
}

/* ── 顶部导航（编辑页）─────────────────────── */
.edit-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 80rpx 24rpx 16rpx;
  position: relative;
  z-index: 1;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 6rpx;
  padding: 10rpx 16rpx;
  border-radius: 20rpx;
  background: rgba(155, 135, 209, 0.12);
}

.back-icon { font-size: 36rpx; color: #c4b4f0; line-height: 1; }
.back-text { font-size: 26rpx; color: #c4b4f0; }

.edit-nav-title {
  font-size: 32rpx;
  color: #e8e0ff;
  font-weight: 600;
}

/* ── Loading ─────────────────────────────── */
.loading-screen {
  height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 44rpx;
  background: #0a0a1a;
}

.loading-orb {
  width: 200rpx; height: 200rpx;
  position: relative;
  display: flex; align-items: center; justify-content: center;
}

.orb-ring {
  position: absolute;
  border: 1rpx solid rgba(155, 135, 209, 0.35);
  border-radius: 50%;
  animation: spin 4s linear infinite;
}

.r1 { width: 100rpx; height: 100rpx; }
.r2 { width: 150rpx; height: 150rpx; animation-duration: 6s; border-style: dashed; opacity: 0.7; }
.r3 { width: 200rpx; height: 200rpx; animation-duration: 10s; opacity: 0.4; }

@keyframes spin { to { transform: rotate(360deg); } }

.orb-symbol { font-size: 56rpx; color: #9b87d1; z-index: 1; text-shadow: 0 0 20rpx rgba(155,135,209,0.8); }
.loading-text { font-size: 30rpx; color: #c4b4f0; letter-spacing: 2rpx; }
.loading-sub { font-size: 23rpx; color: rgba(155, 135, 209, 0.5); }

/* ── 表单区域 ─────────────────────────────── */
.page-scroll { position: relative; z-index: 1; padding: 0 26rpx; }

.form-header {
  padding-top: 56rpx;
  padding-bottom: 36rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14rpx;
}

.form-icon-wrap {
  width: 96rpx; height: 96rpx;
  border-radius: 28rpx;
  background: rgba(155, 135, 209, 0.12);
  border: 1.5rpx solid rgba(155, 135, 209, 0.35);
  display: flex; align-items: center; justify-content: center;
  margin-bottom: 6rpx;
  box-shadow: 0 4rpx 24rpx rgba(155, 135, 209, 0.2);
}

.form-icon { font-size: 48rpx; color: #9b87d1; }
.form-title { font-size: 44rpx; color: #e8e0ff; font-weight: 700; display: block; }
.form-desc { font-size: 25rpx; color: rgba(155, 135, 209, 0.55); text-align: center; line-height: 1.65; display: block; }

.form-card {
  background: rgba(20, 16, 42, 0.90);
  border: 1.5rpx solid rgba(155, 135, 209, 0.18);
  border-radius: 24rpx;
  padding: 32rpx;
  margin-bottom: 18rpx;
  box-shadow: 0 2rpx 20rpx rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(12rpx);
}

.field-label {
  font-size: 23rpx;
  color: rgba(155, 135, 209, 0.6);
  display: block;
  margin-bottom: 14rpx;
  letter-spacing: 1rpx;
}

.date-row { display: flex; gap: 11rpx; }
.time-row { display: flex; gap: 14rpx; }
.date-picker { flex: 1; }
.time-picker { flex: 1; }

.picker-btn {
  background: rgba(30, 24, 60, 0.85);
  border: 1.5rpx solid rgba(155, 135, 209, 0.22);
  border-radius: 14rpx;
  padding: 16rpx;
  text-align: center;
  font-size: 27rpx;
  color: #d8d0f8;
}

/* ── 出生信息卡片 ─────────────────────────── */
.birth-info-card {
  background: rgba(20, 16, 42, 0.90);
  border: 1.5rpx solid rgba(155, 135, 209, 0.18);
  border-radius: 24rpx;
  padding: 28rpx;
  margin-bottom: 18rpx;
  box-shadow: 0 2rpx 20rpx rgba(0, 0, 0, 0.3);
}

.birth-info-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}

.birth-info-label {
  font-size: 28rpx;
  color: #e8e0ff;
  font-weight: 600;
}

.edit-birth-btn {
  display: flex;
  align-items: center;
  gap: 7rpx;
  background: rgba(155, 135, 209, 0.12);
  border: 1rpx solid rgba(155, 135, 209, 0.3);
  border-radius: 16rpx;
  padding: 9rpx 18rpx;
}

.edit-birth-icon { font-size: 24rpx; }
.edit-birth-text { font-size: 24rpx; color: #9b87d1; }

.birth-info-content {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.birth-info-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.birth-info-key {
  font-size: 24rpx;
  color: rgba(155, 135, 209, 0.55);
  width: 160rpx;
  flex-shrink: 0;
}

.birth-info-val {
  font-size: 26rpx;
  color: #d8d0f8;
  font-weight: 500;
}

.birth-info-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
  padding: 24rpx 0;
}

.birth-info-empty-icon { font-size: 42rpx; }
.birth-info-empty-text { font-size: 28rpx; color: #c4b4f0; font-weight: 600; }
.birth-info-empty-sub { font-size: 23rpx; color: rgba(155, 135, 209, 0.5); }

/* ── 城市搜索 ─────────────────────────────── */
.city-search-wrap {
  position: relative;
  z-index: 10;
}

.city-input-row {
  display: flex;
  gap: 14rpx;
  align-items: stretch;
}

.input-wrap {
  background: rgba(30, 24, 60, 0.85);
  border: 1.5rpx solid rgba(155, 135, 209, 0.22);
  border-radius: 14rpx;
  padding: 16rpx 18rpx;
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.input-icon { font-size: 28rpx; flex-shrink: 0; }
.city-input { flex: 1; font-size: 28rpx; color: #d8d0f8; min-width: 0; }

.input-clear {
  width: 38rpx; height: 38rpx;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}

.clear-icon { font-size: 22rpx; color: rgba(155, 135, 209, 0.4); }

.map-btn {
  background: rgba(155, 135, 209, 0.12);
  border: 1.5rpx solid rgba(155, 135, 209, 0.3);
  border-radius: 14rpx;
  padding: 14rpx 18rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5rpx;
  flex-shrink: 0;
  min-width: 96rpx;
}

.map-btn-icon { font-size: 30rpx; line-height: 1; }
.map-btn-text { font-size: 21rpx; color: #c4b4f0; }

.city-suggestions {
  position: absolute;
  top: 100%;
  left: 0; right: 0;
  margin-top: 8rpx;
  background: rgba(16, 12, 36, 0.97);
  border: 1.5rpx solid rgba(155, 135, 209, 0.25);
  border-radius: 16rpx;
  overflow: hidden;
  z-index: 100;
  box-shadow: 0 8rpx 32rpx rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(20rpx);
}

.city-suggestion-item {
  display: flex;
  align-items: center;
  gap: 14rpx;
  padding: 20rpx 22rpx;
  border-bottom: 1rpx solid rgba(155, 135, 209, 0.08);
}

.city-suggestion-item:last-child { border-bottom: none; }
.suggestion-icon { font-size: 26rpx; flex-shrink: 0; }
.suggestion-info { flex: 1; }
.suggestion-name { font-size: 29rpx; color: #d8d0f8; display: block; }
.suggestion-addr { font-size: 22rpx; color: rgba(155, 135, 209, 0.55); display: block; margin-top: 3rpx; }

.location-tag {
  display: flex;
  align-items: center;
  gap: 9rpx;
  margin-top: 12rpx;
  background: rgba(70, 160, 110, 0.12);
  border: 1rpx solid rgba(100, 200, 150, 0.3);
  border-radius: 10rpx;
  padding: 9rpx 14rpx;
}

.location-tag-icon { font-size: 22rpx; color: #70e0a0; flex-shrink: 0; }
.location-tag-text { font-size: 21rpx; color: #70e0a0; line-height: 1.4; }

.tip-card {
  background: rgba(20, 40, 70, 0.50);
  border: 1rpx solid rgba(80, 150, 220, 0.20);
  border-radius: 16rpx;
  padding: 18rpx 22rpx;
  margin-bottom: 18rpx;
  display: flex;
  gap: 12rpx;
  align-items: flex-start;
}

.tip-icon { font-size: 26rpx; flex-shrink: 0; }
.tip-text { font-size: 23rpx; color: rgba(120, 180, 240, 0.75); line-height: 1.7; }

/* ── 修改出生信息提示 ─────────────────────── */
.warning-card {
  background: rgba(80, 60, 10, 0.40);
  border: 1rpx solid rgba(200, 160, 50, 0.30);
  border-radius: 16rpx;
  padding: 18rpx 22rpx;
  margin-bottom: 18rpx;
  display: flex;
  gap: 12rpx;
  align-items: flex-start;
}

.warning-icon { font-size: 26rpx; flex-shrink: 0; }
.warning-text { font-size: 23rpx; color: rgba(220, 180, 60, 0.8); line-height: 1.7; }

.submit-btn {
  background: linear-gradient(135deg, #8a70c8, #9b87d1);
  border-radius: 20rpx;
  padding: 30rpx;
  text-align: center;
  box-shadow: 0 8rpx 32rpx rgba(155, 135, 209, 0.4);
  margin-bottom: 18rpx;
}

.submit-btn.btn-loading {
  opacity: 0.7;
}

.submit-btn.btn-disabled {
  background: rgba(60, 50, 90, 0.6);
  box-shadow: none;
  opacity: 0.7;
}

.submit-text { font-size: 32rpx; color: white; font-weight: 600; letter-spacing: 4rpx; }

/* ── 结果页 ─────────────────────────────── */
.result-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  position: relative;
  z-index: 1;
}

.chart-visual {
  background: linear-gradient(180deg, rgba(10, 8, 28, 0.98) 0%, rgba(16, 12, 36, 0.95) 100%);
  height: 370rpx;
  position: relative;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  border-bottom: 1rpx solid rgba(155, 135, 209, 0.15);
}

.chart-bg-glow {
  position: absolute;
  width: 300rpx; height: 300rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(155, 135, 209, 0.18), transparent);
  top: 50%; left: 50%;
  transform: translate(-50%, -50%);
}

.chart-circle {
  position: absolute;
  border-radius: 50%;
  border: 1rpx solid rgba(155, 135, 209, 0.28);
  top: 50%; left: 50%;
  transform: translate(-50%, -50%);
}

.outer-circle { width: 320rpx; height: 320rpx; }
.mid-circle { width: 240rpx; height: 240rpx; border-style: dashed; opacity: 0.55; }
.inner-circle { width: 160rpx; height: 160rpx; }

.house-line {
  position: absolute;
  top: 50%; left: 50%;
  width: 160rpx; height: 1rpx;
  background: rgba(155, 135, 209, 0.15);
  transform-origin: left center;
}

.planet-marker {
  position: absolute;
  top: 50%; left: 50%;
  font-size: 22rpx;
}

.planet-symbol-marker { font-size: 22rpx; }

.chart-center {
  position: absolute;
  width: 58rpx; height: 58rpx;
  border-radius: 50%;
  background: rgba(155, 135, 209, 0.15);
  border: 1.5rpx solid rgba(155, 135, 209, 0.4);
  display: flex; align-items: center; justify-content: center;
  z-index: 2;
  box-shadow: 0 0 20rpx rgba(155, 135, 209, 0.3);
}

.chart-center-text { font-size: 26rpx; color: #c4b4f0; }

/* 上升点标记 */
.asc-marker {
  position: absolute;
  right: 12rpx;
  top: 50%;
  transform: translateY(-50%);
  background: rgba(155, 135, 209, 0.12);
  border: 1rpx solid rgba(155, 135, 209, 0.3);
  border-radius: 8rpx;
  padding: 3rpx 8rpx;
}
.asc-text { font-size: 18rpx; color: #c4b4f0; font-weight: 600; }

/* 摘要行 */
.summary-row {
  background: rgba(16, 12, 36, 0.95);
  border-bottom: 1rpx solid rgba(155, 135, 209, 0.12);
  padding: 18rpx 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.summary-item { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 5rpx; }
.summary-label { font-size: 21rpx; color: rgba(155, 135, 209, 0.55); }
.summary-val { font-size: 27rpx; color: #e8e0ff; font-weight: 600; }
.summary-divider { width: 1rpx; height: 44rpx; background: rgba(155, 135, 209, 0.15); }

/* Tabs */
.tabs-bar {
  display: flex;
  background: rgba(16, 12, 36, 0.95);
  border-bottom: 1rpx solid rgba(155, 135, 209, 0.12);
  flex-shrink: 0;
}

.tab-item {
  flex: 1; padding: 22rpx;
  text-align: center;
  font-size: 27rpx;
  color: rgba(155, 135, 209, 0.4);
  position: relative;
}

.tab-item.active { color: #c4b4f0; font-weight: 600; }

.tab-item.active::after {
  content: '';
  position: absolute;
  bottom: 0; left: 20%; right: 20%;
  height: 3rpx;
  background: linear-gradient(90deg, #c3aaee, #9b87d1);
  border-radius: 2rpx;
}

.tab-content {
  flex: 1;
  padding: 22rpx 26rpx;
  background: rgba(10, 8, 24, 0.98);
}

/* 行星列表 */
.planet-row {
  display: flex;
  align-items: center;
  gap: 18rpx;
  padding: 18rpx 0;
  border-bottom: 1rpx solid rgba(155, 135, 209, 0.1);
}

.planet-symbol-wrap {
  width: 56rpx; height: 56rpx;
  border-radius: 14rpx;
  border: 1rpx solid;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}

.planet-symbol { font-size: 26rpx; }
.planet-info { flex: 1; }
.planet-name { font-size: 27rpx; color: #e8e0ff; display: block; font-weight: 600; }
.planet-sign { font-size: 23rpx; color: rgba(155, 135, 209, 0.55); }

.planet-bar-wrap { width: 110rpx; height: 5rpx; background: rgba(155, 135, 209, 0.12); border-radius: 3rpx; overflow: hidden; }
.planet-bar { height: 100%; border-radius: 3rpx; opacity: 0.8; }

/* 空数据提示 */
.data-empty-tip {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  padding: 56rpx 0;
  opacity: 0.7;
}
.data-empty-icon { font-size: 40rpx; color: rgba(155, 135, 209, 0.35); }
.data-empty-text { font-size: 25rpx; color: rgba(155, 135, 209, 0.45); }

/* 相位统计摘要 */
.aspects-summary {
  display: flex;
  gap: 0;
  background: rgba(20, 16, 42, 0.85);
  border: 1rpx solid rgba(155, 135, 209, 0.15);
  border-radius: 16rpx;
  margin-bottom: 18rpx;
  overflow: hidden;
}
.asp-stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5rpx;
  padding: 18rpx 0;
  border-right: 1rpx solid rgba(155, 135, 209, 0.1);
}
.asp-stat:last-child { border-right: none; }
.asp-stat-num { font-size: 32rpx; font-weight: 700; line-height: 1; }
.asp-stat-label { font-size: 20rpx; color: rgba(155, 135, 209, 0.5); }

/* 相位列表 */
.aspect-row {
  display: flex;
  align-items: center;
  gap: 10rpx;
  padding: 18rpx 0;
  border-bottom: 1rpx solid rgba(155, 135, 209, 0.08);
}

.asp-p1, .asp-p2 { flex: 1; font-size: 24rpx; color: rgba(200, 190, 230, 0.75); }
.asp-p2 { text-align: right; }

.asp-badge-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
  flex-shrink: 0;
  min-width: 100rpx;
}

.asp-badge {
  padding: 6rpx 14rpx;
  border-radius: 20rpx;
}

.asp-orb {
  font-size: 18rpx;
  color: rgba(155, 135, 209, 0.4);
}

.badge-positive { background: rgba(60, 160, 100, 0.15); border: 1rpx solid rgba(80, 200, 130, 0.3); }
.badge-positive .asp-symbol { color: #60e0a0; font-size: 22rpx; }
.badge-challenge { background: rgba(200, 60, 60, 0.12); border: 1rpx solid rgba(240, 100, 90, 0.25); }
.badge-challenge .asp-symbol { color: #f08070; font-size: 22rpx; }
.badge-neutral { background: rgba(160, 130, 40, 0.12); border: 1rpx solid rgba(220, 180, 80, 0.25); }
.badge-neutral .asp-symbol { color: #e0c060; font-size: 22rpx; }

/* AI 解读区 */
.interpret-panel { padding: 6rpx 0; }

.focus-scroll-wrap { margin-bottom: 24rpx; }
.focus-scroll { white-space: nowrap; }
.focus-list { display: flex; gap: 14rpx; padding-right: 24rpx; }

.focus-chip {
  display: inline-flex;
  align-items: center;
  gap: 9rpx;
  padding: 12rpx 22rpx;
  border-radius: 30rpx;
  background: rgba(30, 24, 60, 0.85);
  border: 1.5rpx solid rgba(155, 135, 209, 0.18);
  white-space: nowrap;
  flex-shrink: 0;
}

.focus-active {
  background: rgba(155, 135, 209, 0.15);
  border-color: rgba(155, 135, 209, 0.5);
}

.focus-chip-icon { font-size: 24rpx; }
.focus-chip-label { font-size: 24rpx; color: rgba(155, 135, 209, 0.55); }
.focus-active .focus-chip-label { color: #c4b4f0; }

.interpret-trigger {
  position: relative;
  background: rgba(20, 16, 42, 0.90);
  border: 1.5rpx solid rgba(155, 135, 209, 0.25);
  border-radius: 24rpx;
  padding: 44rpx 28rpx;
  text-align: center;
  overflow: hidden;
}

.trigger-glow {
  position: absolute;
  top: -40rpx; left: 50%;
  width: 200rpx; height: 200rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(155, 135, 209, 0.2), transparent);
  transform: translateX(-50%);
}

.trigger-icon { font-size: 44rpx; color: #c4b4f0; display: block; margin-bottom: 14rpx; text-shadow: 0 0 20rpx rgba(155,135,209,0.8); }
.trigger-text { font-size: 34rpx; color: #e8e0ff; font-weight: 600; display: block; margin-bottom: 9rpx; }
.trigger-sub { font-size: 23rpx; color: rgba(155, 135, 209, 0.5); display: block; }

.interpret-content {
  background: rgba(20, 16, 42, 0.90);
  border: 1.5rpx solid rgba(155, 135, 209, 0.18);
  border-radius: 20rpx;
  padding: 26rpx 28rpx;
}

.interpret-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18rpx;
}

.interpret-focus-label { font-size: 25rpx; color: #c4b4f0; font-weight: 600; }

.typing-indicator { display: flex; gap: 7rpx; align-items: center; }
.typing-dot {
  width: 9rpx; height: 9rpx;
  border-radius: 50%;
  background: #9b87d1;
  animation: bounce 1.2s ease-in-out infinite;
}
.typing-dot:nth-child(2) { animation-delay: 0.2s; }
.typing-dot:nth-child(3) { animation-delay: 0.4s; }

@keyframes bounce {
  0%, 80%, 100% { transform: translateY(0); opacity: 0.4; }
  40% { transform: translateY(-7rpx); opacity: 1; }
}

.interpret-text { font-size: 27rpx; color: rgba(220, 210, 250, 0.85); line-height: 2; white-space: pre-wrap; }

.re-interpret-btn {
  text-align: center;
  padding: 22rpx;
  color: #c4b4f0;
  font-size: 25rpx;
  border: 1.5rpx solid rgba(155, 135, 209, 0.25);
  border-radius: 16rpx;
  margin-top: 14rpx;
  background: rgba(30, 24, 60, 0.85);
}

/* 底部操作栏 */
.result-actions {
  padding: 18rpx 24rpx;
  padding-bottom: calc(18rpx + env(safe-area-inset-bottom));
  display: flex;
  gap: 10rpx;
  background: rgba(16, 12, 36, 0.97);
  backdrop-filter: blur(20rpx);
  border-top: 1rpx solid rgba(155, 135, 209, 0.15);
  flex-shrink: 0;
}

.action-btn-outline {
  flex: 1; padding: 24rpx;
  text-align: center;
  border: 1.5rpx solid rgba(155, 135, 209, 0.28);
  border-radius: 16rpx;
  font-size: 24rpx;
  color: rgba(155, 135, 209, 0.7);
  background: rgba(30, 24, 60, 0.8);
}

.action-btn-secondary {
  flex: 1.4; padding: 24rpx;
  text-align: center;
  border: 1.5rpx solid rgba(155, 135, 209, 0.25);
  border-radius: 16rpx;
  font-size: 24rpx;
  color: rgba(200, 190, 230, 0.7);
  background: rgba(30, 24, 60, 0.8);
}

.action-btn-primary {
  flex: 1.4; padding: 24rpx;
  text-align: center;
  background: linear-gradient(135deg, #8a70c8, #9b87d1);
  border-radius: 16rpx;
  font-size: 24rpx;
  color: white;
  font-weight: 600;
  box-shadow: 0 4rpx 24rpx rgba(155, 135, 209, 0.4);
}

/* ── 地图弹窗 ─────────────────────────────── */
.map-modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(30, 20, 60, 0.55);
  z-index: 1000;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.map-modal-panel {
  width: 100%;
  background: rgba(16, 12, 36, 0.98);
  border-radius: 32rpx 32rpx 0 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  max-height: 88vh;
}

.map-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 30rpx 18rpx;
  border-bottom: 1rpx solid rgba(155, 135, 209, 0.15);
  flex-shrink: 0;
}

.map-modal-title {
  font-size: 29rpx;
  color: #e8e0ff;
  font-weight: 600;
}

.map-modal-close {
  width: 52rpx; height: 52rpx;
  border-radius: 50%;
  background: rgba(155, 135, 209, 0.12);
  display: flex; align-items: center; justify-content: center;
}

.map-close-icon {
  font-size: 24rpx;
  color: #c4b4f0;
}

.map-component {
  width: 100%;
  height: 520rpx;
  flex-shrink: 0;
}

.map-confirm-bar {
  background: rgba(16, 12, 36, 0.98);
  border-top: 1rpx solid rgba(155, 135, 209, 0.15);
  padding: 20rpx 28rpx;
  padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  flex-shrink: 0;
}

.map-confirm-info {
  display: flex;
  align-items: center;
  gap: 12rpx;
  flex: 1;
  min-width: 0;
}

.map-confirm-icon {
  font-size: 30rpx;
  flex-shrink: 0;
}

.map-confirm-label {
  font-size: 23rpx;
  color: rgba(155, 135, 209, 0.55);
  display: block;
}

.map-confirm-addr {
  font-size: 26rpx;
  color: #d8d0f8;
  display: block;
  margin-top: 3rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.map-confirm-actions {
  display: flex;
  gap: 14rpx;
  flex-shrink: 0;
}

.map-cancel-btn {
  padding: 16rpx 28rpx;
  border-radius: 14rpx;
  border: 1.5rpx solid rgba(155, 135, 209, 0.28);
  font-size: 27rpx;
  color: #c4b4f0;
  background: rgba(30, 24, 60, 0.8);
}

.map-ok-btn {
  padding: 16rpx 32rpx;
  border-radius: 14rpx;
  background: linear-gradient(135deg, #8a70c8, #9b87d1);
  font-size: 27rpx;
  color: white;
  font-weight: 600;
  box-shadow: 0 3rpx 16rpx rgba(155, 135, 209, 0.4);
}

.map-tip-bar {
  background: rgba(16, 12, 36, 0.98);
  border-top: 1rpx solid rgba(155, 135, 209, 0.15);
  padding: 20rpx 28rpx;
  padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
  text-align: center;
  flex-shrink: 0;
}

.map-tip-text {
  font-size: 25rpx;
  color: rgba(155, 135, 209, 0.5);
}
</style>

