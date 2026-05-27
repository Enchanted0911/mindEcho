"use strict";
const common_vendor = require("../common/vendor.js");
const useUserStore = common_vendor.defineStore("user", () => {
  const token = common_vendor.ref("");
  const userInfo = common_vendor.ref(null);
  const isLoggedIn = common_vendor.computed(() => !!token.value);
  const isVip = common_vendor.computed(() => {
    var _a;
    return ((_a = userInfo.value) == null ? void 0 : _a.isVip) ?? false;
  });
  const currentPersonality = common_vendor.computed(() => {
    var _a;
    return ((_a = userInfo.value) == null ? void 0 : _a.personality) ?? "gentle_sister";
  });
  function setToken(newToken) {
    token.value = newToken;
    common_vendor.index.setStorageSync("token", newToken);
  }
  function setUserInfo(info) {
    userInfo.value = info;
    common_vendor.index.setStorageSync("userInfo", JSON.stringify(info));
  }
  function logout() {
    token.value = "";
    userInfo.value = null;
    common_vendor.index.removeStorageSync("token");
    common_vendor.index.removeStorageSync("userInfo");
    common_vendor.index.reLaunch({ url: "/pages/login/index" });
  }
  function updatePersonality(personality) {
    if (userInfo.value) {
      userInfo.value.personality = personality;
      common_vendor.index.setStorageSync("userInfo", JSON.stringify(userInfo.value));
    }
  }
  return {
    token,
    userInfo,
    isLoggedIn,
    isVip,
    currentPersonality,
    setToken,
    setUserInfo,
    logout,
    updatePersonality
  };
});
exports.useUserStore = useUserStore;
//# sourceMappingURL=../../.sourcemap/mp-weixin/store/user.js.map
