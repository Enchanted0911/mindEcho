"use strict";
const common_vendor = require("../../common/vendor.js");
const store_user = require("../../store/user.js");
const utils_emotion = require("../../utils/emotion.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const userStore = store_user.useUserStore();
    const personality = common_vendor.computed(() => utils_emotion.getPersonalityInfo(userStore.currentPersonality));
    function goToVip() {
      common_vendor.index.navigateTo({ url: "/pages/vip/index" });
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
        b: common_vendor.t(((_c = common_vendor.unref(userStore).userInfo) == null ? void 0 : _c.nickname) || "用户"),
        c: common_vendor.unref(userStore).isVip
      }, common_vendor.unref(userStore).isVip ? {} : {
        d: common_vendor.o(goToVip, "35")
      }, {
        e: common_vendor.t(personality.value.emoji),
        f: common_vendor.t(personality.value.label),
        g: common_vendor.t(personality.value.desc),
        h: common_vendor.o(($event) => common_vendor.index.switchTab({
          url: "/pages/chat/index"
        }), "e7"),
        i: common_vendor.o(goToVip, "55"),
        j: common_vendor.o(handleLogout, "e4")
      });
    };
  }
});
wx.createPage(_sfc_main);
//# sourceMappingURL=../../../.sourcemap/mp-weixin/pages/me/index.js.map
