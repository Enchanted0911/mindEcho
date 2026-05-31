"use strict";
var __defProp = Object.defineProperty;
var __defNormalProp = (obj, key, value) => key in obj ? __defProp(obj, key, { enumerable: true, configurable: true, writable: true, value }) : obj[key] = value;
var __publicField = (obj, key, value) => {
  __defNormalProp(obj, typeof key !== "symbol" ? key + "" : key, value);
  return value;
};
const common_vendor = require("../common/vendor.js");
const store_user = require("../store/user.js");
const BASE_URL = "http://localhost:8080/api";
function getToken() {
  return common_vendor.index.getStorageSync("token") || "";
}
class ApiError extends Error {
  constructor(message, code) {
    super(message);
    __publicField(this, "code");
    this.code = code;
    this.name = "ApiError";
  }
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
          try {
            const userStore = store_user.useUserStore();
            userStore.logout();
          } catch (_) {
            common_vendor.index.removeStorageSync("token");
            common_vendor.index.removeStorageSync("userInfo");
            common_vendor.index.reLaunch({ url: "/pages/login/index" });
          }
          reject(new ApiError("未授权，请重新登录", 2001));
          return;
        }
        if (response.code === 0) {
          resolve(response.data);
        } else {
          const silentCodes = /* @__PURE__ */ new Set([6001, 7001, 7002, 7003, 7004, 7005]);
          if (!silentCodes.has(response.code)) {
            common_vendor.index.showToast({
              title: response.message || "请求失败",
              icon: "none",
              duration: 2e3
            });
          }
          reject(new ApiError(response.message || "请求失败", response.code));
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
function put(url, data) {
  return request({ url, method: "PUT", data });
}
function del(url) {
  return request({ url, method: "DELETE" });
}
exports.BASE_URL = BASE_URL;
exports.del = del;
exports.get = get;
exports.post = post;
exports.put = put;
//# sourceMappingURL=../../.sourcemap/mp-weixin/utils/request.js.map
