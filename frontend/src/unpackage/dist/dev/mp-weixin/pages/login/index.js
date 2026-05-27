"use strict";
const common_vendor = require("../../common/vendor.js");
const api_auth = require("../../api/auth.js");
const store_user = require("../../store/user.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const userStore = store_user.useUserStore();
    const isLoading = common_vendor.ref(false);
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
        common_vendor.index.showToast({
          title: "登录失败，请重试",
          icon: "none",
          duration: 2e3
        });
      } finally {
        isLoading.value = false;
      }
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: common_vendor.f(30, (i, k0, i0) => {
          return {
            a: i
          };
        }),
        b: Math.random() * 100 + "%",
        c: Math.random() * 100 + "%",
        d: Math.random() * 3 + "s",
        e: Math.random() * 2 + 1 + "px",
        f: Math.random() * 2 + 1 + "px",
        g: !isLoading.value
      }, !isLoading.value ? {} : {}, {
        h: isLoading.value,
        i: isLoading.value,
        j: common_vendor.o(handleLogin, "a1")
      });
    };
  }
});
wx.createPage(_sfc_main);
//# sourceMappingURL=../../../.sourcemap/mp-weixin/pages/login/index.js.map
