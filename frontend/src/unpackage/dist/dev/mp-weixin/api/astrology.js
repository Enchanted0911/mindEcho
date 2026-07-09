"use strict";
const utils_request = require("../utils/request.js");
function getUserAstrologyInfo() {
  return utils_request.get("/astrology/info");
}
function getNatalChart() {
  return utils_request.post("/astrology/natal", {});
}
function interpretNatal(data) {
  return utils_request.post("/astrology/natal/interpret", data ?? {});
}
function getSynastryChart() {
  return utils_request.post("/astrology/synastry", {});
}
function interpretSynastry(data) {
  return utils_request.post("/astrology/synastry/interpret", data ?? {});
}
function getTransitChart(data) {
  return utils_request.post("/astrology/transit", data ?? {});
}
function interpretTransit(data) {
  return utils_request.post("/astrology/transit/interpret", data ?? {});
}
exports.getNatalChart = getNatalChart;
exports.getSynastryChart = getSynastryChart;
exports.getTransitChart = getTransitChart;
exports.getUserAstrologyInfo = getUserAstrologyInfo;
exports.interpretNatal = interpretNatal;
exports.interpretSynastry = interpretSynastry;
exports.interpretTransit = interpretTransit;
//# sourceMappingURL=../../.sourcemap/mp-weixin/api/astrology.js.map
