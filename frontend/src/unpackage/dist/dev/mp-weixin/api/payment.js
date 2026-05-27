"use strict";
const common_vendor = require("../common/vendor.js");
const utils_request = require("../utils/request.js");
function createOrder(vipType) {
  return utils_request.post("/payment/order", { vipType });
}
function wxPay(params) {
  return new Promise((resolve, reject) => {
    if (!params) {
      reject(new Error("支付参数错误"));
      return;
    }
    common_vendor.index.requestPayment({
      provider: "wxpay",
      timeStamp: params.timeStamp,
      nonceStr: params.nonceStr,
      package: params.pkg,
      signType: params.signType,
      paySign: params.paySign,
      success: () => resolve(),
      fail: (err) => reject(err)
    });
  });
}
exports.createOrder = createOrder;
exports.wxPay = wxPay;
//# sourceMappingURL=../../.sourcemap/mp-weixin/api/payment.js.map
