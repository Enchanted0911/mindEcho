"use strict";
const common_vendor = require("../../common/vendor.js");
const api_astrology = require("../../api/astrology.js");
const store_user = require("../../store/user.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const userStore = store_user.useUserStore();
    const hasNatal = common_vendor.ref(false);
    const isChecking = common_vendor.ref(true);
    const PLANETS = [
      {
        id: "sun",
        name: "太阳",
        symbol: "☉",
        color: "#FFD060",
        size: 26,
        orbitR: 0,
        speed: 0,
        angle: 0,
        desc: "生命力与自我核心"
      },
      {
        id: "mercury",
        name: "水星",
        symbol: "☿",
        color: "#B0C8E0",
        size: 9,
        orbitR: 80,
        speed: 1.8,
        angle: 30,
        desc: "思维、沟通与学习"
      },
      {
        id: "venus",
        name: "金星",
        symbol: "♀",
        color: "#FFB8A8",
        size: 13,
        orbitR: 120,
        speed: 1.2,
        angle: 80,
        desc: "爱、美与价值观"
      },
      {
        id: "earth",
        name: "地球",
        symbol: "🌍",
        color: "#60B8E0",
        size: 13,
        orbitR: 165,
        speed: 0.9,
        angle: 150,
        desc: "你所在的世界"
      },
      {
        id: "moon",
        name: "月亮",
        symbol: "☽",
        color: "#D8D0FF",
        size: 9,
        orbitR: 190,
        speed: 2.5,
        angle: 60,
        desc: "情感、直觉与潜意识"
      },
      {
        id: "mars",
        name: "火星",
        symbol: "♂",
        color: "#FF8070",
        size: 11,
        orbitR: 230,
        speed: 0.55,
        angle: 220,
        desc: "行动力、欲望与冲突"
      },
      {
        id: "jupiter",
        name: "木星",
        symbol: "♃",
        color: "#E0C090",
        size: 20,
        orbitR: 285,
        speed: 0.22,
        angle: 310,
        desc: "幸运、扩展与哲学"
      },
      {
        id: "saturn",
        name: "土星",
        symbol: "♄",
        color: "#C8B890",
        size: 17,
        orbitR: 335,
        speed: 0.12,
        angle: 180,
        desc: "规则、纪律与命运课题"
      }
    ];
    const planets = common_vendor.ref(PLANETS.map((p) => ({ ...p })));
    let animTimer = null;
    function startAnimation() {
      animTimer = setInterval(() => {
        planets.value.forEach((p) => {
          if (p.orbitR > 0) {
            p.angle = (p.angle + p.speed * 0.12) % 360;
          }
        });
      }, 50);
    }
    function stopAnimation() {
      if (animTimer)
        clearInterval(animTimer);
    }
    const selectedPlanet = common_vendor.ref(null);
    const showPlanetInfo = common_vendor.ref(false);
    function onPlanetTap(planet) {
      selectedPlanet.value = planet;
      showPlanetInfo.value = true;
    }
    function closePlanetInfo() {
      showPlanetInfo.value = false;
      selectedPlanet.value = null;
    }
    const showFeature = common_vendor.ref(null);
    function openFeature(key) {
      showFeature.value = key;
    }
    function closeFeature() {
      showFeature.value = null;
    }
    const FEATURE_INFO = {
      natal: {
        title: "本命盘",
        subtitle: "Natal Chart",
        icon: "☉",
        color: "#9b87d1",
        desc: "探索你的星盘结构、行星能量与人生底色，了解性格深层动力。",
        action: "开始分析",
        actionFn: () => {
          closeFeature();
          common_vendor.index.navigateTo({ url: "/pages/astrology/natal" });
        }
      },
      synastry: {
        title: "和盘",
        subtitle: "Synastry Chart",
        icon: "♀",
        color: "#60b8e0",
        desc: "与另一个人的星盘相遇，揭示关系动力、吸引力与挑战所在。",
        action: "开始分析",
        actionFn: () => {
          closeFeature();
          common_vendor.index.navigateTo({ url: "/pages/astrology/synastry" });
        }
      },
      transit: {
        title: "流运",
        subtitle: "Transit Reading",
        icon: "♄",
        color: "#ffb060",
        desc: "当前天空行星正与你的星盘共鸣，了解近期能量流动与变化机遇。",
        action: "开始分析",
        actionFn: () => {
          closeFeature();
          common_vendor.index.navigateTo({ url: "/pages/astrology/transit" });
        }
      },
      interpret: {
        title: "解读",
        subtitle: "AI Interpretation",
        icon: "✦",
        color: "#70c890",
        desc: "由 AI 深度解读你的星盘，涵盖性格、情感、天赋、成长课题。",
        action: "开始解读",
        actionFn: () => {
          closeFeature();
          common_vendor.index.navigateTo({ url: "/pages/astrology/natal" });
        }
      }
    };
    common_vendor.onMounted(async () => {
      const loggedIn = userStore.restoreFromStorage();
      if (!loggedIn) {
        common_vendor.index.reLaunch({ url: "/pages/login/index" });
        return;
      }
      try {
        hasNatal.value = await api_astrology.checkNatalChart();
      } catch (e) {
        hasNatal.value = false;
      } finally {
        isChecking.value = false;
      }
      startAnimation();
    });
    common_vendor.onUnmounted(() => {
      stopAnimation();
    });
    function getPlanetPos(p) {
      const cx = 375, cy = 375;
      if (p.orbitR === 0)
        return { x: cx, y: cy };
      const rad = (p.angle - 90) * Math.PI / 180;
      return {
        x: cx + p.orbitR * Math.cos(rad),
        y: cy + p.orbitR * Math.sin(rad)
      };
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: common_vendor.f(planets.value.filter((x) => x.orbitR > 0), (p, k0, i0) => {
          return {
            a: "orbit_" + p.id,
            b: p.orbitR * 2 + "rpx",
            c: p.orbitR * 2 + "rpx",
            d: -p.orbitR + "rpx",
            e: -p.orbitR + "rpx"
          };
        }),
        b: common_vendor.f(planets.value, (p, k0, i0) => {
          return common_vendor.e({
            a: p.id === "saturn"
          }, p.id === "saturn" ? {} : {}, {
            b: p.id !== "earth"
          }, p.id !== "earth" ? {
            c: common_vendor.t(p.symbol),
            d: Math.max(p.size * 0.9, 9) + "rpx",
            e: p.id === "sun" ? "#7a4a00" : "rgba(255,255,255,0.9)"
          } : {}, {
            f: "planet_" + p.id,
            g: p.size * 2 + "rpx",
            h: p.size * 2 + "rpx",
            i: getPlanetPos(p).x - p.size + "rpx",
            j: getPlanetPos(p).y - p.size + "rpx",
            k: p.id === "sun" ? "radial-gradient(circle at 38% 38%, #fff8d0, " + p.color + " 60%, #c07010)" : p.id === "earth" ? "radial-gradient(circle at 38% 38%, #a8dcf8, #3890c0 60%, #1a5070)" : "radial-gradient(circle at 38% 38%, " + p.color + "ee, " + p.color + "88)",
            l: p.id === "sun" ? "0 0 28rpx " + p.color + ", 0 0 60rpx " + p.color + "66, 0 0 100rpx " + p.color + "22" : "0 0 12rpx " + p.color + "88, 0 0 24rpx " + p.color + "33",
            m: common_vendor.o(($event) => onPlanetTap(p), "planet_" + p.id)
          });
        }),
        c: common_vendor.o(($event) => openFeature("natal"), "7b"),
        d: common_vendor.o(($event) => openFeature("synastry"), "08"),
        e: common_vendor.o(($event) => openFeature("transit"), "5a"),
        f: common_vendor.o(($event) => openFeature("interpret"), "68"),
        g: !isChecking.value && !hasNatal.value
      }, !isChecking.value && !hasNatal.value ? {
        h: common_vendor.o(($event) => openFeature("natal"), "5b")
      } : {}, {
        i: showPlanetInfo.value && selectedPlanet.value
      }, showPlanetInfo.value && selectedPlanet.value ? common_vendor.e({
        j: common_vendor.t(selectedPlanet.value.id === "earth" ? "🌍" : selectedPlanet.value.symbol),
        k: selectedPlanet.value.id === "earth" ? "#3890c0" : selectedPlanet.value.color,
        l: selectedPlanet.value.color + "15",
        m: selectedPlanet.value.color + "50",
        n: common_vendor.t(selectedPlanet.value.name),
        o: common_vendor.t(selectedPlanet.value.desc),
        p: common_vendor.o(closePlanetInfo, "ce"),
        q: selectedPlanet.value.sign
      }, selectedPlanet.value.sign ? common_vendor.e({
        r: common_vendor.t(selectedPlanet.value.sign),
        s: selectedPlanet.value.house
      }, selectedPlanet.value.house ? {
        t: common_vendor.t(selectedPlanet.value.house)
      } : {}, {
        v: selectedPlanet.value.degree
      }, selectedPlanet.value.degree ? {
        w: common_vendor.t(selectedPlanet.value.degree)
      } : {}) : {
        x: common_vendor.t(selectedPlanet.value.name),
        y: common_vendor.o(() => {
          closePlanetInfo();
          openFeature("natal");
        }, "72")
      }, {
        z: common_vendor.o(() => {
        }, "29"),
        A: common_vendor.o(closePlanetInfo, "89")
      }) : {}, {
        B: showFeature.value
      }, showFeature.value ? common_vendor.e({
        C: common_vendor.o(closeFeature, "f8"),
        D: showFeature.value && FEATURE_INFO[showFeature.value]
      }, showFeature.value && FEATURE_INFO[showFeature.value] ? {
        E: common_vendor.t(FEATURE_INFO[showFeature.value].icon),
        F: FEATURE_INFO[showFeature.value].color,
        G: FEATURE_INFO[showFeature.value].color + "15",
        H: FEATURE_INFO[showFeature.value].color + "40",
        I: common_vendor.t(FEATURE_INFO[showFeature.value].title),
        J: common_vendor.t(FEATURE_INFO[showFeature.value].subtitle),
        K: common_vendor.t(FEATURE_INFO[showFeature.value].desc),
        L: common_vendor.t(FEATURE_INFO[showFeature.value].action),
        M: "linear-gradient(135deg, " + FEATURE_INFO[showFeature.value].color + "bb, " + FEATURE_INFO[showFeature.value].color + ")",
        N: common_vendor.o(
          //@ts-ignore
          (...args) => FEATURE_INFO[showFeature.value].actionFn && FEATURE_INFO[showFeature.value].actionFn(...args),
          "ff"
        )
      } : {}, {
        O: common_vendor.o(() => {
        }, "63"),
        P: common_vendor.o(closeFeature, "9a")
      }) : {});
    };
  }
});
wx.createPage(_sfc_main);
//# sourceMappingURL=../../../.sourcemap/mp-weixin/pages/astrology/index.js.map
