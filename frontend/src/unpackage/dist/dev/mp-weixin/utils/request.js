"use strict";
const common_vendor = require("../common/vendor.js");
const BASE_URL = "http://localhost:8080/api";
function getToken() {
  return common_vendor.index.getStorageSync("token") || "";
}
function request(options) {
  return new Promise((resolve, reject) => {
    const token = getToken();
    const header = {
      "Content-Type": "application/json",
      ...options.header
    };
    if (token) {
      header["Authorization"] = `Bearer ${token}`;
    }
    common_vendor.index.request({
      url: BASE_URL + options.url,
      method: options.method || "GET",
      data: options.data,
      header,
      success: (res) => {
        const response = res.data;
        if (res.statusCode === 401) {
          common_vendor.index.removeStorageSync("token");
          common_vendor.index.reLaunch({ url: "/pages/login/index" });
          reject(new Error("未授权，请重新登录"));
          return;
        }
        if (response.code === 0) {
          resolve(response.data);
        } else {
          common_vendor.index.showToast({
            title: response.message || "请求失败",
            icon: "none",
            duration: 2e3
          });
          reject(new Error(response.message));
        }
      },
      fail: (err) => {
        common_vendor.index.showToast({
          title: "网络请求失败",
          icon: "none",
          duration: 2e3
        });
        reject(err);
      }
    });
  });
}
function get(url, data) {
  return request({ url, method: "GET", data });
}
function post(url, data) {
  return request({ url, method: "POST", data });
}
function del(url) {
  return request({ url, method: "DELETE" });
}
exports.del = del;
exports.get = get;
exports.post = post;
//# sourceMappingURL=../../.sourcemap/mp-weixin/utils/request.js.map
