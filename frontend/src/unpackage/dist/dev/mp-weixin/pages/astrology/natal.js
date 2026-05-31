"use strict";
const common_vendor = require("../../common/vendor.js");
const api_astrology = require("../../api/astrology.js");
const api_auth = require("../../api/auth.js");
const store_user = require("../../store/user.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "natal",
  setup(__props) {
    const userStore = store_user.useUserStore();
    const editForm = common_vendor.reactive({
      year: (/* @__PURE__ */ new Date()).getFullYear() - 25,
      month: 1,
      day: 1,
      hour: 8,
      minute: 0,
      city: "",
      lat: null,
      lng: null
    });
    const step = common_vendor.ref("form");
    const loadingText = common_vendor.ref("正在解析星体轨迹...");
    const chartData = common_vendor.ref(null);
    const interpretation = common_vendor.ref("");
    const isInterpreting = common_vendor.ref(false);
    const selectedFocus = common_vendor.ref("personality");
    const activeTab = common_vendor.ref("planets");
    let typingGeneration = 0;
    const citySearchKeyword = common_vendor.ref("");
    const citySearchResults = common_vendor.ref([]);
    const showCitySuggestions = common_vendor.ref(false);
    const showMapModal = common_vendor.ref(false);
    const mapLat = common_vendor.ref(39.9042);
    const mapLng = common_vendor.ref(116.4074);
    const mapScale = common_vendor.ref(10);
    const pendingLat = common_vendor.ref(null);
    const pendingLng = common_vendor.ref(null);
    const showMapConfirm = common_vendor.ref(false);
    const mapMarkers = common_vendor.ref([]);
    const mapAddress = common_vendor.ref("");
    const isSavingBirthInfo = common_vendor.ref(false);
    function hasBirthInfo() {
      const info = userStore.userInfo;
      return !!((info == null ? void 0 : info.birthCity) && (info == null ? void 0 : info.birthTime));
    }
    common_vendor.onMounted(() => {
      if (hasBirthInfo()) {
        step.value = "form";
      } else {
        initEditFormFromStore();
        step.value = "birthEdit";
      }
    });
    function initEditFormFromStore() {
      const info = userStore.userInfo;
      if (info == null ? void 0 : info.birthCity) {
        citySearchKeyword.value = info.birthCity;
        editForm.city = info.birthCity;
        editForm.lat = info.birthLat ?? null;
        editForm.lng = info.birthLng ?? null;
      }
      if (info == null ? void 0 : info.birthTime) {
        try {
          const [datePart, timePart] = info.birthTime.split(" ");
          const [y, m, d] = datePart.split("-").map(Number);
          const [h, min] = timePart.split(":").map(Number);
          if (y && m && d) {
            editForm.year = y;
            editForm.month = m;
            editForm.day = d;
          }
          if (!isNaN(h))
            editForm.hour = h;
          if (!isNaN(min))
            editForm.minute = min;
        } catch {
        }
      }
    }
    function openBirthEdit() {
      initEditFormFromStore();
      citySearchKeyword.value = editForm.city;
      showCitySuggestions.value = false;
      step.value = "birthEdit";
    }
    const FOCUS_OPTIONS = [
      { key: "personality", label: "性格人格", icon: "✨" },
      { key: "emotion", label: "情感模式", icon: "💫" },
      { key: "career", label: "天赋事业", icon: "⚡" },
      { key: "growth", label: "成长课题", icon: "🌱" },
      { key: "shadow", label: "阴影人格", icon: "🌑" }
    ];
    const PLANET_DISPLAY = {
      sun: { symbol: "☉", name: "太阳", color: "#FFD700" },
      moon: { symbol: "☽", name: "月亮", color: "#C8C8FF" },
      mercury: { symbol: "☿", name: "水星", color: "#90EE90" },
      venus: { symbol: "♀", name: "金星", color: "#FFB6C1" },
      mars: { symbol: "♂", name: "火星", color: "#FF6B6B" },
      jupiter: { symbol: "♃", name: "木星", color: "#DEB887" },
      saturn: { symbol: "♄", name: "土星", color: "#8B9DC3" },
      uranus: { symbol: "⛢", name: "天王星", color: "#7FDBFF" },
      neptune: { symbol: "♆", name: "海王星", color: "#4169E1" },
      pluto: { symbol: "♇", name: "冥王星", color: "#9B59B6" },
      // 轴点：Python 库(kerykeion/flatlib)可能以多种名称返回
      ascendant: { symbol: "AC", name: "上升点", color: "#E8D5B7" },
      asc: { symbol: "AC", name: "上升点", color: "#E8D5B7" },
      "asc.": { symbol: "AC", name: "上升点", color: "#E8D5B7" },
      midheaven: { symbol: "MC", name: "天顶", color: "#B7D5E8" },
      mc: { symbol: "MC", name: "天顶", color: "#B7D5E8" },
      "mc.": { symbol: "MC", name: "天顶", color: "#B7D5E8" },
      "medium coeli": { symbol: "MC", name: "天顶", color: "#B7D5E8" },
      ic: { symbol: "IC", name: "天底", color: "#D5B7E8" },
      "ic.": { symbol: "IC", name: "天底", color: "#D5B7E8" },
      "imum coeli": { symbol: "IC", name: "天底", color: "#D5B7E8" },
      descendant: { symbol: "DC", name: "下降点", color: "#E8B7D5" },
      dc: { symbol: "DC", name: "下降点", color: "#E8B7D5" },
      "dc.": { symbol: "DC", name: "下降点", color: "#E8B7D5" },
      // 节点
      "north node": { symbol: "☊", name: "北交点", color: "#C8E8B7" },
      north_node: { symbol: "☊", name: "北交点", color: "#C8E8B7" },
      "south node": { symbol: "☋", name: "南交点", color: "#E8C8B7" },
      south_node: { symbol: "☋", name: "南交点", color: "#E8C8B7" },
      chiron: { symbol: "⚷", name: "凯龙星", color: "#B7E8C8" }
    };
    const ASPECT_NAME_MAP = {
      conjunction: { label: "☌ 合相", harmony: "neutral" },
      sextile: { label: "⚹ 六分", harmony: "positive" },
      square: { label: "□ 四分", harmony: "challenge" },
      trine: { label: "△ 三分", harmony: "positive" },
      opposition: { label: "☍ 对分", harmony: "challenge" },
      quincunx: { label: "⚻ 梅花", harmony: "neutral" },
      semisextile: { label: "⚺ 二十分", harmony: "neutral" },
      sesquiquadrate: { label: "⚼ 倍半方", harmony: "challenge" },
      semisquare: { label: "∠ 半方", harmony: "challenge" },
      quintile: { label: "五分", harmony: "positive" },
      biquintile: { label: "双五分", harmony: "positive" }
    };
    const ZODIAC_ZH = {
      aries: "白羊座",
      taurus: "金牛座",
      gemini: "双子座",
      cancer: "巨蟹座",
      leo: "狮子座",
      virgo: "处女座",
      libra: "天秤座",
      scorpio: "天蝎座",
      sagittarius: "射手座",
      capricorn: "摩羯座",
      aquarius: "水瓶座",
      pisces: "双鱼座"
    };
    function zodiacZh(sign) {
      if (!sign)
        return "";
      const key = sign.toLowerCase().trim();
      return ZODIAC_ZH[key] || sign;
    }
    const realPlanets = common_vendor.computed(() => {
      var _a, _b;
      const planets = (_b = (_a = chartData.value) == null ? void 0 : _a.chart) == null ? void 0 : _b.planets;
      if (!planets)
        return [];
      const result = [];
      const planetOrder = ["sun", "moon", "mercury", "venus", "mars", "jupiter", "saturn", "uranus", "neptune", "pluto"];
      for (const key of planetOrder) {
        const p = planets[key];
        const display = PLANET_DISPLAY[key];
        if (!p || !display)
          continue;
        const signRaw = p.sign || p.zodiac_sign || p.zodiac || "";
        const sign = zodiacZh(signRaw);
        const houseNum = p.house ?? p.house_number ?? null;
        const house = houseNum != null ? `第${houseNum}宫` : "";
        const degree = p.longitude ?? p.degree ?? p.lon ?? null;
        let strength = 60;
        if (p.speed != null) {
          strength = Math.min(100, Math.max(20, Math.round(Math.abs(Number(p.speed)) * 200 + 40)));
        } else if (p.strength != null) {
          strength = Math.min(100, Math.max(20, Math.round(Number(p.strength))));
        }
        result.push({ symbol: display.symbol, name: display.name, sign, house, color: display.color, strength, degree });
      }
      return result;
    });
    const AXIS_KEYS = /* @__PURE__ */ new Set(["ascendant", "asc", "asc.", "midheaven", "mc", "mc.", "medium coeli", "descendant", "dc", "dc.", "ic", "ic.", "imum coeli"]);
    const realAspects = common_vendor.computed(() => {
      var _a, _b, _c, _d, _e;
      const aspects = (_b = (_a = chartData.value) == null ? void 0 : _a.chart) == null ? void 0 : _b.aspects;
      if (!aspects || !Array.isArray(aspects))
        return [];
      const angles = (_d = (_c = chartData.value) == null ? void 0 : _c.chart) == null ? void 0 : _d.angles;
      const angleSignMap = {};
      if (angles) {
        for (const key of Object.keys(angles)) {
          const sign = ((_e = angles[key]) == null ? void 0 : _e.sign) || "";
          if (sign)
            angleSignMap[key.toLowerCase()] = sign;
        }
        if (angleSignMap["ascendant"] && !angleSignMap["asc"])
          angleSignMap["asc"] = angleSignMap["ascendant"];
        if (angleSignMap["midheaven"] && !angleSignMap["mc"])
          angleSignMap["mc"] = angleSignMap["midheaven"];
        if (angleSignMap["mc"] && !angleSignMap["midheaven"])
          angleSignMap["midheaven"] = angleSignMap["mc"];
      }
      return aspects.slice(0, 20).map((a) => {
        const p1Key = (a.planet1 || a.body1 || a.planet_a || a.p1 || a.planet || "").toLowerCase().trim();
        const p2Key = (a.planet2 || a.body2 || a.planet_b || a.p2 || a.other_planet || "").toLowerCase().trim();
        if (AXIS_KEYS.has(p1Key) && AXIS_KEYS.has(p2Key))
          return null;
        const p1Display = PLANET_DISPLAY[p1Key];
        const p2Display = PLANET_DISPLAY[p2Key];
        if (!p1Display && !p2Display && (!p1Key || !p2Key))
          return null;
        const aspectType = (a.aspect || a.type || a.aspect_type || "conjunction").toLowerCase().trim();
        const aspectInfo = ASPECT_NAME_MAP[aspectType] ?? { label: aspectType, harmony: "neutral" };
        const p1SignRaw = a.planet1_sign || a.sign1 || a.p1_sign || angleSignMap[p1Key] || "";
        const p2SignRaw = a.planet2_sign || a.sign2 || a.p2_sign || angleSignMap[p2Key] || "";
        const p1SignZh = zodiacZh(p1SignRaw);
        const p2SignZh = zodiacZh(p2SignRaw);
        const p1Label = p1Display ? `${p1Display.symbol} ${p1Display.name}${p1SignZh ? " · " + p1SignZh : ""}` : p1Key || "?";
        const p2Label = p2Display ? `${p2Display.symbol} ${p2Display.name}${p2SignZh ? " · " + p2SignZh : ""}` : p2Key || "?";
        const orb = a.orb ?? a.angle_diff ?? null;
        const orbStr = orb != null ? ` (${Math.abs(Number(orb)).toFixed(1)}°)` : "";
        return {
          p1: p1Label,
          aspect: aspectInfo.label,
          p2: p2Label,
          harmony: aspectInfo.harmony,
          orbStr
        };
      }).filter(Boolean);
    });
    const chartSummary = common_vendor.computed(() => {
      var _a, _b, _c, _d, _e, _f, _g, _h, _i, _j, _k, _l, _m, _n, _o, _p, _q;
      const s = (_a = chartData.value) == null ? void 0 : _a.summary;
      if (!s) {
        const planets = (_c = (_b = chartData.value) == null ? void 0 : _b.chart) == null ? void 0 : _c.planets;
        const angles = (_e = (_d = chartData.value) == null ? void 0 : _d.chart) == null ? void 0 : _e.angles;
        return {
          sunSign: zodiacZh(((_f = planets == null ? void 0 : planets.sun) == null ? void 0 : _f.sign) || ((_g = planets == null ? void 0 : planets.sun) == null ? void 0 : _g.zodiac_sign) || ""),
          moonSign: zodiacZh(((_h = planets == null ? void 0 : planets.moon) == null ? void 0 : _h.sign) || ((_i = planets == null ? void 0 : planets.moon) == null ? void 0 : _i.zodiac_sign) || ""),
          ascSign: zodiacZh(((_j = angles == null ? void 0 : angles.ascendant) == null ? void 0 : _j.sign) || ((_k = angles == null ? void 0 : angles.ascendant) == null ? void 0 : _k.zodiac_sign) || "")
        };
      }
      return {
        sunSign: zodiacZh(((_l = s.sun) == null ? void 0 : _l.sign) || ((_m = s.sun) == null ? void 0 : _m.zodiac_sign) || ""),
        moonSign: zodiacZh(((_n = s.moon) == null ? void 0 : _n.sign) || ((_o = s.moon) == null ? void 0 : _o.zodiac_sign) || ""),
        ascSign: zodiacZh(((_p = s.ascendant) == null ? void 0 : _p.sign) || ((_q = s.ascendant) == null ? void 0 : _q.zodiac_sign) || "")
      };
    });
    const YEARS = Array.from({ length: 80 }, (_, i) => 1950 + i);
    const MONTHS = Array.from({ length: 12 }, (_, i) => i + 1);
    const DAYS = Array.from({ length: 31 }, (_, i) => i + 1);
    const HOURS = Array.from({ length: 24 }, (_, i) => i);
    const MINUTES = Array.from({ length: 60 }, (_, i) => i);
    const YEAR_OPTIONS = YEARS.map((y) => ({ label: y + "年", value: y }));
    const MONTH_OPTIONS = MONTHS.map((m) => ({ label: m + "月", value: m }));
    const DAY_OPTIONS = DAYS.map((d) => ({ label: d + "日", value: d }));
    const HOUR_OPTIONS = HOURS.map((h) => ({ label: h + "时", value: h }));
    const MINUTE_OPTIONS = MINUTES.map((m) => ({ label: String(m).padStart(2, "0") + "分", value: m }));
    function onYearChange(e) {
      editForm.year = Number(YEAR_OPTIONS[e.detail.value].value);
    }
    function onMonthChange(e) {
      editForm.month = Number(MONTH_OPTIONS[e.detail.value].value);
    }
    function onDayChange(e) {
      editForm.day = Number(DAY_OPTIONS[e.detail.value].value);
    }
    function onHourChange(e) {
      editForm.hour = Number(HOUR_OPTIONS[e.detail.value].value);
    }
    function onMinuteChange(e) {
      editForm.minute = Number(MINUTE_OPTIONS[e.detail.value].value);
    }
    const MAJOR_CITIES = [
      { name: "北京", lat: 39.9042, lng: 116.4074, address: "北京市" },
      { name: "上海", lat: 31.2304, lng: 121.4737, address: "上海市" },
      { name: "广州", lat: 23.1291, lng: 113.2644, address: "广东省广州市" },
      { name: "深圳", lat: 22.5431, lng: 114.0579, address: "广东省深圳市" },
      { name: "杭州", lat: 30.2741, lng: 120.1551, address: "浙江省杭州市" },
      { name: "成都", lat: 30.5728, lng: 104.0668, address: "四川省成都市" },
      { name: "重庆", lat: 29.563, lng: 106.5516, address: "重庆市" },
      { name: "武汉", lat: 30.5928, lng: 114.3055, address: "湖北省武汉市" },
      { name: "西安", lat: 34.3416, lng: 108.9398, address: "陕西省西安市" },
      { name: "南京", lat: 32.0603, lng: 118.7969, address: "江苏省南京市" },
      { name: "天津", lat: 39.3434, lng: 117.3616, address: "天津市" },
      { name: "苏州", lat: 31.2989, lng: 120.5853, address: "江苏省苏州市" },
      { name: "郑州", lat: 34.7473, lng: 113.6249, address: "河南省郑州市" },
      { name: "长沙", lat: 28.2278, lng: 112.9388, address: "湖南省长沙市" },
      { name: "沈阳", lat: 41.8057, lng: 123.4315, address: "辽宁省沈阳市" },
      { name: "青岛", lat: 36.0671, lng: 120.3826, address: "山东省青岛市" },
      { name: "济南", lat: 36.6512, lng: 117.1201, address: "山东省济南市" },
      { name: "大连", lat: 38.914, lng: 121.6147, address: "辽宁省大连市" },
      { name: "厦门", lat: 24.4798, lng: 118.0894, address: "福建省厦门市" },
      { name: "福州", lat: 26.0745, lng: 119.2965, address: "福建省福州市" },
      { name: "宁波", lat: 29.8683, lng: 121.544, address: "浙江省宁波市" },
      { name: "无锡", lat: 31.4912, lng: 120.3119, address: "江苏省无锡市" },
      { name: "合肥", lat: 31.8206, lng: 117.2272, address: "安徽省合肥市" },
      { name: "昆明", lat: 25.0453, lng: 102.7097, address: "云南省昆明市" },
      { name: "哈尔滨", lat: 45.8038, lng: 126.5349, address: "黑龙江省哈尔滨市" },
      { name: "长春", lat: 43.8171, lng: 125.3235, address: "吉林省长春市" },
      { name: "南昌", lat: 28.682, lng: 115.8582, address: "江西省南昌市" },
      { name: "贵阳", lat: 26.647, lng: 106.6302, address: "贵州省贵阳市" },
      { name: "南宁", lat: 22.817, lng: 108.3665, address: "广西壮族自治区南宁市" },
      { name: "呼和浩特", lat: 40.8414, lng: 111.7519, address: "内蒙古自治区呼和浩特市" },
      { name: "乌鲁木齐", lat: 43.8256, lng: 87.6168, address: "新疆维吾尔自治区乌鲁木齐市" },
      { name: "拉萨", lat: 29.65, lng: 91.1, address: "西藏自治区拉萨市" },
      { name: "银川", lat: 38.4872, lng: 106.2309, address: "宁夏回族自治区银川市" },
      { name: "西宁", lat: 36.6177, lng: 101.7782, address: "青海省西宁市" },
      { name: "兰州", lat: 36.0611, lng: 103.8343, address: "甘肃省兰州市" },
      { name: "太原", lat: 37.8706, lng: 112.5489, address: "山西省太原市" },
      { name: "石家庄", lat: 38.0428, lng: 114.5149, address: "河北省石家庄市" },
      { name: "海口", lat: 20.044, lng: 110.1991, address: "海南省海口市" }
    ];
    let searchTimer = null;
    function onCityInput(e) {
      const kw = e.detail.value;
      citySearchKeyword.value = kw;
      if (!kw.trim()) {
        showCitySuggestions.value = false;
        citySearchResults.value = [];
        editForm.city = "";
        editForm.lat = null;
        editForm.lng = null;
        return;
      }
      clearTimeout(searchTimer);
      searchTimer = setTimeout(() => {
        searchCities(kw.trim());
      }, 300);
    }
    function searchCities(keyword) {
      const matched = MAJOR_CITIES.filter(
        (c) => {
          var _a;
          return c.name.includes(keyword) || ((_a = c.address) == null ? void 0 : _a.includes(keyword));
        }
      ).slice(0, 6);
      citySearchResults.value = matched;
      showCitySuggestions.value = matched.length > 0;
    }
    function selectCity(city) {
      editForm.city = city.name;
      editForm.lat = city.lat;
      editForm.lng = city.lng;
      citySearchKeyword.value = city.name;
      showCitySuggestions.value = false;
    }
    function openMapModal() {
      showCitySuggestions.value = false;
      if (editForm.lat && editForm.lng) {
        mapLat.value = editForm.lat;
        mapLng.value = editForm.lng;
        mapMarkers.value = [{ id: 1, latitude: editForm.lat, longitude: editForm.lng, width: 32, height: 32 }];
      } else {
        mapLat.value = 39.9042;
        mapLng.value = 116.4074;
        mapMarkers.value = [];
      }
      pendingLat.value = null;
      pendingLng.value = null;
      showMapConfirm.value = false;
      mapAddress.value = "";
      showMapModal.value = true;
    }
    function onMapTap(e) {
      var _a, _b, _c, _d;
      const lat = ((_a = e.detail) == null ? void 0 : _a.latitude) ?? ((_b = e.detail) == null ? void 0 : _b.lat);
      const lng = ((_c = e.detail) == null ? void 0 : _c.longitude) ?? ((_d = e.detail) == null ? void 0 : _d.lng);
      if (!lat || !lng)
        return;
      pendingLat.value = lat;
      pendingLng.value = lng;
      mapLat.value = lat;
      mapLng.value = lng;
      mapMarkers.value = [{ id: 1, latitude: lat, longitude: lng, width: 32, height: 32 }];
      mapAddress.value = `${lat.toFixed(4)}°N, ${lng.toFixed(4)}°E`;
      showMapConfirm.value = true;
    }
    function confirmMapLocation() {
      if (pendingLat.value === null || pendingLng.value === null)
        return;
      editForm.lat = pendingLat.value;
      editForm.lng = pendingLng.value;
      const nearest = findNearestCity(pendingLat.value, pendingLng.value);
      if (nearest) {
        editForm.city = nearest.name;
        citySearchKeyword.value = nearest.name;
      } else {
        const label = `${pendingLat.value.toFixed(2)}°N,${pendingLng.value.toFixed(2)}°E`;
        editForm.city = label;
        citySearchKeyword.value = label;
      }
      showMapConfirm.value = false;
      showMapModal.value = false;
    }
    function cancelMapLocation() {
      showMapConfirm.value = false;
      pendingLat.value = null;
      pendingLng.value = null;
      if (editForm.lat && editForm.lng) {
        mapMarkers.value = [{ id: 1, latitude: editForm.lat, longitude: editForm.lng, width: 32, height: 32 }];
      } else {
        mapMarkers.value = [];
      }
    }
    function closeMapModal() {
      showMapModal.value = false;
      showMapConfirm.value = false;
    }
    function findNearestCity(lat, lng) {
      let minDist = Infinity;
      let nearest = null;
      for (const c of MAJOR_CITIES) {
        const d = Math.sqrt((c.lat - lat) ** 2 + (c.lng - lng) ** 2);
        if (d < minDist) {
          minDist = d;
          nearest = c;
        }
      }
      return minDist <= 2 ? nearest : null;
    }
    function closeSuggestions() {
      showCitySuggestions.value = false;
    }
    function getBirthTimeStr() {
      const m = String(editForm.month).padStart(2, "0");
      const d = String(editForm.day).padStart(2, "0");
      const h = String(editForm.hour).padStart(2, "0");
      const min = String(editForm.minute).padStart(2, "0");
      return `${editForm.year}-${m}-${d} ${h}:${min}`;
    }
    async function saveBirthInfo() {
      if (!editForm.city.trim()) {
        common_vendor.index.showToast({ title: "请输入或选择出生城市", icon: "none" });
        return;
      }
      isSavingBirthInfo.value = true;
      const birthTime = getBirthTimeStr();
      try {
        await api_auth.updateBirthInfo({
          birthCity: editForm.city,
          birthLat: editForm.lat,
          birthLng: editForm.lng,
          birthTime
        });
        userStore.updateBirthInfo(editForm.city, editForm.lat, editForm.lng, birthTime);
        chartData.value = null;
        interpretation.value = "";
        common_vendor.index.showToast({ title: "出生信息已保存", icon: "success" });
        step.value = "form";
      } catch (e) {
        userStore.updateBirthInfo(editForm.city, editForm.lat, editForm.lng, birthTime);
        chartData.value = null;
        interpretation.value = "";
        common_vendor.index.showToast({ title: "保存失败，已本地更新", icon: "none" });
        step.value = "form";
      } finally {
        isSavingBirthInfo.value = false;
      }
    }
    async function calculateChart() {
      var _a;
      if (!hasBirthInfo()) {
        common_vendor.index.showModal({
          title: "未设置出生信息",
          content: "计算本命盘需要先设置出生信息，是否现在设置？",
          confirmText: "去设置",
          cancelText: "取消",
          success: (res) => {
            if (res.confirm) {
              openBirthEdit();
            }
          }
        });
        return;
      }
      step.value = "loading";
      const LOADING_TEXTS = [
        "正在解析星体轨迹...",
        "正在连接宇宙能量...",
        "计算行星精确位置...",
        "生成你的星盘地图..."
      ];
      let textIdx = 0;
      const textTimer = setInterval(() => {
        textIdx = (textIdx + 1) % LOADING_TEXTS.length;
        loadingText.value = LOADING_TEXTS[textIdx];
      }, 1500);
      try {
        const result = await api_astrology.getNatalChart();
        chartData.value = result;
        step.value = "result";
      } catch (e) {
        if ((e == null ? void 0 : e.code) === 7001 || ((_a = e == null ? void 0 : e.message) == null ? void 0 : _a.includes("出生信息"))) {
          step.value = "form";
          common_vendor.index.showModal({
            title: "未设置出生信息",
            content: "请先设置出生信息再计算本命盘",
            confirmText: "去设置",
            cancelText: "取消",
            success: (res) => {
              if (res.confirm)
                openBirthEdit();
            }
          });
        } else {
          chartData.value = { chart: null, summary: null, savedToProfile: false };
          step.value = "result";
        }
      } finally {
        clearInterval(textTimer);
      }
    }
    async function getInterpretation() {
      if (!chartData.value)
        return;
      const myGeneration = ++typingGeneration;
      isInterpreting.value = true;
      interpretation.value = "";
      activeTab.value = "interpret";
      const TYPING_DELAY = 30;
      try {
        const result = await api_astrology.interpretNatal({
          focus: selectedFocus.value,
          tone: "gentle"
        });
        const text = result.interpretation;
        for (let i = 0; i <= text.length; i++) {
          if (typingGeneration !== myGeneration) {
            interpretation.value = text;
            return;
          }
          await new Promise((r) => setTimeout(r, TYPING_DELAY));
          if (typingGeneration !== myGeneration) {
            interpretation.value = text;
            return;
          }
          interpretation.value = text.slice(0, i);
        }
      } catch (e) {
        const mockText = `✨ 你的太阳位于狮子座，赋予你天然的光芒与表达欲望，你渴望被看见、被认可，同时也有着深沉的创造力。

月亮落在双鱼座的你，内心世界如海洋般深邃而敏感，情绪细腻，容易与他人的感受产生共鸣。这份敏感既是礼物，也需要学会为自己设立边界。

太阳与月亮形成柔和的三分相位，意味着你的意识与潜意识之间流动顺畅，自我认知相对清晰，内心的光与柔可以和谐共存。

最需要关注的成长课题在于：学会在展示自我的同时，也给自己静默的空间。🌙`;
        for (let i = 0; i <= mockText.length; i++) {
          if (typingGeneration !== myGeneration) {
            interpretation.value = mockText;
            return;
          }
          await new Promise((r) => setTimeout(r, 20));
          if (typingGeneration !== myGeneration) {
            interpretation.value = mockText;
            return;
          }
          interpretation.value = mockText.slice(0, i);
        }
      } finally {
        if (typingGeneration === myGeneration) {
          isInterpreting.value = false;
        }
      }
    }
    function backToForm() {
      step.value = "form";
      chartData.value = null;
      interpretation.value = "";
    }
    function goInterpret() {
      activeTab.value = "interpret";
      if (!interpretation.value) {
        getInterpretation();
      }
    }
    return (_ctx, _cache) => {
      var _a, _b, _c, _d, _e, _f, _g, _h, _i, _j, _k, _l, _m, _n, _o;
      return common_vendor.e({
        a: step.value === "birthEdit"
      }, step.value === "birthEdit" ? common_vendor.e({
        b: common_vendor.t(hasBirthInfo() ? "返回" : "取消"),
        c: common_vendor.o(($event) => hasBirthInfo() ? step.value = "form" : common_vendor.index.navigateBack(), "4c"),
        d: common_vendor.t(editForm.year),
        e: common_vendor.unref(YEAR_OPTIONS),
        f: common_vendor.unref(YEARS).indexOf(editForm.year),
        g: common_vendor.o(onYearChange, "53"),
        h: common_vendor.t(editForm.month),
        i: common_vendor.unref(MONTH_OPTIONS),
        j: editForm.month - 1,
        k: common_vendor.o(onMonthChange, "5a"),
        l: common_vendor.t(editForm.day),
        m: common_vendor.unref(DAY_OPTIONS),
        n: editForm.day - 1,
        o: common_vendor.o(onDayChange, "6d"),
        p: common_vendor.t(editForm.hour),
        q: common_vendor.unref(HOUR_OPTIONS),
        r: editForm.hour,
        s: common_vendor.o(onHourChange, "23"),
        t: common_vendor.t(String(editForm.minute).padStart(2, "0")),
        v: common_vendor.unref(MINUTE_OPTIONS),
        w: common_vendor.unref(MINUTES).indexOf(editForm.minute),
        x: common_vendor.o(onMinuteChange, "44"),
        y: citySearchKeyword.value,
        z: common_vendor.o(onCityInput, "70"),
        A: citySearchKeyword.value
      }, citySearchKeyword.value ? {
        B: common_vendor.o(() => {
          citySearchKeyword.value = "";
          editForm.city = "";
          editForm.lat = null;
          editForm.lng = null;
          showCitySuggestions.value = false;
        }, "3b")
      } : {}, {
        C: common_vendor.o(openMapModal, "8b"),
        D: showCitySuggestions.value && citySearchResults.value.length > 0
      }, showCitySuggestions.value && citySearchResults.value.length > 0 ? {
        E: common_vendor.f(citySearchResults.value, (city, k0, i0) => {
          return common_vendor.e({
            a: common_vendor.t(city.name),
            b: city.address
          }, city.address ? {
            c: common_vendor.t(city.address)
          } : {}, {
            d: city.name,
            e: common_vendor.o(($event) => selectCity(city), city.name)
          });
        })
      } : {}, {
        F: editForm.lat && editForm.lng
      }, editForm.lat && editForm.lng ? {
        G: common_vendor.t(editForm.city),
        H: common_vendor.t(editForm.lat.toFixed(4)),
        I: common_vendor.t(editForm.lng.toFixed(4))
      } : {}, {
        J: common_vendor.o(() => {
        }, "61"),
        K: hasBirthInfo()
      }, hasBirthInfo() ? {} : {}, {
        L: common_vendor.t(isSavingBirthInfo.value ? "保存中..." : "✦ 保存出生信息"),
        M: isSavingBirthInfo.value ? 1 : "",
        N: common_vendor.o(saveBirthInfo, "08")
      }) : {}, {
        O: step.value === "loading"
      }, step.value === "loading" ? {
        P: common_vendor.t(loadingText.value)
      } : {}, {
        Q: step.value === "form"
      }, step.value === "form" ? common_vendor.e({
        R: common_vendor.o(openBirthEdit, "42"),
        S: hasBirthInfo()
      }, hasBirthInfo() ? common_vendor.e({
        T: common_vendor.t((_a = common_vendor.unref(userStore).userInfo) == null ? void 0 : _a.birthTime),
        U: common_vendor.t((_b = common_vendor.unref(userStore).userInfo) == null ? void 0 : _b.birthCity),
        V: (_c = common_vendor.unref(userStore).userInfo) == null ? void 0 : _c.birthLat
      }, ((_d = common_vendor.unref(userStore).userInfo) == null ? void 0 : _d.birthLat) ? {
        W: common_vendor.t((_f = (_e = common_vendor.unref(userStore).userInfo) == null ? void 0 : _e.birthLat) == null ? void 0 : _f.toFixed(4)),
        X: common_vendor.t((_h = (_g = common_vendor.unref(userStore).userInfo) == null ? void 0 : _g.birthLng) == null ? void 0 : _h.toFixed(4))
      } : {}) : {
        Y: common_vendor.o(openBirthEdit, "46")
      }, {
        Z: !hasBirthInfo() ? 1 : "",
        aa: common_vendor.o(calculateChart, "bc")
      }) : {}, {
        ab: showMapModal.value
      }, showMapModal.value ? common_vendor.e({
        ac: common_vendor.o(closeMapModal, "bc"),
        ad: mapLat.value,
        ae: mapLng.value,
        af: mapScale.value,
        ag: mapMarkers.value,
        ah: common_vendor.o(onMapTap, "47"),
        ai: showMapConfirm.value
      }, showMapConfirm.value ? {
        aj: common_vendor.t(mapAddress.value),
        ak: common_vendor.o(cancelMapLocation, "02"),
        al: common_vendor.o(confirmMapLocation, "5f")
      } : {}, {
        am: common_vendor.o(() => {
        }, "9e"),
        an: common_vendor.o(closeMapModal, "bf")
      }) : {}, {
        ao: step.value === "result"
      }, step.value === "result" ? common_vendor.e({
        ap: common_vendor.f(12, (i, k0, i0) => {
          return {
            a: i,
            b: `rotate(${i * 30}deg)`
          };
        }),
        aq: common_vendor.f(realPlanets.value, (p, idx, i0) => {
          return {
            a: common_vendor.t(p.symbol),
            b: idx,
            c: p.degree != null ? `rotate(${p.degree - 90}deg) translateY(-110rpx) rotate(-${p.degree - 90}deg)` : `rotate(${idx * 36}deg) translateY(-110rpx) rotate(-${idx * 36}deg)`,
            d: p.color
          };
        }),
        ar: (_k = (_j = (_i = chartData.value) == null ? void 0 : _i.chart) == null ? void 0 : _j.angles) == null ? void 0 : _k.ascendant
      }, ((_n = (_m = (_l = chartData.value) == null ? void 0 : _l.chart) == null ? void 0 : _m.angles) == null ? void 0 : _n.ascendant) ? {} : {}, {
        as: common_vendor.t(chartSummary.value.sunSign || "—"),
        at: common_vendor.t(chartSummary.value.moonSign || "—"),
        av: common_vendor.t(chartSummary.value.ascSign || "—"),
        aw: activeTab.value === "planets" ? 1 : "",
        ax: common_vendor.o(($event) => activeTab.value = "planets", "fb"),
        ay: activeTab.value === "aspects" ? 1 : "",
        az: common_vendor.o(($event) => activeTab.value = "aspects", "1d"),
        aA: activeTab.value === "interpret" ? 1 : "",
        aB: common_vendor.o(($event) => activeTab.value = "interpret", "dd"),
        aC: activeTab.value === "planets"
      }, activeTab.value === "planets" ? common_vendor.e({
        aD: realPlanets.value.length === 0
      }, realPlanets.value.length === 0 ? {} : {}, {
        aE: common_vendor.f(realPlanets.value, (planet, k0, i0) => {
          return {
            a: common_vendor.t(planet.symbol),
            b: planet.color,
            c: planet.color + "22",
            d: planet.color + "44",
            e: common_vendor.t(planet.name),
            f: common_vendor.t(planet.sign || "—"),
            g: common_vendor.t(planet.house ? " · " + planet.house : ""),
            h: planet.strength + "%",
            i: planet.color,
            j: planet.name
          };
        })
      }) : {}, {
        aF: activeTab.value === "aspects"
      }, activeTab.value === "aspects" ? common_vendor.e({
        aG: realAspects.value.length === 0
      }, realAspects.value.length === 0 ? {} : {}, {
        aH: realAspects.value.length > 0
      }, realAspects.value.length > 0 ? {
        aI: common_vendor.t(realAspects.value.filter((a) => (a == null ? void 0 : a.harmony) === "positive").length),
        aJ: common_vendor.t(realAspects.value.filter((a) => (a == null ? void 0 : a.harmony) === "challenge").length),
        aK: common_vendor.t(realAspects.value.filter((a) => (a == null ? void 0 : a.harmony) === "neutral").length),
        aL: common_vendor.t(realAspects.value.length)
      } : {}, {
        aM: common_vendor.f(realAspects.value, (asp, idx, i0) => {
          return common_vendor.e({
            a: common_vendor.t(asp == null ? void 0 : asp.p1),
            b: common_vendor.t(asp == null ? void 0 : asp.aspect),
            c: common_vendor.n("badge-" + (asp == null ? void 0 : asp.harmony)),
            d: asp == null ? void 0 : asp.orbStr
          }, (asp == null ? void 0 : asp.orbStr) ? {
            e: common_vendor.t(asp.orbStr)
          } : {}, {
            f: common_vendor.t(asp == null ? void 0 : asp.p2),
            g: idx,
            h: common_vendor.n("aspect-" + (asp == null ? void 0 : asp.harmony))
          });
        })
      }) : {}, {
        aN: activeTab.value === "interpret"
      }, activeTab.value === "interpret" ? common_vendor.e({
        aO: common_vendor.f(FOCUS_OPTIONS, (opt, k0, i0) => {
          return {
            a: common_vendor.t(opt.icon),
            b: common_vendor.t(opt.label),
            c: opt.key,
            d: selectedFocus.value === opt.key ? 1 : "",
            e: common_vendor.o(($event) => selectedFocus.value = opt.key, opt.key)
          };
        }),
        aP: !interpretation.value && !isInterpreting.value
      }, !interpretation.value && !isInterpreting.value ? {
        aQ: common_vendor.o(getInterpretation, "33")
      } : {}, {
        aR: interpretation.value || isInterpreting.value
      }, interpretation.value || isInterpreting.value ? common_vendor.e({
        aS: common_vendor.t((_o = FOCUS_OPTIONS.find((o) => o.key === selectedFocus.value)) == null ? void 0 : _o.label),
        aT: isInterpreting.value
      }, isInterpreting.value ? {} : {}, {
        aU: common_vendor.t(interpretation.value)
      }) : {}, {
        aV: interpretation.value && !isInterpreting.value
      }, interpretation.value && !isInterpreting.value ? {
        aW: common_vendor.o(getInterpretation, "75")
      } : {}) : {}, {
        aX: common_vendor.o(backToForm, "f3"),
        aY: common_vendor.o(openBirthEdit, "3b"),
        aZ: common_vendor.o(goInterpret, "55")
      }) : {}, {
        ba: common_vendor.o(closeSuggestions, "5e")
      });
    };
  }
});
wx.createPage(_sfc_main);
//# sourceMappingURL=../../../.sourcemap/mp-weixin/pages/astrology/natal.js.map
