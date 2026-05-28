"use strict";
const common_vendor = require("../common/vendor.js");
require("./user.js");
require("./chat.js");
require("./diary.js");
require("./personality.js");
const pinia = common_vendor.createPinia();
function setupStore(app) {
  app.use(pinia);
}
exports.setupStore = setupStore;
//# sourceMappingURL=../../.sourcemap/mp-weixin/store/index.js.map
