"use strict";
const common_vendor = require("../common/vendor.js");
const utils_request = require("../utils/request.js");
function getSessionList(page = 1, size = 20) {
  return utils_request.get(`/chat/sessions?page=${page}&size=${size}`);
}
function getMessageList(sessionId) {
  return utils_request.get(`/chat/sessions/${sessionId}/messages`);
}
function deleteSession(sessionId) {
  return utils_request.del(`/chat/sessions/${sessionId}`);
}
function createSseConnection(sessionId, message, onChunk, onDone, onError) {
  const token = common_vendor.index.getStorageSync("token");
  const baseUrl = "http://localhost:8080/api";
  const requestTask = common_vendor.index.request({
    url: `${baseUrl}/chat/send`,
    method: "POST",
    data: { sessionId, message },
    header: {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${token}`,
      "Accept": "text/event-stream"
    },
    enableChunked: true,
    responseType: "arraybuffer",
    success: () => {
      onDone();
    },
    fail: (err) => {
      onError(err);
    }
  });
  requestTask.onChunkReceived((response) => {
    try {
      const decoder = new TextDecoder("utf-8");
      const text = decoder.decode(response.data);
      const lines = text.split("\n");
      for (const line of lines) {
        if (line.startsWith("data:")) {
          const data = line.slice(5).trim();
          if (data === "[DONE]") {
            onDone();
            return;
          }
          if (data) {
            onChunk(data);
          }
        } else if (line.startsWith("event:done") || line.includes("[DONE]")) {
          onDone();
        }
      }
    } catch (e) {
      common_vendor.index.__f__("error", "at api/chat.ts:98", "Chunk parse error:", e);
    }
  });
}
exports.createSseConnection = createSseConnection;
exports.deleteSession = deleteSession;
exports.getMessageList = getMessageList;
exports.getSessionList = getSessionList;
//# sourceMappingURL=../../.sourcemap/mp-weixin/api/chat.js.map
