"use strict";
const common_vendor = require("../../common/vendor.js");
const store_user = require("../../store/user.js");
const store_personality = require("../../store/personality.js");
const utils_emotion = require("../../utils/emotion.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const userStore = store_user.useUserStore();
    const personalityStore = store_personality.usePersonalityStore();
    common_vendor.onMounted(async () => {
      await personalityStore.ensureLoaded();
    });
    const personality = common_vendor.computed(() => {
      const found = personalityStore.findByCode(userStore.currentPersonality);
      if (found) {
        return { label: found.name, desc: found.description, emoji: found.emoji };
      }
      return utils_emotion.getPersonalityInfo(userStore.currentPersonality);
    });
    function goToVip() {
      common_vendor.index.navigateTo({ url: "/pages/vip/index" });
    }
    function handleNotification() {
      common_vendor.index.showToast({ title: "通知设置即将上线", icon: "none" });
    }
    function handlePrivacy() {
      common_vendor.index.showToast({ title: "隐私设置即将上线", icon: "none" });
    }
    function handleHelp() {
      common_vendor.index.showToast({ title: "如有问题请联系客服", icon: "none" });
    }
    function handleAgreement() {
      common_vendor.index.showToast({ title: "正在加载用户协议...", icon: "none" });
    }
    function handleLogout() {
      common_vendor.index.showModal({
        title: "退出登录",
        content: "确认退出登录吗？",
        success: (res) => {
          if (res.confirm) {
            userStore.logout();
          }
        }
      });
    }
    return (_ctx, _cache) => {
      var _a, _b, _c;
      return common_vendor.e({
        a: common_vendor.t(((_b = (_a = common_vendor.unref(userStore).userInfo) == null ? void 0 : _a.nickname) == null ? void 0 : _b[0]) || "🌙"),
        b: common_vendor.unref(userStore).isVip
      }, common_vendor.unref(userStore).isVip ? {} : {}, {
        c: common_vendor.t(((_c = common_vendor.unref(userStore).userInfo) == null ? void 0 : _c.nickname) || "用户"),
        d: common_vendor.unref(userStore).isVip
      }, common_vendor.unref(userStore).isVip ? {} : {
        e: common_vendor.o(goToVip, "65")
      }, {
        f: common_vendor.o(goToVip, "bd"),
        g: common_vendor.t(personality.value.emoji),
        h: common_vendor.t(personality.value.label),
        i: common_vendor.t(personality.value.desc),
        j: common_vendor.o(($event) => common_vendor.index.switchTab({
          url: "/pages/chat/index"
        }), "8d"),
        k: common_vendor.o(($event) => common_vendor.index.switchTab({
          url: "/pages/astrology/index"
        }), "f8"),
        l: common_vendor.o(($event) => common_vendor.index.navigateTo({
          url: "/pages/astrology/natal"
        }), "77"),
        m: common_vendor.o(($event) => common_vendor.index.navigateTo({
          url: "/pages/astrology/synastry"
        }), "1e"),
        n: common_vendor.o(($event) => common_vendor.index.navigateTo({
          url: "/pages/astrology/transit"
        }), "c1"),
        o: common_vendor.o(goToVip, "c4"),
        p: common_vendor.o(handleNotification, "3f"),
        q: common_vendor.o(handlePrivacy, "4d"),
        r: common_vendor.o(handleHelp, "24"),
        s: common_vendor.o(handleAgreement, "cc"),
        t: common_vendor.o(handleLogout, "d0")
      });
    };
  }
});
wx.createPage(_sfc_main);
//# sourceMappingURL=../../../.sourcemap/mp-weixin/pages/me/index.js.map
