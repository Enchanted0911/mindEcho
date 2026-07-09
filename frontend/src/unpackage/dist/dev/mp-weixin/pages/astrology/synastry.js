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
    function hasSynastryPartner() {
      const u = userStore.userInfo;
      return !!((u == null ? void 0 : u.synastryPartnerCity) && (u == null ? void 0 : u.synastryPartnerTime));
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
      if (refMax !== void 0 && refMax !== null) {
        if (refMax <= 0)
          return 0;
        const mapped = Math.round(raw / refMax * 100);
        return Math.max(0, Math.min(100, mapped));
      }
      if (raw <= 1)
        return Math.round(raw * 100);
      if (raw <= 100)
        return Math.max(0, Math.min(100, Math.round(raw)));
      return 100;
    }
    const realAnalysis = common_vendor.computed(() => {
      var _a;
      const rm = (_a = chartData.value) == null ? void 0 : _a.relationshipModel;
      if (!rm)
        return [];
      const items = [];
      const attractionRaw = rm.attraction_score ?? rm.attraction ?? rm.magnetic_attraction;
      if (attractionRaw != null) {
        const val = normalizeScore(Number(attractionRaw), 100);
        items.push({ label: "吸引力", value: val, color: "#e070a0", desc: rm.attraction_desc || "双方星盘间的吸引强度" });
      }
      const emotionalRaw = rm.emotional_score ?? rm.emotional_compatibility ?? rm.emotional_match ?? rm.emotion_score;
      if (emotionalRaw != null) {
        const val = normalizeScore(Number(emotionalRaw), 100);
        items.push({ label: "情绪匹配", value: val, color: "#7080f0", desc: rm.emotion_desc || "情感波动的共鸣程度" });
      }
      const conflictRaw = rm.conflict_score ?? rm.conflict_index ?? rm.tension;
      if (conflictRaw != null) {
        const val = normalizeScore(Number(conflictRaw), 100);
        items.push({ label: "冲突指数", value: val, color: "#f09040", desc: rm.conflict_desc || "关系中的摩擦与张力" });
      }
      const stabilityRaw = rm.stability_score ?? rm.long_term_stability ?? rm.stability;
      if (stabilityRaw != null) {
        const val = normalizeScore(Number(stabilityRaw), 100);
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
      "power tension": { title: "权力张力", icon: "⚔️", type: "challenge", desc: "双方存在控制与主导权的博弈" },
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
        const strength = a.strength != null ? Math.round(Number(a.strength) * 100) : null;
        const rawP1Sign = a.planet1_sign || a.sign1 || a.p1_sign || angleSignMap.value[p1Raw] || "";
        const rawP2Sign = a.planet2_sign || a.sign2 || a.p2_sign || angleSignMap.value[p2Raw] || "";
        const p1Sign = zodiacZhS(rawP1Sign);
        const p2Sign = zodiacZhS(rawP2Sign);
        const tags = Array.isArray(a.tags) ? a.tags.map((t) => String(t).toLowerCase()) : [];
        let harmony = (aspectInfo == null ? void 0 : aspectInfo.harmony) || "neutral";
        if (tags.includes("conflict"))
          harmony = "challenge";
        else if (tags.includes("attraction") || tags.includes("emotional")) {
          if (harmony !== "challenge")
            harmony = "positive";
        }
        return {
          p1Symbol: (p1Info == null ? void 0 : p1Info.symbol) || p1Raw.slice(0, 2).toUpperCase(),
          p1Name: (p1Info == null ? void 0 : p1Info.name) || p1Raw,
          p1Sign,
          p2Symbol: (p2Info == null ? void 0 : p2Info.symbol) || p2Raw.slice(0, 2).toUpperCase(),
          p2Name: (p2Info == null ? void 0 : p2Info.name) || p2Raw,
          p2Sign,
          aspectSymbol: (aspectInfo == null ? void 0 : aspectInfo.symbol) || aspectType.slice(0, 2),
          aspectLabel: (aspectInfo == null ? void 0 : aspectInfo.label) || aspectType,
          harmony,
          orb: orbStr,
          strength,
          tags,
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
    const houseOverlay = common_vendor.computed(() => {
      var _a, _b;
      const overlay = (_b = (_a = chartData.value) == null ? void 0 : _a.chart) == null ? void 0 : _b.house_overlay;
      if (!overlay || !Array.isArray(overlay) || overlay.length === 0)
        return null;
      const meItems = [];
      const partnerItems = [];
      for (const item of overlay) {
        const planet = String(item.planet || "").toLowerCase();
        const house = Number(item.target_house);
        const pInfo = PLANET_DISPLAY_S[planet];
        if (!house || isNaN(house))
          continue;
        if (item.source_chart === "chart_b") {
          partnerItems.push({ planet, house, pInfo });
        } else {
          meItems.push({ planet, house, pInfo });
        }
      }
      if (meItems.length === 0 && partnerItems.length === 0)
        return null;
      return { me: meItems, partner: partnerItems };
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
      const emotionalNorm = emotional != null ? normalizeScore(Number(emotional), 100) : 50;
      const attractionNorm = attraction != null ? normalizeScore(Number(attraction), 100) : 50;
      const stabilityNorm = stability != null ? normalizeScore(Number(stability), 100) : 50;
      const conflictNorm = conflict != null ? normalizeScore(Number(conflict), 100) : 0;
      const composite = Math.round(
        emotionalNorm * 0.4 + attractionNorm * 0.3 + stabilityNorm * 0.2 - conflictNorm * 0.1
      );
      return Math.min(100, Math.max(0, composite));
    });
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
      { name: "漯河", lat: 33.5757, lng: 114.0164, address: "河南省漯河市", pinyin: "luohe lh" },
      { name: "三门峡", lat: 34.7734, lng: 111.201, address: "河南省三门峡市", pinyin: "sanmenxia smx" },
      // 辽宁省
      { name: "沈阳", lat: 41.8057, lng: 123.4315, address: "辽宁省沈阳市", pinyin: "shenyang sy" },
      { name: "大连", lat: 38.914, lng: 121.6147, address: "辽宁省大连市", pinyin: "dalian dl" },
      { name: "鞍山", lat: 41.1085, lng: 122.9958, address: "辽宁省鞍山市", pinyin: "anshan as" },
      { name: "抚顺", lat: 41.8797, lng: 123.9571, address: "辽宁省抚顺市", pinyin: "fushun fs2" },
      { name: "锦州", lat: 41.1305, lng: 121.1268, address: "辽宁省锦州市", pinyin: "jinzhou jz3" },
      { name: "营口", lat: 40.6672, lng: 122.2347, address: "辽宁省营口市", pinyin: "yingkou yk" },
      { name: "丹东", lat: 40.1292, lng: 124.3545, address: "辽宁省丹东市", pinyin: "dandong dd" },
      // 陕西省
      { name: "西安", lat: 34.3416, lng: 108.9398, address: "陕西省西安市", pinyin: "xian xa" },
      { name: "咸阳", lat: 34.3297, lng: 108.7089, address: "陕西省咸阳市", pinyin: "xianyang xy3" },
      { name: "宝鸡", lat: 34.3617, lng: 107.2373, address: "陕西省宝鸡市", pinyin: "baoji bj2" },
      { name: "渭南", lat: 34.4997, lng: 109.5095, address: "陕西省渭南市", pinyin: "weinan wn" },
      { name: "汉中", lat: 33.0667, lng: 107.0282, address: "陕西省汉中市", pinyin: "hanzhong hz3" },
      { name: "榆林", lat: 38.2856, lng: 109.7342, address: "陕西省榆林市", pinyin: "yulin yl" },
      { name: "延安", lat: 36.5853, lng: 109.4897, address: "陕西省延安市", pinyin: "yanan yan" },
      // 安徽省
      { name: "合肥", lat: 31.8206, lng: 117.2272, address: "安徽省合肥市", pinyin: "hefei hf" },
      { name: "芜湖", lat: 31.352, lng: 118.4329, address: "安徽省芜湖市", pinyin: "wuhu wh3" },
      { name: "蚌埠", lat: 32.9162, lng: 117.3795, address: "安徽省蚌埠市", pinyin: "bengbu bb" },
      { name: "淮南", lat: 32.6252, lng: 116.9993, address: "安徽省淮南市", pinyin: "huainan hn" },
      { name: "马鞍山", lat: 31.6704, lng: 118.5066, address: "安徽省马鞍山市", pinyin: "maanshan mas" },
      { name: "安庆", lat: 30.543, lng: 117.0633, address: "安徽省安庆市", pinyin: "anqing aq" },
      { name: "黄山", lat: 29.7151, lng: 118.338, address: "安徽省黄山市", pinyin: "huangshan hs2" },
      { name: "阜阳", lat: 32.8989, lng: 115.8149, address: "安徽省阜阳市", pinyin: "fuyang fy" },
      // 河北省
      { name: "石家庄", lat: 38.0428, lng: 114.5149, address: "河北省石家庄市", pinyin: "shijiazhuang sjz" },
      { name: "唐山", lat: 39.631, lng: 118.18, address: "河北省唐山市", pinyin: "tangshan ts" },
      { name: "秦皇岛", lat: 39.9355, lng: 119.5994, address: "河北省秦皇岛市", pinyin: "qinhuangdao qhd" },
      { name: "保定", lat: 38.8736, lng: 115.4644, address: "河北省保定市", pinyin: "baoding bd" },
      { name: "邯郸", lat: 36.6251, lng: 114.5389, address: "河北省邯郸市", pinyin: "handan hd" },
      { name: "张家口", lat: 40.8114, lng: 114.8796, address: "河北省张家口市", pinyin: "zhangjiakou zjk" },
      { name: "廊坊", lat: 39.5382, lng: 116.7032, address: "河北省廊坊市", pinyin: "langfang lf" },
      // 山西省
      { name: "太原", lat: 37.8706, lng: 112.5489, address: "山西省太原市", pinyin: "taiyuan ty" },
      { name: "大同", lat: 40.0766, lng: 113.2982, address: "山西省大同市", pinyin: "datong dt" },
      { name: "长治", lat: 36.1956, lng: 113.1164, address: "山西省长治市", pinyin: "changzhi cz6" },
      { name: "运城", lat: 35.0224, lng: 111.007, address: "山西省运城市", pinyin: "yuncheng yc3" },
      // 黑龙江省
      { name: "哈尔滨", lat: 45.8038, lng: 126.5349, address: "黑龙江省哈尔滨市", pinyin: "haerbin hrb" },
      { name: "齐齐哈尔", lat: 47.3479, lng: 123.9182, address: "黑龙江省齐齐哈尔市", pinyin: "qiqihaer qqhr" },
      { name: "大庆", lat: 46.5897, lng: 125.1032, address: "黑龙江省大庆市", pinyin: "daqing dq" },
      { name: "牡丹江", lat: 44.5526, lng: 129.6328, address: "黑龙江省牡丹江市", pinyin: "mudanjiang mdj" },
      // 吉林省
      { name: "长春", lat: 43.8171, lng: 125.3235, address: "吉林省长春市", pinyin: "changchun cc" },
      { name: "吉林", lat: 43.8378, lng: 126.5496, address: "吉林省吉林市", pinyin: "jilin jl" },
      { name: "延吉", lat: 42.9099, lng: 129.513, address: "吉林省延吉市", pinyin: "yanji yj2" },
      // 江西省
      { name: "南昌", lat: 28.682, lng: 115.8582, address: "江西省南昌市", pinyin: "nanchang nc2" },
      { name: "赣州", lat: 25.8311, lng: 114.933, address: "江西省赣州市", pinyin: "ganzhou gz2" },
      { name: "九江", lat: 29.7055, lng: 115.9926, address: "江西省九江市", pinyin: "jiujiang jj" },
      { name: "景德镇", lat: 29.2687, lng: 117.1786, address: "江西省景德镇市", pinyin: "jingdezhen jdz" },
      { name: "上饶", lat: 28.4544, lng: 117.9429, address: "江西省上饶市", pinyin: "shangrao sr" },
      // 云南省
      { name: "昆明", lat: 25.0453, lng: 102.7097, address: "云南省昆明市", pinyin: "kunming km" },
      { name: "大理", lat: 25.6066, lng: 100.2598, address: "云南省大理市", pinyin: "dali dl2" },
      { name: "丽江", lat: 26.8721, lng: 100.2331, address: "云南省丽江市", pinyin: "lijiang lj" },
      { name: "西双版纳", lat: 22.0073, lng: 100.7974, address: "云南省西双版纳傣族自治州", pinyin: "xishuangbanna xsbn" },
      // 贵州省
      { name: "贵阳", lat: 26.647, lng: 106.6302, address: "贵州省贵阳市", pinyin: "guiyang gy2" },
      { name: "遵义", lat: 27.7251, lng: 106.9271, address: "贵州省遵义市", pinyin: "zunyi zy2" },
      // 广西壮族自治区
      { name: "南宁", lat: 22.817, lng: 108.3665, address: "广西壮族自治区南宁市", pinyin: "nanning nn" },
      { name: "柳州", lat: 24.3264, lng: 109.4281, address: "广西壮族自治区柳州市", pinyin: "liuzhou lz2" },
      { name: "桂林", lat: 25.2736, lng: 110.2907, address: "广西壮族自治区桂林市", pinyin: "guilin gl" },
      { name: "北海", lat: 21.4808, lng: 109.1196, address: "广西壮族自治区北海市", pinyin: "beihai bh" },
      // 海南省
      { name: "海口", lat: 20.044, lng: 110.1991, address: "海南省海口市", pinyin: "haikou hk" },
      { name: "三亚", lat: 18.2528, lng: 109.5119, address: "海南省三亚市", pinyin: "sanya sy5" },
      // 内蒙古自治区
      { name: "呼和浩特", lat: 40.8414, lng: 111.7519, address: "内蒙古自治区呼和浩特市", pinyin: "huhehaote hhht" },
      { name: "包头", lat: 40.6575, lng: 109.8401, address: "内蒙古自治区包头市", pinyin: "baotou bt" },
      { name: "鄂尔多斯", lat: 39.6086, lng: 109.7814, address: "内蒙古自治区鄂尔多斯市", pinyin: "eerduosi eeds" },
      { name: "赤峰", lat: 42.2574, lng: 118.8881, address: "内蒙古自治区赤峰市", pinyin: "chifeng cf" },
      // 新疆维吾尔自治区
      { name: "乌鲁木齐", lat: 43.8256, lng: 87.6168, address: "新疆维吾尔自治区乌鲁木齐市", pinyin: "wulumuqi wlmq" },
      { name: "喀什", lat: 39.4673, lng: 75.9896, address: "新疆维吾尔自治区喀什地区", pinyin: "kashi ks" },
      { name: "哈密", lat: 42.8176, lng: 93.5142, address: "新疆维吾尔自治区哈密市", pinyin: "hami hm" },
      // 西藏自治区
      { name: "拉萨", lat: 29.65, lng: 91.1, address: "西藏自治区拉萨市", pinyin: "lasa ls3" },
      { name: "日喀则", lat: 29.2677, lng: 88.885, address: "西藏自治区日喀则市", pinyin: "rikaze rkz" },
      { name: "林芝", lat: 29.649, lng: 94.3625, address: "西藏自治区林芝市", pinyin: "linzhi lz3" },
      // 宁夏回族自治区
      { name: "银川", lat: 38.4872, lng: 106.2309, address: "宁夏回族自治区银川市", pinyin: "yinchuan yc6" },
      // 青海省
      { name: "西宁", lat: 36.6177, lng: 101.7782, address: "青海省西宁市", pinyin: "xining xn" },
      // 甘肃省
      { name: "兰州", lat: 36.0611, lng: 103.8343, address: "甘肃省兰州市", pinyin: "lanzhou lz4" },
      { name: "天水", lat: 34.5807, lng: 105.7245, address: "甘肃省天水市", pinyin: "tianshui ts2" },
      { name: "酒泉", lat: 39.7432, lng: 98.4938, address: "甘肃省酒泉市", pinyin: "jiuquan jq" },
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
      "扬": "扬州"
    };
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
    common_vendor.onMounted(async () => {
      prefillPartnerFromStore();
      const astroInfo = userStore.astrologyInfo;
      if (hasBirthInfo() && hasSynastryPartner() && (astroInfo == null ? void 0 : astroInfo.hasSynastryCache)) {
        await calculateSynastry();
      }
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
        const keyword = kw.toLowerCase().trim();
        const aliasTarget = CITY_ALIASES[keyword];
        const matched = MAJOR_CITIES.filter((c) => {
          var _a;
          if (c.name.includes(keyword))
            return true;
          if ((_a = c.address) == null ? void 0 : _a.includes(keyword))
            return true;
          if (c.pinyin && c.pinyin.toLowerCase().includes(keyword))
            return true;
          if (aliasTarget && c.name === aliasTarget)
            return true;
          return false;
        }).slice(0, 8);
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
        step.value = "form";
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
        userStore.updateAstrologyCache({ hasSynastryCache: true });
      } catch (e) {
        const errCode = (_a = e == null ? void 0 : e.data) == null ? void 0 : _a.code;
        if (errCode === 7001) {
          clearInterval(timer);
          step.value = "form";
          userStore.updateAstrologyCache({ hasSynastryCache: false });
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
        }),
        ai: houseOverlay.value
      }, houseOverlay.value ? common_vendor.e({
        aj: houseOverlay.value.partner.length > 0
      }, houseOverlay.value.partner.length > 0 ? {
        ak: common_vendor.t(partnerName.value || "Ta"),
        al: common_vendor.f(houseOverlay.value.partner, (item, k0, i0) => {
          var _a2, _b;
          return {
            a: common_vendor.t(((_a2 = item.pInfo) == null ? void 0 : _a2.symbol) || item.planet.slice(0, 2).toUpperCase()),
            b: common_vendor.t(((_b = item.pInfo) == null ? void 0 : _b.name) || item.planet),
            c: common_vendor.t(item.house),
            d: item.planet
          };
        })
      } : {}, {
        am: houseOverlay.value.me.length > 0
      }, houseOverlay.value.me.length > 0 ? {
        an: common_vendor.t(partnerName.value || "Ta"),
        ao: common_vendor.f(houseOverlay.value.me, (item, k0, i0) => {
          var _a2, _b;
          return {
            a: common_vendor.t(((_a2 = item.pInfo) == null ? void 0 : _a2.symbol) || item.planet.slice(0, 2).toUpperCase()),
            b: common_vendor.t(((_b = item.pInfo) == null ? void 0 : _b.name) || item.planet),
            c: common_vendor.t(item.house),
            d: item.planet
          };
        })
      } : {}) : {}) : {}, {
        ap: activeTab.value === "aspects"
      }, activeTab.value === "aspects" ? common_vendor.e({
        aq: aspectStats.value
      }, aspectStats.value ? {
        ar: common_vendor.t(aspectStats.value.total),
        as: common_vendor.t(aspectStats.value.positive),
        at: common_vendor.t(aspectStats.value.neutral),
        av: common_vendor.t(aspectStats.value.challenge)
      } : {}, {
        aw: realAspects.value.length === 0
      }, realAspects.value.length === 0 ? {} : {}, {
        ax: common_vendor.f(realAspects.value, (asp, idx, i0) => {
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
            k: asp.strength != null
          }, asp.strength != null ? {
            l: common_vendor.t(asp.strength)
          } : {}, {
            m: common_vendor.t(asp.p2Symbol),
            n: common_vendor.n("asp-sw-" + asp.harmony),
            o: common_vendor.t(asp.p2Name),
            p: asp.p2Sign
          }, asp.p2Sign ? {
            q: common_vendor.t(asp.p2Sign)
          } : {}, {
            r: asp.tags && asp.tags.length > 0
          }, asp.tags && asp.tags.length > 0 ? {
            s: common_vendor.f(asp.tags, (tag, k1, i1) => {
              return {
                a: common_vendor.t(tag === "emotional" ? "情感" : tag === "attraction" ? "吸引" : tag === "conflict" ? "冲突" : tag),
                b: tag,
                c: common_vendor.n("asp-tag-" + tag)
              };
            })
          } : {}, {
            t: asp.description
          }, asp.description ? {
            v: common_vendor.t(asp.description)
          } : {}, {
            w: idx,
            x: common_vendor.n("asp-" + asp.harmony)
          });
        })
      }) : {}, {
        ay: activeTab.value === "themes"
      }, activeTab.value === "themes" ? common_vendor.e({
        az: realThemes.value.length === 0
      }, realThemes.value.length === 0 ? {} : {}, {
        aA: common_vendor.f(realThemes.value, (theme, idx, i0) => {
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
        aB: activeTab.value === "interpret"
      }, activeTab.value === "interpret" ? common_vendor.e({
        aC: common_vendor.f(INTERPRET_TYPES, (t, k0, i0) => {
          return {
            a: common_vendor.t(t.icon),
            b: common_vendor.t(t.label),
            c: t.key,
            d: selectedInterpretType.value === t.key ? 1 : "",
            e: common_vendor.o(($event) => selectedInterpretType.value = t.key, t.key)
          };
        }),
        aD: !interpretation.value && !isInterpreting.value
      }, !interpretation.value && !isInterpreting.value ? {
        aE: common_vendor.o(getInterpretation, "32")
      } : {}, {
        aF: interpretation.value || isInterpreting.value
      }, interpretation.value || isInterpreting.value ? common_vendor.e({
        aG: common_vendor.t((_a = INTERPRET_TYPES.find((t) => t.key === selectedInterpretType.value)) == null ? void 0 : _a.label),
        aH: isInterpreting.value
      }, isInterpreting.value ? {} : {}, {
        aI: common_vendor.t(interpretation.value)
      }) : {}, {
        aJ: interpretation.value && !isInterpreting.value
      }, interpretation.value && !isInterpreting.value ? {
        aK: common_vendor.o(getInterpretation, "fa")
      } : {}) : {}, {
        aL: common_vendor.o(($event) => step.value = "form", "04"),
        aM: common_vendor.o(goInterpret, "93")
      }) : {}, {
        aN: common_vendor.o(closeAllSuggestions, "9a")
      });
    };
  }
});
wx.createPage(_sfc_main);
//# sourceMappingURL=../../../.sourcemap/mp-weixin/pages/astrology/synastry.js.map
