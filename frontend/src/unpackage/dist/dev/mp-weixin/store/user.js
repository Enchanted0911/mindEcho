"use strict";
const common_vendor = require("../common/vendor.js");
const useUserStore = common_vendor.defineStore("user", () => {
  const token = common_vendor.ref("");
  const userInfo = common_vendor.ref(null);
  const astrologyInfo = common_vendor.ref(null);
  const isLoggedIn = common_vendor.computed(() => !!token.value);
  const isVip = common_vendor.computed(() => {
    var _a;
    return ((_a = userInfo.value) == null ? void 0 : _a.isVip) ?? false;
  });
  const currentPersonality = common_vendor.computed(() => {
    var _a;
    return ((_a = userInfo.value) == null ? void 0 : _a.aiPersonality) ?? "gentle_female";
  });
  const hasBirthInfo = common_vendor.computed(
    () => {
      var _a, _b;
      return !!(((_a = astrologyInfo.value) == null ? void 0 : _a.birthCity) && ((_b = astrologyInfo.value) == null ? void 0 : _b.birthTime));
    }
  );
  const hasSynastryPartner = common_vendor.computed(
    () => {
      var _a, _b;
      return !!(((_a = astrologyInfo.value) == null ? void 0 : _a.synastryPartnerCity) && ((_b = astrologyInfo.value) == null ? void 0 : _b.synastryPartnerTime));
    }
  );
  function setToken(newToken) {
    token.value = newToken;
    common_vendor.index.setStorageSync("token", newToken);
  }
  function setUserInfo(info) {
    userInfo.value = info;
    common_vendor.index.setStorageSync("userInfo", JSON.stringify(info));
  }
  function setAstrologyInfo(info) {
    astrologyInfo.value = info;
    common_vendor.index.setStorageSync("astrologyInfo", JSON.stringify(info));
    if (userInfo.value) {
      userInfo.value.birthCity = info.birthCity ?? null;
      userInfo.value.birthLat = info.birthLat ?? null;
      userInfo.value.birthLng = info.birthLng ?? null;
      userInfo.value.birthTime = info.birthTime ?? null;
      userInfo.value.synastryPartnerName = info.synastryPartnerName ?? null;
      userInfo.value.synastryPartnerCity = info.synastryPartnerCity ?? null;
      userInfo.value.synastryPartnerLat = info.synastryPartnerLat ?? null;
      userInfo.value.synastryPartnerLng = info.synastryPartnerLng ?? null;
      userInfo.value.synastryPartnerTime = info.synastryPartnerTime ?? null;
      userInfo.value.transitTargetDate = info.transitTargetDate ?? null;
      common_vendor.index.setStorageSync("userInfo", JSON.stringify(userInfo.value));
    }
  }
  function updateAstrologyCache(patch) {
    if (astrologyInfo.value) {
      astrologyInfo.value = { ...astrologyInfo.value, ...patch };
      common_vendor.index.setStorageSync("astrologyInfo", JSON.stringify(astrologyInfo.value));
    }
  }
  function logout() {
    token.value = "";
    userInfo.value = null;
    astrologyInfo.value = null;
    common_vendor.index.removeStorageSync("token");
    common_vendor.index.removeStorageSync("userInfo");
    common_vendor.index.removeStorageSync("astrologyInfo");
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
      const savedAstrologyInfoStr = common_vendor.index.getStorageSync("astrologyInfo");
      if (savedToken) {
        token.value = savedToken;
        if (savedUserInfoStr) {
          userInfo.value = JSON.parse(savedUserInfoStr);
        }
        if (savedAstrologyInfoStr) {
          astrologyInfo.value = JSON.parse(savedAstrologyInfoStr);
        }
        return true;
      }
    } catch (e) {
      common_vendor.index.__f__("warn", "at store/user.ts:181", "[userStore] restoreFromStorage failed:", e);
    }
    return false;
  }
  return {
    token,
    userInfo,
    astrologyInfo,
    isLoggedIn,
    isVip,
    currentPersonality,
    hasBirthInfo,
    hasSynastryPartner,
    setToken,
    setUserInfo,
    setAstrologyInfo,
    updateAstrologyCache,
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
