<script setup lang="ts">
import {computed, nextTick, onMounted, ref} from 'vue'
import {useChatStore} from '../../store/chat'
import {useUserStore} from '../../store/user'
import {createSseConnection, getMessageList} from '../../api/chat'
import {getPersonalityInfo} from '../../utils/emotion'

const chatStore = useChatStore()
const userStore = useUserStore()

const inputText = ref('')
const scrollToBottom = ref('msg-bottom')
const showSessionPanel = ref(false)
const showPersonalityPicker = ref(false)
const isComposing = ref(false)

const personality = computed(() => getPersonalityInfo(userStore.currentPersonality))
const messages = computed(() => chatStore.messages)
const isStreaming = computed(() => chatStore.isStreaming)

onMounted(() => {
  // 加载历史消息（如果有会话）
  if (chatStore.currentSessionId) {
    loadHistory()
  }
})

async function loadHistory() {
  try {
    const list = await getMessageList(chatStore.currentSessionId!)
    chatStore.clearMessages()
    list.forEach(msg => {
      chatStore.addMessage({
        id: String(msg.id),
        role: msg.role,
        content: msg.content,
        emotion: msg.emotion,
        riskLevel: msg.riskLevel,
        createdAt: new Date(msg.createdTime).getTime()
      })
    })
    scrollToMsg()
  } catch (e) {
    console.error('Load history failed:', e)
  }
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || isStreaming.value) return

  inputText.value = ''

  // 添加用户消息
  const userMsgId = `user_${Date.now()}`
  chatStore.addMessage({
    id: userMsgId,
    role: 'user',
    content: text,
    createdAt: Date.now()
  })
  scrollToMsg()

  // 添加 AI 消息占位
  const aiMsgId = `ai_${Date.now()}`
  chatStore.addMessage({
    id: aiMsgId,
    role: 'assistant',
    content: '',
    createdAt: Date.now(),
    isStreaming: true
  })
  chatStore.startStreaming(aiMsgId)

  // 建立 SSE 连接
  createSseConnection(
    chatStore.currentSessionId,
    text,
    (chunk) => {
      chatStore.updateStreamingMessage(aiMsgId, chunk)
      scrollToMsg()
    },
    () => {
      chatStore.finishStreaming(aiMsgId)
      scrollToMsg()
    },
    (err) => {
      console.error('SSE error:', err)
      chatStore.finishStreaming(aiMsgId)
      uni.showToast({ title: 'AI 回复失败，请重试', icon: 'none' })
    }
  )
}

function scrollToMsg() {
  nextTick(() => {
    scrollToBottom.value = 'msg-bottom'
  })
}

function startNewChat() {
  chatStore.setCurrentSession(null)
  chatStore.clearMessages()
  showSessionPanel.value = false
}

const PERSONALITIES = [
  { code: 'gentle_sister', label: '温柔姐姐', emoji: '🌸', desc: '温柔陪伴' },
  { code: 'rational_mentor', label: '理性导师', emoji: '🎯', desc: '冷静分析' },
  { code: 'snarky_friend', label: '毒舌朋友', emoji: '😏', desc: '搞笑吐槽' },
  { code: 'midnight_hollow', label: '深夜树洞', emoji: '🌙', desc: '安静倾听' }
]

async function selectPersonality(code: string) {
  userStore.updatePersonality(code)
  showPersonalityPicker.value = false
  startNewChat()
  uni.showToast({
    title: `已切换到${PERSONALITIES.find(p => p.code === code)?.label}`,
    icon: 'none'
  })
}
</script>

<template>
  <view class="chat-page">
    <!-- 顶部栏 -->
    <view class="chat-header">
      <view class="header-left" @click="showSessionPanel = !showSessionPanel">
        <text class="header-icon">☰</text>
      </view>
      <view class="header-center" @click="showPersonalityPicker = true">
        <text class="personality-emoji">{{ personality.emoji }}</text>
        <text class="personality-name">{{ personality.label }}</text>
        <text class="header-chevron">›</text>
      </view>
      <view class="header-right" @click="startNewChat">
        <text class="header-icon">✏️</text>
      </view>
    </view>

    <!-- 消息列表 -->
    <scroll-view
      class="messages-container"
      scroll-y
      :scroll-into-view="scrollToBottom"
      :scroll-with-animation="true"
    >
      <!-- 欢迎消息 -->
      <view v-if="messages.length === 0" class="welcome-area">
        <text class="welcome-emoji">{{ personality.emoji }}</text>
        <text class="welcome-text">嗨，我是你的{{ personality.label }}</text>
        <text class="welcome-desc">有什么想聊的吗？今天感觉怎么样？🌙</text>
        <view class="quick-replies">
          <text
            v-for="reply in ['我今天心情不好', '我有点焦虑', '能陪我聊聊吗？']"
            :key="reply"
            class="quick-reply-btn"
            @click="inputText = reply"
          >{{ reply }}</text>
        </view>
      </view>

      <!-- 消息气泡 -->
      <view
        v-for="msg in messages"
        :key="msg.id"
        class="message-wrapper"
        :class="{ 'message-user': msg.role === 'user', 'message-ai': msg.role === 'assistant' }"
      >
        <!-- AI 头像 -->
        <view v-if="msg.role === 'assistant'" class="avatar ai-avatar">
          {{ personality.emoji }}
        </view>

        <view
          class="message-bubble"
          :class="{
            'bubble-user': msg.role === 'user',
            'bubble-ai': msg.role === 'assistant',
            'bubble-streaming': msg.isStreaming
          }"
        >
          <text class="message-text" :user-select="true">{{ msg.content }}</text>
          <!-- 打字机光标 -->
          <text v-if="msg.isStreaming" class="typing-cursor">▋</text>
        </view>
      </view>

      <!-- 底部锚点 -->
      <view id="msg-bottom" class="msg-bottom-anchor" />
    </scroll-view>

    <!-- 输入区域 -->
    <view class="input-area">
      <view class="input-wrapper">
        <textarea
          v-model="inputText"
          class="message-input"
          placeholder="说说你的心情..."
          :placeholder-style="'color: #5a5070; font-size: 28rpx'"
          :auto-height="true"
          :max-height="120"
          :show-confirm-bar="false"
          :disabled="isStreaming"
          @confirm="sendMessage"
        />
      </view>
      <view
        class="send-btn"
        :class="{ 'send-btn-active': inputText.trim() && !isStreaming, 'send-btn-loading': isStreaming }"
        @click="sendMessage"
      >
        <text v-if="!isStreaming">↑</text>
        <text v-else class="loading-dot">●</text>
      </view>
    </view>

    <!-- 人格选择器弹窗 -->
    <view v-if="showPersonalityPicker" class="modal-overlay" @click.self="showPersonalityPicker = false">
      <view class="personality-picker">
        <text class="picker-title">选择 AI 人格</text>
        <view class="personality-grid">
          <view
            v-for="p in PERSONALITIES"
            :key="p.code"
            class="personality-card"
            :class="{ 'personality-active': userStore.currentPersonality === p.code }"
            @click="selectPersonality(p.code)"
          >
            <text class="p-emoji">{{ p.emoji }}</text>
            <text class="p-name">{{ p.label }}</text>
            <text class="p-desc">{{ p.desc }}</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style>
.chat-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #0f0f1a;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 100rpx 32rpx 24rpx;
  background: rgba(15, 15, 26, 0.95);
  backdrop-filter: blur(20rpx);
  border-bottom: 1rpx solid rgba(255, 255, 255, 0.05);
}

.header-icon {
  font-size: 40rpx;
  color: #7a6b9a;
  padding: 16rpx;
}

.header-center {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.personality-emoji {
  font-size: 36rpx;
}

.personality-name {
  font-size: 32rpx;
  color: #e8d5ff;
  font-weight: 600;
}

.header-chevron {
  color: #7a6b9a;
  font-size: 32rpx;
}

.messages-container {
  flex: 1;
  padding: 24rpx 24rpx 0;
  overflow: hidden;
}

.welcome-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80rpx 40rpx;
  gap: 20rpx;
}

.welcome-emoji {
  font-size: 80rpx;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.1); }
}

.welcome-text {
  font-size: 36rpx;
  color: #e8d5ff;
  font-weight: 600;
}

.welcome-desc {
  font-size: 28rpx;
  color: #7a6b9a;
  text-align: center;
}

.quick-replies {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  justify-content: center;
  margin-top: 20rpx;
}

.quick-reply-btn {
  background: rgba(184, 158, 232, 0.12);
  border: 1rpx solid rgba(184, 158, 232, 0.25);
  color: #b89ee8;
  font-size: 26rpx;
  padding: 16rpx 28rpx;
  border-radius: 40rpx;
}

.message-wrapper {
  display: flex;
  margin-bottom: 24rpx;
  align-items: flex-end;
  gap: 16rpx;
}

.message-user {
  flex-direction: row-reverse;
}

.message-ai {
  flex-direction: row;
}

.avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 36rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36rpx;
  flex-shrink: 0;
}

.ai-avatar {
  background: rgba(184, 158, 232, 0.15);
}

.message-bubble {
  max-width: 70%;
  padding: 24rpx 28rpx;
  border-radius: 24rpx;
  word-break: break-word;
}

.bubble-user {
  background: linear-gradient(135deg, #b89ee8 0%, #8b6fd1 100%);
  border-bottom-right-radius: 8rpx;
}

.bubble-ai {
  background: rgba(255, 255, 255, 0.06);
  border: 1rpx solid rgba(255, 255, 255, 0.08);
  border-bottom-left-radius: 8rpx;
}

.bubble-streaming {
  border-bottom-left-radius: 8rpx;
}

.message-text {
  font-size: 30rpx;
  line-height: 1.6;
  color: #e8e0f0;
}

.bubble-user .message-text {
  color: white;
}

.typing-cursor {
  color: #b89ee8;
  font-size: 28rpx;
  animation: blink 1s infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.msg-bottom-anchor {
  height: 20rpx;
}

.input-area {
  display: flex;
  align-items: flex-end;
  gap: 16rpx;
  padding: 20rpx 24rpx 48rpx;
  background: rgba(15, 15, 26, 0.97);
  border-top: 1rpx solid rgba(255, 255, 255, 0.06);
}

.input-wrapper {
  flex: 1;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 24rpx;
  border: 1rpx solid rgba(184, 158, 232, 0.2);
  padding: 20rpx 28rpx;
  min-height: 80rpx;
  display: flex;
  align-items: center;
}

.message-input {
  flex: 1;
  font-size: 30rpx;
  color: #e8d5ff;
  line-height: 1.5;
  background: transparent;
  width: 100%;
}

.send-btn {
  width: 80rpx;
  height: 80rpx;
  border-radius: 40rpx;
  background: rgba(139, 111, 209, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.2s;
}

.send-btn-active {
  background: linear-gradient(135deg, #b89ee8 0%, #8b6fd1 100%);
  box-shadow: 0 4rpx 20rpx rgba(139, 111, 209, 0.4);
}

.send-btn text {
  color: white;
  font-size: 36rpx;
  font-weight: bold;
}

.loading-dot {
  animation: pulse 0.8s infinite;
  font-size: 28rpx !important;
}

/* 人格选择器 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: flex-end;
  z-index: 100;
}

.personality-picker {
  background: #1a1a2e;
  border-top-left-radius: 40rpx;
  border-top-right-radius: 40rpx;
  padding: 40rpx 32rpx 80rpx;
  width: 100%;
  border: 1rpx solid rgba(184, 158, 232, 0.15);
}

.picker-title {
  font-size: 34rpx;
  color: #e8d5ff;
  font-weight: 600;
  text-align: center;
  display: block;
  margin-bottom: 40rpx;
}

.personality-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24rpx;
}

.personality-card {
  background: rgba(255, 255, 255, 0.04);
  border: 1rpx solid rgba(184, 158, 232, 0.12);
  border-radius: 24rpx;
  padding: 32rpx 24rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  transition: all 0.2s;
}

.personality-active {
  background: rgba(184, 158, 232, 0.15);
  border-color: rgba(184, 158, 232, 0.5);
}

.p-emoji {
  font-size: 56rpx;
}

.p-name {
  font-size: 28rpx;
  color: #e8d5ff;
  font-weight: 600;
}

.p-desc {
  font-size: 24rpx;
  color: #7a6b9a;
}
</style>

