"use strict";
const common_vendor = require("../common/vendor.js");
const useChatStore = common_vendor.defineStore("chat", () => {
  const currentSessionId = common_vendor.ref(null);
  const messages = common_vendor.ref([]);
  const sessions = common_vendor.ref([]);
  const isLoading = common_vendor.ref(false);
  const isStreaming = common_vendor.ref(false);
  const streamingMessageId = common_vendor.ref(null);
  function setCurrentSession(sessionId) {
    currentSessionId.value = sessionId;
  }
  function addMessage(message) {
    messages.value.push(message);
  }
  function updateStreamingMessage(messageId, chunk) {
    const msg = messages.value.find((m) => m.id === messageId);
    if (msg) {
      msg.content += chunk;
    }
  }
  function finishStreaming(messageId) {
    const msg = messages.value.find((m) => m.id === messageId);
    if (msg) {
      msg.isStreaming = false;
    }
    isStreaming.value = false;
    streamingMessageId.value = null;
  }
  function clearMessages() {
    messages.value = [];
  }
  function setSessions(list) {
    sessions.value = list;
  }
  function startStreaming(messageId) {
    isStreaming.value = true;
    streamingMessageId.value = messageId;
  }
  return {
    currentSessionId,
    messages,
    sessions,
    isLoading,
    isStreaming,
    streamingMessageId,
    setCurrentSession,
    addMessage,
    updateStreamingMessage,
    finishStreaming,
    clearMessages,
    setSessions,
    startStreaming
  };
});
exports.useChatStore = useChatStore;
//# sourceMappingURL=../../.sourcemap/mp-weixin/store/chat.js.map
