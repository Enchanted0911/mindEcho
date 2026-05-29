"use strict";
const utils_request = require("../utils/request.js");
function getNatalChart(data) {
  return utils_request.post("/astrology/natal", data);
}
function interpretNatal(data) {
  return utils_request.post("/astrology/natal/interpret", data);
}
function checkNatalChart() {
  return utils_request.get("/astrology/natal/check");
}
function getSynastryChart(data) {
  return utils_request.post("/astrology/synastry", data);
}
function interpretSynastry(data) {
  return utils_request.post("/astrology/synastry/interpret", data);
}
function getTransitChart(data) {
  return utils_request.post("/astrology/transit", data);
}
function interpretTransit(data) {
  return utils_request.post("/astrology/transit/interpret", data);
}
exports.checkNatalChart = checkNatalChart;
exports.getNatalChart = getNatalChart;
exports.getSynastryChart = getSynastryChart;
exports.getTransitChart = getTransitChart;
exports.interpretNatal = interpretNatal;
exports.interpretSynastry = interpretSynastry;
exports.interpretTransit = interpretTransit;
//# sourceMappingURL=../../.sourcemap/mp-weixin/api/astrology.js.map
