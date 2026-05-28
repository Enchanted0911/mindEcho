"use strict";
const common_vendor = require("../common/vendor.js");
const utils_request = require("../utils/request.js");
function getSessionList(page = 1, size = 20) {
  return utils_request.get(`/chat/sessions?page=${page}&size=${size}`);
}
function getMessageList(sessionId, page = 1, size = 20) {
  return utils_request.get(`/chat/sessions/${sessionId}/messages?page=${page}&size=${size}`);
}
function deleteSession(sessionId) {
  return utils_request.del(`/chat/sessions/${sessionId}`);
}
function stopStreaming() {
  return utils_request.post("/chat/stop");
}
function createEditSseConnection(messageId, sessionId, message, onChunk, onDone, onError) {
  return createSseRequest("/chat/edit", { messageId, sessionId, message }, onChunk, onDone, onError);
}
function createSseConnection(sessionId, message, onChunk, onDone, onError) {
  return createSseRequest("/chat/send", { sessionId, message }, onChunk, onDone, onError);
}
function createSseRequest(path, data, onChunk, onDone, onError) {
  const token = common_vendor.index.getStorageSync("token");
  const baseUrl = "http://localhost:8080/api";
  const decoder = new TextDecoder("utf-8", { fatal: false });
  let sseBuffer = "";
  let doneTriggered = false;
  const triggerDone = () => {
    if (!doneTriggered) {
      doneTriggered = true;
      onDone();
    }
  };
  const flushBuffer = () => {
    const frames = sseBuffer.split("\n\n");
    sseBuffer = frames.pop() ?? "";
    for (const frame of frames) {
      const lines = frame.split("\n");
      for (const line of lines) {
        if (line.startsWith("data:")) {
          const data2 = line.slice(5).trim();
          if (data2 === "[DONE]") {
            triggerDone();
            return;
          }
          if (data2) {
            onChunk(data2);
          }
        } else if (line.startsWith("event:") && line.includes("done")) {
          triggerDone();
        }
      }
    }
  };
  const requestTask = common_vendor.index.request({
    url: `${baseUrl}${path}`,
    method: "POST",
    data,
    header: {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${token}`,
      "Accept": "text/event-stream"
    },
    enableChunked: true,
    responseType: "arraybuffer",
    success: () => {
      const remaining = decoder.decode(new ArrayBuffer(0));
      if (remaining) {
        sseBuffer += remaining;
        flushBuffer();
      }
      triggerDone();
    },
    fail: (err) => {
      onError(err);
    }
  });
  requestTask.onChunkReceived((response) => {
    try {
      const text = decoder.decode(response.data, { stream: true });
      sseBuffer += text;
      flushBuffer();
    } catch (e) {
      common_vendor.index.__f__("error", "at api/chat.ts:189", "Chunk parse error:", e);
    }
  });
  return requestTask;
}
exports.createEditSseConnection = createEditSseConnection;
exports.createSseConnection = createSseConnection;
exports.deleteSession = deleteSession;
exports.getMessageList = getMessageList;
exports.getSessionList = getSessionList;
exports.stopStreaming = stopStreaming;
//# sourceMappingURL=../../.sourcemap/mp-weixin/api/chat.js.map
