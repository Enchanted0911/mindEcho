"use strict";
const common_vendor = require("../../common/vendor.js");
const store_chat = require("../../store/chat.js");
const store_user = require("../../store/user.js");
const store_personality = require("../../store/personality.js");
const api_chat = require("../../api/chat.js");
const utils_emotion = require("../../utils/emotion.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const chatStore = store_chat.useChatStore();
    const userStore = store_user.useUserStore();
    const personalityStore = store_personality.usePersonalityStore();
    const inputText = common_vendor.ref("");
    const scrollToBottom = common_vendor.ref("msg-bottom");
    let scrollToggle = false;
    const scrollToAnchor = common_vendor.ref("");
    const showSessionPanel = common_vendor.ref(false);
    const showPersonalityPicker = common_vendor.ref(false);
    common_vendor.ref(false);
    const isLoadingSessions = common_vendor.ref(false);
    const showEditPopup = common_vendor.ref(false);
    const editTargetMsgId = common_vendor.ref("");
    const editText = common_vendor.ref("");
    common_vendor.computed(() => personalityStore.list);
    const femalePersonalities = common_vendor.computed(() => personalityStore.femaleList);
    const malePersonalities = common_vendor.computed(() => personalityStore.maleList);
    const personality = common_vendor.computed(() => {
      const found = personalityStore.findByCode(userStore.currentPersonality);
      return found ?? { code: userStore.currentPersonality, name: "心屿", emoji: "🌸", description: "", gender: "female", style: "gentle" };
    });
    const messages = common_vendor.computed(() => chatStore.messages);
    const isStreaming = common_vendor.computed(() => chatStore.isStreaming);
    const sessions = common_vendor.computed(() => chatStore.sessions);
    const isLoadingMoreMsg = common_vendor.computed(() => chatStore.isLoadingMoreMsg);
    const isLoadingMoreSession = common_vendor.computed(() => chatStore.isLoadingMoreSession);
    common_vendor.onMounted(async () => {
      await personalityStore.ensureLoaded();
      await loadSessions(true);
      if (chatStore.currentSessionId) {
        await loadHistory(chatStore.currentSessionId);
      }
    });
    async function loadSessions(reset = false) {
      if (!reset && (!chatStore.hasMoreSessions() || chatStore.isLoadingMoreSession))
        return;
      const nextPage = reset ? 1 : chatStore.sessionPage + 1;
      if (!reset)
        chatStore.isLoadingMoreSession = true;
      else
        isLoadingSessions.value = true;
      try {
        const result = await api_chat.getSessionList(nextPage, 20);
        if (reset) {
          chatStore.setSessions(result.records || [], result.pages ?? 1);
        } else {
          chatStore.appendSessions(result.records || [], nextPage, result.pages ?? nextPage);
        }
      } catch (e) {
        common_vendor.index.__f__("error", "at pages/chat/index.vue:83", "Load sessions failed:", e);
      } finally {
        isLoadingSessions.value = false;
        chatStore.isLoadingMoreSession = false;
      }
    }
    function onSessionScrollToLower() {
      loadSessions(false);
    }
    function applyMessagePage(records, page, totalPages, prepend) {
      const sorted = [...records].reverse().map((msg) => ({
        id: String(msg.id),
        role: msg.role,
        content: msg.content,
        emotion: msg.emotion,
        riskLevel: msg.riskLevel,
        createdAt: utils_emotion.parseDate(msg.createdTime).getTime()
      }));
      chatStore.msgTotalPages = totalPages;
      chatStore.msgPage = page;
      if (prepend) {
        chatStore.prependMessages(sorted);
      } else {
        chatStore.clearMessages();
        sorted.forEach((m) => chatStore.addMessage(m));
      }
    }
    async function loadHistory(sessionId) {
      chatStore.clearMessages();
      try {
        const result = await api_chat.getMessageList(sessionId, 1, 20);
        applyMessagePage(result.records, 1, result.pages, false);
        scrollToMsg();
      } catch (e) {
        common_vendor.index.__f__("error", "at pages/chat/index.vue:140", "Load history failed:", e);
      }
    }
    async function onMsgScrollToUpper() {
      var _a;
      if (!chatStore.currentSessionId)
        return;
      if (!chatStore.hasMoreMessages() || isLoadingMoreMsg.value)
        return;
      chatStore.isLoadingMoreMsg = true;
      const nextPage = chatStore.msgPage + 1;
      const anchorId = ((_a = messages.value[0]) == null ? void 0 : _a.id) ?? "";
      try {
        const result = await api_chat.getMessageList(chatStore.currentSessionId, nextPage, 20);
        if (result.records.length > 0) {
          applyMessagePage(result.records, nextPage, result.pages, true);
          if (anchorId) {
            await common_vendor.nextTick$1();
            scrollToAnchor.value = `msg-${anchorId}`;
            setTimeout(() => {
              scrollToAnchor.value = "";
            }, 300);
          }
        } else {
          chatStore.msgTotalPages = chatStore.msgPage;
        }
      } catch (e) {
        common_vendor.index.__f__("error", "at pages/chat/index.vue:170", "Load more messages failed:", e);
      } finally {
        chatStore.isLoadingMoreMsg = false;
      }
    }
    async function switchSession(sessionId) {
      chatStore.setCurrentSession(sessionId);
      showSessionPanel.value = false;
      await loadHistory(sessionId);
    }
    async function removeSession(sessionId) {
      common_vendor.index.showModal({
        title: "删除会话",
        content: "确认删除这条对话记录？",
        success: async (res) => {
          if (res.confirm) {
            try {
              await api_chat.deleteSession(sessionId);
              chatStore.setSessions(
                sessions.value.filter((s) => s.id !== sessionId),
                chatStore.sessionTotalPages
              );
              if (chatStore.currentSessionId === sessionId) {
                chatStore.setCurrentSession(null);
                chatStore.clearMessages();
              }
            } catch (e) {
              common_vendor.index.showToast({ title: "删除失败", icon: "none" });
            }
          }
        }
      });
    }
    async function sendMessage() {
      const text = inputText.value.trim();
      if (!text || isStreaming.value)
        return;
      inputText.value = "";
      const userMsgId = `user_${Date.now()}`;
      chatStore.addMessage({
        id: userMsgId,
        role: "user",
        content: text,
        createdAt: Date.now()
      });
      scrollToMsg();
      const aiMsgId = `ai_${Date.now()}`;
      chatStore.addMessage({
        id: aiMsgId,
        role: "assistant",
        content: "",
        createdAt: Date.now(),
        isStreaming: true
      });
      const task = api_chat.createSseConnection(
        chatStore.currentSessionId,
        text,
        (chunk) => {
          chatStore.updateStreamingMessage(aiMsgId, chunk);
          scrollToMsg();
        },
        () => {
          chatStore.finishStreaming(aiMsgId);
          chatStore.setActiveRequestTask(null);
          loadSessions(true);
          scrollToMsg();
        },
        (err) => {
          common_vendor.index.__f__("error", "at pages/chat/index.vue:249", "SSE error:", err);
          chatStore.finishStreaming(aiMsgId);
          chatStore.setActiveRequestTask(null);
          common_vendor.index.showToast({ title: "AI 回复失败，请重试", icon: "none" });
        }
      );
      chatStore.startStreaming(aiMsgId, task);
    }
    async function handleStopStreaming() {
      try {
        await api_chat.stopStreaming();
      } catch (e) {
        common_vendor.index.__f__("error", "at pages/chat/index.vue:265", "Stop streaming failed:", e);
      }
      if (chatStore.activeRequestTask) {
        chatStore.activeRequestTask.abort();
        chatStore.setActiveRequestTask(null);
      }
      if (chatStore.streamingMessageId) {
        chatStore.finishStreaming(chatStore.streamingMessageId);
      }
    }
    function onLongPressUserMsg(msgId, content) {
      if (isStreaming.value)
        return;
      editTargetMsgId.value = msgId;
      editText.value = content;
      showEditPopup.value = true;
    }
    function onTapUserMsg(msgId, content) {
      if (isStreaming.value)
        return;
      common_vendor.index.showActionSheet({
        itemList: ["编辑并重新发送"],
        success: (res) => {
          if (res.tapIndex === 0) {
            editTargetMsgId.value = msgId;
            editText.value = content;
            showEditPopup.value = true;
          }
        }
      });
    }
    async function confirmEdit() {
      const newText = editText.value.trim();
      if (!newText || !editTargetMsgId.value || !chatStore.currentSessionId)
        return;
      showEditPopup.value = false;
      chatStore.updateMessageContent(editTargetMsgId.value, newText);
      chatStore.removeMessagesAfter(editTargetMsgId.value);
      scrollToMsg();
      const aiMsgId = `ai_edit_${Date.now()}`;
      chatStore.addMessage({
        id: aiMsgId,
        role: "assistant",
        content: "",
        createdAt: Date.now(),
        isStreaming: true
      });
      scrollToMsg();
      const task = api_chat.createEditSseConnection(
        editTargetMsgId.value,
        chatStore.currentSessionId,
        newText,
        (chunk) => {
          chatStore.updateStreamingMessage(aiMsgId, chunk);
          scrollToMsg();
        },
        () => {
          chatStore.finishStreaming(aiMsgId);
          chatStore.setActiveRequestTask(null);
          loadSessions(true);
          scrollToMsg();
        },
        (err) => {
          common_vendor.index.__f__("error", "at pages/chat/index.vue:342", "Edit SSE error:", err);
          chatStore.finishStreaming(aiMsgId);
          chatStore.setActiveRequestTask(null);
          common_vendor.index.showToast({ title: "重新发送失败，请重试", icon: "none" });
        }
      );
      chatStore.startStreaming(aiMsgId, task);
    }
    function scrollToMsg() {
      common_vendor.nextTick$1(() => {
        scrollToggle = !scrollToggle;
        scrollToBottom.value = scrollToggle ? "msg-bottom" : "msg-bottom-alt";
      });
    }
    function startNewChat() {
      chatStore.setCurrentSession(null);
      chatStore.clearMessages();
      showSessionPanel.value = false;
      loadSessions(true);
    }
    async function selectPersonality(code) {
      userStore.updatePersonality(code);
      showPersonalityPicker.value = false;
      startNewChat();
      const found = personalityStore.findByCode(code);
      common_vendor.index.showToast({
        title: `已切换到 ${(found == null ? void 0 : found.name) ?? "心屿"}`,
        icon: "none"
      });
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: common_vendor.o(($event) => showSessionPanel.value = !showSessionPanel.value, "c8"),
        b: common_vendor.t(personality.value.emoji),
        c: common_vendor.t(personality.value.name),
        d: common_vendor.o(($event) => showPersonalityPicker.value = true, "59"),
        e: common_vendor.o(startNewChat, "dc"),
        f: isLoadingMoreMsg.value
      }, isLoadingMoreMsg.value ? {} : common_vendor.unref(chatStore).msgTotalPages !== -1 && !common_vendor.unref(chatStore).hasMoreMessages() ? {} : {}, {
        g: common_vendor.unref(chatStore).msgTotalPages !== -1 && !common_vendor.unref(chatStore).hasMoreMessages(),
        h: messages.value.length === 0
      }, messages.value.length === 0 ? {
        i: common_vendor.t(personality.value.emoji),
        j: common_vendor.t(personality.value.name),
        k: common_vendor.f(["我今天心情不好", "我有点焦虑", "能陪我聊聊吗？"], (reply, k0, i0) => {
          return {
            a: common_vendor.t(reply),
            b: reply,
            c: common_vendor.o(($event) => inputText.value = reply, reply)
          };
        })
      } : {}, {
        l: common_vendor.f(messages.value, (msg, k0, i0) => {
          return common_vendor.e({
            a: msg.role === "assistant"
          }, msg.role === "assistant" ? {
            b: common_vendor.t(personality.value.emoji)
          } : {}, {
            c: common_vendor.t(msg.content),
            d: msg.isStreaming
          }, msg.isStreaming ? {} : {}, {
            e: msg.role === "user" && !isStreaming.value
          }, msg.role === "user" && !isStreaming.value ? {} : {}, {
            f: msg.role === "user" ? 1 : "",
            g: msg.role === "assistant" ? 1 : "",
            h: msg.isStreaming ? 1 : "",
            i: common_vendor.o(($event) => msg.role === "user" && !isStreaming.value ? onLongPressUserMsg(msg.id, msg.content) : void 0, msg.id),
            j: common_vendor.o(($event) => msg.role === "user" && !isStreaming.value ? onTapUserMsg(msg.id, msg.content) : void 0, msg.id),
            k: msg.id,
            l: `msg-${msg.id}`,
            m: msg.role === "user" ? 1 : "",
            n: msg.role === "assistant" ? 1 : ""
          });
        }),
        m: scrollToAnchor.value || scrollToBottom.value,
        n: common_vendor.o(onMsgScrollToUpper, "ac"),
        o: isStreaming.value,
        p: common_vendor.o(sendMessage, "06"),
        q: inputText.value,
        r: common_vendor.o(($event) => inputText.value = $event.detail.value, "c0"),
        s: isStreaming.value
      }, isStreaming.value ? {
        t: common_vendor.o(handleStopStreaming, "7b")
      } : {
        v: inputText.value.trim() ? 1 : "",
        w: common_vendor.o(sendMessage, "47")
      }, {
        x: showSessionPanel.value
      }, showSessionPanel.value ? common_vendor.e({
        y: common_vendor.o(($event) => showSessionPanel.value = false, "1f"),
        z: common_vendor.o(startNewChat, "41"),
        A: isLoadingSessions.value
      }, isLoadingSessions.value ? {} : sessions.value.length === 0 ? {} : common_vendor.e({
        C: common_vendor.f(sessions.value, (session, k0, i0) => {
          return {
            a: common_vendor.t(session.title || "新对话"),
            b: common_vendor.t(session.updatedTime ? session.updatedTime.slice(5, 16) : ""),
            c: common_vendor.o(($event) => removeSession(session.id), session.id),
            d: session.id,
            e: common_vendor.unref(chatStore).currentSessionId === session.id ? 1 : "",
            f: common_vendor.o(($event) => switchSession(session.id), session.id)
          };
        }),
        D: isLoadingMoreSession.value
      }, isLoadingMoreSession.value ? {} : !common_vendor.unref(chatStore).hasMoreSessions() ? {} : {}, {
        E: !common_vendor.unref(chatStore).hasMoreSessions(),
        F: common_vendor.o(onSessionScrollToLower, "a2")
      }), {
        B: sessions.value.length === 0,
        G: common_vendor.o(() => {
        }, "f7"),
        H: common_vendor.o(($event) => showSessionPanel.value = false, "50")
      }) : {}, {
        I: showEditPopup.value
      }, showEditPopup.value ? {
        J: common_vendor.o(($event) => showEditPopup.value = false, "b1"),
        K: editText.value,
        L: common_vendor.o(($event) => editText.value = $event.detail.value, "ab"),
        M: common_vendor.o(($event) => showEditPopup.value = false, "9f"),
        N: common_vendor.o(confirmEdit, "c3"),
        O: common_vendor.o(() => {
        }, "d6"),
        P: common_vendor.o(($event) => showEditPopup.value = false, "2f")
      } : {}, {
        Q: showPersonalityPicker.value
      }, showPersonalityPicker.value ? common_vendor.e({
        R: common_vendor.o(($event) => showPersonalityPicker.value = false, "b0"),
        S: femalePersonalities.value.length > 0
      }, femalePersonalities.value.length > 0 ? {
        T: common_vendor.f(femalePersonalities.value, (p, k0, i0) => {
          return {
            a: common_vendor.t(p.emoji),
            b: common_vendor.t(p.name),
            c: common_vendor.t(p.description),
            d: p.code,
            e: common_vendor.unref(userStore).currentPersonality === p.code ? 1 : "",
            f: common_vendor.o(($event) => selectPersonality(p.code), p.code)
          };
        })
      } : {}, {
        U: malePersonalities.value.length > 0
      }, malePersonalities.value.length > 0 ? {
        V: common_vendor.f(malePersonalities.value, (p, k0, i0) => {
          return {
            a: common_vendor.t(p.emoji),
            b: common_vendor.t(p.name),
            c: common_vendor.t(p.description),
            d: p.code,
            e: common_vendor.unref(userStore).currentPersonality === p.code ? 1 : "",
            f: common_vendor.o(($event) => selectPersonality(p.code), p.code)
          };
        })
      } : {}, {
        W: common_vendor.unref(personalityStore).loading
      }, common_vendor.unref(personalityStore).loading ? {} : {}, {
        X: common_vendor.o(() => {
        }, "1e"),
        Y: common_vendor.o(($event) => showPersonalityPicker.value = false, "b1")
      }) : {});
    };
  }
});
wx.createPage(_sfc_main);
//# sourceMappingURL=../../../.sourcemap/mp-weixin/pages/chat/index.js.map
