"use strict";
const common_vendor = require("../../common/vendor.js");
const api_astrology = require("../../api/astrology.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "synastry",
  setup(__props) {
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
    const YEAR_OPTIONS = YEARS.map((y) => ({ label: y + "年", value: y }));
    const MONTH_OPTIONS = MONTHS.map((m) => ({ label: m + "月", value: m }));
    const DAY_OPTIONS = DAYS.map((d) => ({ label: d + "日", value: d }));
    const HOUR_OPTIONS = HOURS.map((h) => ({ label: h + "时", value: h }));
    const selfForm = common_vendor.reactive({ year: 1998, month: 6, day: 15, hour: 8, city: "北京" });
    const partnerForm = common_vendor.reactive({ year: 1997, month: 3, day: 22, hour: 10, city: "上海" });
    const partnerName = common_vendor.ref("Ta");
    function onSelfYearChange(e) {
      selfForm.year = YEAR_OPTIONS[e.detail.value].value;
    }
    function onSelfMonthChange(e) {
      selfForm.month = MONTH_OPTIONS[e.detail.value].value;
    }
    function onSelfDayChange(e) {
      selfForm.day = DAY_OPTIONS[e.detail.value].value;
    }
    function onSelfHourChange(e) {
      selfForm.hour = HOUR_OPTIONS[e.detail.value].value;
    }
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
    const INTERPRET_TYPES = [
      { key: "love", label: "爱情关系", icon: "💞" },
      { key: "marriage", label: "婚姻契合", icon: "💍" },
      { key: "soul", label: "灵魂连接", icon: "✨" },
      { key: "emotion", label: "情绪共鸣", icon: "🌊" }
    ];
    const MOCK_ANALYSIS = [
      { label: "吸引力", value: 82, color: "#e070a0", desc: "强烈的磁场吸引" },
      { label: "情绪匹配", value: 68, color: "#7080f0", desc: "情感波动相似" },
      { label: "冲突指数", value: 34, color: "#f09040", desc: "少量摩擦" },
      { label: "长期稳定", value: 75, color: "#50c878", desc: "基础稳固" }
    ];
    const MOCK_THEMES = [
      { icon: "🔥", title: "强烈吸引", desc: "金星与火星的连接带来无法忽视的磁场感应，初次相遇时便有明确的化学反应。", type: "positive" },
      { icon: "🌊", title: "情绪依赖", desc: "月亮与海王星的相位创造了深层情感联系，你们之间容易产生强烈的情感依附感。", type: "neutral" },
      { icon: "⚡", title: "权力拉扯", desc: "火星与冥王星的四分相提示双方在某些议题上可能产生控制欲的冲突，需要有意识地协商。", type: "challenge" }
    ];
    async function calculateSynastry() {
      if (!selfForm.city || !partnerForm.city) {
        common_vendor.index.showToast({ title: "请填写出生城市", icon: "none" });
        return;
      }
      step.value = "loading";
      const TEXTS = ["正在解析双人星盘能量...", "计算相位连接...", "正在连接宇宙能量...", "生成关系地图..."];
      let idx = 0;
      const timer = setInterval(() => {
        loadingText.value = TEXTS[++idx % TEXTS.length];
      }, 1500);
      try {
        const result = await api_astrology.getSynastryChart({
          selfBirthInfo: { year: selfForm.year, month: selfForm.month, day: selfForm.day, hour: selfForm.hour, minute: 0, city: selfForm.city },
          partnerBirthInfo: { year: partnerForm.year, month: partnerForm.month, day: partnerForm.day, hour: partnerForm.hour, minute: 0, city: partnerForm.city },
          partnerName: partnerName.value
        });
        chartData.value = result;
      } catch {
        chartData.value = { relationshipModel: null, aspects: [], themes: [], chart: null };
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
        const result = await api_astrology.interpretSynastry({ chart: (_a = chartData.value) == null ? void 0 : _a.chart, focus: "compatibility", interpretType: selectedInterpretType.value, tone: "gentle" });
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
      }, step.value === "form" ? {
        d: common_vendor.t(selfForm.year),
        e: common_vendor.unref(YEAR_OPTIONS),
        f: common_vendor.unref(YEARS).indexOf(selfForm.year),
        g: common_vendor.o(onSelfYearChange, "34"),
        h: common_vendor.t(selfForm.month),
        i: common_vendor.unref(MONTH_OPTIONS),
        j: selfForm.month - 1,
        k: common_vendor.o(onSelfMonthChange, "53"),
        l: common_vendor.t(selfForm.day),
        m: common_vendor.unref(DAY_OPTIONS),
        n: selfForm.day - 1,
        o: common_vendor.o(onSelfDayChange, "c0"),
        p: common_vendor.t(selfForm.hour),
        q: common_vendor.unref(HOUR_OPTIONS),
        r: selfForm.hour,
        s: common_vendor.o(onSelfHourChange, "44"),
        t: selfForm.city,
        v: common_vendor.o(($event) => selfForm.city = $event.detail.value, "35"),
        w: partnerName.value,
        x: common_vendor.o(($event) => partnerName.value = $event.detail.value, "69"),
        y: common_vendor.t(partnerForm.year),
        z: common_vendor.unref(YEAR_OPTIONS),
        A: common_vendor.unref(YEARS).indexOf(partnerForm.year),
        B: common_vendor.o(onPartnerYearChange, "aa"),
        C: common_vendor.t(partnerForm.month),
        D: common_vendor.unref(MONTH_OPTIONS),
        E: partnerForm.month - 1,
        F: common_vendor.o(onPartnerMonthChange, "5b"),
        G: common_vendor.t(partnerForm.day),
        H: common_vendor.unref(DAY_OPTIONS),
        I: partnerForm.day - 1,
        J: common_vendor.o(onPartnerDayChange, "91"),
        K: common_vendor.t(partnerForm.hour),
        L: common_vendor.unref(HOUR_OPTIONS),
        M: partnerForm.hour,
        N: common_vendor.o(onPartnerHourChange, "ce"),
        O: partnerForm.city,
        P: common_vendor.o(($event) => partnerForm.city = $event.detail.value, "52"),
        Q: common_vendor.o(calculateSynastry, "a4")
      } : {}, {
        R: step.value === "result"
      }, step.value === "result" ? common_vendor.e({
        S: common_vendor.t(partnerName.value || "Ta"),
        T: activeTab.value === "analysis" ? 1 : "",
        U: common_vendor.o(($event) => activeTab.value = "analysis", "d2"),
        V: activeTab.value === "themes" ? 1 : "",
        W: common_vendor.o(($event) => activeTab.value = "themes", "0e"),
        X: activeTab.value === "interpret" ? 1 : "",
        Y: common_vendor.o(($event) => activeTab.value = "interpret", "e6"),
        Z: activeTab.value === "analysis"
      }, activeTab.value === "analysis" ? {
        aa: common_vendor.f(MOCK_ANALYSIS, (item, k0, i0) => {
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
      } : {}, {
        ab: activeTab.value === "themes"
      }, activeTab.value === "themes" ? {
        ac: common_vendor.f(MOCK_THEMES, (theme, k0, i0) => {
          return {
            a: common_vendor.t(theme.icon),
            b: common_vendor.t(theme.title),
            c: common_vendor.t(theme.desc),
            d: theme.title,
            e: common_vendor.n("theme-" + theme.type)
          };
        })
      } : {}, {
        ad: activeTab.value === "interpret"
      }, activeTab.value === "interpret" ? common_vendor.e({
        ae: common_vendor.f(INTERPRET_TYPES, (t, k0, i0) => {
          return {
            a: common_vendor.t(t.icon),
            b: common_vendor.t(t.label),
            c: t.key,
            d: selectedInterpretType.value === t.key ? 1 : "",
            e: common_vendor.o(($event) => selectedInterpretType.value = t.key, t.key)
          };
        }),
        af: !interpretation.value && !isInterpreting.value
      }, !interpretation.value && !isInterpreting.value ? {
        ag: common_vendor.o(getInterpretation, "7b")
      } : {}, {
        ah: interpretation.value || isInterpreting.value
      }, interpretation.value || isInterpreting.value ? common_vendor.e({
        ai: common_vendor.t((_a = INTERPRET_TYPES.find((t) => t.key === selectedInterpretType.value)) == null ? void 0 : _a.label),
        aj: isInterpreting.value
      }, isInterpreting.value ? {} : {}, {
        ak: common_vendor.t(interpretation.value)
      }) : {}, {
        al: interpretation.value && !isInterpreting.value
      }, interpretation.value && !isInterpreting.value ? {
        am: common_vendor.o(getInterpretation, "d7")
      } : {}) : {}, {
        an: common_vendor.o(($event) => step.value = "form", "3f"),
        ao: common_vendor.o(goInterpret, "74")
      }) : {});
    };
  }
});
wx.createPage(_sfc_main);
//# sourceMappingURL=../../../.sourcemap/mp-weixin/pages/astrology/synastry.js.map
