"use strict";
const utils_request = require("../utils/request.js");
function wxLogin(code) {
  return utils_request.post("/auth/wx-login", { code });
}
function getProfile() {
  return utils_request.get("/auth/profile");
}
function updateBirthInfo(data) {
  return utils_request.put("/auth/profile/birth", data);
}
function updateSynastryPartner(data) {
  return utils_request.put("/auth/profile/synastry-partner", data);
}
exports.getProfile = getProfile;
exports.updateBirthInfo = updateBirthInfo;
exports.updateSynastryPartner = updateSynastryPartner;
exports.wxLogin = wxLogin;
//# sourceMappingURL=../../.sourcemap/mp-weixin/api/auth.js.map
