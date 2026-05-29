"use strict";
const common_vendor = require("../../common/vendor.js");
const api_astrology = require("../../api/astrology.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "transit",
  setup(__props) {
    const step = common_vendor.ref("form");
    const loadingText = common_vendor.ref("正在解析当前星体轨迹...");
    const chartData = common_vendor.ref(null);
    const interpretation = common_vendor.ref("");
    const isInterpreting = common_vendor.ref(false);
    const selectedInterpretType = common_vendor.ref("today_emotion");
    const activeTab = common_vendor.ref("today");
    let typingGeneration = 0;
    const today = /* @__PURE__ */ new Date();
    const todayStr = common_vendor.computed(() => `${today.getFullYear()}年${today.getMonth() + 1}月${today.getDate()}日`);
    const YEARS = Array.from({ length: 80 }, (_, i) => 1950 + i);
    const MONTHS = Array.from({ length: 12 }, (_, i) => i + 1);
    const DAYS = Array.from({ length: 31 }, (_, i) => i + 1);
    const HOURS = Array.from({ length: 24 }, (_, i) => i);
    const YEAR_OPTIONS = YEARS.map((y) => ({ label: y + "年", value: y }));
    const MONTH_OPTIONS = MONTHS.map((m) => ({ label: m + "月", value: m }));
    const DAY_OPTIONS = DAYS.map((d) => ({ label: d + "日", value: d }));
    const HOUR_OPTIONS = HOURS.map((h) => ({ label: h + "时", value: h }));
    const form = common_vendor.reactive({ year: 1998, month: 6, day: 15, hour: 8, city: "北京" });
    function onYearChange(e) {
      form.year = YEAR_OPTIONS[e.detail.value].value;
    }
    function onMonthChange(e) {
      form.month = MONTH_OPTIONS[e.detail.value].value;
    }
    function onDayChange(e) {
      form.day = DAY_OPTIONS[e.detail.value].value;
    }
    function onHourChange(e) {
      form.hour = HOUR_OPTIONS[e.detail.value].value;
    }
    const INTERPRET_TYPES = [
      { key: "today_emotion", label: "今日情绪", icon: "💭" },
      { key: "relationship", label: "近期关系", icon: "💫" },
      { key: "stress", label: "压力感知", icon: "⚡" },
      { key: "growth", label: "成长方向", icon: "🌱" }
    ];
    const TODAY_HIGHLIGHTS = [
      { symbol: "☿", name: "水星", aspect: "逆行", impact: "沟通与出行可能受阻，适合反思而非推进", energy: "caution" },
      { symbol: "♃", name: "木星", aspect: "拱本命太阳", impact: "带来扩展性机遇，直觉准确，适合冒险", energy: "positive" },
      { symbol: "☽", name: "月亮", aspect: "位于天蝎座", impact: "情绪深沉而敏感，容易洞察事物的深层含义", energy: "deep" }
    ];
    const TODAY_ENERGY = {
      overall: 72,
      emotion: 58,
      action: 80,
      social: 65
    };
    const TRANSIT_EVENTS = [
      {
        planets: "♄ 土星",
        aspect: "合",
        natal: "☽ 月亮",
        date: "近 3 周",
        duration: "持续约 6 周",
        desc: "你可能感受到情感上的压力与责任感加重，这是一段需要沉淀内心的时期。",
        intensity: "strong",
        type: "challenge"
      },
      {
        planets: "♃ 木星",
        aspect: "拱",
        natal: "☉ 太阳",
        date: "近 2 个月",
        duration: "持续约 3 个月",
        desc: "能量充沛，有利于事业推进和自我展示，机会值得主动把握。",
        intensity: "strong",
        type: "positive"
      },
      {
        planets: "♀ 金星",
        aspect: "六分",
        natal: "♂ 火星",
        date: "近 2 周",
        duration: "持续约 2 周",
        desc: "人际关系和谐，情感表达流畅，恋爱运势良好的时期。",
        intensity: "medium",
        type: "positive"
      },
      {
        planets: "♂ 火星",
        aspect: "四分",
        natal: "♄ 土星",
        date: "近 1 个月",
        duration: "持续约 1 个月",
        desc: "执行力遇到阻碍，需要避免冲动和对抗，耐心比速度更重要。",
        intensity: "medium",
        type: "challenge"
      }
    ];
    async function calculateTransit() {
      if (!form.city) {
        common_vendor.index.showToast({ title: "请填写出生城市", icon: "none" });
        return;
      }
      step.value = "loading";
      const TEXTS = ["正在解析当前星体轨迹...", "连接今日宇宙能量...", "计算流运相位...", "生成能量地图..."];
      let idx = 0;
      const timer = setInterval(() => {
        loadingText.value = TEXTS[++idx % TEXTS.length];
      }, 1400);
      try {
        const result = await api_astrology.getTransitChart({
          birthInfo: { year: form.year, month: form.month, day: form.day, hour: form.hour, minute: 0, city: form.city }
        });
        chartData.value = result;
      } catch {
        chartData.value = { events: [], summary: null, chart: null };
      } finally {
        clearInterval(timer);
        step.value = "result";
      }
    }
    async function getInterpretation() {
      var _a;
      const myGeneration = ++typingGeneration;
      isInterpreting.value = true;
      interpretation.value = "";
      activeTab.value = "interpret";
      try {
        const result = await api_astrology.interpretTransit({ chart: (_a = chartData.value) == null ? void 0 : _a.chart, focus: "current", interpretType: selectedInterpretType.value, tone: "gentle" });
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
      var _a;
      return common_vendor.e({
        a: step.value === "loading"
      }, step.value === "loading" ? {
        b: common_vendor.t(loadingText.value)
      } : {}, {
        c: step.value === "form"
      }, step.value === "form" ? {
        d: common_vendor.t(todayStr.value),
        e: common_vendor.t(form.year),
        f: common_vendor.unref(YEAR_OPTIONS),
        g: common_vendor.unref(YEARS).indexOf(form.year),
        h: common_vendor.o(onYearChange, "9e"),
        i: common_vendor.t(form.month),
        j: common_vendor.unref(MONTH_OPTIONS),
        k: form.month - 1,
        l: common_vendor.o(onMonthChange, "f9"),
        m: common_vendor.t(form.day),
        n: common_vendor.unref(DAY_OPTIONS),
        o: form.day - 1,
        p: common_vendor.o(onDayChange, "5c"),
        q: common_vendor.t(form.hour),
        r: common_vendor.unref(HOUR_OPTIONS),
        s: form.hour,
        t: common_vendor.o(onHourChange, "09"),
        v: form.city,
        w: common_vendor.o(($event) => form.city = $event.detail.value, "eb"),
        x: common_vendor.o(calculateTransit, "17")
      } : {}, {
        y: step.value === "result"
      }, step.value === "result" ? common_vendor.e({
        z: common_vendor.t(todayStr.value),
        A: common_vendor.t(TODAY_ENERGY.overall),
        B: activeTab.value === "today" ? 1 : "",
        C: common_vendor.o(($event) => activeTab.value = "today", "03"),
        D: activeTab.value === "events" ? 1 : "",
        E: common_vendor.o(($event) => activeTab.value = "events", "ea"),
        F: activeTab.value === "interpret" ? 1 : "",
        G: common_vendor.o(($event) => activeTab.value = "interpret", "b2"),
        H: activeTab.value === "today"
      }, activeTab.value === "today" ? {
        I: common_vendor.f(TODAY_ENERGY, (val, key, i0) => {
          return {
            a: common_vendor.t(key === "overall" ? "综合" : key === "emotion" ? "情感" : key === "action" ? "行动" : "社交"),
            b: common_vendor.t(val),
            c: `conic-gradient(#f09040 ${val * 3.6}deg, rgba(255,255,255,0.05) 0deg)`,
            d: key
          };
        }),
        J: common_vendor.f(TODAY_HIGHLIGHTS, (h, k0, i0) => {
          return {
            a: common_vendor.t(h.symbol),
            b: common_vendor.t(h.name),
            c: common_vendor.t(h.aspect),
            d: common_vendor.n("hab-" + h.energy),
            e: common_vendor.t(h.impact),
            f: h.name,
            g: common_vendor.n("hc-" + h.energy)
          };
        })
      } : {}, {
        K: activeTab.value === "events"
      }, activeTab.value === "events" ? {
        L: common_vendor.f(TRANSIT_EVENTS, (ev, k0, i0) => {
          return {
            a: common_vendor.t(ev.planets),
            b: common_vendor.t(ev.aspect),
            c: common_vendor.n("eab-" + ev.type),
            d: common_vendor.t(ev.natal),
            e: common_vendor.t(ev.intensity === "strong" ? "强" : "中"),
            f: common_vendor.n("ei-" + ev.intensity),
            g: common_vendor.t(ev.date),
            h: common_vendor.t(ev.duration),
            i: common_vendor.t(ev.desc),
            j: ev.planets + ev.natal,
            k: common_vendor.n("ec-" + ev.type)
          };
        })
      } : {}, {
        M: activeTab.value === "interpret"
      }, activeTab.value === "interpret" ? common_vendor.e({
        N: common_vendor.f(INTERPRET_TYPES, (t, k0, i0) => {
          return {
            a: common_vendor.t(t.icon),
            b: common_vendor.t(t.label),
            c: t.key,
            d: selectedInterpretType.value === t.key ? 1 : "",
            e: common_vendor.o(($event) => selectedInterpretType.value = t.key, t.key)
          };
        }),
        O: !interpretation.value && !isInterpreting.value
      }, !interpretation.value && !isInterpreting.value ? {
        P: common_vendor.o(getInterpretation, "2d")
      } : {}, {
        Q: interpretation.value || isInterpreting.value
      }, interpretation.value || isInterpreting.value ? common_vendor.e({
        R: common_vendor.t((_a = INTERPRET_TYPES.find((t) => t.key === selectedInterpretType.value)) == null ? void 0 : _a.label),
        S: isInterpreting.value
      }, isInterpreting.value ? {} : {}, {
        T: common_vendor.t(interpretation.value)
      }) : {}, {
        U: interpretation.value && !isInterpreting.value
      }, interpretation.value && !isInterpreting.value ? {
        V: common_vendor.o(getInterpretation, "10")
      } : {}) : {}, {
        W: common_vendor.o(($event) => step.value = "form", "3a"),
        X: common_vendor.o(goInterpret, "9c")
      }) : {});
    };
  }
});
wx.createPage(_sfc_main);
//# sourceMappingURL=../../../.sourcemap/mp-weixin/pages/astrology/transit.js.map
