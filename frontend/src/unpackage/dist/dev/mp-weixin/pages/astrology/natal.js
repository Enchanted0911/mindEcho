"use strict";
const common_vendor = require("../../common/vendor.js");
const api_astrology = require("../../api/astrology.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "natal",
  setup(__props) {
    const step = common_vendor.ref("form");
    const loadingText = common_vendor.ref("正在解析星体轨迹...");
    const chartData = common_vendor.ref(null);
    const interpretation = common_vendor.ref("");
    const isInterpreting = common_vendor.ref(false);
    const selectedFocus = common_vendor.ref("personality");
    const activeTab = common_vendor.ref("planets");
    let typingGeneration = 0;
    const form = common_vendor.reactive({
      year: (/* @__PURE__ */ new Date()).getFullYear() - 25,
      month: 1,
      day: 1,
      hour: 8,
      minute: 0,
      city: ""
    });
    const FOCUS_OPTIONS = [
      { key: "personality", label: "性格人格", icon: "✨" },
      { key: "emotion", label: "情感模式", icon: "💫" },
      { key: "career", label: "天赋事业", icon: "⚡" },
      { key: "growth", label: "成长课题", icon: "🌱" },
      { key: "shadow", label: "阴影人格", icon: "🌑" }
    ];
    const MOCK_PLANETS = [
      { symbol: "☉", name: "太阳", sign: "狮子座", house: "7宫", color: "#FFD700", strength: 85 },
      { symbol: "☽", name: "月亮", sign: "双鱼座", house: "2宫", color: "#C8C8FF", strength: 72 },
      { symbol: "☿", name: "水星", sign: "处女座", house: "8宫", color: "#90EE90", strength: 68 },
      { symbol: "♀", name: "金星", sign: "天蝎座", house: "10宫", color: "#FFB6C1", strength: 78 },
      { symbol: "♂", name: "火星", sign: "射手座", house: "11宫", color: "#FF6B6B", strength: 62 },
      { symbol: "♃", name: "木星", sign: "天秤座", house: "9宫", color: "#DEB887", strength: 55 },
      { symbol: "♄", name: "土星", sign: "摩羯座", house: "12宫", color: "#8B9DC3", strength: 48 }
    ];
    const MOCK_ASPECTS = [
      { p1: "☉ 太阳", aspect: "△ 三分", p2: "☽ 月亮", harmony: "positive" },
      { p1: "☽ 月亮", aspect: "□ 四分", p2: "♄ 土星", harmony: "challenge" },
      { p1: "♀ 金星", aspect: "☌ 合", p2: "♂ 火星", harmony: "neutral" },
      { p1: "☿ 水星", aspect: "⚹ 六分", p2: "♃ 木星", harmony: "positive" }
    ];
    const YEARS = Array.from({ length: 80 }, (_, i) => 1950 + i);
    const MONTHS = Array.from({ length: 12 }, (_, i) => i + 1);
    const DAYS = Array.from({ length: 31 }, (_, i) => i + 1);
    const HOURS = Array.from({ length: 24 }, (_, i) => i);
    const MINUTES = [0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55];
    const YEAR_OPTIONS = YEARS.map((y) => ({ label: y + "年", value: y }));
    const MONTH_OPTIONS = MONTHS.map((m) => ({ label: m + "月", value: m }));
    const DAY_OPTIONS = DAYS.map((d) => ({ label: d + "日", value: d }));
    const HOUR_OPTIONS = HOURS.map((h) => ({ label: h + "时", value: h }));
    const MINUTE_OPTIONS = MINUTES.map((m) => ({ label: String(m).padStart(2, "0") + "分", value: m }));
    function onYearChange(e) {
      form.year = Number(YEAR_OPTIONS[e.detail.value].value);
    }
    function onMonthChange(e) {
      form.month = Number(MONTH_OPTIONS[e.detail.value].value);
    }
    function onDayChange(e) {
      form.day = Number(DAY_OPTIONS[e.detail.value].value);
    }
    function onHourChange(e) {
      form.hour = Number(HOUR_OPTIONS[e.detail.value].value);
    }
    function onMinuteChange(e) {
      form.minute = Number(MINUTE_OPTIONS[e.detail.value].value);
    }
    async function calculateChart() {
      if (!form.city.trim()) {
        common_vendor.index.showToast({ title: "请输入出生城市", icon: "none" });
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
        const result = await api_astrology.getNatalChart({
          birthInfo: {
            year: form.year,
            month: form.month,
            day: form.day,
            hour: form.hour,
            minute: form.minute,
            city: form.city
          },
          saveToProfile: true
        });
        chartData.value = result;
        step.value = "result";
      } catch (e) {
        chartData.value = { chart: null, summary: null, savedToProfile: false };
        step.value = "result";
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
          chart: chartData.value.chart,
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
      var _a;
      return common_vendor.e({
        a: step.value === "loading"
      }, step.value === "loading" ? {
        b: common_vendor.t(loadingText.value)
      } : {}, {
        c: step.value === "form"
      }, step.value === "form" ? {
        d: common_vendor.t(form.year),
        e: common_vendor.unref(YEAR_OPTIONS),
        f: common_vendor.unref(YEARS).indexOf(form.year),
        g: common_vendor.o(onYearChange, "9d"),
        h: common_vendor.t(form.month),
        i: common_vendor.unref(MONTH_OPTIONS),
        j: form.month - 1,
        k: common_vendor.o(onMonthChange, "fd"),
        l: common_vendor.t(form.day),
        m: common_vendor.unref(DAY_OPTIONS),
        n: form.day - 1,
        o: common_vendor.o(onDayChange, "d2"),
        p: common_vendor.t(form.hour),
        q: common_vendor.unref(HOUR_OPTIONS),
        r: form.hour,
        s: common_vendor.o(onHourChange, "1b"),
        t: common_vendor.t(String(form.minute).padStart(2, "0")),
        v: common_vendor.unref(MINUTE_OPTIONS),
        w: MINUTES.indexOf(form.minute),
        x: common_vendor.o(onMinuteChange, "48"),
        y: form.city,
        z: common_vendor.o(($event) => form.city = $event.detail.value, "10"),
        A: common_vendor.o(calculateChart, "4f")
      } : {}, {
        B: step.value === "result"
      }, step.value === "result" ? common_vendor.e({
        C: common_vendor.f(12, (i, k0, i0) => {
          return {
            a: i,
            b: `rotate(${i * 30}deg)`
          };
        }),
        D: common_vendor.f(MOCK_PLANETS, (p, idx, i0) => {
          return {
            a: common_vendor.t(p.symbol),
            b: idx,
            c: `rotate(${idx * 51.4}deg) translateX(110rpx) rotate(-${idx * 51.4}deg)`,
            d: p.color
          };
        }),
        E: activeTab.value === "planets" ? 1 : "",
        F: common_vendor.o(($event) => activeTab.value = "planets", "17"),
        G: activeTab.value === "aspects" ? 1 : "",
        H: common_vendor.o(($event) => activeTab.value = "aspects", "ae"),
        I: activeTab.value === "interpret" ? 1 : "",
        J: common_vendor.o(($event) => activeTab.value = "interpret", "ce"),
        K: activeTab.value === "planets"
      }, activeTab.value === "planets" ? {
        L: common_vendor.f(MOCK_PLANETS, (planet, k0, i0) => {
          return {
            a: common_vendor.t(planet.symbol),
            b: planet.color,
            c: planet.color + "22",
            d: planet.color + "44",
            e: common_vendor.t(planet.name),
            f: common_vendor.t(planet.sign),
            g: common_vendor.t(planet.house),
            h: planet.strength + "%",
            i: planet.color,
            j: planet.name
          };
        })
      } : {}, {
        M: activeTab.value === "aspects"
      }, activeTab.value === "aspects" ? {
        N: common_vendor.f(MOCK_ASPECTS, (asp, k0, i0) => {
          return {
            a: common_vendor.t(asp.p1),
            b: common_vendor.t(asp.aspect),
            c: common_vendor.n("badge-" + asp.harmony),
            d: common_vendor.t(asp.p2),
            e: asp.p1 + asp.p2,
            f: common_vendor.n("aspect-" + asp.harmony)
          };
        })
      } : {}, {
        O: activeTab.value === "interpret"
      }, activeTab.value === "interpret" ? common_vendor.e({
        P: common_vendor.f(FOCUS_OPTIONS, (opt, k0, i0) => {
          return {
            a: common_vendor.t(opt.icon),
            b: common_vendor.t(opt.label),
            c: opt.key,
            d: selectedFocus.value === opt.key ? 1 : "",
            e: common_vendor.o(($event) => selectedFocus.value = opt.key, opt.key)
          };
        }),
        Q: !interpretation.value && !isInterpreting.value
      }, !interpretation.value && !isInterpreting.value ? {
        R: common_vendor.o(getInterpretation, "38")
      } : {}, {
        S: interpretation.value || isInterpreting.value
      }, interpretation.value || isInterpreting.value ? common_vendor.e({
        T: common_vendor.t((_a = FOCUS_OPTIONS.find((o) => o.key === selectedFocus.value)) == null ? void 0 : _a.label),
        U: isInterpreting.value
      }, isInterpreting.value ? {} : {}, {
        V: common_vendor.t(interpretation.value)
      }) : {}, {
        W: interpretation.value && !isInterpreting.value
      }, interpretation.value && !isInterpreting.value ? {
        X: common_vendor.o(getInterpretation, "20")
      } : {}) : {}, {
        Y: common_vendor.o(backToForm, "b8"),
        Z: common_vendor.o(goInterpret, "bb")
      }) : {});
    };
  }
});
wx.createPage(_sfc_main);
//# sourceMappingURL=../../../.sourcemap/mp-weixin/pages/astrology/natal.js.map
