"use strict";
const common_vendor = require("../../common/vendor.js");
const api_astrology = require("../../api/astrology.js");
const api_auth = require("../../api/auth.js");
const store_user = require("../../store/user.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "synastry",
  setup(__props) {
    const PLANET_DISPLAY_S = {
      sun: { symbol: "☉", name: "太阳" },
      moon: { symbol: "☽", name: "月亮" },
      mercury: { symbol: "☿", name: "水星" },
      venus: { symbol: "♀", name: "金星" },
      mars: { symbol: "♂", name: "火星" },
      jupiter: { symbol: "♃", name: "木星" },
      saturn: { symbol: "♄", name: "土星" },
      uranus: { symbol: "⛢", name: "天王星" },
      neptune: { symbol: "♆", name: "海王星" },
      pluto: { symbol: "♇", name: "冥王星" },
      // 额外节点
      "north node": { symbol: "☊", name: "北交点" },
      "south node": { symbol: "☋", name: "南交点" },
      chiron: { symbol: "⚷", name: "凯龙星" },
      // 轴点：Python 库(kerykeion/flatlib)可能以多种名称返回 ASC / MC / IC / DC
      ascendant: { symbol: "AC", name: "上升点" },
      asc: { symbol: "AC", name: "上升点" },
      "asc.": { symbol: "AC", name: "上升点" },
      as: { symbol: "AC", name: "上升点" },
      midheaven: { symbol: "MC", name: "天顶" },
      mc: { symbol: "MC", name: "天顶" },
      "mc.": { symbol: "MC", name: "天顶" },
      "medium coeli": { symbol: "MC", name: "天顶" },
      // IC（天底）——Python 返回 "IC" / "Imum Coeli" 等
      ic: { symbol: "IC", name: "天底" },
      "ic.": { symbol: "IC", name: "天底" },
      "imum coeli": { symbol: "IC", name: "天底" },
      // DC（下降点）
      descendant: { symbol: "DC", name: "下降点" },
      dc: { symbol: "DC", name: "下降点" },
      "dc.": { symbol: "DC", name: "下降点" }
    };
    const ASPECT_LABEL_S = {
      conjunction: { label: "合相", symbol: "☌", harmony: "neutral" },
      sextile: { label: "六分相", symbol: "⚹", harmony: "positive" },
      square: { label: "四分相", symbol: "□", harmony: "challenge" },
      trine: { label: "三分相", symbol: "△", harmony: "positive" },
      opposition: { label: "对分相", symbol: "☍", harmony: "challenge" },
      quincunx: { label: "十二分之五", symbol: "⚻", harmony: "neutral" },
      semisextile: { label: "十二分之一", symbol: "⚺", harmony: "neutral" },
      sesquiquadrate: { label: "倍半四分", symbol: "⚼", harmony: "challenge" },
      semisquare: { label: "八分相", symbol: "∠", harmony: "challenge" },
      quintile: { label: "五分相", symbol: "Q", harmony: "positive" },
      biquintile: { label: "二五分相", symbol: "bQ", harmony: "positive" }
    };
    const ZODIAC_ZH_S = {
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
    function zodiacZhS(en) {
      var _a;
      return ZODIAC_ZH_S[(_a = en == null ? void 0 : en.toLowerCase()) == null ? void 0 : _a.trim()] || en || "";
    }
    const userStore = store_user.useUserStore();
    const step = common_vendor.ref("form");
    const loadingText = common_vendor.ref("正在解析双人星盘能量...");
    const chartData = common_vendor.ref(null);
    const interpretation = common_vendor.ref("");
    const isInterpreting = common_vendor.ref(false);
    const selectedInterpretType = common_vendor.ref("love");
    const activeTab = common_vendor.ref("analysis");
    let typingGeneration = 0;
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
    function hasBirthInfo() {
      const u = userStore.userInfo;
      return !!((u == null ? void 0 : u.birthCity) && (u == null ? void 0 : u.birthTime) && (u == null ? void 0 : u.birthLat) && (u == null ? void 0 : u.birthLng));
    }
    function goToNatalPage() {
      common_vendor.index.navigateTo({ url: "/pages/astrology/natal" });
    }
    const partnerForm = common_vendor.reactive({ year: 1997, month: 3, day: 22, hour: 10, minute: 0, city: "", lat: null, lng: null });
    const partnerName = common_vendor.ref("Ta");
    function onPartnerYearChange(e) {
      partnerForm.year = YEAR_OPTIONS[e.detail.value].value;
    }
    function onPartnerMonthChange(e) {
      partnerForm.month = MONTH_OPTIONS[e.detail.value].value;
    }
    function onPartnerDayChange(e) {
      partnerForm.day = DAY_OPTIONS[e.detail.value].value;
    }
    function onPartnerHourChange(e) {
      partnerForm.hour = HOUR_OPTIONS[e.detail.value].value;
    }
    function onPartnerMinuteChange(e) {
      partnerForm.minute = MINUTE_OPTIONS[e.detail.value].value;
    }
    const INTERPRET_TYPES = [
      { key: "love", label: "爱情关系", icon: "💞" },
      { key: "marriage", label: "婚姻契合", icon: "💍" },
      { key: "soul", label: "灵魂连接", icon: "✨" },
      { key: "emotion", label: "情绪共鸣", icon: "🌊" }
    ];
    function normalizeScore(raw, refMax) {
      if (refMax <= 0)
        return 0;
      const mapped = Math.round(raw / refMax * 100);
      return Math.max(0, Math.min(100, mapped));
    }
    const realAnalysis = common_vendor.computed(() => {
      var _a;
      const rm = (_a = chartData.value) == null ? void 0 : _a.relationshipModel;
      if (!rm)
        return [];
      const items = [];
      const attractionRaw = rm.attraction_score ?? rm.attraction ?? rm.magnetic_attraction;
      if (attractionRaw != null) {
        const val = normalizeScore(Number(attractionRaw), 5);
        items.push({ label: "吸引力", value: val, color: "#e070a0", desc: rm.attraction_desc || "双方星盘间的吸引强度" });
      }
      const emotionalRaw = rm.emotional_score ?? rm.emotional_compatibility ?? rm.emotional_match ?? rm.emotion_score;
      if (emotionalRaw != null) {
        const val = normalizeScore(Number(emotionalRaw), 15);
        items.push({ label: "情绪匹配", value: val, color: "#7080f0", desc: rm.emotion_desc || "情感波动的共鸣程度" });
      }
      const conflictRaw = rm.conflict_score ?? rm.conflict_index ?? rm.tension;
      if (conflictRaw != null) {
        const val = normalizeScore(Number(conflictRaw), 5);
        items.push({ label: "冲突指数", value: val, color: "#f09040", desc: rm.conflict_desc || "关系中的摩擦与张力" });
      }
      const stabilityRaw = rm.stability_score ?? rm.long_term_stability ?? rm.stability;
      if (stabilityRaw != null) {
        const val = normalizeScore(Number(stabilityRaw), 5);
        items.push({ label: "长期稳定", value: val, color: "#50c878", desc: rm.stability_desc || "长期关系的稳定基础" });
      }
      if (items.length === 0 && rm.compatibility_score != null) {
        items.push({ label: "综合契合", value: normalizeScore(Number(rm.compatibility_score), 100), color: "#9b87d1", desc: rm.summary || "整体关系契合度" });
      }
      return items;
    });
    const THEME_MAP = {
      // 情感类
      "emotional dependency": { title: "情感依赖", icon: "🌊", type: "neutral", desc: "双方在情感上有较深的相互依赖" },
      "emotional resonance": { title: "情感共鸣", icon: "💞", type: "positive", desc: "情绪状态高度同频，互相理解" },
      "emotional connection": { title: "情感纽带", icon: "💫", type: "positive", desc: "建立了深厚的情感连接" },
      "emotional conflict": { title: "情感冲突", icon: "⚡", type: "challenge", desc: "情绪表达方式存在摩擦" },
      // 稳定类
      "long-term stability": { title: "长期稳定", icon: "⚓", type: "positive", desc: "关系具备长久发展的坚实基础" },
      "stable foundation": { title: "稳固根基", icon: "🏛️", type: "positive", desc: "价值观一致，关系基础牢固" },
      "commitment potential": { title: "承诺潜力", icon: "💍", type: "positive", desc: "双方有建立长期承诺的倾向" },
      // 吸引类
      "intense attraction": { title: "强烈吸引", icon: "🔥", type: "positive", desc: "双方之间存在强烈的相互吸引" },
      "magnetic attraction": { title: "磁场吸引", icon: "✨", type: "positive", desc: "天然的磁场感应，互相被吸引" },
      "physical attraction": { title: "肢体吸引", icon: "💫", type: "positive", desc: "肢体层面有较强的吸引力" },
      // 沟通类
      "intellectual connection": { title: "智识共鸣", icon: "💡", type: "positive", desc: "思维方式相近，交流顺畅" },
      "communication harmony": { title: "沟通和谐", icon: "🗣️", type: "positive", desc: "表达方式互补，沟通无障碍" },
      "communication challenges": { title: "沟通挑战", icon: "⚡", type: "challenge", desc: "表达风格不同，需要更多耐心" },
      // 成长类
      "transformative relationship": { title: "蜕变关系", icon: "🦋", type: "neutral", desc: "这段关系将带来深刻的自我转化" },
      "growth potential": { title: "成长潜力", icon: "🌱", type: "positive", desc: "相互促进，共同成长" },
      "karmic connection": { title: "命运羁绊", icon: "🔮", type: "neutral", desc: "似乎有超越此生的灵魂连接" },
      "soul connection": { title: "灵魂连接", icon: "🌟", type: "positive", desc: "灵魂层面的深度共鸣" },
      // 挑战类
      "power struggles": { title: "权力拉锯", icon: "⚔️", type: "challenge", desc: "双方都有较强的主导欲，需要协调" },
      "tension and conflict": { title: "紧张冲突", icon: "⚡", type: "challenge", desc: "关系中存在一定的内在张力" },
      "value differences": { title: "价值差异", icon: "🌀", type: "challenge", desc: "核心价值观上存在分歧需磨合" }
    };
    function resolveTheme(raw) {
      const lower = raw.toLowerCase().trim();
      const mapped = THEME_MAP[lower];
      if (mapped)
        return mapped;
      for (const [key, val] of Object.entries(THEME_MAP)) {
        if (lower.includes(key) || key.includes(lower))
          return val;
      }
      const isChallenge = /conflict|struggle|tension|challenge|difficult|opposition|friction/i.test(raw);
      const isPositive = /harmony|resonance|attraction|connection|stability|growth|soul|love|romantic/i.test(raw);
      return {
        icon: isChallenge ? "⚡" : isPositive ? "✨" : "💫",
        title: raw,
        desc: "",
        type: isChallenge ? "challenge" : isPositive ? "positive" : "neutral"
      };
    }
    const realThemes = common_vendor.computed(() => {
      var _a, _b;
      const themesRaw = (_a = chartData.value) == null ? void 0 : _a.themes;
      if (themesRaw && Array.isArray(themesRaw) && themesRaw.length > 0) {
        return themesRaw.slice(0, 4).map((t) => {
          if (typeof t === "string") {
            return resolveTheme(t);
          }
          return {
            icon: t.icon || (t.type === "positive" ? "✨" : t.type === "challenge" ? "⚡" : "💫"),
            title: t.title || t.name || t.theme || String(t),
            desc: t.desc || t.description || "",
            type: t.type || "neutral"
          };
        });
      }
      const aspects = (_b = chartData.value) == null ? void 0 : _b.aspects;
      if (!aspects || !Array.isArray(aspects) || aspects.length === 0)
        return [];
      return aspects.slice(0, 3).map((a) => {
        const p1Key = (a.planet_a || a.planet1 || a.body1 || "").toLowerCase();
        const p2Key = (a.planet_b || a.planet2 || a.body2 || "").toLowerCase();
        const aspectType = (a.aspect_type || a.aspect || a.type || "").toLowerCase();
        const p1 = PLANET_DISPLAY_S[p1Key];
        const p2 = PLANET_DISPLAY_S[p2Key];
        const aspectLabel = ASPECT_LABEL_S[aspectType];
        const harmony = (aspectLabel == null ? void 0 : aspectLabel.harmony) || "neutral";
        const icon = harmony === "positive" ? "✨" : harmony === "challenge" ? "⚡" : "💫";
        const title = [(p1 == null ? void 0 : p1.name) || p1Key, (aspectLabel == null ? void 0 : aspectLabel.label) || aspectType, (p2 == null ? void 0 : p2.name) || p2Key].filter(Boolean).join(" ");
        const desc = a.description || a.interpretation || "";
        return { icon, title, desc, type: harmony };
      });
    });
    const AXIS_KEYS_S = /* @__PURE__ */ new Set(["ascendant", "asc", "as", "asc.", "midheaven", "mc", "mc.", "medium coeli", "descendant", "dc", "dc.", "ic", "ic.", "imum coeli"]);
    const angleSignMap = common_vendor.computed(() => {
      var _a, _b, _c;
      const angles = (_b = (_a = chartData.value) == null ? void 0 : _a.chart) == null ? void 0 : _b.angles;
      if (!angles)
        return {};
      const result = {};
      for (const key of Object.keys(angles)) {
        const sign = ((_c = angles[key]) == null ? void 0 : _c.sign) || "";
        if (sign)
          result[key.toLowerCase()] = sign;
      }
      if (result["ascendant"] && !result["asc"])
        result["asc"] = result["ascendant"];
      if (result["midheaven"] && !result["mc"])
        result["mc"] = result["midheaven"];
      if (result["mc"] && !result["midheaven"])
        result["midheaven"] = result["mc"];
      if (result["descendant"] && !result["dc"])
        result["dc"] = result["descendant"];
      if (result["ic"])
        result["ic."] = result["ic"];
      return result;
    });
    const realAspects = common_vendor.computed(() => {
      var _a;
      const aspects = (_a = chartData.value) == null ? void 0 : _a.aspects;
      if (!aspects || !Array.isArray(aspects) || aspects.length === 0)
        return [];
      return aspects.map((a) => {
        const p1Raw = (a.planet1 || a.body1 || a.planet_a || a.p1 || a.person1_planet || a.chart1_planet || a.planet || "").toLowerCase().trim();
        const p2Raw = (a.planet2 || a.body2 || a.planet_b || a.p2 || a.person2_planet || a.chart2_planet || a.natal_planet || "").toLowerCase().trim();
        if (AXIS_KEYS_S.has(p1Raw) && AXIS_KEYS_S.has(p2Raw))
          return null;
        const aspectType = (a.aspect || a.type || a.aspect_type || a.aspect_name || "conjunction").toLowerCase().trim();
        const p1Info = PLANET_DISPLAY_S[p1Raw];
        const p2Info = PLANET_DISPLAY_S[p2Raw];
        const aspectInfo = ASPECT_LABEL_S[aspectType];
        const orb = a.orb ?? a.orb_value ?? a.exact_orb ?? null;
        const orbStr = orb != null ? `${Math.abs(Number(orb)).toFixed(1)}°` : "";
        const rawP1Sign = a.planet1_sign || a.sign1 || a.p1_sign || angleSignMap.value[p1Raw] || "";
        const rawP2Sign = a.planet2_sign || a.sign2 || a.p2_sign || angleSignMap.value[p2Raw] || "";
        const p1Sign = zodiacZhS(rawP1Sign);
        const p2Sign = zodiacZhS(rawP2Sign);
        return {
          p1Symbol: (p1Info == null ? void 0 : p1Info.symbol) || p1Raw.slice(0, 2).toUpperCase(),
          p1Name: (p1Info == null ? void 0 : p1Info.name) || p1Raw,
          p1Sign,
          p2Symbol: (p2Info == null ? void 0 : p2Info.symbol) || p2Raw.slice(0, 2).toUpperCase(),
          p2Name: (p2Info == null ? void 0 : p2Info.name) || p2Raw,
          p2Sign,
          aspectSymbol: (aspectInfo == null ? void 0 : aspectInfo.symbol) || aspectType.slice(0, 2),
          aspectLabel: (aspectInfo == null ? void 0 : aspectInfo.label) || aspectType,
          harmony: (aspectInfo == null ? void 0 : aspectInfo.harmony) || "neutral",
          orb: orbStr,
          description: a.description || a.interpretation || a.impact || "",
          exactness: a.exactness || a.exact || ""
        };
      }).filter(Boolean);
    });
    const aspectStats = common_vendor.computed(() => {
      const list = realAspects.value;
      if (list.length === 0)
        return null;
      const pos = list.filter((a) => a.harmony === "positive").length;
      const neg = list.filter((a) => a.harmony === "challenge").length;
      const neu = list.filter((a) => a.harmony === "neutral").length;
      return { total: list.length, positive: pos, challenge: neg, neutral: neu };
    });
    const compatibilityScore = common_vendor.computed(() => {
      var _a;
      const rm = (_a = chartData.value) == null ? void 0 : _a.relationshipModel;
      if (!rm)
        return null;
      const directScore = rm.compatibility_score ?? rm.overall_score ?? rm.total_score;
      if (directScore != null)
        return Math.min(100, Math.max(0, Math.round(Number(directScore))));
      const emotional = rm.emotional_score ?? rm.emotional_compatibility ?? rm.emotional_match ?? rm.emotion_score;
      const attraction = rm.attraction_score ?? rm.attraction ?? rm.magnetic_attraction;
      const stability = rm.stability_score ?? rm.long_term_stability ?? rm.stability;
      const conflict = rm.conflict_score ?? rm.conflict_index ?? rm.tension;
      if (emotional == null && attraction == null && stability == null)
        return null;
      const emotionalNorm = emotional != null ? normalizeScore(Number(emotional), 15) : 50;
      const attractionNorm = attraction != null ? normalizeScore(Number(attraction), 5) : 50;
      const stabilityNorm = stability != null ? normalizeScore(Number(stability), 5) : 50;
      const conflictNorm = conflict != null ? normalizeScore(Number(conflict), 5) : 0;
      const composite = Math.round(
        emotionalNorm * 0.4 + attractionNorm * 0.3 + stabilityNorm * 0.2 - conflictNorm * 0.1
      );
      return Math.min(100, Math.max(0, composite));
    });
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
      { name: "乌鲁木齐", lat: 43.8256, lng: 87.6168, address: "新疆维吾尔自治区乌鲁木齐市" },
      { name: "拉萨", lat: 29.65, lng: 91.1, address: "西藏自治区拉萨市" },
      { name: "兰州", lat: 36.0611, lng: 103.8343, address: "甘肃省兰州市" },
      { name: "太原", lat: 37.8706, lng: 112.5489, address: "山西省太原市" },
      { name: "石家庄", lat: 38.0428, lng: 114.5149, address: "河北省石家庄市" },
      { name: "海口", lat: 20.044, lng: 110.1991, address: "海南省海口市" }
    ];
    let partnerTimer = null;
    const partnerCityKeyword = common_vendor.ref("");
    const partnerCityResults = common_vendor.ref([]);
    const showPartnerSuggestions = common_vendor.ref(false);
    function prefillPartnerFromStore() {
      const u = userStore.userInfo;
      if (!u)
        return;
      if (u.synastryPartnerCity) {
        partnerForm.city = u.synastryPartnerCity;
        partnerForm.lat = u.synastryPartnerLat ?? null;
        partnerForm.lng = u.synastryPartnerLng ?? null;
        partnerCityKeyword.value = u.synastryPartnerCity;
      }
      if (u.synastryPartnerName) {
        partnerName.value = u.synastryPartnerName;
      }
      if (u.synastryPartnerTime) {
        try {
          const [datePart, timePart] = u.synastryPartnerTime.split(" ");
          const [y, mo, d] = datePart.split("-").map(Number);
          const [h, min] = timePart.split(":").map(Number);
          if (y)
            partnerForm.year = y;
          if (mo)
            partnerForm.month = mo;
          if (d)
            partnerForm.day = d;
          if (h !== void 0)
            partnerForm.hour = h;
          if (min !== void 0 && !isNaN(min))
            partnerForm.minute = min;
        } catch (_) {
        }
      }
    }
    common_vendor.onMounted(() => {
      prefillPartnerFromStore();
    });
    function onPartnerCityInput(e) {
      const kw = e.detail.value;
      partnerCityKeyword.value = kw;
      if (!kw.trim()) {
        showPartnerSuggestions.value = false;
        partnerCityResults.value = [];
        partnerForm.city = "";
        partnerForm.lat = null;
        partnerForm.lng = null;
        return;
      }
      clearTimeout(partnerTimer);
      partnerTimer = setTimeout(() => {
        const matched = MAJOR_CITIES.filter((c) => {
          var _a;
          return c.name.includes(kw) || ((_a = c.address) == null ? void 0 : _a.includes(kw));
        }).slice(0, 6);
        partnerCityResults.value = matched;
        showPartnerSuggestions.value = matched.length > 0;
      }, 300);
    }
    function selectPartnerCity(city) {
      partnerForm.city = city.name;
      partnerForm.lat = city.lat;
      partnerForm.lng = city.lng;
      partnerCityKeyword.value = city.name;
      showPartnerSuggestions.value = false;
    }
    function closeAllSuggestions() {
      showPartnerSuggestions.value = false;
    }
    common_vendor.watch(
      () => {
        var _a;
        return (_a = userStore.userInfo) == null ? void 0 : _a.birthTime;
      },
      (newVal, oldVal) => {
        if (oldVal && newVal !== oldVal) {
          chartData.value = null;
          interpretation.value = "";
          if (step.value === "result") {
            step.value = "form";
          }
        }
      }
    );
    const selfBirthDisplay = common_vendor.computed(() => {
      const u = userStore.userInfo;
      if (!(u == null ? void 0 : u.birthTime) || !(u == null ? void 0 : u.birthCity))
        return null;
      const parts = u.birthTime.split(" ");
      const date = parts[0] || "";
      const time = parts[1] || "";
      return `${date} ${time} · ${u.birthCity}`;
    });
    async function calculateSynastry() {
      var _a;
      if (!hasBirthInfo()) {
        common_vendor.index.showModal({
          title: "需要设置出生信息",
          content: "计算和盘需要先设置您的出生信息，去本命盘页面设置吗？",
          confirmText: "去设置",
          cancelText: "取消",
          success: (res) => {
            if (res.confirm)
              goToNatalPage();
          }
        });
        return;
      }
      if (!partnerForm.city) {
        common_vendor.index.showToast({ title: "请填写 Ta 的出生城市", icon: "none" });
        return;
      }
      step.value = "loading";
      const TEXTS = ["正在解析双人星盘能量...", "计算相位连接...", "正在连接宇宙能量...", "生成关系地图..."];
      let idx = 0;
      const timer = setInterval(() => {
        loadingText.value = TEXTS[++idx % TEXTS.length];
      }, 1500);
      try {
        const pName = partnerName.value || "Ta";
        const partnerTimeStr = `${String(partnerForm.year).padStart(4, "0")}-${String(partnerForm.month).padStart(2, "0")}-${String(partnerForm.day).padStart(2, "0")} ${String(partnerForm.hour).padStart(2, "0")}:${String(partnerForm.minute).padStart(2, "0")}`;
        await api_auth.updateSynastryPartner({
          partnerName: pName,
          partnerCity: partnerForm.city,
          partnerLat: partnerForm.lat,
          partnerLng: partnerForm.lng,
          partnerTime: partnerTimeStr
        });
        const result = await api_astrology.getSynastryChart();
        chartData.value = result;
        userStore.updateSynastryPartner(
          pName,
          partnerForm.city,
          partnerForm.lat,
          partnerForm.lng,
          partnerTimeStr
        );
      } catch (e) {
        const errCode = (_a = e == null ? void 0 : e.data) == null ? void 0 : _a.code;
        if (errCode === 7001) {
          clearInterval(timer);
          step.value = "form";
          common_vendor.index.showModal({
            title: "需要设置出生信息",
            content: "请先在本命盘页面设置您的出生信息",
            confirmText: "去设置",
            cancelText: "取消",
            success: (res) => {
              if (res.confirm)
                goToNatalPage();
            }
          });
          return;
        }
        chartData.value = { relationshipModel: null, aspects: [], themes: [], chart: null };
      } finally {
        clearInterval(timer);
        step.value = "result";
      }
    }
    async function getInterpretation() {
      const myGeneration = ++typingGeneration;
      isInterpreting.value = true;
      interpretation.value = "";
      activeTab.value = "interpret";
      try {
        const typeMap = {
          love: "romantic",
          marriage: "romantic",
          soul: "friendship",
          emotion: "romantic"
        };
        const relationshipType = typeMap[selectedInterpretType.value] ?? "romantic";
        const result = await api_astrology.interpretSynastry({ relationshipType, focus: selectedInterpretType.value, tone: "gentle" });
        const text = result.interpretation;
        for (let i = 0; i <= text.length; i++) {
          if (typingGeneration !== myGeneration) {
            interpretation.value = text;
            return;
          }
          interpretation.value = text.slice(0, i);
          await new Promise((r) => setTimeout(r, 25));
        }
      } catch {
        const mock = `💞 你们之间有一种深刻的灵魂契约感。金星与月亮的六分相位意味着你们的情感表达方式天然和谐，彼此能够理解对方在关系中的需求。

这份关系中有强烈的吸引力，但也要注意火星与土星的紧张相位——在某些具体事务的执行和推进上，双方可能会产生节奏不一致的摩擦。

长期来看，你们关系中最稳固的基础来自于相似的价值观和人生方向感，这是一种可以共同成长的连接方式。🌙`;
        for (let i = 0; i <= mock.length; i++) {
          if (typingGeneration !== myGeneration) {
            interpretation.value = mock;
            return;
          }
          interpretation.value = mock.slice(0, i);
          await new Promise((r) => setTimeout(r, 20));
        }
      } finally {
        if (typingGeneration === myGeneration) {
          isInterpreting.value = false;
        }
      }
    }
    function goInterpret() {
      activeTab.value = "interpret";
      if (!interpretation.value)
        getInterpretation();
    }
    return (_ctx, _cache) => {
      var _a;
      return common_vendor.e({
        a: step.value === "loading"
      }, step.value === "loading" ? {
        b: common_vendor.t(loadingText.value)
      } : {}, {
        c: step.value === "form"
      }, step.value === "form" ? common_vendor.e({
        d: common_vendor.t(hasBirthInfo() ? "✏️ 修改" : "⚙️ 去设置"),
        e: common_vendor.o(goToNatalPage, "bf"),
        f: hasBirthInfo()
      }, hasBirthInfo() ? {
        g: common_vendor.t(selfBirthDisplay.value)
      } : {
        h: common_vendor.o(goToNatalPage, "e0")
      }, {
        i: partnerName.value,
        j: common_vendor.o(($event) => partnerName.value = $event.detail.value, "cd"),
        k: common_vendor.t(partnerForm.year),
        l: common_vendor.unref(YEAR_OPTIONS),
        m: common_vendor.unref(YEARS).indexOf(partnerForm.year),
        n: common_vendor.o(onPartnerYearChange, "82"),
        o: common_vendor.t(partnerForm.month),
        p: common_vendor.unref(MONTH_OPTIONS),
        q: partnerForm.month - 1,
        r: common_vendor.o(onPartnerMonthChange, "32"),
        s: common_vendor.t(partnerForm.day),
        t: common_vendor.unref(DAY_OPTIONS),
        v: partnerForm.day - 1,
        w: common_vendor.o(onPartnerDayChange, "14"),
        x: common_vendor.t(partnerForm.hour),
        y: common_vendor.unref(HOUR_OPTIONS),
        z: partnerForm.hour,
        A: common_vendor.o(onPartnerHourChange, "7f"),
        B: common_vendor.t(String(partnerForm.minute).padStart(2, "0")),
        C: common_vendor.unref(MINUTE_OPTIONS),
        D: partnerForm.minute,
        E: common_vendor.o(onPartnerMinuteChange, "eb"),
        F: partnerCityKeyword.value,
        G: common_vendor.o(onPartnerCityInput, "de"),
        H: partnerCityKeyword.value
      }, partnerCityKeyword.value ? {
        I: common_vendor.o(() => {
          partnerCityKeyword.value = "";
          partnerForm.city = "";
          partnerForm.lat = null;
          partnerForm.lng = null;
          showPartnerSuggestions.value = false;
        }, "ab")
      } : {}, {
        J: showPartnerSuggestions.value && partnerCityResults.value.length > 0
      }, showPartnerSuggestions.value && partnerCityResults.value.length > 0 ? {
        K: common_vendor.f(partnerCityResults.value, (city, k0, i0) => {
          return common_vendor.e({
            a: common_vendor.t(city.name),
            b: city.address
          }, city.address ? {
            c: common_vendor.t(city.address)
          } : {}, {
            d: city.name,
            e: common_vendor.o(($event) => selectPartnerCity(city), city.name)
          });
        })
      } : {}, {
        L: partnerForm.lat && partnerForm.lng
      }, partnerForm.lat && partnerForm.lng ? {
        M: common_vendor.t(partnerForm.city)
      } : {}, {
        N: common_vendor.o(() => {
        }, "b4"),
        O: common_vendor.o(() => {
        }, "b3"),
        P: !hasBirthInfo() ? 1 : "",
        Q: common_vendor.o(calculateSynastry, "c5")
      }) : {}, {
        R: step.value === "result"
      }, step.value === "result" ? common_vendor.e({
        S: common_vendor.t(compatibilityScore.value != null ? compatibilityScore.value : "—"),
        T: compatibilityScore.value != null
      }, compatibilityScore.value != null ? {} : {}, {
        U: common_vendor.t(partnerName.value || "Ta"),
        V: activeTab.value === "analysis" ? 1 : "",
        W: common_vendor.o(($event) => activeTab.value = "analysis", "b1"),
        X: aspectStats.value
      }, aspectStats.value ? {
        Y: common_vendor.t(aspectStats.value.total)
      } : {}, {
        Z: activeTab.value === "aspects" ? 1 : "",
        aa: common_vendor.o(($event) => activeTab.value = "aspects", "aa"),
        ab: activeTab.value === "themes" ? 1 : "",
        ac: common_vendor.o(($event) => activeTab.value = "themes", "22"),
        ad: activeTab.value === "interpret" ? 1 : "",
        ae: common_vendor.o(($event) => activeTab.value = "interpret", "14"),
        af: activeTab.value === "analysis"
      }, activeTab.value === "analysis" ? common_vendor.e({
        ag: realAnalysis.value.length === 0
      }, realAnalysis.value.length === 0 ? {} : {}, {
        ah: common_vendor.f(realAnalysis.value, (item, k0, i0) => {
          return {
            a: common_vendor.t(item.label),
            b: common_vendor.t(item.value),
            c: item.color,
            d: item.value + "%",
            e: item.color,
            f: common_vendor.t(item.desc),
            g: item.label
          };
        })
      }) : {}, {
        ai: activeTab.value === "aspects"
      }, activeTab.value === "aspects" ? common_vendor.e({
        aj: aspectStats.value
      }, aspectStats.value ? {
        ak: common_vendor.t(aspectStats.value.total),
        al: common_vendor.t(aspectStats.value.positive),
        am: common_vendor.t(aspectStats.value.neutral),
        an: common_vendor.t(aspectStats.value.challenge)
      } : {}, {
        ao: realAspects.value.length === 0
      }, realAspects.value.length === 0 ? {} : {}, {
        ap: common_vendor.f(realAspects.value, (asp, idx, i0) => {
          return common_vendor.e({
            a: common_vendor.t(asp.p1Symbol),
            b: common_vendor.n("asp-sw-" + asp.harmony),
            c: common_vendor.t(asp.p1Name),
            d: asp.p1Sign
          }, asp.p1Sign ? {
            e: common_vendor.t(asp.p1Sign)
          } : {}, {
            f: common_vendor.t(asp.aspectSymbol),
            g: common_vendor.n("asym-" + asp.harmony),
            h: common_vendor.t(asp.aspectLabel),
            i: asp.orb
          }, asp.orb ? {
            j: common_vendor.t(asp.orb)
          } : {}, {
            k: common_vendor.t(asp.p2Symbol),
            l: common_vendor.n("asp-sw-" + asp.harmony),
            m: common_vendor.t(asp.p2Name),
            n: asp.p2Sign
          }, asp.p2Sign ? {
            o: common_vendor.t(asp.p2Sign)
          } : {}, {
            p: asp.description
          }, asp.description ? {
            q: common_vendor.t(asp.description)
          } : {}, {
            r: idx,
            s: common_vendor.n("asp-" + asp.harmony)
          });
        })
      }) : {}, {
        aq: activeTab.value === "themes"
      }, activeTab.value === "themes" ? common_vendor.e({
        ar: realThemes.value.length === 0
      }, realThemes.value.length === 0 ? {} : {}, {
        as: common_vendor.f(realThemes.value, (theme, idx, i0) => {
          return common_vendor.e({
            a: common_vendor.t(theme.icon),
            b: common_vendor.t(theme.title),
            c: theme.desc
          }, theme.desc ? {
            d: common_vendor.t(theme.desc)
          } : {}, {
            e: idx,
            f: common_vendor.n("theme-" + theme.type)
          });
        })
      }) : {}, {
        at: activeTab.value === "interpret"
      }, activeTab.value === "interpret" ? common_vendor.e({
        av: common_vendor.f(INTERPRET_TYPES, (t, k0, i0) => {
          return {
            a: common_vendor.t(t.icon),
            b: common_vendor.t(t.label),
            c: t.key,
            d: selectedInterpretType.value === t.key ? 1 : "",
            e: common_vendor.o(($event) => selectedInterpretType.value = t.key, t.key)
          };
        }),
        aw: !interpretation.value && !isInterpreting.value
      }, !interpretation.value && !isInterpreting.value ? {
        ax: common_vendor.o(getInterpretation, "aa")
      } : {}, {
        ay: interpretation.value || isInterpreting.value
      }, interpretation.value || isInterpreting.value ? common_vendor.e({
        az: common_vendor.t((_a = INTERPRET_TYPES.find((t) => t.key === selectedInterpretType.value)) == null ? void 0 : _a.label),
        aA: isInterpreting.value
      }, isInterpreting.value ? {} : {}, {
        aB: common_vendor.t(interpretation.value)
      }) : {}, {
        aC: interpretation.value && !isInterpreting.value
      }, interpretation.value && !isInterpreting.value ? {
        aD: common_vendor.o(getInterpretation, "bb")
      } : {}) : {}, {
        aE: common_vendor.o(($event) => step.value = "form", "a6"),
        aF: common_vendor.o(goInterpret, "e5")
      }) : {}, {
        aG: common_vendor.o(closeAllSuggestions, "9a")
      });
    };
  }
});
wx.createPage(_sfc_main);
//# sourceMappingURL=../../../.sourcemap/mp-weixin/pages/astrology/synastry.js.map
