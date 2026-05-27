"use strict";
const utils_request = require("../utils/request.js");
function wxLogin(code) {
  return utils_request.post("/auth/wx-login", { code });
}
exports.wxLogin = wxLogin;
//# sourceMappingURL=../../.sourcemap/mp-weixin/api/auth.js.map
