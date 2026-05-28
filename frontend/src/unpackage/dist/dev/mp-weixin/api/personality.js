"use strict";
const utils_request = require("../utils/request.js");
function getPersonalityList() {
  return utils_request.get("/personality/list");
}
exports.getPersonalityList = getPersonalityList;
//# sourceMappingURL=../../.sourcemap/mp-weixin/api/personality.js.map
