"use strict";
const common_vendor = require("../../common/vendor.js");
const store_chat = require("../../store/chat.js");
const store_user = require("../../store/user.js");
const api_chat = require("../../api/chat.js");
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
    const personality = common_vendor.computed(() => utils_emotion.getPersonalityInfo(userStore.currentPersonality));
    const messages = common_vendor.computed(() => chatStore.messages);
    const isStreaming = common_vendor.computed(() => chatStore.isStreaming);
    common_vendor.onMounted(() => {
      if (chatStore.currentSessionId) {
        loadHistory();
      }
    });
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
            createdAt: new Date(msg.createdTime).getTime()
          });
        });
        scrollToMsg();
      } catch (e) {
        common_vendor.index.__f__("error", "at pages/chat/index.vue:44", "Load history failed:", e);
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
          common_vendor.index.__f__("error", "at pages/chat/index.vue:88", "SSE error:", err);
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
    }
    const PERSONALITIES = [
      { code: "gentle_sister", label: "温柔姐姐", emoji: "🌸", desc: "温柔陪伴" },
      { code: "rational_mentor", label: "理性导师", emoji: "🎯", desc: "冷静分析" },
      { code: "snarky_friend", label: "毒舌朋友", emoji: "😏", desc: "搞笑吐槽" },
      { code: "midnight_hollow", label: "深夜树洞", emoji: "🌙", desc: "安静倾听" }
    ];
    async function selectPersonality(code) {
      var _a;
      userStore.updatePersonality(code);
      showPersonalityPicker.value = false;
      startNewChat();
      common_vendor.index.showToast({
        title: `已切换到${(_a = PERSONALITIES.find((p) => p.code === code)) == null ? void 0 : _a.label}`,
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
        t: showPersonalityPicker.value
      }, showPersonalityPicker.value ? {
        v: common_vendor.f(PERSONALITIES, (p, k0, i0) => {
          return {
            a: common_vendor.t(p.emoji),
            b: common_vendor.t(p.label),
            c: common_vendor.t(p.desc),
            d: p.code,
            e: common_vendor.unref(userStore).currentPersonality === p.code ? 1 : "",
            f: common_vendor.o(($event) => selectPersonality(p.code), p.code)
          };
        }),
        w: common_vendor.o(($event) => showPersonalityPicker.value = false, "2a")
      } : {});
    };
  }
});
wx.createPage(_sfc_main);
//# sourceMappingURL=../../../.sourcemap/mp-weixin/pages/chat/index.js.map
