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
    return ((_a = userInfo.value) == null ? void 0 : _a.aiPersonality) ?? "gentle_female";
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
  function updatePersonality(aiPersonality) {
    if (userInfo.value) {
      userInfo.value.aiPersonality = aiPersonality;
      common_vendor.index.setStorageSync("userInfo", JSON.stringify(userInfo.value));
    }
  }
  function updateBirthInfo(birthCity, birthLat, birthLng, birthTime) {
    if (userInfo.value) {
      userInfo.value.birthCity = birthCity;
      userInfo.value.birthLat = birthLat;
      userInfo.value.birthLng = birthLng;
      userInfo.value.birthTime = birthTime;
      common_vendor.index.setStorageSync("userInfo", JSON.stringify(userInfo.value));
    }
  }
  function updateSynastryPartner(partnerName, partnerCity, partnerLat, partnerLng, partnerTime) {
    if (userInfo.value) {
      userInfo.value.synastryPartnerName = partnerName;
      userInfo.value.synastryPartnerCity = partnerCity;
      userInfo.value.synastryPartnerLat = partnerLat;
      userInfo.value.synastryPartnerLng = partnerLng;
      userInfo.value.synastryPartnerTime = partnerTime;
      common_vendor.index.setStorageSync("userInfo", JSON.stringify(userInfo.value));
    }
  }
  function updateTransitDate(targetDate) {
    if (userInfo.value) {
      userInfo.value.transitTargetDate = targetDate;
      common_vendor.index.setStorageSync("userInfo", JSON.stringify(userInfo.value));
    }
  }
  function restoreFromStorage() {
    if (token.value)
      return true;
    try {
      const savedToken = common_vendor.index.getStorageSync("token");
      const savedUserInfoStr = common_vendor.index.getStorageSync("userInfo");
      if (savedToken) {
        token.value = savedToken;
        if (savedUserInfoStr) {
          userInfo.value = JSON.parse(savedUserInfoStr);
        }
        return true;
      }
    } catch (e) {
      common_vendor.index.__f__("warn", "at store/user.ts:132", "[userStore] restoreFromStorage failed:", e);
    }
    return false;
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
    updatePersonality,
    updateBirthInfo,
    updateSynastryPartner,
    updateTransitDate,
    restoreFromStorage
  };
});
exports.useUserStore = useUserStore;
//# sourceMappingURL=../../.sourcemap/mp-weixin/store/user.js.map
