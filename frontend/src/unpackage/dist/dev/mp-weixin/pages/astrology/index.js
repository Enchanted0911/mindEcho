"use strict";
const common_vendor = require("../../common/vendor.js");
const api_astrology = require("../../api/astrology.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const hasNatal = common_vendor.ref(false);
    const isChecking = common_vendor.ref(true);
    const STAR_QUOTES = [
      "星盘是灵魂的地图，而你才是旅者。",
      "行星不决定命运，只描绘倾向。",
      "了解自己，是所有关系的起点。",
      "流运是风，你决定如何扬帆。",
      "内心的宇宙，比星空更辽阔。"
    ];
    const quoteIndex = common_vendor.ref(Math.floor(Math.random() * STAR_QUOTES.length));
    common_vendor.onMounted(async () => {
      try {
        hasNatal.value = await api_astrology.checkNatalChart();
      } catch (e) {
        hasNatal.value = false;
      } finally {
        isChecking.value = false;
      }
    });
    function goNatal() {
      common_vendor.index.navigateTo({ url: "/pages/astrology/natal" });
    }
    function goSynastry() {
      common_vendor.index.navigateTo({ url: "/pages/astrology/synastry" });
    }
    function goTransit() {
      common_vendor.index.navigateTo({ url: "/pages/astrology/transit" });
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: common_vendor.f(30, (i, k0, i0) => {
          return {
            a: i,
            b: Math.sin(i * 137.5) * 50 + 50 + "%",
            c: Math.cos(i * 97.3) * 50 + 50 + "%",
            d: (i % 3 === 0 ? 3 : i % 3 === 1 ? 2 : 1.5) + "rpx",
            e: (i % 3 === 0 ? 3 : i % 3 === 1 ? 2 : 1.5) + "rpx",
            f: i * 0.3 + "s",
            g: 0.3 + i % 5 * 0.1
          };
        }),
        b: common_vendor.t(STAR_QUOTES[quoteIndex.value]),
        c: !isChecking.value && !hasNatal.value
      }, !isChecking.value && !hasNatal.value ? {
        d: common_vendor.o(goNatal, "a6")
      } : {}, {
        e: common_vendor.o(goNatal, "dc"),
        f: common_vendor.o(goSynastry, "73"),
        g: common_vendor.o(goTransit, "f6")
      });
    };
  }
});
wx.createPage(_sfc_main);
//# sourceMappingURL=../../../.sourcemap/mp-weixin/pages/astrology/index.js.map
