"use strict";
const common_vendor = require("../common/vendor.js");
const useChatStore = common_vendor.defineStore("chat", () => {
  const currentSessionId = common_vendor.ref(null);
  const messages = common_vendor.ref([]);
  const isLoading = common_vendor.ref(false);
  const isStreaming = common_vendor.ref(false);
  const streamingMessageId = common_vendor.ref(null);
  const activeRequestTask = common_vendor.ref(null);
  const editingMessageId = common_vendor.ref(null);
  const msgPage = common_vendor.ref(1);
  const msgTotalPages = common_vendor.ref(-1);
  const isLoadingMoreMsg = common_vendor.ref(false);
  const sessions = common_vendor.ref([]);
  const sessionPage = common_vendor.ref(1);
  const sessionTotalPages = common_vendor.ref(-1);
  const isLoadingMoreSession = common_vendor.ref(false);
  function setCurrentSession(sessionId) {
    currentSessionId.value = sessionId;
  }
  function addMessage(message) {
    messages.value.push(message);
  }
  function prependMessages(older) {
    const existingIds = new Set(messages.value.map((m) => m.id));
    const deduped = older.filter((m) => !existingIds.has(m.id));
    messages.value = [...deduped, ...messages.value];
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
    msgPage.value = 1;
    msgTotalPages.value = -1;
    isLoadingMoreMsg.value = false;
  }
  function startStreaming(messageId, task) {
    isStreaming.value = true;
    streamingMessageId.value = messageId;
    if (task) {
      activeRequestTask.value = task;
    }
  }
  function setActiveRequestTask(task) {
    activeRequestTask.value = task;
  }
  function setEditingMessageId(id) {
    editingMessageId.value = id;
  }
  function updateMessageContent(messageId, newContent) {
    const msg = messages.value.find((m) => m.id === messageId);
    if (msg) {
      msg.content = newContent;
    }
  }
  function removeMessagesAfter(messageId) {
    const idx = messages.value.findIndex((m) => m.id === messageId);
    if (idx !== -1) {
      messages.value = messages.value.slice(0, idx + 1);
    }
  }
  function hasMoreMessages() {
    if (msgTotalPages.value === -1)
      return true;
    return msgPage.value < msgTotalPages.value;
  }
  function setSessions(list, totalPages = 1) {
    sessions.value = list;
    sessionPage.value = 1;
    sessionTotalPages.value = totalPages;
    isLoadingMoreSession.value = false;
  }
  function appendSessions(more, newPage, totalPages) {
    const existingIds = new Set(sessions.value.map((s) => s.id));
    const deduped = more.filter((s) => !existingIds.has(s.id));
    sessions.value = [...sessions.value, ...deduped];
    sessionPage.value = newPage;
    sessionTotalPages.value = totalPages;
    isLoadingMoreSession.value = false;
  }
  function hasMoreSessions() {
    if (sessionTotalPages.value === -1)
      return true;
    return sessionPage.value < sessionTotalPages.value;
  }
  return {
    currentSessionId,
    messages,
    isLoading,
    isStreaming,
    streamingMessageId,
    activeRequestTask,
    editingMessageId,
    msgPage,
    msgTotalPages,
    isLoadingMoreMsg,
    sessions,
    sessionPage,
    sessionTotalPages,
    isLoadingMoreSession,
    setCurrentSession,
    addMessage,
    prependMessages,
    updateStreamingMessage,
    finishStreaming,
    clearMessages,
    startStreaming,
    setActiveRequestTask,
    setEditingMessageId,
    updateMessageContent,
    removeMessagesAfter,
    hasMoreMessages,
    setSessions,
    appendSessions,
    hasMoreSessions
  };
});
exports.useChatStore = useChatStore;
//# sourceMappingURL=../../.sourcemap/mp-weixin/store/chat.js.map
