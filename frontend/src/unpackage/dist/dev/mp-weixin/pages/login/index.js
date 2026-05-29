"use strict";
const common_vendor = require("../../common/vendor.js");
const api_auth = require("../../api/auth.js");
const store_user = require("../../store/user.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const userStore = store_user.useUserStore();
    const isLoading = common_vendor.ref(false);
    const STARS = Array.from({ length: 30 }, () => ({
      left: Math.random() * 100 + "%",
      top: Math.random() * 100 + "%",
      animationDelay: Math.random() * 3 + "s",
      width: Math.random() * 2 + 1 + "px",
      height: Math.random() * 2 + 1 + "px"
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
        common_vendor.index.__f__("error", "at pages/login/index.vue:45", "Login error:", error);
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
            b: common_vendor.s(star)
          };
        }),
        b: !isLoading.value
      }, !isLoading.value ? {} : {}, {
        c: isLoading.value,
        d: isLoading.value,
        e: common_vendor.o(handleLogin, "9a")
      });
    };
  }
});
wx.createPage(_sfc_main);
//# sourceMappingURL=../../../.sourcemap/mp-weixin/pages/login/index.js.map
