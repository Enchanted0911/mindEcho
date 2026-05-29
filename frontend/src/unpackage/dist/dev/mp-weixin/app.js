"use strict";
Object.defineProperty(exports, Symbol.toStringTag, { value: "Module" });
const common_vendor = require("./common/vendor.js");
const store_user = require("./store/user.js");
const store_index = require("./store/index.js");
if (!Math) {
  "./pages/login/index.js";
  "./pages/chat/index.js";
  "./pages/diary/index.js";
  "./pages/astrology/index.js";
  "./pages/astrology/natal.js";
  "./pages/astrology/synastry.js";
  "./pages/astrology/transit.js";
  "./pages/me/index.js";
  "./pages/vip/index.js";
}
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "App",
  setup(__props) {
    common_vendor.onLaunch(() => {
      const userStore = store_user.useUserStore();
      const token = common_vendor.index.getStorageSync("token");
      if (token) {
        userStore.setToken(token);
        const userInfo = common_vendor.index.getStorageSync("userInfo");
        if (userInfo) {
          userStore.setUserInfo(JSON.parse(userInfo));
        }
      } else {
        common_vendor.index.reLaunch({ url: "/pages/login/index" });
      }
    });
    common_vendor.onShow(() => {
    });
    return (_ctx, _cache) => {
      return {};
    };
  }
});
function createApp() {
  const app = common_vendor.createSSRApp(_sfc_main);
  store_index.setupStore(app);
  return { app };
}
createApp().app.mount("#app");
exports.createApp = createApp;
//# sourceMappingURL=../.sourcemap/mp-weixin/app.js.map
