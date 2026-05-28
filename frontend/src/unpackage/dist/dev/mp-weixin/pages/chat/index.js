"use strict";
const common_vendor = require("../../common/vendor.js");
const store_chat = require("../../store/chat.js");
const store_user = require("../../store/user.js");
const api_chat = require("../../api/chat.js");
const api_personality = require("../../api/personality.js");
const utils_emotion = require("../../utils/emotion.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const chatStore = store_chat.useChatStore();
    const userStore = store_user.useUserStore();
    const inputText = common_vendor.ref("");
    const scrollToBottom = common_vendor.ref("msg-bottom");
    const showSessionPanel = common_vendor.ref(false);
    const showPersonalityPicker = common_vendor.ref(false);
    common_vendor.ref(false);
    const isLoadingSessions = common_vendor.ref(false);
    const personalities = common_vendor.ref([]);
    const personality = common_vendor.computed(() => {
      const found = personalities.value.find((p) => p.code === userStore.currentPersonality);
      return found || { code: userStore.currentPersonality, name: "心屿", emoji: "🌸", description: "", gender: "female", style: "gentle" };
    });
    const femalePersonalities = common_vendor.computed(() => personalities.value.filter((p) => p.gender === "female"));
    const malePersonalities = common_vendor.computed(() => personalities.value.filter((p) => p.gender === "male"));
    const messages = common_vendor.computed(() => chatStore.messages);
    const isStreaming = common_vendor.computed(() => chatStore.isStreaming);
    const sessions = common_vendor.computed(() => chatStore.sessions);
    common_vendor.onMounted(() => {
      loadPersonalities();
      loadSessions();
      if (chatStore.currentSessionId) {
        loadHistory();
      }
    });
    async function loadPersonalities() {
      try {
        personalities.value = await api_personality.getPersonalityList();
      } catch (e) {
        common_vendor.index.__f__("error", "at pages/chat/index.vue:48", "Load personalities failed:", e);
      }
    }
    async function loadSessions() {
      isLoadingSessions.value = true;
      try {
        const result = await api_chat.getSessionList();
        chatStore.setSessions(result.records || []);
      } catch (e) {
        common_vendor.index.__f__("error", "at pages/chat/index.vue:58", "Load sessions failed:", e);
      } finally {
        isLoadingSessions.value = false;
      }
    }
    async function switchSession(sessionId) {
      chatStore.setCurrentSession(sessionId);
      chatStore.clearMessages();
      showSessionPanel.value = false;
      try {
        const list = await api_chat.getMessageList(sessionId);
        list.forEach((msg) => {
          chatStore.addMessage({
            id: String(msg.id),
            role: msg.role,
            content: msg.content,
            emotion: msg.emotion,
            riskLevel: msg.riskLevel,
            createdAt: utils_emotion.parseDate(msg.createdTime).getTime()
          });
        });
        scrollToMsg();
      } catch (e) {
        common_vendor.index.__f__("error", "at pages/chat/index.vue:82", "Switch session failed:", e);
      }
    }
    async function removeSession(sessionId) {
      common_vendor.index.showModal({
        title: "删除会话",
        content: "确认删除这条对话记录？",
        success: async (res) => {
          if (res.confirm) {
            try {
              await api_chat.deleteSession(sessionId);
              chatStore.setSessions(sessions.value.filter((s) => s.id !== sessionId));
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
    async function loadHistory() {
      try {
        const list = await api_chat.getMessageList(chatStore.currentSessionId);
        chatStore.clearMessages();
        list.forEach((msg) => {
          chatStore.addMessage({
            id: String(msg.id),
            role: msg.role,
            content: msg.content,
            emotion: msg.emotion,
            riskLevel: msg.riskLevel,
            createdAt: utils_emotion.parseDate(msg.createdTime).getTime()
          });
        });
        scrollToMsg();
      } catch (e) {
        common_vendor.index.__f__("error", "at pages/chat/index.vue:124", "Load history failed:", e);
      }
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
      chatStore.startStreaming(aiMsgId);
      api_chat.createSseConnection(
        chatStore.currentSessionId,
        text,
        (chunk) => {
          chatStore.updateStreamingMessage(aiMsgId, chunk);
          scrollToMsg();
        },
        () => {
          chatStore.finishStreaming(aiMsgId);
          scrollToMsg();
        },
        (err) => {
          common_vendor.index.__f__("error", "at pages/chat/index.vue:168", "SSE error:", err);
          chatStore.finishStreaming(aiMsgId);
          common_vendor.index.showToast({ title: "AI 回复失败，请重试", icon: "none" });
        }
      );
    }
    function scrollToMsg() {
      common_vendor.nextTick$1(() => {
        scrollToBottom.value = "msg-bottom";
      });
    }
    function startNewChat() {
      chatStore.setCurrentSession(null);
      chatStore.clearMessages();
      showSessionPanel.value = false;
      loadSessions();
    }
    async function selectPersonality(code) {
      userStore.updatePersonality(code);
      showPersonalityPicker.value = false;
      startNewChat();
      const found = personalities.value.find((p) => p.code === code);
      common_vendor.index.showToast({
        title: `已切换到 ${(found == null ? void 0 : found.name) ?? "心屿"}`,
        icon: "none"
      });
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: common_vendor.o(($event) => showSessionPanel.value = !showSessionPanel.value, "c8"),
        b: common_vendor.t(personality.value.emoji),
        c: common_vendor.t(personality.value.label),
        d: common_vendor.o(($event) => showPersonalityPicker.value = true, "59"),
        e: common_vendor.o(startNewChat, "a2"),
        f: messages.value.length === 0
      }, messages.value.length === 0 ? {
        g: common_vendor.t(personality.value.emoji),
        h: common_vendor.t(personality.value.label),
        i: common_vendor.f(["我今天心情不好", "我有点焦虑", "能陪我聊聊吗？"], (reply, k0, i0) => {
          return {
            a: common_vendor.t(reply),
            b: reply,
            c: common_vendor.o(($event) => inputText.value = reply, reply)
          };
        })
      } : {}, {
        j: common_vendor.f(messages.value, (msg, k0, i0) => {
          return common_vendor.e({
            a: msg.role === "assistant"
          }, msg.role === "assistant" ? {
            b: common_vendor.t(personality.value.emoji)
          } : {}, {
            c: common_vendor.t(msg.content),
            d: msg.isStreaming
          }, msg.isStreaming ? {} : {}, {
            e: msg.role === "user" ? 1 : "",
            f: msg.role === "assistant" ? 1 : "",
            g: msg.isStreaming ? 1 : "",
            h: msg.id,
            i: msg.role === "user" ? 1 : "",
            j: msg.role === "assistant" ? 1 : ""
          });
        }),
        k: scrollToBottom.value,
        l: isStreaming.value,
        m: common_vendor.o(sendMessage, "ff"),
        n: inputText.value,
        o: common_vendor.o(($event) => inputText.value = $event.detail.value, "19"),
        p: !isStreaming.value
      }, !isStreaming.value ? {} : {}, {
        q: inputText.value.trim() && !isStreaming.value ? 1 : "",
        r: isStreaming.value ? 1 : "",
        s: common_vendor.o(sendMessage, "29"),
        t: showSessionPanel.value
      }, showSessionPanel.value ? common_vendor.e({
        v: common_vendor.o(($event) => showSessionPanel.value = false, "aa"),
        w: common_vendor.o(startNewChat, "dd"),
        x: isLoadingSessions.value
      }, isLoadingSessions.value ? {} : sessions.value.length === 0 ? {} : {
        z: common_vendor.f(sessions.value, (session, k0, i0) => {
          return {
            a: common_vendor.t(session.title || "新对话"),
            b: common_vendor.t(session.updatedTime ? session.updatedTime.slice(5, 16) : ""),
            c: common_vendor.o(($event) => removeSession(session.id), session.id),
            d: session.id,
            e: common_vendor.unref(chatStore).currentSessionId === session.id ? 1 : "",
            f: common_vendor.o(($event) => switchSession(session.id), session.id)
          };
        })
      }, {
        y: sessions.value.length === 0,
        A: common_vendor.o(() => {
        }, "5c"),
        B: common_vendor.o(($event) => showSessionPanel.value = false, "18")
      }) : {}, {
        C: showPersonalityPicker.value
      }, showPersonalityPicker.value ? common_vendor.e({
        D: common_vendor.o(($event) => showPersonalityPicker.value = false, "a9"),
        E: femalePersonalities.value.length > 0
      }, femalePersonalities.value.length > 0 ? {
        F: common_vendor.f(femalePersonalities.value, (p, k0, i0) => {
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
        G: malePersonalities.value.length > 0
      }, malePersonalities.value.length > 0 ? {
        H: common_vendor.f(malePersonalities.value, (p, k0, i0) => {
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
        I: personalities.value.length === 0
      }, personalities.value.length === 0 ? {} : {}, {
        J: common_vendor.o(() => {
        }, "86"),
        K: common_vendor.o(($event) => showPersonalityPicker.value = false, "92")
      }) : {});
    };
  }
});
wx.createPage(_sfc_main);
//# sourceMappingURL=../../../.sourcemap/mp-weixin/pages/chat/index.js.map
