"use strict";
const common_vendor = require("../common/vendor.js");
const utils_request = require("../utils/request.js");
function getMessageList(sessionId) {
  return utils_request.get(`/chat/sessions/${sessionId}/messages`);
}
function createSseConnection(sessionId, message, onChunk, onDone, onError) {
  const token = common_vendor.index.getStorageSync("token");
  const baseUrl = "http://localhost:8080/api";
  common_vendor.index.request({
    url: `${baseUrl}/chat/send`,
    method: "POST",
    data: { sessionId, message },
    header: {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${token}`,
      "Accept": "text/event-stream"
    },
    enableChunked: true,
    // 开启流式接收
    success: () => {
      onDone();
    },
    fail: (err) => {
      onError(err);
    }
  });
  common_vendor.index.onChunkReceived((response) => {
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
      } else if (line.startsWith("event:done")) {
        onDone();
      }
    }
  });
}
exports.createSseConnection = createSseConnection;
exports.getMessageList = getMessageList;
//# sourceMappingURL=../../.sourcemap/mp-weixin/api/chat.js.map
