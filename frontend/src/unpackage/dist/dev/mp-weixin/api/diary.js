"use strict";
const utils_request = require("../utils/request.js");
function saveDiary(data) {
  return utils_request.post("/diary/save", data);
}
function getDiaryList(page = 1, size = 20) {
  return utils_request.get(`/diary/list?page=${page}&size=${size}`);
}
function getAiSummary(id) {
  return utils_request.get(`/diary/${id}/ai-summary`);
}
exports.getAiSummary = getAiSummary;
exports.getDiaryList = getDiaryList;
exports.saveDiary = saveDiary;
//# sourceMappingURL=../../.sourcemap/mp-weixin/api/diary.js.map
