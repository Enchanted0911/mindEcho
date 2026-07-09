"use strict";
const common_vendor = require("../../common/vendor.js");
const api_astrology = require("../../api/astrology.js");
const store_user = require("../../store/user.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const userStore = store_user.useUserStore();
    const today = /* @__PURE__ */ new Date();
    const monthDay = `${today.getMonth() + 1}月${today.getDate()}日`;
    const isRefreshing = common_vendor.ref(false);
    const todayFortune = common_vendor.ref({
      planet: "太阳",
      sign: "天蝎座",
      icon: "🔥",
      iconBg: "#E07428",
      summary: "今天能量转向深层探索，适合进行内省或处理复杂的财务问题。直觉力增强，相信你的第一感..."
    });
    const birthInfo = common_vendor.computed(() => userStore.astrologyInfo);
    const hasBirth = common_vendor.computed(() => {
      var _a, _b;
      return !!(((_a = birthInfo.value) == null ? void 0 : _a.birthCity) && ((_b = birthInfo.value) == null ? void 0 : _b.birthTime));
    });
    const birthDisplayTime = common_vendor.computed(() => {
      var _a;
      const t = (_a = birthInfo.value) == null ? void 0 : _a.birthTime;
      if (!t)
        return null;
      try {
        const [date, time] = t.split(" ");
        const [y, m, d] = date.split("-").map(Number);
        return `${y}年${m}月${d}日 ${time}`;
      } catch {
        return t;
      }
    });
    common_vendor.onMounted(async () => {
      const loggedIn = userStore.restoreFromStorage();
      if (!loggedIn) {
        common_vendor.index.reLaunch({ url: "/pages/login/index" });
        return;
      }
      if (!userStore.astrologyInfo) {
        try {
          const info = await api_astrology.getUserAstrologyInfo();
          userStore.setAstrologyInfo(info);
        } catch (e) {
          common_vendor.index.__f__("warn", "at pages/astrology/index.vue:50", "[astrology/index] Failed to fetch astrology info:", e);
        }
      }
    });
    async function refreshAstrologyInfo() {
      if (isRefreshing.value)
        return;
      isRefreshing.value = true;
      try {
        const info = await api_astrology.getUserAstrologyInfo();
        userStore.setAstrologyInfo(info);
        common_vendor.index.showToast({ title: "信息已刷新", icon: "success", duration: 1500 });
      } catch {
        common_vendor.index.showToast({ title: "刷新失败", icon: "none", duration: 1500 });
      } finally {
        isRefreshing.value = false;
      }
    }
    function goToNatal() {
      common_vendor.index.navigateTo({ url: "/pages/astrology/natal" });
    }
    function goToSynastry() {
      common_vendor.index.navigateTo({ url: "/pages/astrology/synastry" });
    }
    function goToTransit() {
      common_vendor.index.navigateTo({ url: "/pages/astrology/transit" });
    }
    function goToInterpret() {
      common_vendor.index.navigateTo({ url: "/pages/astrology/natal" });
    }
    function goToDetail() {
      common_vendor.index.navigateTo({ url: "/pages/astrology/transit" });
    }
    function goSetBirthInfo() {
      common_vendor.index.navigateTo({ url: "/pages/astrology/natal" });
    }
    return (_ctx, _cache) => {
      var _a, _b, _c, _d, _e, _f, _g;
      return common_vendor.e({
        a: hasBirth.value
      }, hasBirth.value ? {
        b: isRefreshing.value ? 1 : "",
        c: common_vendor.o(refreshAstrologyInfo, "29"),
        d: common_vendor.o(goSetBirthInfo, "8c"),
        e: common_vendor.t(birthDisplayTime.value),
        f: common_vendor.t((_a = birthInfo.value) == null ? void 0 : _a.birthCity),
        g: common_vendor.t(((_b = birthInfo.value) == null ? void 0 : _b.hasNatalCache) ? "已计算" : "未计算"),
        h: common_vendor.n(((_c = birthInfo.value) == null ? void 0 : _c.hasNatalCache) ? "badge-ready" : "badge-empty"),
        i: common_vendor.t(((_d = birthInfo.value) == null ? void 0 : _d.hasSynastryCache) ? "已计算" : "未计算"),
        j: common_vendor.n(((_e = birthInfo.value) == null ? void 0 : _e.hasSynastryCache) ? "badge-ready" : "badge-empty"),
        k: common_vendor.t(((_f = birthInfo.value) == null ? void 0 : _f.hasTransitCache) ? "已计算" : "未计算"),
        l: common_vendor.n(((_g = birthInfo.value) == null ? void 0 : _g.hasTransitCache) ? "badge-ready" : "badge-empty")
      } : {
        m: common_vendor.o(goSetBirthInfo, "45")
      }, {
        n: common_vendor.o(($event) => hasBirth.value ? null : goSetBirthInfo(), "ef"),
        o: common_vendor.o(goToNatal, "cf"),
        p: common_vendor.o(goToSynastry, "c2"),
        q: common_vendor.o(goToTransit, "e6"),
        r: common_vendor.o(goToInterpret, "38"),
        s: common_vendor.t(monthDay),
        t: common_vendor.t(todayFortune.value.icon),
        v: todayFortune.value.iconBg,
        w: common_vendor.t(todayFortune.value.planet),
        x: common_vendor.t(todayFortune.value.sign),
        y: common_vendor.t(todayFortune.value.summary),
        z: common_vendor.o(goToDetail, "12"),
        A: common_vendor.o(goToInterpret, "26")
      });
    };
  }
});
wx.createPage(_sfc_main);
//# sourceMappingURL=../../../.sourcemap/mp-weixin/pages/astrology/index.js.map
