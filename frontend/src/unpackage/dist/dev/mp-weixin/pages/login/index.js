"use strict";
const common_vendor = require("../../common/vendor.js");
const api_auth = require("../../api/auth.js");
const store_user = require("../../store/user.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const userStore = store_user.useUserStore();
    const isLoading = common_vendor.ref(false);
    const STARS = Array.from({ length: 60 }, () => ({
      left: Math.random() * 100 + "%",
      top: Math.random() * 100 + "%",
      animationDelay: Math.random() * 5 + "s",
      animationDuration: Math.random() * 4 + 3 + "s",
      size: Math.random() * 2 + 0.5 + "px",
      opacity: Math.random() * 0.6 + 0.2
    }));
    async function handleLogin() {
      if (isLoading.value)
        return;
      isLoading.value = true;
      try {
        const loginResult = await new Promise((resolve, reject) => {
          common_vendor.index.login({
            provider: "weixin",
            success: (res) => resolve({ code: res.code }),
            fail: (err) => reject(err)
          });
        });
        const response = await api_auth.wxLogin(loginResult.code);
        userStore.setToken(response.token);
        userStore.setUserInfo(response.userInfo);
        common_vendor.index.switchTab({ url: "/pages/chat/index" });
      } catch (error) {
        common_vendor.index.__f__("error", "at pages/login/index.vue:36", "Login error:", error);
        const msg = (error == null ? void 0 : error.errMsg) || "";
        if (msg.includes("cancel") || msg.includes("deny")) {
          isLoading.value = false;
          return;
        }
        common_vendor.index.showToast({
          title: (error == null ? void 0 : error.message) || "登录失败，请重试",
          icon: "none",
          duration: 2e3
        });
      } finally {
        isLoading.value = false;
      }
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: common_vendor.f(common_vendor.unref(STARS), (star, i, i0) => {
          return {
            a: i,
            b: star.left,
            c: star.top,
            d: star.size,
            e: star.size,
            f: star.animationDelay,
            g: star.animationDuration,
            h: star.opacity
          };
        }),
        b: common_vendor.f([{
          icon: "💬",
          text: "倾听你的心声"
        }, {
          icon: "🧠",
          text: "记住你的故事"
        }, {
          icon: "✨",
          text: "陪你走过每一夜"
        }], (f, i, i0) => {
          return {
            a: common_vendor.t(f.icon),
            b: common_vendor.t(f.text),
            c: i
          };
        }),
        c: !isLoading.value
      }, !isLoading.value ? {} : {}, {
        d: common_vendor.t(isLoading.value ? "登录中..." : "微信一键登录"),
        e: isLoading.value ? 1 : "",
        f: common_vendor.o(handleLogin, "f4")
      });
    };
  }
});
wx.createPage(_sfc_main);
//# sourceMappingURL=../../../.sourcemap/mp-weixin/pages/login/index.js.map
