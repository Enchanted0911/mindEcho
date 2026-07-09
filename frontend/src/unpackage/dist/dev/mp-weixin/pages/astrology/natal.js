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
    common_vendor.onMounted(async () => {
      step.value = "form";
      const astroInfo = userStore.astrologyInfo;
      if (hasBirthInfo() && (astroInfo == null ? void 0 : astroInfo.hasNatalCache)) {
        await calculateChart();
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
      // 直辖市
      { name: "北京", lat: 39.9042, lng: 116.4074, address: "北京市", pinyin: "beijing bj" },
      { name: "上海", lat: 31.2304, lng: 121.4737, address: "上海市", pinyin: "shanghai sh" },
      { name: "天津", lat: 39.3434, lng: 117.3616, address: "天津市", pinyin: "tianjin tj" },
      { name: "重庆", lat: 29.563, lng: 106.5516, address: "重庆市", pinyin: "chongqing cq" },
      // 广东省
      { name: "广州", lat: 23.1291, lng: 113.2644, address: "广东省广州市", pinyin: "guangzhou gz" },
      { name: "深圳", lat: 22.5431, lng: 114.0579, address: "广东省深圳市", pinyin: "shenzhen sz" },
      { name: "东莞", lat: 23.0207, lng: 113.7518, address: "广东省东莞市", pinyin: "dongguan dg" },
      { name: "佛山", lat: 23.0219, lng: 113.1219, address: "广东省佛山市", pinyin: "foshan fs" },
      { name: "珠海", lat: 22.2711, lng: 113.5767, address: "广东省珠海市", pinyin: "zhuhai zh" },
      { name: "惠州", lat: 23.1115, lng: 114.4152, address: "广东省惠州市", pinyin: "huizhou hz" },
      { name: "中山", lat: 22.5176, lng: 113.3926, address: "广东省中山市", pinyin: "zhongshan zs" },
      { name: "江门", lat: 22.5788, lng: 113.0819, address: "广东省江门市", pinyin: "jiangmen jm" },
      { name: "湛江", lat: 21.2707, lng: 110.3594, address: "广东省湛江市", pinyin: "zhanjiang zj" },
      { name: "汕头", lat: 23.3535, lng: 116.682, address: "广东省汕头市", pinyin: "shantou st" },
      { name: "潮州", lat: 23.6567, lng: 116.6226, address: "广东省潮州市", pinyin: "chaozhou cz" },
      { name: "揭阳", lat: 23.5497, lng: 116.3724, address: "广东省揭阳市", pinyin: "jieyang jy" },
      { name: "茂名", lat: 21.6631, lng: 110.9253, address: "广东省茂名市", pinyin: "maoming mm" },
      { name: "肇庆", lat: 23.047, lng: 112.4653, address: "广东省肇庆市", pinyin: "zhaoqing zq" },
      { name: "梅州", lat: 24.2882, lng: 116.1225, address: "广东省梅州市", pinyin: "meizhou mz" },
      { name: "清远", lat: 23.682, lng: 113.0563, address: "广东省清远市", pinyin: "qingyuan qy" },
      { name: "河源", lat: 23.7435, lng: 114.6979, address: "广东省河源市", pinyin: "heyuan hy" },
      { name: "阳江", lat: 21.8581, lng: 111.9822, address: "广东省阳江市", pinyin: "yangjiang yj" },
      { name: "云浮", lat: 22.9151, lng: 112.0445, address: "广东省云浮市", pinyin: "yunfu yf" },
      { name: "韶关", lat: 24.8107, lng: 113.5975, address: "广东省韶关市", pinyin: "shaoguan sg" },
      { name: "汕尾", lat: 22.7748, lng: 115.3756, address: "广东省汕尾市", pinyin: "shanwei sw" },
      // 浙江省
      { name: "杭州", lat: 30.2741, lng: 120.1551, address: "浙江省杭州市", pinyin: "hangzhou hz" },
      { name: "宁波", lat: 29.8683, lng: 121.544, address: "浙江省宁波市", pinyin: "ningbo nb" },
      { name: "温州", lat: 27.9938, lng: 120.6994, address: "浙江省温州市", pinyin: "wenzhou wz" },
      { name: "嘉兴", lat: 30.7522, lng: 120.7551, address: "浙江省嘉兴市", pinyin: "jiaxing jx" },
      { name: "湖州", lat: 30.8703, lng: 120.0869, address: "浙江省湖州市", pinyin: "huzhou huz" },
      { name: "绍兴", lat: 30.0023, lng: 120.5832, address: "浙江省绍兴市", pinyin: "shaoxing sx" },
      { name: "金华", lat: 29.0788, lng: 119.6474, address: "浙江省金华市", pinyin: "jinhua jh" },
      { name: "衢州", lat: 28.9359, lng: 118.8741, address: "浙江省衢州市", pinyin: "quzhou qz" },
      { name: "舟山", lat: 30.0361, lng: 122.1067, address: "浙江省舟山市", pinyin: "zhoushan zs2" },
      { name: "台州", lat: 28.6561, lng: 121.4206, address: "浙江省台州市", pinyin: "taizhou tz" },
      { name: "丽水", lat: 28.4677, lng: 119.923, address: "浙江省丽水市", pinyin: "lishui ls" },
      // 江苏省
      { name: "南京", lat: 32.0603, lng: 118.7969, address: "江苏省南京市", pinyin: "nanjing nj" },
      { name: "苏州", lat: 31.2989, lng: 120.5853, address: "江苏省苏州市", pinyin: "suzhou sz2" },
      { name: "无锡", lat: 31.4912, lng: 120.3119, address: "江苏省无锡市", pinyin: "wuxi wx" },
      { name: "常州", lat: 31.7744, lng: 119.9741, address: "江苏省常州市", pinyin: "changzhou cz2" },
      { name: "南通", lat: 31.9801, lng: 120.8944, address: "江苏省南通市", pinyin: "nantong nt" },
      { name: "扬州", lat: 32.3936, lng: 119.4127, address: "江苏省扬州市", pinyin: "yangzhou yz" },
      { name: "徐州", lat: 34.2044, lng: 117.2847, address: "江苏省徐州市", pinyin: "xuzhou xz" },
      { name: "镇江", lat: 32.1875, lng: 119.4253, address: "江苏省镇江市", pinyin: "zhenjiang zj2" },
      { name: "泰州", lat: 32.4547, lng: 119.9229, address: "江苏省泰州市", pinyin: "taizhou2 tz2" },
      { name: "盐城", lat: 33.348, lng: 120.1631, address: "江苏省盐城市", pinyin: "yancheng yc" },
      { name: "淮安", lat: 33.5518, lng: 119.0214, address: "江苏省淮安市", pinyin: "huaian ha" },
      { name: "连云港", lat: 34.5965, lng: 119.2214, address: "江苏省连云港市", pinyin: "lianyungang lyg" },
      { name: "宿迁", lat: 33.9631, lng: 118.275, address: "江苏省宿迁市", pinyin: "suqian sq" },
      // 四川省
      { name: "成都", lat: 30.5728, lng: 104.0668, address: "四川省成都市", pinyin: "chengdu cd" },
      { name: "绵阳", lat: 31.4678, lng: 104.6796, address: "四川省绵阳市", pinyin: "mianyang my" },
      { name: "德阳", lat: 31.127, lng: 104.3976, address: "四川省德阳市", pinyin: "deyang dy" },
      { name: "宜宾", lat: 28.7514, lng: 104.6426, address: "四川省宜宾市", pinyin: "yibin yb" },
      { name: "泸州", lat: 28.8718, lng: 105.4425, address: "四川省泸州市", pinyin: "luzhou lz" },
      { name: "南充", lat: 30.8368, lng: 106.1105, address: "四川省南充市", pinyin: "nanchong nc" },
      { name: "自贡", lat: 29.339, lng: 104.7787, address: "四川省自贡市", pinyin: "zigong zg" },
      { name: "攀枝花", lat: 26.5824, lng: 101.7183, address: "四川省攀枝花市", pinyin: "panzhihua pzh" },
      { name: "广元", lat: 32.4355, lng: 105.8434, address: "四川省广元市", pinyin: "guangyuan gy" },
      { name: "遂宁", lat: 30.5331, lng: 105.5927, address: "四川省遂宁市", pinyin: "suining sn" },
      { name: "内江", lat: 29.5806, lng: 105.0585, address: "四川省内江市", pinyin: "neijiang nj2" },
      { name: "乐山", lat: 29.5527, lng: 103.7661, address: "四川省乐山市", pinyin: "leshan ls2" },
      { name: "眉山", lat: 30.0748, lng: 103.8486, address: "四川省眉山市", pinyin: "meishan ms" },
      { name: "雅安", lat: 29.9997, lng: 103.0015, address: "四川省雅安市", pinyin: "yaan ya" },
      { name: "巴中", lat: 31.867, lng: 106.7478, address: "四川省巴中市", pinyin: "bazhong bz" },
      { name: "资阳", lat: 30.1221, lng: 104.6278, address: "四川省资阳市", pinyin: "ziyang zy" },
      // 湖北省
      { name: "武汉", lat: 30.5928, lng: 114.3055, address: "湖北省武汉市", pinyin: "wuhan wh" },
      { name: "宜昌", lat: 30.6918, lng: 111.2864, address: "湖北省宜昌市", pinyin: "yichang yc2" },
      { name: "襄阳", lat: 32.0084, lng: 112.1223, address: "湖北省襄阳市", pinyin: "xiangyang xy" },
      { name: "荆州", lat: 30.3354, lng: 112.2396, address: "湖北省荆州市", pinyin: "jingzhou jz" },
      { name: "十堰", lat: 32.6292, lng: 110.7987, address: "湖北省十堰市", pinyin: "shiyan sy2" },
      { name: "黄石", lat: 30.2006, lng: 115.0387, address: "湖北省黄石市", pinyin: "huangshi hs" },
      { name: "鄂州", lat: 30.3916, lng: 114.8951, address: "湖北省鄂州市", pinyin: "ezhou ez" },
      { name: "孝感", lat: 30.9244, lng: 113.9161, address: "湖北省孝感市", pinyin: "xiaogan xg" },
      { name: "黄冈", lat: 30.4534, lng: 114.8722, address: "湖北省黄冈市", pinyin: "huanggang hg" },
      { name: "随州", lat: 31.6899, lng: 113.3826, address: "湖北省随州市", pinyin: "suizhou sz3" },
      // 湖南省
      { name: "长沙", lat: 28.2278, lng: 112.9388, address: "湖南省长沙市", pinyin: "changsha cs" },
      { name: "株洲", lat: 27.8274, lng: 113.134, address: "湖南省株洲市", pinyin: "zhuzhou zz" },
      { name: "湘潭", lat: 27.8295, lng: 112.9447, address: "湖南省湘潭市", pinyin: "xiangtan xt" },
      { name: "岳阳", lat: 29.3572, lng: 113.1289, address: "湖南省岳阳市", pinyin: "yueyang yy" },
      { name: "常德", lat: 29.0322, lng: 111.6986, address: "湖南省常德市", pinyin: "changde cd2" },
      { name: "衡阳", lat: 26.8933, lng: 112.5719, address: "湖南省衡阳市", pinyin: "hengyang hy2" },
      { name: "邵阳", lat: 27.2394, lng: 111.4678, address: "湖南省邵阳市", pinyin: "shaoyang sy3" },
      { name: "益阳", lat: 28.5539, lng: 112.3551, address: "湖南省益阳市", pinyin: "yiyang yy2" },
      { name: "娄底", lat: 27.7003, lng: 111.9954, address: "湖南省娄底市", pinyin: "loudi ld" },
      { name: "郴州", lat: 25.77, lng: 113.0148, address: "湖南省郴州市", pinyin: "chenzhou cz3" },
      { name: "永州", lat: 26.4202, lng: 111.6148, address: "湖南省永州市", pinyin: "yongzhou yz2" },
      { name: "怀化", lat: 27.5703, lng: 109.9588, address: "湖南省怀化市", pinyin: "huaihua hh" },
      { name: "张家界", lat: 29.1248, lng: 110.4791, address: "湖南省张家界市", pinyin: "zhangjiajie zjj" },
      // 福建省
      { name: "福州", lat: 26.0745, lng: 119.2965, address: "福建省福州市", pinyin: "fuzhou fz" },
      { name: "厦门", lat: 24.4798, lng: 118.0894, address: "福建省厦门市", pinyin: "xiamen xm" },
      { name: "泉州", lat: 24.8741, lng: 118.6757, address: "福建省泉州市", pinyin: "quanzhou qz2" },
      { name: "漳州", lat: 24.5141, lng: 117.6472, address: "福建省漳州市", pinyin: "zhangzhou zz2" },
      { name: "莆田", lat: 25.454, lng: 119.0073, address: "福建省莆田市", pinyin: "putian pt" },
      { name: "三明", lat: 26.2654, lng: 117.6386, address: "福建省三明市", pinyin: "sanming sm" },
      { name: "南平", lat: 26.6351, lng: 118.1786, address: "福建省南平市", pinyin: "nanping np" },
      { name: "龙岩", lat: 25.0751, lng: 117.0177, address: "福建省龙岩市", pinyin: "longyan ly" },
      { name: "宁德", lat: 26.6658, lng: 119.5479, address: "福建省宁德市", pinyin: "ningde nd" },
      // 山东省
      { name: "济南", lat: 36.6512, lng: 117.1201, address: "山东省济南市", pinyin: "jinan jn" },
      { name: "青岛", lat: 36.0671, lng: 120.3826, address: "山东省青岛市", pinyin: "qingdao qd" },
      { name: "烟台", lat: 37.4638, lng: 121.4479, address: "山东省烟台市", pinyin: "yantai yt" },
      { name: "潍坊", lat: 36.7071, lng: 119.1616, address: "山东省潍坊市", pinyin: "weifang wf" },
      { name: "威海", lat: 37.513, lng: 122.1219, address: "山东省威海市", pinyin: "weihai wh2" },
      { name: "淄博", lat: 36.8132, lng: 118.0549, address: "山东省淄博市", pinyin: "zibo zb" },
      { name: "临沂", lat: 35.1046, lng: 118.3564, address: "山东省临沂市", pinyin: "linyi ly2" },
      { name: "济宁", lat: 35.4146, lng: 116.5869, address: "山东省济宁市", pinyin: "jining jn2" },
      { name: "菏泽", lat: 35.2333, lng: 115.48, address: "山东省菏泽市", pinyin: "heze hz2" },
      { name: "泰安", lat: 36.1996, lng: 117.0878, address: "山东省泰安市", pinyin: "taian ta" },
      { name: "东营", lat: 37.4343, lng: 118.6748, address: "山东省东营市", pinyin: "dongying dy2" },
      { name: "聊城", lat: 36.4562, lng: 115.9855, address: "山东省聊城市", pinyin: "liaocheng lc" },
      { name: "滨州", lat: 37.3836, lng: 117.97, address: "山东省滨州市", pinyin: "binzhou bz2" },
      { name: "德州", lat: 37.4354, lng: 116.3592, address: "山东省德州市", pinyin: "dezhou dz" },
      { name: "枣庄", lat: 34.8107, lng: 117.3219, address: "山东省枣庄市", pinyin: "zaozhuang zz3" },
      { name: "日照", lat: 35.4164, lng: 119.5268, address: "山东省日照市", pinyin: "rizhao rz" },
      // 河南省
      { name: "郑州", lat: 34.7473, lng: 113.6249, address: "河南省郑州市", pinyin: "zhengzhou zz4" },
      { name: "洛阳", lat: 34.6197, lng: 112.454, address: "河南省洛阳市", pinyin: "luoyang ly3" },
      { name: "开封", lat: 34.7971, lng: 114.3075, address: "河南省开封市", pinyin: "kaifeng kf" },
      { name: "新乡", lat: 35.3028, lng: 113.923, address: "河南省新乡市", pinyin: "xinxiang xx" },
      { name: "安阳", lat: 36.0975, lng: 114.3924, address: "河南省安阳市", pinyin: "anyang ay" },
      { name: "焦作", lat: 35.2395, lng: 113.2418, address: "河南省焦作市", pinyin: "jiaozuo jz2" },
      { name: "南阳", lat: 32.9905, lng: 112.5283, address: "河南省南阳市", pinyin: "nanyang ny" },
      { name: "许昌", lat: 34.0356, lng: 113.8522, address: "河南省许昌市", pinyin: "xuchang xc" },
      { name: "平顶山", lat: 33.7661, lng: 113.2914, address: "河南省平顶山市", pinyin: "pingdingshan pds" },
      { name: "信阳", lat: 32.1472, lng: 114.0913, address: "河南省信阳市", pinyin: "xinyang xy2" },
      { name: "周口", lat: 33.6477, lng: 114.6496, address: "河南省周口市", pinyin: "zhoukou zk" },
      { name: "驻马店", lat: 32.9826, lng: 114.0221, address: "河南省驻马店市", pinyin: "zhumadian zmd" },
      { name: "商丘", lat: 34.4143, lng: 115.6561, address: "河南省商丘市", pinyin: "shangqiu sq2" },
      { name: "濮阳", lat: 35.762, lng: 115.029, address: "河南省濮阳市", pinyin: "puyang py" },
      { name: "鹤壁", lat: 35.7474, lng: 114.2977, address: "河南省鹤壁市", pinyin: "hebi hb" },
      { name: "漯河", lat: 33.5757, lng: 114.0164, address: "河南省漯河市", pinyin: "luohe lh" },
      { name: "三门峡", lat: 34.7734, lng: 111.201, address: "河南省三门峡市", pinyin: "sanmenxia smx" },
      // 辽宁省
      { name: "沈阳", lat: 41.8057, lng: 123.4315, address: "辽宁省沈阳市", pinyin: "shenyang sy" },
      { name: "大连", lat: 38.914, lng: 121.6147, address: "辽宁省大连市", pinyin: "dalian dl" },
      { name: "鞍山", lat: 41.1085, lng: 122.9958, address: "辽宁省鞍山市", pinyin: "anshan as" },
      { name: "抚顺", lat: 41.8797, lng: 123.9571, address: "辽宁省抚顺市", pinyin: "fushun fs2" },
      { name: "本溪", lat: 41.2856, lng: 123.7667, address: "辽宁省本溪市", pinyin: "benxi bx" },
      { name: "锦州", lat: 41.1305, lng: 121.1268, address: "辽宁省锦州市", pinyin: "jinzhou jz3" },
      { name: "营口", lat: 40.6672, lng: 122.2347, address: "辽宁省营口市", pinyin: "yingkou yk" },
      { name: "阜新", lat: 42.0215, lng: 121.6686, address: "辽宁省阜新市", pinyin: "fuxin fx" },
      { name: "辽阳", lat: 41.2694, lng: 123.2354, address: "辽宁省辽阳市", pinyin: "liaoyang liay" },
      { name: "盘锦", lat: 41.1209, lng: 122.0705, address: "辽宁省盘锦市", pinyin: "panjin pj" },
      { name: "铁岭", lat: 42.2861, lng: 123.8443, address: "辽宁省铁岭市", pinyin: "tieling tl" },
      { name: "朝阳", lat: 41.5754, lng: 120.453, address: "辽宁省朝阳市", pinyin: "chaoyang cyang" },
      { name: "葫芦岛", lat: 40.7112, lng: 120.8369, address: "辽宁省葫芦岛市", pinyin: "huludao hld" },
      { name: "丹东", lat: 40.1292, lng: 124.3545, address: "辽宁省丹东市", pinyin: "dandong dd" },
      // 陕西省
      { name: "西安", lat: 34.3416, lng: 108.9398, address: "陕西省西安市", pinyin: "xian xa" },
      { name: "咸阳", lat: 34.3297, lng: 108.7089, address: "陕西省咸阳市", pinyin: "xianyang xy3" },
      { name: "宝鸡", lat: 34.3617, lng: 107.2373, address: "陕西省宝鸡市", pinyin: "baoji bj2" },
      { name: "渭南", lat: 34.4997, lng: 109.5095, address: "陕西省渭南市", pinyin: "weinan wn" },
      { name: "汉中", lat: 33.0667, lng: 107.0282, address: "陕西省汉中市", pinyin: "hanzhong hz3" },
      { name: "榆林", lat: 38.2856, lng: 109.7342, address: "陕西省榆林市", pinyin: "yulin yl" },
      { name: "安康", lat: 32.6841, lng: 109.0293, address: "陕西省安康市", pinyin: "ankang ak" },
      { name: "延安", lat: 36.5853, lng: 109.4897, address: "陕西省延安市", pinyin: "yanan yan" },
      { name: "铜川", lat: 34.8969, lng: 108.9451, address: "陕西省铜川市", pinyin: "tongchuan tc" },
      { name: "商洛", lat: 33.8706, lng: 109.9196, address: "陕西省商洛市", pinyin: "shangluo sl" },
      // 安徽省
      { name: "合肥", lat: 31.8206, lng: 117.2272, address: "安徽省合肥市", pinyin: "hefei hf" },
      { name: "芜湖", lat: 31.352, lng: 118.4329, address: "安徽省芜湖市", pinyin: "wuhu wh3" },
      { name: "蚌埠", lat: 32.9162, lng: 117.3795, address: "安徽省蚌埠市", pinyin: "bengbu bb" },
      { name: "淮南", lat: 32.6252, lng: 116.9993, address: "安徽省淮南市", pinyin: "huainan hn" },
      { name: "马鞍山", lat: 31.6704, lng: 118.5066, address: "安徽省马鞍山市", pinyin: "maanshan mas" },
      { name: "淮北", lat: 33.9559, lng: 116.7954, address: "安徽省淮北市", pinyin: "huaibei hb2" },
      { name: "铜陵", lat: 30.9451, lng: 117.8119, address: "安徽省铜陵市", pinyin: "tongling tl2" },
      { name: "安庆", lat: 30.543, lng: 117.0633, address: "安徽省安庆市", pinyin: "anqing aq" },
      { name: "黄山", lat: 29.7151, lng: 118.338, address: "安徽省黄山市", pinyin: "huangshan hs2" },
      { name: "滁州", lat: 32.3025, lng: 118.3166, address: "安徽省滁州市", pinyin: "chuzhou cz4" },
      { name: "阜阳", lat: 32.8989, lng: 115.8149, address: "安徽省阜阳市", pinyin: "fuyang fy" },
      { name: "宿州", lat: 33.6464, lng: 116.9641, address: "安徽省宿州市", pinyin: "suzhou sz4" },
      { name: "六安", lat: 31.7347, lng: 116.5231, address: "安徽省六安市", pinyin: "liuan la" },
      { name: "亳州", lat: 33.8445, lng: 115.7797, address: "安徽省亳州市", pinyin: "bozhou bz3" },
      { name: "池州", lat: 30.6648, lng: 117.4898, address: "安徽省池州市", pinyin: "chizhou cz5" },
      { name: "宣城", lat: 30.9406, lng: 118.7592, address: "安徽省宣城市", pinyin: "xuancheng xc2" },
      // 河北省
      { name: "石家庄", lat: 38.0428, lng: 114.5149, address: "河北省石家庄市", pinyin: "shijiazhuang sjz" },
      { name: "唐山", lat: 39.631, lng: 118.18, address: "河北省唐山市", pinyin: "tangshan ts" },
      { name: "秦皇岛", lat: 39.9355, lng: 119.5994, address: "河北省秦皇岛市", pinyin: "qinhuangdao qhd" },
      { name: "保定", lat: 38.8736, lng: 115.4644, address: "河北省保定市", pinyin: "baoding bd" },
      { name: "邯郸", lat: 36.6251, lng: 114.5389, address: "河北省邯郸市", pinyin: "handan hd" },
      { name: "邢台", lat: 37.0682, lng: 114.5048, address: "河北省邢台市", pinyin: "xingtai xt2" },
      { name: "张家口", lat: 40.8114, lng: 114.8796, address: "河北省张家口市", pinyin: "zhangjiakou zjk" },
      { name: "承德", lat: 40.9517, lng: 117.9626, address: "河北省承德市", pinyin: "chengde cgd" },
      { name: "沧州", lat: 38.3037, lng: 116.8388, address: "河北省沧州市", pinyin: "cangzhou cgz" },
      { name: "廊坊", lat: 39.5382, lng: 116.7032, address: "河北省廊坊市", pinyin: "langfang lf" },
      { name: "衡水", lat: 37.7357, lng: 115.671, address: "河北省衡水市", pinyin: "hengshui hs3" },
      // 山西省
      { name: "太原", lat: 37.8706, lng: 112.5489, address: "山西省太原市", pinyin: "taiyuan ty" },
      { name: "大同", lat: 40.0766, lng: 113.2982, address: "山西省大同市", pinyin: "datong dt" },
      { name: "阳泉", lat: 37.8579, lng: 113.5805, address: "山西省阳泉市", pinyin: "yangquan yq" },
      { name: "长治", lat: 36.1956, lng: 113.1164, address: "山西省长治市", pinyin: "changzhi cz6" },
      { name: "晋城", lat: 35.4906, lng: 112.8516, address: "山西省晋城市", pinyin: "jincheng jc" },
      { name: "朔州", lat: 39.3312, lng: 112.4328, address: "山西省朔州市", pinyin: "shuozhou sz5" },
      { name: "晋中", lat: 37.6872, lng: 112.7523, address: "山西省晋中市", pinyin: "jinzhong jz4" },
      { name: "运城", lat: 35.0224, lng: 111.007, address: "山西省运城市", pinyin: "yuncheng yc3" },
      { name: "忻州", lat: 38.4164, lng: 112.7343, address: "山西省忻州市", pinyin: "xinzhou xz2" },
      { name: "临汾", lat: 36.0882, lng: 111.5189, address: "山西省临汾市", pinyin: "linfen lf2" },
      { name: "吕梁", lat: 37.5177, lng: 111.1437, address: "山西省吕梁市", pinyin: "lvliang ll" },
      // 黑龙江省
      { name: "哈尔滨", lat: 45.8038, lng: 126.5349, address: "黑龙江省哈尔滨市", pinyin: "haerbin hrb" },
      { name: "齐齐哈尔", lat: 47.3479, lng: 123.9182, address: "黑龙江省齐齐哈尔市", pinyin: "qiqihaer qqhr" },
      { name: "大庆", lat: 46.5897, lng: 125.1032, address: "黑龙江省大庆市", pinyin: "daqing dq" },
      { name: "绥化", lat: 46.6537, lng: 126.9993, address: "黑龙江省绥化市", pinyin: "suihua sh2" },
      { name: "牡丹江", lat: 44.5526, lng: 129.6328, address: "黑龙江省牡丹江市", pinyin: "mudanjiang mdj" },
      { name: "佳木斯", lat: 46.7996, lng: 130.3751, address: "黑龙江省佳木斯市", pinyin: "jiamusi jms" },
      { name: "鸡西", lat: 45.2953, lng: 130.9694, address: "黑龙江省鸡西市", pinyin: "jixi jx2" },
      { name: "双鸭山", lat: 46.643, lng: 131.1611, address: "黑龙江省双鸭山市", pinyin: "shuangyashan sys" },
      { name: "鹤岗", lat: 47.3488, lng: 130.298, address: "黑龙江省鹤岗市", pinyin: "hegang hg2" },
      { name: "七台河", lat: 45.7708, lng: 131.0033, address: "黑龙江省七台河市", pinyin: "qitaihe qth" },
      { name: "黑河", lat: 50.2452, lng: 127.5287, address: "黑龙江省黑河市", pinyin: "heihe hh2" },
      { name: "伊春", lat: 47.7272, lng: 128.91, address: "黑龙江省伊春市", pinyin: "yichun yc4" },
      // 吉林省
      { name: "长春", lat: 43.8171, lng: 125.3235, address: "吉林省长春市", pinyin: "changchun cc" },
      { name: "吉林", lat: 43.8378, lng: 126.5496, address: "吉林省吉林市", pinyin: "jilin jl" },
      { name: "四平", lat: 43.1668, lng: 124.3504, address: "吉林省四平市", pinyin: "siping sp" },
      { name: "延吉", lat: 42.9099, lng: 129.513, address: "吉林省延吉市", pinyin: "yanji yj2" },
      { name: "通化", lat: 41.7284, lng: 125.9393, address: "吉林省通化市", pinyin: "tonghua th" },
      { name: "白城", lat: 45.6199, lng: 122.8394, address: "吉林省白城市", pinyin: "baicheng bc" },
      { name: "松原", lat: 45.1415, lng: 124.8254, address: "吉林省松原市", pinyin: "songyuan sy4" },
      { name: "辽源", lat: 42.9023, lng: 125.1434, address: "吉林省辽源市", pinyin: "liaoyuan ly4" },
      { name: "白山", lat: 41.9395, lng: 126.4196, address: "吉林省白山市", pinyin: "baishan bs" },
      // 江西省
      { name: "南昌", lat: 28.682, lng: 115.8582, address: "江西省南昌市", pinyin: "nanchang nc2" },
      { name: "赣州", lat: 25.8311, lng: 114.933, address: "江西省赣州市", pinyin: "ganzhou gz2" },
      { name: "九江", lat: 29.7055, lng: 115.9926, address: "江西省九江市", pinyin: "jiujiang jj" },
      { name: "景德镇", lat: 29.2687, lng: 117.1786, address: "江西省景德镇市", pinyin: "jingdezhen jdz" },
      { name: "萍乡", lat: 27.6238, lng: 113.8545, address: "江西省萍乡市", pinyin: "pingxiang px" },
      { name: "上饶", lat: 28.4544, lng: 117.9429, address: "江西省上饶市", pinyin: "shangrao sr" },
      { name: "吉安", lat: 27.1138, lng: 114.9924, address: "江西省吉安市", pinyin: "jian ja" },
      { name: "宜春", lat: 27.8138, lng: 114.4162, address: "江西省宜春市", pinyin: "yichun2 yc5" },
      { name: "抚州", lat: 27.9538, lng: 116.3583, address: "江西省抚州市", pinyin: "fuzhou2 fz2" },
      { name: "鹰潭", lat: 28.26, lng: 117.0659, address: "江西省鹰潭市", pinyin: "yingtan yt2" },
      { name: "新余", lat: 27.8174, lng: 114.9168, address: "江西省新余市", pinyin: "xinyu xy4" },
      // 云南省
      { name: "昆明", lat: 25.0453, lng: 102.7097, address: "云南省昆明市", pinyin: "kunming km" },
      { name: "曲靖", lat: 25.4897, lng: 103.7968, address: "云南省曲靖市", pinyin: "qujing qj" },
      { name: "玉溪", lat: 24.352, lng: 102.5456, address: "云南省玉溪市", pinyin: "yuxi yx" },
      { name: "大理", lat: 25.6066, lng: 100.2598, address: "云南省大理市", pinyin: "dali dl2" },
      { name: "丽江", lat: 26.8721, lng: 100.2331, address: "云南省丽江市", pinyin: "lijiang lj" },
      { name: "保山", lat: 25.1121, lng: 99.1624, address: "云南省保山市", pinyin: "baoshan bshan" },
      { name: "昭通", lat: 27.3381, lng: 103.7172, address: "云南省昭通市", pinyin: "zhaotong zt" },
      { name: "文山", lat: 23.3688, lng: 104.2448, address: "云南省文山市", pinyin: "wenshan ws" },
      { name: "红河", lat: 23.3637, lng: 103.3748, address: "云南省红河州", pinyin: "honghe hh3" },
      { name: "西双版纳", lat: 22.0073, lng: 100.7974, address: "云南省西双版纳傣族自治州", pinyin: "xishuangbanna xsbn" },
      { name: "德宏", lat: 24.4316, lng: 98.5859, address: "云南省德宏傣族景颇族自治州", pinyin: "dehong dhong" },
      // 贵州省
      { name: "贵阳", lat: 26.647, lng: 106.6302, address: "贵州省贵阳市", pinyin: "guiyang gy2" },
      { name: "遵义", lat: 27.7251, lng: 106.9271, address: "贵州省遵义市", pinyin: "zunyi zy2" },
      { name: "毕节", lat: 27.3013, lng: 105.2919, address: "贵州省毕节市", pinyin: "bijie bj3" },
      { name: "铜仁", lat: 27.7299, lng: 109.1813, address: "贵州省铜仁市", pinyin: "tongren tr" },
      { name: "安顺", lat: 26.2453, lng: 105.932, address: "贵州省安顺市", pinyin: "anshun as2" },
      { name: "六盘水", lat: 26.5838, lng: 104.8309, address: "贵州省六盘水市", pinyin: "liupanshui lps" },
      { name: "凯里", lat: 26.5683, lng: 107.9773, address: "贵州省凯里市", pinyin: "kaili kl" },
      { name: "兴义", lat: 25.0921, lng: 104.8945, address: "贵州省兴义市", pinyin: "xingyi xy5" },
      // 广西壮族自治区
      { name: "南宁", lat: 22.817, lng: 108.3665, address: "广西壮族自治区南宁市", pinyin: "nanning nn" },
      { name: "柳州", lat: 24.3264, lng: 109.4281, address: "广西壮族自治区柳州市", pinyin: "liuzhou lz2" },
      { name: "桂林", lat: 25.2736, lng: 110.2907, address: "广西壮族自治区桂林市", pinyin: "guilin gl" },
      { name: "玉林", lat: 22.654, lng: 110.1642, address: "广西壮族自治区玉林市", pinyin: "yulin2 yl2" },
      { name: "梧州", lat: 23.4864, lng: 111.3133, address: "广西壮族自治区梧州市", pinyin: "wuzhou wz2" },
      { name: "北海", lat: 21.4808, lng: 109.1196, address: "广西壮族自治区北海市", pinyin: "beihai bh" },
      { name: "防城港", lat: 21.6861, lng: 108.3524, address: "广西壮族自治区防城港市", pinyin: "fangchenggang fcg" },
      { name: "钦州", lat: 21.9797, lng: 108.6543, address: "广西壮族自治区钦州市", pinyin: "qinzhou qz3" },
      { name: "贵港", lat: 23.1116, lng: 109.6019, address: "广西壮族自治区贵港市", pinyin: "guigang gg" },
      { name: "百色", lat: 23.9021, lng: 106.618, address: "广西壮族自治区百色市", pinyin: "baise bs2" },
      { name: "来宾", lat: 23.7502, lng: 109.2266, address: "广西壮族自治区来宾市", pinyin: "laibin lb" },
      { name: "崇左", lat: 22.4156, lng: 107.3641, address: "广西壮族自治区崇左市", pinyin: "chongzuo cz7" },
      { name: "贺州", lat: 24.414, lng: 111.5662, address: "广西壮族自治区贺州市", pinyin: "hezhou hez" },
      { name: "河池", lat: 24.6936, lng: 108.0852, address: "广西壮族自治区河池市", pinyin: "hechi hc" },
      // 海南省
      { name: "海口", lat: 20.044, lng: 110.1991, address: "海南省海口市", pinyin: "haikou hk" },
      { name: "三亚", lat: 18.2528, lng: 109.5119, address: "海南省三亚市", pinyin: "sanya sy5" },
      { name: "三沙", lat: 16.8299, lng: 112.334, address: "海南省三沙市", pinyin: "sansha ss" },
      { name: "儋州", lat: 19.5198, lng: 109.5809, address: "海南省儋州市", pinyin: "danzhou dz2" },
      // 内蒙古自治区
      { name: "呼和浩特", lat: 40.8414, lng: 111.7519, address: "内蒙古自治区呼和浩特市", pinyin: "huhehaote hhht" },
      { name: "包头", lat: 40.6575, lng: 109.8401, address: "内蒙古自治区包头市", pinyin: "baotou bt" },
      { name: "鄂尔多斯", lat: 39.6086, lng: 109.7814, address: "内蒙古自治区鄂尔多斯市", pinyin: "eerduosi eeds" },
      { name: "赤峰", lat: 42.2574, lng: 118.8881, address: "内蒙古自治区赤峰市", pinyin: "chifeng cf" },
      { name: "通辽", lat: 43.652, lng: 122.2437, address: "内蒙古自治区通辽市", pinyin: "tongliao tl3" },
      { name: "乌兰察布", lat: 40.9938, lng: 113.1143, address: "内蒙古自治区乌兰察布市", pinyin: "wulanchabu wlcb" },
      { name: "巴彦淖尔", lat: 40.7448, lng: 107.3875, address: "内蒙古自治区巴彦淖尔市", pinyin: "bayannaoer byne" },
      { name: "呼伦贝尔", lat: 49.2116, lng: 119.7658, address: "内蒙古自治区呼伦贝尔市", pinyin: "hulunbeier hlbe" },
      // 新疆维吾尔自治区
      { name: "乌鲁木齐", lat: 43.8256, lng: 87.6168, address: "新疆维吾尔自治区乌鲁木齐市", pinyin: "wulumuqi wlmq" },
      { name: "喀什", lat: 39.4673, lng: 75.9896, address: "新疆维吾尔自治区喀什地区", pinyin: "kashi ks" },
      { name: "克拉玛依", lat: 45.58, lng: 84.8891, address: "新疆维吾尔自治区克拉玛依市", pinyin: "kelamayi klmy" },
      { name: "吐鲁番", lat: 42.9478, lng: 89.1837, address: "新疆维吾尔自治区吐鲁番市", pinyin: "tulufan tlf" },
      { name: "哈密", lat: 42.8176, lng: 93.5142, address: "新疆维吾尔自治区哈密市", pinyin: "hami hm" },
      { name: "和田", lat: 37.1102, lng: 79.9217, address: "新疆维吾尔自治区和田地区", pinyin: "hetian ht" },
      { name: "阿克苏", lat: 41.1681, lng: 80.2601, address: "新疆维吾尔自治区阿克苏地区", pinyin: "akesu aks" },
      { name: "伊宁", lat: 43.9074, lng: 81.3244, address: "新疆维吾尔自治区伊宁市", pinyin: "yining yn" },
      { name: "石河子", lat: 44.3059, lng: 86.0817, address: "新疆维吾尔自治区石河子市", pinyin: "shihezi shz" },
      { name: "库尔勒", lat: 41.7254, lng: 86.1525, address: "新疆维吾尔自治区库尔勒市", pinyin: "kuerle kel" },
      // 西藏自治区
      { name: "拉萨", lat: 29.65, lng: 91.1, address: "西藏自治区拉萨市", pinyin: "lasa ls3" },
      { name: "日喀则", lat: 29.2677, lng: 88.885, address: "西藏自治区日喀则市", pinyin: "rikaze rkz" },
      { name: "昌都", lat: 31.1423, lng: 97.1717, address: "西藏自治区昌都市", pinyin: "changdu cd3" },
      { name: "林芝", lat: 29.649, lng: 94.3625, address: "西藏自治区林芝市", pinyin: "linzhi lz3" },
      { name: "山南", lat: 29.228, lng: 91.7732, address: "西藏自治区山南市", pinyin: "shannan sn2" },
      { name: "那曲", lat: 31.4768, lng: 92.0513, address: "西藏自治区那曲市", pinyin: "naqu nq" },
      // 宁夏回族自治区
      { name: "银川", lat: 38.4872, lng: 106.2309, address: "宁夏回族自治区银川市", pinyin: "yinchuan yc6" },
      { name: "石嘴山", lat: 38.9842, lng: 106.3895, address: "宁夏回族自治区石嘴山市", pinyin: "shizuishan szs" },
      { name: "吴忠", lat: 37.9974, lng: 106.199, address: "宁夏回族自治区吴忠市", pinyin: "wuzhong wz3" },
      { name: "固原", lat: 36.0154, lng: 106.2424, address: "宁夏回族自治区固原市", pinyin: "guyuan gy3" },
      { name: "中卫", lat: 37.52, lng: 105.1896, address: "宁夏回族自治区中卫市", pinyin: "zhongwei zw" },
      // 青海省
      { name: "西宁", lat: 36.6177, lng: 101.7782, address: "青海省西宁市", pinyin: "xining xn" },
      { name: "海东", lat: 36.5024, lng: 102.1042, address: "青海省海东市", pinyin: "haidong hd2" },
      { name: "格尔木", lat: 36.4028, lng: 94.8984, address: "青海省格尔木市", pinyin: "geermu gem" },
      { name: "德令哈", lat: 37.3647, lng: 97.3617, address: "青海省德令哈市", pinyin: "delingha dlh" },
      // 甘肃省
      { name: "兰州", lat: 36.0611, lng: 103.8343, address: "甘肃省兰州市", pinyin: "lanzhou lz4" },
      { name: "天水", lat: 34.5807, lng: 105.7245, address: "甘肃省天水市", pinyin: "tianshui ts2" },
      { name: "武威", lat: 37.9284, lng: 102.634, address: "甘肃省武威市", pinyin: "wuwei ww" },
      { name: "酒泉", lat: 39.7432, lng: 98.4938, address: "甘肃省酒泉市", pinyin: "jiuquan jq" },
      { name: "张掖", lat: 38.9258, lng: 100.445, address: "甘肃省张掖市", pinyin: "zhangye zy3" },
      { name: "庆阳", lat: 35.7093, lng: 107.6423, address: "甘肃省庆阳市", pinyin: "qingyang qyang" },
      { name: "平凉", lat: 35.5424, lng: 106.6654, address: "甘肃省平凉市", pinyin: "pingliang pl" },
      { name: "定西", lat: 35.5815, lng: 104.6268, address: "甘肃省定西市", pinyin: "dingxi dx" },
      { name: "陇南", lat: 33.4023, lng: 104.9236, address: "甘肃省陇南市", pinyin: "longnan ln" },
      { name: "嘉峪关", lat: 39.7867, lng: 98.2891, address: "甘肃省嘉峪关市", pinyin: "jiayuguan jyg" },
      { name: "金昌", lat: 38.5205, lng: 102.1879, address: "甘肃省金昌市", pinyin: "jinchang jcg" },
      { name: "白银", lat: 36.5446, lng: 104.1385, address: "甘肃省白银市", pinyin: "baiyin by" },
      // 港澳台
      { name: "香港", lat: 22.3193, lng: 114.1694, address: "香港特别行政区", pinyin: "xianggang hk2 hong kong" },
      { name: "澳门", lat: 22.1987, lng: 113.5439, address: "澳门特别行政区", pinyin: "aomen macao macau" },
      { name: "台北", lat: 25.0478, lng: 121.5319, address: "台湾省台北市", pinyin: "taibei tb" },
      { name: "台中", lat: 24.1477, lng: 120.6736, address: "台湾省台中市", pinyin: "taizhong tz3" },
      { name: "高雄", lat: 22.6273, lng: 120.3014, address: "台湾省高雄市", pinyin: "gaoxiong gx" },
      { name: "台南", lat: 22.9908, lng: 120.2133, address: "台湾省台南市", pinyin: "tainan tn" }
    ];
    const CITY_ALIASES = {
      "京": "北京",
      "沪": "上海",
      "渝": "重庆",
      "津": "天津",
      "穗": "广州",
      "深": "深圳",
      "杭": "杭州",
      "苏": "苏州",
      "宁": "南京",
      "汉": "武汉",
      "蓉": "成都",
      "锡": "无锡",
      "常": "常州",
      "扬": "扬州",
      "镇": "镇江",
      "徐": "徐州",
      "通": "南通"
    };
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
      const kw = keyword.toLowerCase().trim();
      if (!kw) {
        citySearchResults.value = [];
        showCitySuggestions.value = false;
        return;
      }
      const aliasTarget = CITY_ALIASES[kw];
      const matched = MAJOR_CITIES.filter((c) => {
        var _a;
        if (c.name.includes(kw))
          return true;
        if ((_a = c.address) == null ? void 0 : _a.includes(kw))
          return true;
        if (c.pinyin && c.pinyin.toLowerCase().includes(kw))
          return true;
        return !!(aliasTarget && c.name === aliasTarget);
      }).slice(0, 8);
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
        userStore.updateAstrologyCache({
          birthCity: editForm.city,
          birthLat: editForm.lat,
          birthLng: editForm.lng,
          birthTime,
          hasNatalCache: false,
          hasSynastryCache: false,
          hasTransitCache: false
        });
        chartData.value = null;
        interpretation.value = "";
        common_vendor.index.showToast({ title: "出生信息已保存", icon: "success" });
        step.value = "form";
      } catch (e) {
        userStore.updateBirthInfo(editForm.city, editForm.lat, editForm.lng, birthTime);
        userStore.updateAstrologyCache({
          birthCity: editForm.city,
          birthLat: editForm.lat,
          birthLng: editForm.lng,
          birthTime,
          hasNatalCache: false,
          hasSynastryCache: false,
          hasTransitCache: false
        });
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
        chartData.value = await api_astrology.getNatalChart();
        step.value = "result";
        userStore.updateAstrologyCache({ hasNatalCache: true });
      } catch (e) {
        if ((e == null ? void 0 : e.code) === 7001 || ((_a = e == null ? void 0 : e.message) == null ? void 0 : _a.includes("出生信息"))) {
          step.value = "form";
          userStore.updateAstrologyCache({ hasNatalCache: false });
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
        aa: common_vendor.o(calculateChart, "dd")
      }) : {}, {
        ab: showMapModal.value
      }, showMapModal.value ? common_vendor.e({
        ac: common_vendor.o(closeMapModal, "c8"),
        ad: mapLat.value,
        ae: mapLng.value,
        af: mapScale.value,
        ag: mapMarkers.value,
        ah: common_vendor.o(onMapTap, "c3"),
        ai: showMapConfirm.value
      }, showMapConfirm.value ? {
        aj: common_vendor.t(mapAddress.value),
        ak: common_vendor.o(cancelMapLocation, "0b"),
        al: common_vendor.o(confirmMapLocation, "7f")
      } : {}, {
        am: common_vendor.o(() => {
        }, "0a"),
        an: common_vendor.o(closeMapModal, "0c")
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
        ax: common_vendor.o(($event) => activeTab.value = "planets", "2f"),
        ay: activeTab.value === "aspects" ? 1 : "",
        az: common_vendor.o(($event) => activeTab.value = "aspects", "75"),
        aA: activeTab.value === "interpret" ? 1 : "",
        aB: common_vendor.o(($event) => activeTab.value = "interpret", "54"),
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
        aQ: common_vendor.o(getInterpretation, "d9")
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
        aW: common_vendor.o(getInterpretation, "af")
      } : {}) : {}, {
        aX: common_vendor.o(backToForm, "51"),
        aY: common_vendor.o(openBirthEdit, "c0"),
        aZ: common_vendor.o(goInterpret, "5f")
      }) : {}, {
        ba: common_vendor.o(closeSuggestions, "5e")
      });
    };
  }
});
wx.createPage(_sfc_main);
//# sourceMappingURL=../../../.sourcemap/mp-weixin/pages/astrology/natal.js.map
