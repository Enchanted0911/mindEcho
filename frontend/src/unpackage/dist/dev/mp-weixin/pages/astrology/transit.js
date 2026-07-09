"use strict";
const common_vendor = require("../../common/vendor.js");
const api_astrology = require("../../api/astrology.js");
const store_user = require("../../store/user.js");
const pickerMinDate = "2000-01-01";
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "transit",
  setup(__props) {
    const userStore = store_user.useUserStore();
    const step = common_vendor.ref("form");
    const loadingText = common_vendor.ref("正在解析当前星体轨迹...");
    const chartData = common_vendor.ref(null);
    const interpretation = common_vendor.ref("");
    const isInterpreting = common_vendor.ref(false);
    const selectedInterpretType = common_vendor.ref("today_emotion");
    const activeTab = common_vendor.ref("today");
    let typingGeneration = 0;
    const today = /* @__PURE__ */ new Date();
    const targetDateStr = common_vendor.ref(formatDateToYMD(today));
    function formatDateToYMD(d) {
      const y = d.getFullYear();
      const m = String(d.getMonth() + 1).padStart(2, "0");
      const day = String(d.getDate()).padStart(2, "0");
      return `${y}-${m}-${day}`;
    }
    function formatDateDisplay(dateStr) {
      if (!dateStr)
        return "";
      const [y, m, d] = dateStr.split("-");
      return `${y}年${parseInt(m)}月${parseInt(d)}日`;
    }
    const pickerMaxDate = (() => {
      const d = /* @__PURE__ */ new Date();
      d.setFullYear(d.getFullYear() + 2);
      return formatDateToYMD(d);
    })();
    function onDatePickerChange(e) {
      targetDateStr.value = e.detail.value;
    }
    const isToday = common_vendor.computed(() => targetDateStr.value === formatDateToYMD(today));
    const targetDateDisplay = common_vendor.computed(() => {
      if (isToday.value)
        return formatDateDisplay(targetDateStr.value) + "（今日）";
      return formatDateDisplay(targetDateStr.value);
    });
    const todayStr = common_vendor.computed(() => targetDateDisplay.value);
    function prefillTransitDateFromStore() {
      const u = userStore.userInfo;
      if (u == null ? void 0 : u.transitTargetDate) {
        targetDateStr.value = u.transitTargetDate;
      }
    }
    common_vendor.onMounted(async () => {
      prefillTransitDateFromStore();
      const astroInfo = userStore.astrologyInfo;
      if (hasBirthInfo() && (astroInfo == null ? void 0 : astroInfo.hasTransitCache)) {
        await calculateTransit();
      }
    });
    function hasBirthInfo() {
      const info = userStore.userInfo;
      return !!((info == null ? void 0 : info.birthCity) && (info == null ? void 0 : info.birthTime));
    }
    function goToNatalPage() {
      common_vendor.index.navigateTo({ url: "/pages/astrology/natal" });
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
    const INTERPRET_TYPES = [
      { key: "today_emotion", label: "今日情绪", icon: "💭" },
      { key: "relationship", label: "近期关系", icon: "💫" },
      { key: "stress", label: "压力感知", icon: "⚡" },
      { key: "growth", label: "成长方向", icon: "🌱" }
    ];
    const PLANET_DISPLAY_T = {
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
      // 轴点与节点（兼容下划线和空格两种写法）
      north_node: { symbol: "☊", name: "北交点" },
      south_node: { symbol: "☋", name: "南交点" },
      "north node": { symbol: "☊", name: "北交点" },
      "south node": { symbol: "☋", name: "南交点" },
      ascendant: { symbol: "ASC", name: "上升点" },
      descendant: { symbol: "DSC", name: "下降点" },
      mc: { symbol: "MC", name: "天顶" },
      ic: { symbol: "IC", name: "天底" },
      midheaven: { symbol: "MC", name: "天顶" },
      chiron: { symbol: "⚷", name: "凯龙星" }
    };
    const ASPECT_LABEL_T = {
      conjunction: { label: "合相", symbol: "☌", harmony: "neutral" },
      sextile: { label: "六分相", symbol: "⚹", harmony: "positive" },
      square: { label: "四分相", symbol: "□", harmony: "challenge" },
      trine: { label: "三分相", symbol: "△", harmony: "positive" },
      opposition: { label: "对分相", symbol: "☍", harmony: "challenge" },
      quincunx: { label: "十二分之五", symbol: "⚻", harmony: "neutral" },
      semisextile: { label: "十二分之一", symbol: "⚺", harmony: "neutral" },
      sesquiquadrate: { label: "倍半四分", symbol: "⚼", harmony: "challenge" },
      semisquare: { label: "八分相", symbol: "∠", harmony: "challenge" }
    };
    const ZODIAC_ZH_T = {
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
    function zodiacZhT(en) {
      var _a;
      return ZODIAC_ZH_T[(_a = en == null ? void 0 : en.toLowerCase()) == null ? void 0 : _a.trim()] || en || "";
    }
    const realTransitEvents = common_vendor.computed(() => {
      var _a;
      const events = (_a = chartData.value) == null ? void 0 : _a.events;
      if (!events || !Array.isArray(events) || events.length === 0)
        return [];
      return events.map((ev) => {
        const tpKey = (ev.transit_planet || ev.transiting_planet || ev.planet || ev.t_planet || ev.tp || ev.body || "").toLowerCase().trim();
        const npKey = (ev.natal_planet || ev.natal_body || ev.native_planet || ev.n_planet || ev.np || "").toLowerCase().trim();
        const aspectType = (ev.aspect_type || ev.aspect || ev.type || ev.aspect_name || "").toLowerCase().trim();
        const tpDisplay = PLANET_DISPLAY_T[tpKey];
        const npDisplay = PLANET_DISPLAY_T[npKey];
        const aspectLabel = ASPECT_LABEL_T[aspectType];
        const planetsLabel = tpDisplay ? `${tpDisplay.symbol} ${tpDisplay.name}` : tpKey || "行星";
        const natalLabel = npDisplay ? `${npDisplay.symbol} ${npDisplay.name}` : npKey || "";
        const aspectStr = aspectLabel ? `${aspectLabel.symbol} ${aspectLabel.label}` : aspectType || "相位";
        const orbVal = ev.orb ?? ev.orb_value ?? ev.exact_orb ?? null;
        const strengthVal = ev.strength ?? ev.intensity_value ?? null;
        let intensity = "medium";
        if (strengthVal != null) {
          const s = Number(strengthVal);
          intensity = s >= 0.7 ? "strong" : s >= 0.3 ? "medium" : "weak";
        } else if (orbVal != null) {
          const o = Math.abs(Number(orbVal));
          intensity = o < 1 ? "strong" : o < 3 ? "medium" : "weak";
        } else if (ev.intensity) {
          const raw = String(ev.intensity).toLowerCase();
          intensity = raw.includes("strong") || raw.includes("exact") ? "strong" : raw.includes("weak") || raw.includes("loose") ? "weak" : "medium";
        }
        const impact = ev.impact || {};
        const emotionVal = impact.emotion ?? null;
        const pressureVal = impact.pressure ?? null;
        const durationDays = impact.duration_days ?? ev.duration_days ?? null;
        const tags = Array.isArray(ev.tags) ? ev.tags : [];
        let type = (aspectLabel == null ? void 0 : aspectLabel.harmony) || "neutral";
        if (emotionVal != null) {
          if (emotionVal <= -0.3 && type !== "challenge")
            type = "challenge";
          else if (emotionVal >= 0.3 && type === "neutral")
            type = "positive";
        }
        const transitSign = zodiacZhT(ev.transit_sign || ev.t_sign || ev.sign || "");
        return {
          planets: planetsLabel,
          aspect: aspectStr,
          aspectRaw: aspectType,
          natal: natalLabel,
          transitSign,
          date: ev.date_range || ev.date || ev.period || "",
          durationDays,
          desc: ev.description || ev.interpretation || ev.meaning || "",
          orb: orbVal != null ? `${Math.abs(Number(orbVal)).toFixed(1)}°` : "",
          strength: strengthVal != null ? Math.round(Number(strengthVal) * 100) : null,
          emotionVal,
          pressureVal,
          tags,
          intensity,
          type
        };
      });
    });
    function buildImpactDesc(emotionVal, pressureVal, tags, harmony, durationDays) {
      const parts = [];
      if (emotionVal != null) {
        if (emotionVal >= 0.5)
          parts.push("情绪提升");
        else if (emotionVal >= 0.2)
          parts.push("情绪偏正向");
        else if (emotionVal <= -0.6)
          parts.push("情绪受压");
        else if (emotionVal <= -0.3)
          parts.push("情绪有挑战");
        else
          parts.push("情绪平稳");
      }
      if (pressureVal != null) {
        if (pressureVal >= 0.8)
          parts.push("压力感较强");
        else if (pressureVal >= 0.5)
          parts.push("压力中等");
        else if (pressureVal < 0.2)
          parts.push("轻松流动");
      }
      const tagZh = {
        // 自我与张力类
        "self-other tension": "自我与他人的张力",
        "ego tension": "自我意识激活",
        "identity challenge": "自我认知挑战",
        "identity focus": "聚焦自我",
        // 注意力与表达类
        "visibility": "关注度上升",
        "self-expression": "自我表达",
        "expression": "表达发挥",
        // 内心与直觉类
        "inner world": "关注内心",
        "intuition": "直觉敏锐",
        "spirituality": "灵性引导",
        "confusion": "迷茫感",
        "self-image": "自我形象",
        // 活力与能量类
        "vitality": "活力增强",
        "confidence": "自信增加",
        "creative flow": "创意流动",
        "creativity": "创意迸发",
        "originality": "创新独创",
        // 情感平衡类
        "emotional resonance": "情感共鸣",
        "emotional flow": "情感流动",
        "sensitivity": "感知敏锐",
        "emotional tension": "情绪波动",
        "mood swings": "心情起伏",
        "emotional conflict": "情感冲突",
        "vulnerability": "脆弱感",
        "nurturing": "滋养关爱",
        "comfort": "舒适安稳",
        "emotional ease": "情绪轻松",
        // 职业与关注类
        "career focus": "职业聚焦",
        // 思维与沟通类
        "mental flow": "思维流畅",
        "mental clarity": "思维清晰",
        "mental tension": "思维紧绷",
        "insight": "洞察力强",
        "learning": "学习成长",
        "dialogue": "沟通顺畅",
        "communication": "表达沟通",
        "debate": "思想碰撞",
        "information conflict": "信息冲突",
        "miscommunication": "沟通失误",
        // 吸引与关系类
        "attraction": "魅力吸引",
        "harmony": "和谐美好",
        "beauty": "美感享受",
        "pleasure": "愉悦享受",
        "social ease": "社交顺畅",
        "affection": "情谊填充",
        // 行动与动力类
        "motivation": "动力充沛",
        "drive": "行动力强",
        "initiative": "主动出击",
        "action": "行动导向",
        "energy surge": "能量激涌",
        // 冲突与张力类
        "conflict": "冲突张力",
        "confrontation": "正面对抗",
        "tension": "张力感",
        "aggression": "冲动倾向",
        "challenge": "需要突破",
        "desire tension": "欲望与身份张力",
        "indulgence": "放纵感官",
        // 稳定与结构类
        "discipline": "自律专注",
        "structure": "稳定建构",
        "restriction": "受到制约",
        "challenge restriction": "限制挑战",
        "patience": "耐心培养",
        "responsibility": "承担责任",
        // 变革与自由类
        "instability": "不稳定感",
        "rebellion": "突破常规",
        "revolution": "变革契机",
        "upheaval": "翻天覆地",
        "innovation": "创新突破",
        "freedom": "自由解放",
        "awakening": "觉醒与气场展开",
        // 灵感与灵性类
        "inspiration": "灵感涌现",
        "compassion": "慈悲共情",
        "opportunity": "机遇降临",
        // 深层转化类
        "evolution": "蜕变成长",
        "transformation": "深层转化",
        "power": "力量聚焦",
        "empowerment": "力量觉醒",
        "deep change": "深层变革",
        "power struggle": "力量博弈",
        "crisis": "危机与转机",
        "compulsion": "执念驱动",
        // 海王星及模糊类
        "disillusionment": "幻灭感",
        "fog": "模糊与幻象",
        "illusion": "幻想与幻象",
        "deception": "醒觉与分辨"
      };
      const tagTexts = tags.slice(0, 2).map((t) => tagZh[t] || "").filter(Boolean);
      if (tagTexts.length > 0)
        parts.push(...tagTexts);
      if (durationDays != null) {
        if (durationDays >= 120)
          parts.push(`持续约${Math.round(durationDays / 30)}个月`);
        else if (durationDays >= 14)
          parts.push(`持续约${Math.round(durationDays / 7)}周`);
        else if (durationDays > 1)
          parts.push(`持续约${durationDays}天`);
        else
          parts.push("当日短暂影响");
      }
      return parts.length > 0 ? parts.join("，") : harmony === "positive" ? "正向流动" : harmony === "challenge" ? "挑战张力" : "中性影响";
    }
    const realHighlights = common_vendor.computed(() => {
      var _a, _b;
      const sum = (_a = chartData.value) == null ? void 0 : _a.summary;
      const highlights = (sum == null ? void 0 : sum.highlights) || (sum == null ? void 0 : sum.key_planets) || (sum == null ? void 0 : sum.featured_planets);
      if (highlights && Array.isArray(highlights) && highlights.length > 0) {
        return highlights.slice(0, 5).map((h) => {
          var _a2;
          const pKey = (h.planet || h.name || h.body || "").toLowerCase().trim();
          const pd = PLANET_DISPLAY_T[pKey];
          const aspectType = (h.aspect || h.aspect_type || "").toLowerCase();
          const aspectInfo = ASPECT_LABEL_T[aspectType];
          const emotionVal = ((_a2 = h.impact) == null ? void 0 : _a2.emotion) ?? null;
          let harmony = h.harmony || (aspectInfo == null ? void 0 : aspectInfo.harmony) || "neutral";
          if (emotionVal != null) {
            if (emotionVal <= -0.3 && harmony !== "challenge")
              harmony = "challenge";
            else if (emotionVal >= 0.3 && harmony === "neutral")
              harmony = "positive";
          }
          return {
            symbol: (pd == null ? void 0 : pd.symbol) || h.symbol || "✦",
            name: (pd == null ? void 0 : pd.name) || h.name || pKey,
            aspect: aspectInfo ? `${aspectInfo.symbol} ${aspectInfo.label}` : h.aspect || h.position || "",
            impact: h.impact || h.description || h.interpretation || h.meaning || "",
            energy: h.energy || (harmony === "positive" ? "positive" : harmony === "challenge" ? "caution" : "deep"),
            sign: zodiacZhT(h.sign || h.transit_sign || ""),
            strength: null
          };
        });
      }
      const events = (_b = chartData.value) == null ? void 0 : _b.events;
      if (!events || !Array.isArray(events))
        return [];
      const sorted = [...events].sort((a, b) => {
        const sa = Number(a.strength ?? 0);
        const sb = Number(b.strength ?? 0);
        return sb - sa;
      });
      return sorted.slice(0, 5).map((ev) => {
        const pKey = (ev.transit_planet || ev.transiting_planet || ev.planet || ev.t_planet || ev.body || "").toLowerCase().trim();
        const npKey = (ev.natal_planet || ev.natal_body || ev.native_planet || ev.n_planet || ev.np || "").toLowerCase().trim();
        const pd = PLANET_DISPLAY_T[pKey];
        const npDisplay = PLANET_DISPLAY_T[npKey];
        const aspectType = (ev.aspect_type || ev.aspect || ev.type || "").toLowerCase().trim();
        const aspectInfo = ASPECT_LABEL_T[aspectType];
        const impact = ev.impact || {};
        const emotionVal = impact.emotion ?? null;
        const pressureVal = impact.pressure ?? null;
        let harmony = (aspectInfo == null ? void 0 : aspectInfo.harmony) || "neutral";
        if (emotionVal != null) {
          if (emotionVal <= -0.3 && harmony !== "challenge")
            harmony = "challenge";
          else if (emotionVal >= 0.3 && harmony === "neutral")
            harmony = "positive";
        }
        const energy = harmony === "positive" ? "positive" : harmony === "challenge" ? "caution" : "deep";
        const durationDays = impact.duration_days ?? ev.duration_days ?? null;
        const tags = Array.isArray(ev.tags) ? ev.tags : [];
        const strengthPct = ev.strength != null ? Math.round(Number(ev.strength) * 100) : null;
        const natalSuffix = npDisplay ? `${npDisplay.symbol}${npDisplay.name}` : npKey || "";
        const aspectStr = aspectInfo ? `${aspectInfo.symbol} ${aspectInfo.label}` : aspectType || "";
        const nameDisplay = natalSuffix ? `${(pd == null ? void 0 : pd.name) || pKey} ${aspectStr} ${natalSuffix}` : (pd == null ? void 0 : pd.name) || pKey || "行星";
        const impactText = ev.description || ev.interpretation || ev.meaning || buildImpactDesc(emotionVal, pressureVal, tags, harmony, durationDays);
        return {
          symbol: (pd == null ? void 0 : pd.symbol) || "✦",
          name: nameDisplay,
          aspect: aspectStr,
          impact: impactText,
          energy,
          sign: zodiacZhT(ev.transit_sign || ev.sign || ""),
          strength: strengthPct,
          durationDays
        };
      });
    });
    const EMOTIONAL_STATE_ZH = {
      positive: { text: "情绪积极向上，充满活力", icon: "😊" },
      negative: { text: "情绪面临挑战，需要关怀自己", icon: "😔" },
      neutral: { text: "情绪平稳，内心安定", icon: "😌" },
      mixed: { text: "情绪交织复杂，起伏变化", icon: "🌊" },
      turbulent: { text: "情绪波动较大，保持觉察", icon: "⚡" },
      calm: { text: "情绪平静如水，适合沉淀", icon: "🌙" }
    };
    const ENERGY_LEVEL_ZH = {
      high: { text: "能量充沛，行动力强", icon: "🔥" },
      medium: { text: "能量适中，稳步前行", icon: "⚡" },
      low: { text: "能量偏低，适合休息蓄力", icon: "🌱" },
      very_high: { text: "能量爆发，把握主动出击", icon: "🌟" },
      very_low: { text: "能量低迷，需要好好休养", icon: "🌿" }
    };
    const LIFE_FOCUS_ZH = {
      "inner world": { text: "关注内在世界与自我成长", icon: "🌸" },
      "relationships": { text: "人际关系是当前主题", icon: "💫" },
      "career": { text: "职业与成就是当前焦点", icon: "🎯" },
      "creativity": { text: "创意与表达正当其时", icon: "✨" },
      "healing": { text: "疗愈与修复是当前需要", icon: "🌿" },
      "transformation": { text: "深层转变正在发生", icon: "🦋" },
      "communication": { text: "沟通与表达是当前主题", icon: "💬" },
      "reflection": { text: "反思与整合的好时机", icon: "🪞" }
    };
    const realSummaryThemes = common_vendor.computed(() => {
      var _a;
      const sum = (_a = chartData.value) == null ? void 0 : _a.summary;
      if (!sum)
        return [];
      const themes = sum.key_themes || sum.themes || sum.focus_areas || sum.highlights_text;
      if (themes && Array.isArray(themes) && themes.length > 0) {
        return themes.slice(0, 4).map((t) => {
          if (typeof t === "string")
            return { text: t, icon: "✦" };
          return { text: t.theme || t.title || t.text || String(t), icon: t.icon || "✦" };
        });
      }
      const desc = sum.description || sum.overview || sum.summary_text;
      if (desc && typeof desc === "string") {
        return [{ text: desc, icon: "✦" }];
      }
      const result = [];
      const emotionalState = (sum.emotional_state || "").toLowerCase().trim();
      if (emotionalState) {
        const es = EMOTIONAL_STATE_ZH[emotionalState];
        result.push(es || { text: `情绪状态：${emotionalState}`, icon: "💭" });
      }
      const energyLevel = (sum.energy_level || "").toLowerCase().trim();
      if (energyLevel) {
        const el = ENERGY_LEVEL_ZH[energyLevel];
        result.push(el || { text: `能量水平：${energyLevel}`, icon: "⚡" });
      }
      const lifeFocus = (sum.life_focus || "").toLowerCase().trim();
      if (lifeFocus) {
        const lf = LIFE_FOCUS_ZH[lifeFocus];
        result.push(lf || { text: `当前焦点：${lifeFocus}`, icon: "🎯" });
      }
      return result;
    });
    const ENERGY_LEVEL_PCT = {
      very_low: 15,
      low: 30,
      medium: 55,
      high: 78,
      very_high: 95
    };
    const realEnergy = common_vendor.computed(() => {
      var _a, _b;
      const sum = (_a = chartData.value) == null ? void 0 : _a.summary;
      let overall = null;
      let emotion = null;
      let action = null;
      let social = null;
      if (sum) {
        const ov = sum.overall_energy ?? sum.overall_score ?? null;
        const em = sum.emotion_energy ?? sum.emotional_energy ?? null;
        const ac = sum.action_energy ?? sum.action_score ?? null;
        const so = sum.social_energy ?? sum.social_score ?? null;
        const clamp = (v) => Math.min(100, Math.max(0, Math.round(v)));
        if (ov != null && !isNaN(Number(ov)))
          overall = clamp(Number(ov));
        if (em != null && !isNaN(Number(em)))
          emotion = clamp(Number(em));
        if (ac != null && !isNaN(Number(ac)))
          action = clamp(Number(ac));
        if (so != null && !isNaN(Number(so)))
          social = clamp(Number(so));
        if (overall == null) {
          const elStr = (sum.energy_level || "").toLowerCase().trim();
          if (elStr && ENERGY_LEVEL_PCT[elStr] != null) {
            overall = ENERGY_LEVEL_PCT[elStr];
          }
        }
      }
      const events = (_b = chartData.value) == null ? void 0 : _b.events;
      if (events && Array.isArray(events) && events.length > 0) {
        if (emotion == null) {
          const emVals = events.map((ev) => {
            var _a2;
            return (_a2 = ev.impact) == null ? void 0 : _a2.emotion;
          }).filter((v) => v != null && !isNaN(Number(v))).map(Number);
          if (emVals.length > 0) {
            const avg = emVals.reduce((a, b) => a + b, 0) / emVals.length;
            emotion = Math.min(100, Math.max(0, Math.round((avg + 1) / 2 * 100)));
          }
        }
        if (action == null) {
          const prVals = events.map((ev) => {
            var _a2;
            return (_a2 = ev.impact) == null ? void 0 : _a2.pressure;
          }).filter((v) => v != null && !isNaN(Number(v))).map(Number);
          if (prVals.length > 0) {
            const avg = prVals.reduce((a, b) => a + b, 0) / prVals.length;
            action = Math.min(100, Math.max(0, Math.round(avg * 100)));
          }
        }
      }
      if (overall == null && emotion == null && action == null && social == null)
        return null;
      return { overall, emotion, action, social };
    });
    async function calculateTransit() {
      var _a;
      if (!hasBirthInfo()) {
        step.value = "form";
        return;
      }
      step.value = "loading";
      const TEXTS = ["正在解析当前星体轨迹...", "连接今日宇宙能量...", "计算流运相位...", "生成能量地图..."];
      let idx = 0;
      const timer = setInterval(() => {
        loadingText.value = TEXTS[++idx % TEXTS.length];
      }, 1400);
      try {
        chartData.value = await api_astrology.getTransitChart({ targetDate: targetDateStr.value });
        userStore.updateTransitDate(targetDateStr.value);
        userStore.updateAstrologyCache({ hasTransitCache: true });
      } catch (e) {
        if ((e == null ? void 0 : e.code) === 7001 || ((_a = e == null ? void 0 : e.message) == null ? void 0 : _a.includes("出生信息"))) {
          step.value = "form";
          userStore.updateAstrologyCache({ hasTransitCache: false });
          clearInterval(timer);
          return;
        }
        chartData.value = { events: [], summary: null };
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
        const result = await api_astrology.interpretTransit({ windowDays: 30, focus: "current", tone: "gentle" });
        const text = result.interpretation;
        for (let i = 0; i <= text.length; i++) {
          if (typingGeneration !== myGeneration) {
            interpretation.value = text;
            return;
          }
          await new Promise((r) => setTimeout(r, 25));
          if (typingGeneration !== myGeneration) {
            interpretation.value = text;
            return;
          }
          interpretation.value = text.slice(0, i);
        }
      } catch {
        const mock = `💭 今天水星逆行的影响让你的思维可能转向内省，这不是推进新计划的最佳时机，但非常适合回顾与整理。

木星正在与你的本命太阳形成柔和的拱相，这是一段内在力量被加强的时期，你的直觉和判断力比平时更加准确，可以信任自己的感受。

今日情绪状态：较为内敛，但内心有稳定的支撑感。适合进行深度思考、写日记或与亲近的人进行真实的对话。避免重要决策的公开展示，但私下的沟通和整理效果会很好。🌙`;
        for (let i = 0; i <= mock.length; i++) {
          if (typingGeneration !== myGeneration) {
            interpretation.value = mock;
            return;
          }
          await new Promise((r) => setTimeout(r, 20));
          if (typingGeneration !== myGeneration) {
            interpretation.value = mock;
            return;
          }
          interpretation.value = mock.slice(0, i);
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
      var _a, _b, _c, _d, _e, _f, _g, _h, _i, _j, _k;
      return common_vendor.e({
        a: common_vendor.f(30, (i, k0, i0) => {
          return {
            a: i,
            b: i * 37 % 100 + "%",
            c: i * 53 % 100 + "%",
            d: i * 0.3 % 3 + "s",
            e: (i % 3 === 0 ? 3 : 2) + "rpx",
            f: (i % 3 === 0 ? 3 : 2) + "rpx",
            g: 0.3 + i % 5 * 0.1
          };
        }),
        b: step.value === "loading"
      }, step.value === "loading" ? {
        c: common_vendor.t(loadingText.value)
      } : {}, {
        d: step.value === "form"
      }, step.value === "form" ? common_vendor.e({
        e: common_vendor.t(todayStr.value),
        f: common_vendor.t(hasBirthInfo() ? "✏ 修改" : "+ 去设置"),
        g: common_vendor.o(goToNatalPage, "26"),
        h: hasBirthInfo()
      }, hasBirthInfo() ? common_vendor.e({
        i: common_vendor.t((_a = common_vendor.unref(userStore).userInfo) == null ? void 0 : _a.birthTime),
        j: common_vendor.t((_b = common_vendor.unref(userStore).userInfo) == null ? void 0 : _b.birthCity),
        k: (_c = common_vendor.unref(userStore).userInfo) == null ? void 0 : _c.birthLat
      }, ((_d = common_vendor.unref(userStore).userInfo) == null ? void 0 : _d.birthLat) ? {
        l: common_vendor.t((_f = (_e = common_vendor.unref(userStore).userInfo) == null ? void 0 : _e.birthLat) == null ? void 0 : _f.toFixed(4)),
        m: common_vendor.t((_h = (_g = common_vendor.unref(userStore).userInfo) == null ? void 0 : _g.birthLng) == null ? void 0 : _h.toFixed(4))
      } : {}) : {
        n: common_vendor.o(goToNatalPage, "76")
      }, {
        o: !isToday.value
      }, !isToday.value ? {} : {}, {
        p: common_vendor.t(targetDateDisplay.value),
        q: targetDateStr.value,
        r: pickerMinDate,
        s: common_vendor.unref(pickerMaxDate),
        t: common_vendor.o(onDatePickerChange, "0b"),
        v: !isToday.value
      }, !isToday.value ? {
        w: common_vendor.o(($event) => targetDateStr.value = formatDateToYMD(common_vendor.unref(today)), "b3")
      } : {}, {
        x: !hasBirthInfo() ? 1 : "",
        y: common_vendor.o(calculateTransit, "73")
      }) : {}, {
        z: step.value === "result"
      }, step.value === "result" ? common_vendor.e({
        A: common_vendor.t(isToday.value ? "今日" : "流运"),
        B: common_vendor.t(todayStr.value),
        C: ((_i = realEnergy.value) == null ? void 0 : _i.overall) != null
      }, ((_j = realEnergy.value) == null ? void 0 : _j.overall) != null ? {
        D: common_vendor.t(realEnergy.value.overall)
      } : {}, {
        E: activeTab.value === "today"
      }, activeTab.value === "today" ? {} : {}, {
        F: activeTab.value === "today" ? 1 : "",
        G: common_vendor.o(($event) => activeTab.value = "today", "c9"),
        H: activeTab.value === "events"
      }, activeTab.value === "events" ? {} : {}, {
        I: activeTab.value === "events" ? 1 : "",
        J: common_vendor.o(($event) => activeTab.value = "events", "29"),
        K: activeTab.value === "interpret"
      }, activeTab.value === "interpret" ? {} : {}, {
        L: activeTab.value === "interpret" ? 1 : "",
        M: common_vendor.o(($event) => activeTab.value = "interpret", "ea"),
        N: activeTab.value === "today"
      }, activeTab.value === "today" ? common_vendor.e({
        O: realEnergy.value
      }, realEnergy.value ? {
        P: common_vendor.f([{
          key: "overall",
          label: "综合",
          color: "#d4a847"
        }, {
          key: "emotion",
          label: "情绪",
          color: "#a87ed0"
        }, {
          key: "action",
          label: "压力",
          color: "#e06060"
        }, {
          key: "social",
          label: "社交",
          color: "#70b8e0"
        }], (item, k0, i0) => {
          return common_vendor.e({
            a: realEnergy.value[item.key] != null
          }, realEnergy.value[item.key] != null ? {
            b: common_vendor.t(item.label),
            c: realEnergy.value[item.key] + "%",
            d: item.color,
            e: common_vendor.t(realEnergy.value[item.key]),
            f: item.color
          } : {}, {
            g: item.key
          });
        })
      } : {}, {
        Q: realSummaryThemes.value.length > 0
      }, realSummaryThemes.value.length > 0 ? {
        R: common_vendor.f(realSummaryThemes.value, (theme, idx, i0) => {
          return {
            a: common_vendor.t(theme.icon),
            b: common_vendor.t(theme.text),
            c: idx
          };
        })
      } : {}, {
        S: realHighlights.value.length === 0
      }, realHighlights.value.length === 0 ? {} : {}, {
        T: common_vendor.f(realHighlights.value, (h, idx, i0) => {
          return common_vendor.e({
            a: common_vendor.t(h.symbol),
            b: common_vendor.n("psw-" + h.energy),
            c: common_vendor.t(h.name),
            d: h.sign
          }, h.sign ? {
            e: common_vendor.t(h.sign)
          } : {}, {
            f: h.aspect
          }, h.aspect ? {
            g: common_vendor.t(h.aspect),
            h: common_vendor.n("ab-" + h.energy)
          } : {}, {
            i: h.impact
          }, h.impact ? {
            j: common_vendor.t(h.impact)
          } : {}, {
            k: idx,
            l: common_vendor.n("pc-" + h.energy)
          });
        })
      }) : {}, {
        U: activeTab.value === "events"
      }, activeTab.value === "events" ? common_vendor.e({
        V: realTransitEvents.value.length === 0
      }, realTransitEvents.value.length === 0 ? {} : {}, {
        W: common_vendor.f(realTransitEvents.value, (ev, idx, i0) => {
          return common_vendor.e({
            a: common_vendor.t(ev.planets),
            b: ev.transitSign
          }, ev.transitSign ? {
            c: common_vendor.t(ev.transitSign)
          } : {}, {
            d: common_vendor.t(ev.aspect),
            e: common_vendor.n("eat-" + ev.type),
            f: common_vendor.t(ev.natal),
            g: common_vendor.t(ev.intensity === "strong" ? "强" : ev.intensity === "weak" ? "弱" : "中"),
            h: common_vendor.n("eid-" + ev.intensity),
            i: ev.orb
          }, ev.orb ? {
            j: common_vendor.t(ev.orb)
          } : {}, {
            k: ev.strength != null
          }, ev.strength != null ? {
            l: common_vendor.n("esf-" + ev.type),
            m: ev.strength + "%",
            n: common_vendor.t(ev.strength),
            o: common_vendor.n("esf-text-" + ev.type)
          } : {}, {
            p: ev.durationDays != null
          }, ev.durationDays != null ? {
            q: common_vendor.t(ev.durationDays >= 120 ? Math.round(ev.durationDays / 30) + "个月" : ev.durationDays >= 14 ? Math.round(ev.durationDays / 7) + "周" : ev.durationDays + "天")
          } : {}, {
            r: ev.date
          }, ev.date ? {
            s: common_vendor.t(ev.date)
          } : {}, {
            t: ev.emotionVal != null || ev.pressureVal != null
          }, ev.emotionVal != null || ev.pressureVal != null ? common_vendor.e({
            v: ev.emotionVal != null
          }, ev.emotionVal != null ? common_vendor.e({
            w: ev.emotionVal >= 0
          }, ev.emotionVal >= 0 ? {
            x: ev.emotionVal * 50 + "%"
          } : {
            y: Math.abs(ev.emotionVal) * 50 + "%"
          }, {
            z: common_vendor.t(ev.emotionVal >= 0 ? "+" : ""),
            A: common_vendor.t((ev.emotionVal * 100).toFixed(0)),
            B: ev.emotionVal >= 0 ? "#40c878" : "#e05050"
          }) : {}, {
            C: ev.pressureVal != null
          }, ev.pressureVal != null ? {
            D: ev.pressureVal * 100 + "%",
            E: common_vendor.t((ev.pressureVal * 100).toFixed(0)),
            F: ev.pressureVal >= 0.6 ? "#e08050" : "#70b8e0"
          } : {}) : {}, {
            G: ev.tags && ev.tags.length > 0
          }, ev.tags && ev.tags.length > 0 ? {
            H: common_vendor.f(ev.tags.slice(0, 4), (tag, ti, i1) => {
              return {
                a: common_vendor.t(tag),
                b: ti
              };
            }),
            I: common_vendor.n("etag-" + ev.type)
          } : {}, {
            J: ev.desc
          }, ev.desc ? {
            K: common_vendor.t(ev.desc)
          } : {}, {
            L: idx,
            M: common_vendor.n("evc-" + ev.type)
          });
        })
      }) : {}, {
        X: activeTab.value === "interpret"
      }, activeTab.value === "interpret" ? common_vendor.e({
        Y: common_vendor.f(INTERPRET_TYPES, (t, k0, i0) => {
          return {
            a: common_vendor.t(t.icon),
            b: common_vendor.t(t.label),
            c: t.key,
            d: selectedInterpretType.value === t.key ? 1 : "",
            e: common_vendor.o(($event) => selectedInterpretType.value = t.key, t.key)
          };
        }),
        Z: !interpretation.value && !isInterpreting.value
      }, !interpretation.value && !isInterpreting.value ? {
        aa: common_vendor.o(getInterpretation, "2b")
      } : {}, {
        ab: interpretation.value || isInterpreting.value
      }, interpretation.value || isInterpreting.value ? common_vendor.e({
        ac: common_vendor.t((_k = INTERPRET_TYPES.find((t) => t.key === selectedInterpretType.value)) == null ? void 0 : _k.label),
        ad: isInterpreting.value
      }, isInterpreting.value ? {} : {}, {
        ae: common_vendor.t(interpretation.value)
      }) : {}, {
        af: interpretation.value && !isInterpreting.value
      }, interpretation.value && !isInterpreting.value ? {
        ag: common_vendor.o(getInterpretation, "f5")
      } : {}) : {}, {
        ah: common_vendor.o(($event) => step.value = "form", "78"),
        ai: common_vendor.o(goInterpret, "c1")
      }) : {});
    };
  }
});
wx.createPage(_sfc_main);
//# sourceMappingURL=../../../.sourcemap/mp-weixin/pages/astrology/transit.js.map
