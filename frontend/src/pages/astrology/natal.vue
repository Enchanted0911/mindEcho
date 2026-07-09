<script setup lang="ts">
import {computed, onMounted, reactive, ref} from 'vue'
import {getNatalChart, interpretNatal, type NatalChartResponse} from '@/api/astrology'
import {updateBirthInfo} from '@/api/auth'
import {useUserStore} from '@/store/user'

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

// ── onMounted：初始化 ────────────────────────────────────
onMounted(async () => {
  // 始终从 form 步骤开始（不再自动跳到 birthEdit）
  // 如果有出生信息且后端有缓存的本命盘，自动加载
  step.value = 'form'
  const astroInfo = userStore.astrologyInfo
  if (hasBirthInfo() && astroInfo?.hasNatalCache) {
    // 静默加载已有缓存的本命盘
    await calculateChart()
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
  // 直辖市
  { name: '北京', lat: 39.9042, lng: 116.4074, address: '北京市', pinyin: 'beijing bj' },
  { name: '上海', lat: 31.2304, lng: 121.4737, address: '上海市', pinyin: 'shanghai sh' },
  { name: '天津', lat: 39.3434, lng: 117.3616, address: '天津市', pinyin: 'tianjin tj' },
  { name: '重庆', lat: 29.5630, lng: 106.5516, address: '重庆市', pinyin: 'chongqing cq' },
  // 广东省
  { name: '广州', lat: 23.1291, lng: 113.2644, address: '广东省广州市', pinyin: 'guangzhou gz' },
  { name: '深圳', lat: 22.5431, lng: 114.0579, address: '广东省深圳市', pinyin: 'shenzhen sz' },
  { name: '东莞', lat: 23.0207, lng: 113.7518, address: '广东省东莞市', pinyin: 'dongguan dg' },
  { name: '佛山', lat: 23.0219, lng: 113.1219, address: '广东省佛山市', pinyin: 'foshan fs' },
  { name: '珠海', lat: 22.2711, lng: 113.5767, address: '广东省珠海市', pinyin: 'zhuhai zh' },
  { name: '惠州', lat: 23.1115, lng: 114.4152, address: '广东省惠州市', pinyin: 'huizhou hz' },
  { name: '中山', lat: 22.5176, lng: 113.3926, address: '广东省中山市', pinyin: 'zhongshan zs' },
  { name: '江门', lat: 22.5788, lng: 113.0819, address: '广东省江门市', pinyin: 'jiangmen jm' },
  { name: '湛江', lat: 21.2707, lng: 110.3594, address: '广东省湛江市', pinyin: 'zhanjiang zj' },
  { name: '汕头', lat: 23.3535, lng: 116.6820, address: '广东省汕头市', pinyin: 'shantou st' },
  { name: '潮州', lat: 23.6567, lng: 116.6226, address: '广东省潮州市', pinyin: 'chaozhou cz' },
  { name: '揭阳', lat: 23.5497, lng: 116.3724, address: '广东省揭阳市', pinyin: 'jieyang jy' },
  { name: '茂名', lat: 21.6631, lng: 110.9253, address: '广东省茂名市', pinyin: 'maoming mm' },
  { name: '肇庆', lat: 23.0470, lng: 112.4653, address: '广东省肇庆市', pinyin: 'zhaoqing zq' },
  { name: '梅州', lat: 24.2882, lng: 116.1225, address: '广东省梅州市', pinyin: 'meizhou mz' },
  { name: '清远', lat: 23.6820, lng: 113.0563, address: '广东省清远市', pinyin: 'qingyuan qy' },
  { name: '河源', lat: 23.7435, lng: 114.6979, address: '广东省河源市', pinyin: 'heyuan hy' },
  { name: '阳江', lat: 21.8581, lng: 111.9822, address: '广东省阳江市', pinyin: 'yangjiang yj' },
  { name: '云浮', lat: 22.9151, lng: 112.0445, address: '广东省云浮市', pinyin: 'yunfu yf' },
  { name: '韶关', lat: 24.8107, lng: 113.5975, address: '广东省韶关市', pinyin: 'shaoguan sg' },
  { name: '汕尾', lat: 22.7748, lng: 115.3756, address: '广东省汕尾市', pinyin: 'shanwei sw' },
  // 浙江省
  { name: '杭州', lat: 30.2741, lng: 120.1551, address: '浙江省杭州市', pinyin: 'hangzhou hz' },
  { name: '宁波', lat: 29.8683, lng: 121.5440, address: '浙江省宁波市', pinyin: 'ningbo nb' },
  { name: '温州', lat: 27.9938, lng: 120.6994, address: '浙江省温州市', pinyin: 'wenzhou wz' },
  { name: '嘉兴', lat: 30.7522, lng: 120.7551, address: '浙江省嘉兴市', pinyin: 'jiaxing jx' },
  { name: '湖州', lat: 30.8703, lng: 120.0869, address: '浙江省湖州市', pinyin: 'huzhou huz' },
  { name: '绍兴', lat: 30.0023, lng: 120.5832, address: '浙江省绍兴市', pinyin: 'shaoxing sx' },
  { name: '金华', lat: 29.0788, lng: 119.6474, address: '浙江省金华市', pinyin: 'jinhua jh' },
  { name: '衢州', lat: 28.9359, lng: 118.8741, address: '浙江省衢州市', pinyin: 'quzhou qz' },
  { name: '舟山', lat: 30.0361, lng: 122.1067, address: '浙江省舟山市', pinyin: 'zhoushan zs2' },
  { name: '台州', lat: 28.6561, lng: 121.4206, address: '浙江省台州市', pinyin: 'taizhou tz' },
  { name: '丽水', lat: 28.4677, lng: 119.9230, address: '浙江省丽水市', pinyin: 'lishui ls' },
  // 江苏省
  { name: '南京', lat: 32.0603, lng: 118.7969, address: '江苏省南京市', pinyin: 'nanjing nj' },
  { name: '苏州', lat: 31.2989, lng: 120.5853, address: '江苏省苏州市', pinyin: 'suzhou sz2' },
  { name: '无锡', lat: 31.4912, lng: 120.3119, address: '江苏省无锡市', pinyin: 'wuxi wx' },
  { name: '常州', lat: 31.7744, lng: 119.9741, address: '江苏省常州市', pinyin: 'changzhou cz2' },
  { name: '南通', lat: 31.9801, lng: 120.8944, address: '江苏省南通市', pinyin: 'nantong nt' },
  { name: '扬州', lat: 32.3936, lng: 119.4127, address: '江苏省扬州市', pinyin: 'yangzhou yz' },
  { name: '徐州', lat: 34.2044, lng: 117.2847, address: '江苏省徐州市', pinyin: 'xuzhou xz' },
  { name: '镇江', lat: 32.1875, lng: 119.4253, address: '江苏省镇江市', pinyin: 'zhenjiang zj2' },
  { name: '泰州', lat: 32.4547, lng: 119.9229, address: '江苏省泰州市', pinyin: 'taizhou2 tz2' },
  { name: '盐城', lat: 33.3480, lng: 120.1631, address: '江苏省盐城市', pinyin: 'yancheng yc' },
  { name: '淮安', lat: 33.5518, lng: 119.0214, address: '江苏省淮安市', pinyin: 'huaian ha' },
  { name: '连云港', lat: 34.5965, lng: 119.2214, address: '江苏省连云港市', pinyin: 'lianyungang lyg' },
  { name: '宿迁', lat: 33.9631, lng: 118.2750, address: '江苏省宿迁市', pinyin: 'suqian sq' },
  // 四川省
  { name: '成都', lat: 30.5728, lng: 104.0668, address: '四川省成都市', pinyin: 'chengdu cd' },
  { name: '绵阳', lat: 31.4678, lng: 104.6796, address: '四川省绵阳市', pinyin: 'mianyang my' },
  { name: '德阳', lat: 31.1270, lng: 104.3976, address: '四川省德阳市', pinyin: 'deyang dy' },
  { name: '宜宾', lat: 28.7514, lng: 104.6426, address: '四川省宜宾市', pinyin: 'yibin yb' },
  { name: '泸州', lat: 28.8718, lng: 105.4425, address: '四川省泸州市', pinyin: 'luzhou lz' },
  { name: '南充', lat: 30.8368, lng: 106.1105, address: '四川省南充市', pinyin: 'nanchong nc' },
  { name: '自贡', lat: 29.3390, lng: 104.7787, address: '四川省自贡市', pinyin: 'zigong zg' },
  { name: '攀枝花', lat: 26.5824, lng: 101.7183, address: '四川省攀枝花市', pinyin: 'panzhihua pzh' },
  { name: '广元', lat: 32.4355, lng: 105.8434, address: '四川省广元市', pinyin: 'guangyuan gy' },
  { name: '遂宁', lat: 30.5331, lng: 105.5927, address: '四川省遂宁市', pinyin: 'suining sn' },
  { name: '内江', lat: 29.5806, lng: 105.0585, address: '四川省内江市', pinyin: 'neijiang nj2' },
  { name: '乐山', lat: 29.5527, lng: 103.7661, address: '四川省乐山市', pinyin: 'leshan ls2' },
  { name: '眉山', lat: 30.0748, lng: 103.8486, address: '四川省眉山市', pinyin: 'meishan ms' },
  { name: '雅安', lat: 29.9997, lng: 103.0015, address: '四川省雅安市', pinyin: 'yaan ya' },
  { name: '巴中', lat: 31.8670, lng: 106.7478, address: '四川省巴中市', pinyin: 'bazhong bz' },
  { name: '资阳', lat: 30.1221, lng: 104.6278, address: '四川省资阳市', pinyin: 'ziyang zy' },
  // 湖北省
  { name: '武汉', lat: 30.5928, lng: 114.3055, address: '湖北省武汉市', pinyin: 'wuhan wh' },
  { name: '宜昌', lat: 30.6918, lng: 111.2864, address: '湖北省宜昌市', pinyin: 'yichang yc2' },
  { name: '襄阳', lat: 32.0084, lng: 112.1223, address: '湖北省襄阳市', pinyin: 'xiangyang xy' },
  { name: '荆州', lat: 30.3354, lng: 112.2396, address: '湖北省荆州市', pinyin: 'jingzhou jz' },
  { name: '十堰', lat: 32.6292, lng: 110.7987, address: '湖北省十堰市', pinyin: 'shiyan sy2' },
  { name: '黄石', lat: 30.2006, lng: 115.0387, address: '湖北省黄石市', pinyin: 'huangshi hs' },
  { name: '鄂州', lat: 30.3916, lng: 114.8951, address: '湖北省鄂州市', pinyin: 'ezhou ez' },
  { name: '孝感', lat: 30.9244, lng: 113.9161, address: '湖北省孝感市', pinyin: 'xiaogan xg' },
  { name: '黄冈', lat: 30.4534, lng: 114.8722, address: '湖北省黄冈市', pinyin: 'huanggang hg' },
  { name: '随州', lat: 31.6899, lng: 113.3826, address: '湖北省随州市', pinyin: 'suizhou sz3' },
  // 湖南省
  { name: '长沙', lat: 28.2278, lng: 112.9388, address: '湖南省长沙市', pinyin: 'changsha cs' },
  { name: '株洲', lat: 27.8274, lng: 113.1340, address: '湖南省株洲市', pinyin: 'zhuzhou zz' },
  { name: '湘潭', lat: 27.8295, lng: 112.9447, address: '湖南省湘潭市', pinyin: 'xiangtan xt' },
  { name: '岳阳', lat: 29.3572, lng: 113.1289, address: '湖南省岳阳市', pinyin: 'yueyang yy' },
  { name: '常德', lat: 29.0322, lng: 111.6986, address: '湖南省常德市', pinyin: 'changde cd2' },
  { name: '衡阳', lat: 26.8933, lng: 112.5719, address: '湖南省衡阳市', pinyin: 'hengyang hy2' },
  { name: '邵阳', lat: 27.2394, lng: 111.4678, address: '湖南省邵阳市', pinyin: 'shaoyang sy3' },
  { name: '益阳', lat: 28.5539, lng: 112.3551, address: '湖南省益阳市', pinyin: 'yiyang yy2' },
  { name: '娄底', lat: 27.7003, lng: 111.9954, address: '湖南省娄底市', pinyin: 'loudi ld' },
  { name: '郴州', lat: 25.7700, lng: 113.0148, address: '湖南省郴州市', pinyin: 'chenzhou cz3' },
  { name: '永州', lat: 26.4202, lng: 111.6148, address: '湖南省永州市', pinyin: 'yongzhou yz2' },
  { name: '怀化', lat: 27.5703, lng: 109.9588, address: '湖南省怀化市', pinyin: 'huaihua hh' },
  { name: '张家界', lat: 29.1248, lng: 110.4791, address: '湖南省张家界市', pinyin: 'zhangjiajie zjj' },
  // 福建省
  { name: '福州', lat: 26.0745, lng: 119.2965, address: '福建省福州市', pinyin: 'fuzhou fz' },
  { name: '厦门', lat: 24.4798, lng: 118.0894, address: '福建省厦门市', pinyin: 'xiamen xm' },
  { name: '泉州', lat: 24.8741, lng: 118.6757, address: '福建省泉州市', pinyin: 'quanzhou qz2' },
  { name: '漳州', lat: 24.5141, lng: 117.6472, address: '福建省漳州市', pinyin: 'zhangzhou zz2' },
  { name: '莆田', lat: 25.4540, lng: 119.0073, address: '福建省莆田市', pinyin: 'putian pt' },
  { name: '三明', lat: 26.2654, lng: 117.6386, address: '福建省三明市', pinyin: 'sanming sm' },
  { name: '南平', lat: 26.6351, lng: 118.1786, address: '福建省南平市', pinyin: 'nanping np' },
  { name: '龙岩', lat: 25.0751, lng: 117.0177, address: '福建省龙岩市', pinyin: 'longyan ly' },
  { name: '宁德', lat: 26.6658, lng: 119.5479, address: '福建省宁德市', pinyin: 'ningde nd' },
  // 山东省
  { name: '济南', lat: 36.6512, lng: 117.1201, address: '山东省济南市', pinyin: 'jinan jn' },
  { name: '青岛', lat: 36.0671, lng: 120.3826, address: '山东省青岛市', pinyin: 'qingdao qd' },
  { name: '烟台', lat: 37.4638, lng: 121.4479, address: '山东省烟台市', pinyin: 'yantai yt' },
  { name: '潍坊', lat: 36.7071, lng: 119.1616, address: '山东省潍坊市', pinyin: 'weifang wf' },
  { name: '威海', lat: 37.5130, lng: 122.1219, address: '山东省威海市', pinyin: 'weihai wh2' },
  { name: '淄博', lat: 36.8132, lng: 118.0549, address: '山东省淄博市', pinyin: 'zibo zb' },
  { name: '临沂', lat: 35.1046, lng: 118.3564, address: '山东省临沂市', pinyin: 'linyi ly2' },
  { name: '济宁', lat: 35.4146, lng: 116.5869, address: '山东省济宁市', pinyin: 'jining jn2' },
  { name: '菏泽', lat: 35.2333, lng: 115.4800, address: '山东省菏泽市', pinyin: 'heze hz2' },
  { name: '泰安', lat: 36.1996, lng: 117.0878, address: '山东省泰安市', pinyin: 'taian ta' },
  { name: '东营', lat: 37.4343, lng: 118.6748, address: '山东省东营市', pinyin: 'dongying dy2' },
  { name: '聊城', lat: 36.4562, lng: 115.9855, address: '山东省聊城市', pinyin: 'liaocheng lc' },
  { name: '滨州', lat: 37.3836, lng: 117.9700, address: '山东省滨州市', pinyin: 'binzhou bz2' },
  { name: '德州', lat: 37.4354, lng: 116.3592, address: '山东省德州市', pinyin: 'dezhou dz' },
  { name: '枣庄', lat: 34.8107, lng: 117.3219, address: '山东省枣庄市', pinyin: 'zaozhuang zz3' },
  { name: '日照', lat: 35.4164, lng: 119.5268, address: '山东省日照市', pinyin: 'rizhao rz' },
  // 河南省
  { name: '郑州', lat: 34.7473, lng: 113.6249, address: '河南省郑州市', pinyin: 'zhengzhou zz4' },
  { name: '洛阳', lat: 34.6197, lng: 112.4540, address: '河南省洛阳市', pinyin: 'luoyang ly3' },
  { name: '开封', lat: 34.7971, lng: 114.3075, address: '河南省开封市', pinyin: 'kaifeng kf' },
  { name: '新乡', lat: 35.3028, lng: 113.9230, address: '河南省新乡市', pinyin: 'xinxiang xx' },
  { name: '安阳', lat: 36.0975, lng: 114.3924, address: '河南省安阳市', pinyin: 'anyang ay' },
  { name: '焦作', lat: 35.2395, lng: 113.2418, address: '河南省焦作市', pinyin: 'jiaozuo jz2' },
  { name: '南阳', lat: 32.9905, lng: 112.5283, address: '河南省南阳市', pinyin: 'nanyang ny' },
  { name: '许昌', lat: 34.0356, lng: 113.8522, address: '河南省许昌市', pinyin: 'xuchang xc' },
  { name: '平顶山', lat: 33.7661, lng: 113.2914, address: '河南省平顶山市', pinyin: 'pingdingshan pds' },
  { name: '信阳', lat: 32.1472, lng: 114.0913, address: '河南省信阳市', pinyin: 'xinyang xy2' },
  { name: '周口', lat: 33.6477, lng: 114.6496, address: '河南省周口市', pinyin: 'zhoukou zk' },
  { name: '驻马店', lat: 32.9826, lng: 114.0221, address: '河南省驻马店市', pinyin: 'zhumadian zmd' },
  { name: '商丘', lat: 34.4143, lng: 115.6561, address: '河南省商丘市', pinyin: 'shangqiu sq2' },
  { name: '濮阳', lat: 35.7620, lng: 115.0290, address: '河南省濮阳市', pinyin: 'puyang py' },
  { name: '鹤壁', lat: 35.7474, lng: 114.2977, address: '河南省鹤壁市', pinyin: 'hebi hb' },
  { name: '漯河', lat: 33.5757, lng: 114.0164, address: '河南省漯河市', pinyin: 'luohe lh' },
  { name: '三门峡', lat: 34.7734, lng: 111.2010, address: '河南省三门峡市', pinyin: 'sanmenxia smx' },
  // 辽宁省
  { name: '沈阳', lat: 41.8057, lng: 123.4315, address: '辽宁省沈阳市', pinyin: 'shenyang sy' },
  { name: '大连', lat: 38.9140, lng: 121.6147, address: '辽宁省大连市', pinyin: 'dalian dl' },
  { name: '鞍山', lat: 41.1085, lng: 122.9958, address: '辽宁省鞍山市', pinyin: 'anshan as' },
  { name: '抚顺', lat: 41.8797, lng: 123.9571, address: '辽宁省抚顺市', pinyin: 'fushun fs2' },
  { name: '本溪', lat: 41.2856, lng: 123.7667, address: '辽宁省本溪市', pinyin: 'benxi bx' },
  { name: '锦州', lat: 41.1305, lng: 121.1268, address: '辽宁省锦州市', pinyin: 'jinzhou jz3' },
  { name: '营口', lat: 40.6672, lng: 122.2347, address: '辽宁省营口市', pinyin: 'yingkou yk' },
  { name: '阜新', lat: 42.0215, lng: 121.6686, address: '辽宁省阜新市', pinyin: 'fuxin fx' },
  { name: '辽阳', lat: 41.2694, lng: 123.2354, address: '辽宁省辽阳市', pinyin: 'liaoyang liay' },
  { name: '盘锦', lat: 41.1209, lng: 122.0705, address: '辽宁省盘锦市', pinyin: 'panjin pj' },
  { name: '铁岭', lat: 42.2861, lng: 123.8443, address: '辽宁省铁岭市', pinyin: 'tieling tl' },
  { name: '朝阳', lat: 41.5754, lng: 120.4530, address: '辽宁省朝阳市', pinyin: 'chaoyang cyang' },
  { name: '葫芦岛', lat: 40.7112, lng: 120.8369, address: '辽宁省葫芦岛市', pinyin: 'huludao hld' },
  { name: '丹东', lat: 40.1292, lng: 124.3545, address: '辽宁省丹东市', pinyin: 'dandong dd' },
  // 陕西省
  { name: '西安', lat: 34.3416, lng: 108.9398, address: '陕西省西安市', pinyin: 'xian xa' },
  { name: '咸阳', lat: 34.3297, lng: 108.7089, address: '陕西省咸阳市', pinyin: 'xianyang xy3' },
  { name: '宝鸡', lat: 34.3617, lng: 107.2373, address: '陕西省宝鸡市', pinyin: 'baoji bj2' },
  { name: '渭南', lat: 34.4997, lng: 109.5095, address: '陕西省渭南市', pinyin: 'weinan wn' },
  { name: '汉中', lat: 33.0667, lng: 107.0282, address: '陕西省汉中市', pinyin: 'hanzhong hz3' },
  { name: '榆林', lat: 38.2856, lng: 109.7342, address: '陕西省榆林市', pinyin: 'yulin yl' },
  { name: '安康', lat: 32.6841, lng: 109.0293, address: '陕西省安康市', pinyin: 'ankang ak' },
  { name: '延安', lat: 36.5853, lng: 109.4897, address: '陕西省延安市', pinyin: 'yanan yan' },
  { name: '铜川', lat: 34.8969, lng: 108.9451, address: '陕西省铜川市', pinyin: 'tongchuan tc' },
  { name: '商洛', lat: 33.8706, lng: 109.9196, address: '陕西省商洛市', pinyin: 'shangluo sl' },
  // 安徽省
  { name: '合肥', lat: 31.8206, lng: 117.2272, address: '安徽省合肥市', pinyin: 'hefei hf' },
  { name: '芜湖', lat: 31.3520, lng: 118.4329, address: '安徽省芜湖市', pinyin: 'wuhu wh3' },
  { name: '蚌埠', lat: 32.9162, lng: 117.3795, address: '安徽省蚌埠市', pinyin: 'bengbu bb' },
  { name: '淮南', lat: 32.6252, lng: 116.9993, address: '安徽省淮南市', pinyin: 'huainan hn' },
  { name: '马鞍山', lat: 31.6704, lng: 118.5066, address: '安徽省马鞍山市', pinyin: 'maanshan mas' },
  { name: '淮北', lat: 33.9559, lng: 116.7954, address: '安徽省淮北市', pinyin: 'huaibei hb2' },
  { name: '铜陵', lat: 30.9451, lng: 117.8119, address: '安徽省铜陵市', pinyin: 'tongling tl2' },
  { name: '安庆', lat: 30.5430, lng: 117.0633, address: '安徽省安庆市', pinyin: 'anqing aq' },
  { name: '黄山', lat: 29.7151, lng: 118.3380, address: '安徽省黄山市', pinyin: 'huangshan hs2' },
  { name: '滁州', lat: 32.3025, lng: 118.3166, address: '安徽省滁州市', pinyin: 'chuzhou cz4' },
  { name: '阜阳', lat: 32.8989, lng: 115.8149, address: '安徽省阜阳市', pinyin: 'fuyang fy' },
  { name: '宿州', lat: 33.6464, lng: 116.9641, address: '安徽省宿州市', pinyin: 'suzhou sz4' },
  { name: '六安', lat: 31.7347, lng: 116.5231, address: '安徽省六安市', pinyin: 'liuan la' },
  { name: '亳州', lat: 33.8445, lng: 115.7797, address: '安徽省亳州市', pinyin: 'bozhou bz3' },
  { name: '池州', lat: 30.6648, lng: 117.4898, address: '安徽省池州市', pinyin: 'chizhou cz5' },
  { name: '宣城', lat: 30.9406, lng: 118.7592, address: '安徽省宣城市', pinyin: 'xuancheng xc2' },
  // 河北省
  { name: '石家庄', lat: 38.0428, lng: 114.5149, address: '河北省石家庄市', pinyin: 'shijiazhuang sjz' },
  { name: '唐山', lat: 39.6310, lng: 118.1800, address: '河北省唐山市', pinyin: 'tangshan ts' },
  { name: '秦皇岛', lat: 39.9355, lng: 119.5994, address: '河北省秦皇岛市', pinyin: 'qinhuangdao qhd' },
  { name: '保定', lat: 38.8736, lng: 115.4644, address: '河北省保定市', pinyin: 'baoding bd' },
  { name: '邯郸', lat: 36.6251, lng: 114.5389, address: '河北省邯郸市', pinyin: 'handan hd' },
  { name: '邢台', lat: 37.0682, lng: 114.5048, address: '河北省邢台市', pinyin: 'xingtai xt2' },
  { name: '张家口', lat: 40.8114, lng: 114.8796, address: '河北省张家口市', pinyin: 'zhangjiakou zjk' },
  { name: '承德', lat: 40.9517, lng: 117.9626, address: '河北省承德市', pinyin: 'chengde cgd' },
  { name: '沧州', lat: 38.3037, lng: 116.8388, address: '河北省沧州市', pinyin: 'cangzhou cgz' },
  { name: '廊坊', lat: 39.5382, lng: 116.7032, address: '河北省廊坊市', pinyin: 'langfang lf' },
  { name: '衡水', lat: 37.7357, lng: 115.6710, address: '河北省衡水市', pinyin: 'hengshui hs3' },
  // 山西省
  { name: '太原', lat: 37.8706, lng: 112.5489, address: '山西省太原市', pinyin: 'taiyuan ty' },
  { name: '大同', lat: 40.0766, lng: 113.2982, address: '山西省大同市', pinyin: 'datong dt' },
  { name: '阳泉', lat: 37.8579, lng: 113.5805, address: '山西省阳泉市', pinyin: 'yangquan yq' },
  { name: '长治', lat: 36.1956, lng: 113.1164, address: '山西省长治市', pinyin: 'changzhi cz6' },
  { name: '晋城', lat: 35.4906, lng: 112.8516, address: '山西省晋城市', pinyin: 'jincheng jc' },
  { name: '朔州', lat: 39.3312, lng: 112.4328, address: '山西省朔州市', pinyin: 'shuozhou sz5' },
  { name: '晋中', lat: 37.6872, lng: 112.7523, address: '山西省晋中市', pinyin: 'jinzhong jz4' },
  { name: '运城', lat: 35.0224, lng: 111.0070, address: '山西省运城市', pinyin: 'yuncheng yc3' },
  { name: '忻州', lat: 38.4164, lng: 112.7343, address: '山西省忻州市', pinyin: 'xinzhou xz2' },
  { name: '临汾', lat: 36.0882, lng: 111.5189, address: '山西省临汾市', pinyin: 'linfen lf2' },
  { name: '吕梁', lat: 37.5177, lng: 111.1437, address: '山西省吕梁市', pinyin: 'lvliang ll' },
  // 黑龙江省
  { name: '哈尔滨', lat: 45.8038, lng: 126.5349, address: '黑龙江省哈尔滨市', pinyin: 'haerbin hrb' },
  { name: '齐齐哈尔', lat: 47.3479, lng: 123.9182, address: '黑龙江省齐齐哈尔市', pinyin: 'qiqihaer qqhr' },
  { name: '大庆', lat: 46.5897, lng: 125.1032, address: '黑龙江省大庆市', pinyin: 'daqing dq' },
  { name: '绥化', lat: 46.6537, lng: 126.9993, address: '黑龙江省绥化市', pinyin: 'suihua sh2' },
  { name: '牡丹江', lat: 44.5526, lng: 129.6328, address: '黑龙江省牡丹江市', pinyin: 'mudanjiang mdj' },
  { name: '佳木斯', lat: 46.7996, lng: 130.3751, address: '黑龙江省佳木斯市', pinyin: 'jiamusi jms' },
  { name: '鸡西', lat: 45.2953, lng: 130.9694, address: '黑龙江省鸡西市', pinyin: 'jixi jx2' },
  { name: '双鸭山', lat: 46.6430, lng: 131.1611, address: '黑龙江省双鸭山市', pinyin: 'shuangyashan sys' },
  { name: '鹤岗', lat: 47.3488, lng: 130.2980, address: '黑龙江省鹤岗市', pinyin: 'hegang hg2' },
  { name: '七台河', lat: 45.7708, lng: 131.0033, address: '黑龙江省七台河市', pinyin: 'qitaihe qth' },
  { name: '黑河', lat: 50.2452, lng: 127.5287, address: '黑龙江省黑河市', pinyin: 'heihe hh2' },
  { name: '伊春', lat: 47.7272, lng: 128.9100, address: '黑龙江省伊春市', pinyin: 'yichun yc4' },
  // 吉林省
  { name: '长春', lat: 43.8171, lng: 125.3235, address: '吉林省长春市', pinyin: 'changchun cc' },
  { name: '吉林', lat: 43.8378, lng: 126.5496, address: '吉林省吉林市', pinyin: 'jilin jl' },
  { name: '四平', lat: 43.1668, lng: 124.3504, address: '吉林省四平市', pinyin: 'siping sp' },
  { name: '延吉', lat: 42.9099, lng: 129.5130, address: '吉林省延吉市', pinyin: 'yanji yj2' },
  { name: '通化', lat: 41.7284, lng: 125.9393, address: '吉林省通化市', pinyin: 'tonghua th' },
  { name: '白城', lat: 45.6199, lng: 122.8394, address: '吉林省白城市', pinyin: 'baicheng bc' },
  { name: '松原', lat: 45.1415, lng: 124.8254, address: '吉林省松原市', pinyin: 'songyuan sy4' },
  { name: '辽源', lat: 42.9023, lng: 125.1434, address: '吉林省辽源市', pinyin: 'liaoyuan ly4' },
  { name: '白山', lat: 41.9395, lng: 126.4196, address: '吉林省白山市', pinyin: 'baishan bs' },
  // 江西省
  { name: '南昌', lat: 28.6820, lng: 115.8582, address: '江西省南昌市', pinyin: 'nanchang nc2' },
  { name: '赣州', lat: 25.8311, lng: 114.9330, address: '江西省赣州市', pinyin: 'ganzhou gz2' },
  { name: '九江', lat: 29.7055, lng: 115.9926, address: '江西省九江市', pinyin: 'jiujiang jj' },
  { name: '景德镇', lat: 29.2687, lng: 117.1786, address: '江西省景德镇市', pinyin: 'jingdezhen jdz' },
  { name: '萍乡', lat: 27.6238, lng: 113.8545, address: '江西省萍乡市', pinyin: 'pingxiang px' },
  { name: '上饶', lat: 28.4544, lng: 117.9429, address: '江西省上饶市', pinyin: 'shangrao sr' },
  { name: '吉安', lat: 27.1138, lng: 114.9924, address: '江西省吉安市', pinyin: 'jian ja' },
  { name: '宜春', lat: 27.8138, lng: 114.4162, address: '江西省宜春市', pinyin: 'yichun2 yc5' },
  { name: '抚州', lat: 27.9538, lng: 116.3583, address: '江西省抚州市', pinyin: 'fuzhou2 fz2' },
  { name: '鹰潭', lat: 28.2600, lng: 117.0659, address: '江西省鹰潭市', pinyin: 'yingtan yt2' },
  { name: '新余', lat: 27.8174, lng: 114.9168, address: '江西省新余市', pinyin: 'xinyu xy4' },
  // 云南省
  { name: '昆明', lat: 25.0453, lng: 102.7097, address: '云南省昆明市', pinyin: 'kunming km' },
  { name: '曲靖', lat: 25.4897, lng: 103.7968, address: '云南省曲靖市', pinyin: 'qujing qj' },
  { name: '玉溪', lat: 24.3520, lng: 102.5456, address: '云南省玉溪市', pinyin: 'yuxi yx' },
  { name: '大理', lat: 25.6066, lng: 100.2598, address: '云南省大理市', pinyin: 'dali dl2' },
  { name: '丽江', lat: 26.8721, lng: 100.2331, address: '云南省丽江市', pinyin: 'lijiang lj' },
  { name: '保山', lat: 25.1121, lng: 99.1624, address: '云南省保山市', pinyin: 'baoshan bshan' },
  { name: '昭通', lat: 27.3381, lng: 103.7172, address: '云南省昭通市', pinyin: 'zhaotong zt' },
  { name: '文山', lat: 23.3688, lng: 104.2448, address: '云南省文山市', pinyin: 'wenshan ws' },
  { name: '红河', lat: 23.3637, lng: 103.3748, address: '云南省红河州', pinyin: 'honghe hh3' },
  { name: '西双版纳', lat: 22.0073, lng: 100.7974, address: '云南省西双版纳傣族自治州', pinyin: 'xishuangbanna xsbn' },
  { name: '德宏', lat: 24.4316, lng: 98.5859, address: '云南省德宏傣族景颇族自治州', pinyin: 'dehong dhong' },
  // 贵州省
  { name: '贵阳', lat: 26.6470, lng: 106.6302, address: '贵州省贵阳市', pinyin: 'guiyang gy2' },
  { name: '遵义', lat: 27.7251, lng: 106.9271, address: '贵州省遵义市', pinyin: 'zunyi zy2' },
  { name: '毕节', lat: 27.3013, lng: 105.2919, address: '贵州省毕节市', pinyin: 'bijie bj3' },
  { name: '铜仁', lat: 27.7299, lng: 109.1813, address: '贵州省铜仁市', pinyin: 'tongren tr' },
  { name: '安顺', lat: 26.2453, lng: 105.9320, address: '贵州省安顺市', pinyin: 'anshun as2' },
  { name: '六盘水', lat: 26.5838, lng: 104.8309, address: '贵州省六盘水市', pinyin: 'liupanshui lps' },
  { name: '凯里', lat: 26.5683, lng: 107.9773, address: '贵州省凯里市', pinyin: 'kaili kl' },
  { name: '兴义', lat: 25.0921, lng: 104.8945, address: '贵州省兴义市', pinyin: 'xingyi xy5' },
  // 广西壮族自治区
  { name: '南宁', lat: 22.8170, lng: 108.3665, address: '广西壮族自治区南宁市', pinyin: 'nanning nn' },
  { name: '柳州', lat: 24.3264, lng: 109.4281, address: '广西壮族自治区柳州市', pinyin: 'liuzhou lz2' },
  { name: '桂林', lat: 25.2736, lng: 110.2907, address: '广西壮族自治区桂林市', pinyin: 'guilin gl' },
  { name: '玉林', lat: 22.6540, lng: 110.1642, address: '广西壮族自治区玉林市', pinyin: 'yulin2 yl2' },
  { name: '梧州', lat: 23.4864, lng: 111.3133, address: '广西壮族自治区梧州市', pinyin: 'wuzhou wz2' },
  { name: '北海', lat: 21.4808, lng: 109.1196, address: '广西壮族自治区北海市', pinyin: 'beihai bh' },
  { name: '防城港', lat: 21.6861, lng: 108.3524, address: '广西壮族自治区防城港市', pinyin: 'fangchenggang fcg' },
  { name: '钦州', lat: 21.9797, lng: 108.6543, address: '广西壮族自治区钦州市', pinyin: 'qinzhou qz3' },
  { name: '贵港', lat: 23.1116, lng: 109.6019, address: '广西壮族自治区贵港市', pinyin: 'guigang gg' },
  { name: '百色', lat: 23.9021, lng: 106.6180, address: '广西壮族自治区百色市', pinyin: 'baise bs2' },
  { name: '来宾', lat: 23.7502, lng: 109.2266, address: '广西壮族自治区来宾市', pinyin: 'laibin lb' },
  { name: '崇左', lat: 22.4156, lng: 107.3641, address: '广西壮族自治区崇左市', pinyin: 'chongzuo cz7' },
  { name: '贺州', lat: 24.4140, lng: 111.5662, address: '广西壮族自治区贺州市', pinyin: 'hezhou hez' },
  { name: '河池', lat: 24.6936, lng: 108.0852, address: '广西壮族自治区河池市', pinyin: 'hechi hc' },
  // 海南省
  { name: '海口', lat: 20.0440, lng: 110.1991, address: '海南省海口市', pinyin: 'haikou hk' },
  { name: '三亚', lat: 18.2528, lng: 109.5119, address: '海南省三亚市', pinyin: 'sanya sy5' },
  { name: '三沙', lat: 16.8299, lng: 112.3340, address: '海南省三沙市', pinyin: 'sansha ss' },
  { name: '儋州', lat: 19.5198, lng: 109.5809, address: '海南省儋州市', pinyin: 'danzhou dz2' },
  // 内蒙古自治区
  { name: '呼和浩特', lat: 40.8414, lng: 111.7519, address: '内蒙古自治区呼和浩特市', pinyin: 'huhehaote hhht' },
  { name: '包头', lat: 40.6575, lng: 109.8401, address: '内蒙古自治区包头市', pinyin: 'baotou bt' },
  { name: '鄂尔多斯', lat: 39.6086, lng: 109.7814, address: '内蒙古自治区鄂尔多斯市', pinyin: 'eerduosi eeds' },
  { name: '赤峰', lat: 42.2574, lng: 118.8881, address: '内蒙古自治区赤峰市', pinyin: 'chifeng cf' },
  { name: '通辽', lat: 43.6520, lng: 122.2437, address: '内蒙古自治区通辽市', pinyin: 'tongliao tl3' },
  { name: '乌兰察布', lat: 40.9938, lng: 113.1143, address: '内蒙古自治区乌兰察布市', pinyin: 'wulanchabu wlcb' },
  { name: '巴彦淖尔', lat: 40.7448, lng: 107.3875, address: '内蒙古自治区巴彦淖尔市', pinyin: 'bayannaoer byne' },
  { name: '呼伦贝尔', lat: 49.2116, lng: 119.7658, address: '内蒙古自治区呼伦贝尔市', pinyin: 'hulunbeier hlbe' },
  // 新疆维吾尔自治区
  { name: '乌鲁木齐', lat: 43.8256, lng: 87.6168, address: '新疆维吾尔自治区乌鲁木齐市', pinyin: 'wulumuqi wlmq' },
  { name: '喀什', lat: 39.4673, lng: 75.9896, address: '新疆维吾尔自治区喀什地区', pinyin: 'kashi ks' },
  { name: '克拉玛依', lat: 45.5800, lng: 84.8891, address: '新疆维吾尔自治区克拉玛依市', pinyin: 'kelamayi klmy' },
  { name: '吐鲁番', lat: 42.9478, lng: 89.1837, address: '新疆维吾尔自治区吐鲁番市', pinyin: 'tulufan tlf' },
  { name: '哈密', lat: 42.8176, lng: 93.5142, address: '新疆维吾尔自治区哈密市', pinyin: 'hami hm' },
  { name: '和田', lat: 37.1102, lng: 79.9217, address: '新疆维吾尔自治区和田地区', pinyin: 'hetian ht' },
  { name: '阿克苏', lat: 41.1681, lng: 80.2601, address: '新疆维吾尔自治区阿克苏地区', pinyin: 'akesu aks' },
  { name: '伊宁', lat: 43.9074, lng: 81.3244, address: '新疆维吾尔自治区伊宁市', pinyin: 'yining yn' },
  { name: '石河子', lat: 44.3059, lng: 86.0817, address: '新疆维吾尔自治区石河子市', pinyin: 'shihezi shz' },
  { name: '库尔勒', lat: 41.7254, lng: 86.1525, address: '新疆维吾尔自治区库尔勒市', pinyin: 'kuerle kel' },
  // 西藏自治区
  { name: '拉萨', lat: 29.6500, lng: 91.1000, address: '西藏自治区拉萨市', pinyin: 'lasa ls3' },
  { name: '日喀则', lat: 29.2677, lng: 88.8850, address: '西藏自治区日喀则市', pinyin: 'rikaze rkz' },
  { name: '昌都', lat: 31.1423, lng: 97.1717, address: '西藏自治区昌都市', pinyin: 'changdu cd3' },
  { name: '林芝', lat: 29.6490, lng: 94.3625, address: '西藏自治区林芝市', pinyin: 'linzhi lz3' },
  { name: '山南', lat: 29.2280, lng: 91.7732, address: '西藏自治区山南市', pinyin: 'shannan sn2' },
  { name: '那曲', lat: 31.4768, lng: 92.0513, address: '西藏自治区那曲市', pinyin: 'naqu nq' },
  // 宁夏回族自治区
  { name: '银川', lat: 38.4872, lng: 106.2309, address: '宁夏回族自治区银川市', pinyin: 'yinchuan yc6' },
  { name: '石嘴山', lat: 38.9842, lng: 106.3895, address: '宁夏回族自治区石嘴山市', pinyin: 'shizuishan szs' },
  { name: '吴忠', lat: 37.9974, lng: 106.1990, address: '宁夏回族自治区吴忠市', pinyin: 'wuzhong wz3' },
  { name: '固原', lat: 36.0154, lng: 106.2424, address: '宁夏回族自治区固原市', pinyin: 'guyuan gy3' },
  { name: '中卫', lat: 37.5200, lng: 105.1896, address: '宁夏回族自治区中卫市', pinyin: 'zhongwei zw' },
  // 青海省
  { name: '西宁', lat: 36.6177, lng: 101.7782, address: '青海省西宁市', pinyin: 'xining xn' },
  { name: '海东', lat: 36.5024, lng: 102.1042, address: '青海省海东市', pinyin: 'haidong hd2' },
  { name: '格尔木', lat: 36.4028, lng: 94.8984, address: '青海省格尔木市', pinyin: 'geermu gem' },
  { name: '德令哈', lat: 37.3647, lng: 97.3617, address: '青海省德令哈市', pinyin: 'delingha dlh' },
  // 甘肃省
  { name: '兰州', lat: 36.0611, lng: 103.8343, address: '甘肃省兰州市', pinyin: 'lanzhou lz4' },
  { name: '天水', lat: 34.5807, lng: 105.7245, address: '甘肃省天水市', pinyin: 'tianshui ts2' },
  { name: '武威', lat: 37.9284, lng: 102.6340, address: '甘肃省武威市', pinyin: 'wuwei ww' },
  { name: '酒泉', lat: 39.7432, lng: 98.4938, address: '甘肃省酒泉市', pinyin: 'jiuquan jq' },
  { name: '张掖', lat: 38.9258, lng: 100.4450, address: '甘肃省张掖市', pinyin: 'zhangye zy3' },
  { name: '庆阳', lat: 35.7093, lng: 107.6423, address: '甘肃省庆阳市', pinyin: 'qingyang qyang' },
  { name: '平凉', lat: 35.5424, lng: 106.6654, address: '甘肃省平凉市', pinyin: 'pingliang pl' },
  { name: '定西', lat: 35.5815, lng: 104.6268, address: '甘肃省定西市', pinyin: 'dingxi dx' },
  { name: '陇南', lat: 33.4023, lng: 104.9236, address: '甘肃省陇南市', pinyin: 'longnan ln' },
  { name: '嘉峪关', lat: 39.7867, lng: 98.2891, address: '甘肃省嘉峪关市', pinyin: 'jiayuguan jyg' },
  { name: '金昌', lat: 38.5205, lng: 102.1879, address: '甘肃省金昌市', pinyin: 'jinchang jcg' },
  { name: '白银', lat: 36.5446, lng: 104.1385, address: '甘肃省白银市', pinyin: 'baiyin by' },
  // 港澳台
  { name: '香港', lat: 22.3193, lng: 114.1694, address: '香港特别行政区', pinyin: 'xianggang hk2 hong kong' },
  { name: '澳门', lat: 22.1987, lng: 113.5439, address: '澳门特别行政区', pinyin: 'aomen macao macau' },
  { name: '台北', lat: 25.0478, lng: 121.5319, address: '台湾省台北市', pinyin: 'taibei tb' },
  { name: '台中', lat: 24.1477, lng: 120.6736, address: '台湾省台中市', pinyin: 'taizhong tz3' },
  { name: '高雄', lat: 22.6273, lng: 120.3014, address: '台湾省高雄市', pinyin: 'gaoxiong gx' },
  { name: '台南', lat: 22.9908, lng: 120.2133, address: '台湾省台南市', pinyin: 'tainan tn' },
]

// 城市别名映射（简称/别名 → 城市名，用于模糊搜索）
const CITY_ALIASES: Record<string, string> = {
  '京': '北京', '沪': '上海', '渝': '重庆', '津': '天津',
  '穗': '广州', '深': '深圳', '杭': '杭州', '苏': '苏州',
  '宁': '南京', '汉': '武汉', '蓉': '成都',
  '锡': '无锡', '常': '常州', '扬': '扬州', '镇': '镇江',
  '徐': '徐州', '通': '南通',
}

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
  const kw = keyword.toLowerCase().trim()
  if (!kw) {
    citySearchResults.value = []
    showCitySuggestions.value = false
    return
  }

  // 检查是否是别名（单字简称匹配）
  const aliasTarget = CITY_ALIASES[kw]

  const matched = MAJOR_CITIES.filter(c => {
    // 1. 城市名直接包含
    if (c.name.includes(kw)) return true
    // 2. 地址包含（支持省份搜索）
    if (c.address?.includes(kw)) return true
    // 3. 拼音匹配（全拼或缩写）
    if (c.pinyin && c.pinyin.toLowerCase().includes(kw)) return true
    // 4. 别名匹配
    return !!(aliasTarget && c.name === aliasTarget)
  }).slice(0, 8)

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
    // 更新 astrologyInfo：出生信息已变更，所有缓存失效
    userStore.updateAstrologyCache({
      birthCity: editForm.city,
      birthLat: editForm.lat,
      birthLng: editForm.lng,
      birthTime,
      hasNatalCache: false,
      hasSynastryCache: false,
      hasTransitCache: false,
    })
    // 清空本地已有的本命盘结果（因为出生信息变了）
    chartData.value = null
    interpretation.value = ''
    uni.showToast({ title: '出生信息已保存', icon: 'success' })
    // 返回本命盘主页
    step.value = 'form'
  } catch (e) {
    // 本地更新（降级）
    userStore.updateBirthInfo(editForm.city, editForm.lat, editForm.lng, birthTime)
    userStore.updateAstrologyCache({
      birthCity: editForm.city,
      birthLat: editForm.lat,
      birthLng: editForm.lng,
      birthTime,
      hasNatalCache: false,
      hasSynastryCache: false,
      hasTransitCache: false,
    })
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
    chartData.value = await getNatalChart()
    step.value = 'result'
    // 更新缓存标志位
    userStore.updateAstrologyCache({ hasNatalCache: true })
  } catch (e: any) {
    // 如果是出生信息未设置的错误（7001），回到 form 展示友好占位
    if (e?.code === 7001 || e?.message?.includes('出生信息')) {
      step.value = 'form'
      userStore.updateAstrologyCache({ hasNatalCache: false })
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
          <text class="birth-info-empty-text">尚未设置出生信息</text>
          <text class="birth-info-empty-sub">点击此处填写，或在星盘首页统一设置</text>
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

