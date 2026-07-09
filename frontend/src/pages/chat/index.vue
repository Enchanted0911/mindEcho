<script setup lang="ts">
import {computed, nextTick, onMounted, ref} from 'vue'
import {useChatStore} from '../../store/chat'
import {useUserStore} from '../../store/user'
import {usePersonalityStore} from '../../store/personality'
import {
  createEditSseConnection,
  createSseConnection,
  deleteSession,
  getMessageList,
  getSessionList,
  stopStreaming
} from '../../api/chat'
import {parseDate} from '../../utils/emotion'

const chatStore = useChatStore()
const userStore = useUserStore()
const personalityStore = usePersonalityStore()

const inputText = ref('')
const scrollToBottom = ref('msg-bottom')
let scrollToggle = false
const scrollToAnchor = ref('')
const showSessionPanel = ref(false)
const showPersonalityPicker = ref(false)
const isComposing = ref(false)
const isLoadingSessions = ref(false)
const selectedMsgId = ref('')
const editingMsgId = ref('')
const editText = ref('')

const personalities = computed(() => personalityStore.list)
const femalePersonalities = computed(() => personalityStore.femaleList)
const malePersonalities = computed(() => personalityStore.maleList)

const personality = computed(() => {
  const found = personalityStore.findByCode(userStore.currentPersonality)
  return found ?? { code: userStore.currentPersonality, name: '心屿', emoji: '🌸', description: '', gender: 'female', style: 'gentle' }
})

const messages = computed(() => chatStore.messages)
const isStreaming = computed(() => chatStore.isStreaming)
const sessions = computed(() => chatStore.sessions)
const isLoadingMoreMsg = computed(() => chatStore.isLoadingMoreMsg)
const isLoadingMoreSession = computed(() => chatStore.isLoadingMoreSession)

onMounted(async () => {
  await personalityStore.ensureLoaded()
  await loadSessions(true)
  if (chatStore.currentSessionId) {
    await loadHistory(chatStore.currentSessionId)
  }
})

async function loadSessions(reset = false) {
  if (!reset && (!chatStore.hasMoreSessions() || chatStore.isLoadingMoreSession)) return

  const nextPage = reset ? 1 : chatStore.sessionPage + 1
  if (!reset) chatStore.isLoadingMoreSession = true
  else isLoadingSessions.value = true

  try {
    const result = await getSessionList(nextPage, 20)
    if (reset) {
      chatStore.setSessions(result.records || [], result.pages ?? 1)
    } else {
      chatStore.appendSessions(result.records || [], nextPage, result.pages ?? nextPage)
    }
  } catch (e) {
    console.error('Load sessions failed:', e)
  } finally {
    isLoadingSessions.value = false
    chatStore.isLoadingMoreSession = false
  }
}

function onSessionScrollToLower() {
  loadSessions(false)
}

function applyMessagePage(
  records: any[],
  page: number,
  totalPages: number,
  prepend: boolean
) {
  const sorted = [...records].reverse().map(msg => ({
    id: String(msg.id),
    role: msg.role as 'user' | 'assistant',
    content: msg.content,
    emotion: msg.emotion,
    riskLevel: msg.riskLevel,
    createdAt: parseDate(msg.createdTime).getTime()
  }))

  chatStore.msgTotalPages = totalPages
  chatStore.msgPage = page

  if (prepend) {
    chatStore.prependMessages(sorted)
  } else {
    chatStore.clearMessages()
    sorted.forEach(m => chatStore.addMessage(m))
  }
}

async function loadHistory(sessionId: string) {
  chatStore.clearMessages()
  try {
    const result = await getMessageList(sessionId, 1, 20)
    applyMessagePage(result.records, 1, result.pages, false)
    scrollToMsg()
  } catch (e) {
    console.error('Load history failed:', e)
  }
}

async function onMsgScrollToUpper() {
  if (!chatStore.currentSessionId) return
  if (!chatStore.hasMoreMessages() || isLoadingMoreMsg.value) return

  chatStore.isLoadingMoreMsg = true
  const nextPage = chatStore.msgPage + 1
  const anchorId = messages.value[0]?.id ?? ''

  try {
    const result = await getMessageList(chatStore.currentSessionId, nextPage, 20)
    if (result.records.length > 0) {
      applyMessagePage(result.records, nextPage, result.pages, true)
      if (anchorId) {
        await nextTick()
        scrollToAnchor.value = `msg-${anchorId}`
        setTimeout(() => { scrollToAnchor.value = '' }, 300)
      }
    } else {
      chatStore.msgTotalPages = chatStore.msgPage
    }
  } catch (e) {
    console.error('Load more messages failed:', e)
  } finally {
    chatStore.isLoadingMoreMsg = false
  }
}

async function switchSession(sessionId: string) {
  chatStore.setCurrentSession(sessionId)
  showSessionPanel.value = false
  await loadHistory(sessionId)
}

async function removeSession(sessionId: string) {
  uni.showModal({
    title: '删除会话',
    content: '确认删除这条对话记录？',
    success: async (res) => {
      if (res.confirm) {
        try {
          await deleteSession(sessionId)
          chatStore.setSessions(
            sessions.value.filter(s => s.id !== sessionId),
            chatStore.sessionTotalPages
          )
          if (chatStore.currentSessionId === sessionId) {
            chatStore.setCurrentSession(null)
            chatStore.clearMessages()
          }
        } catch (e) {
          uni.showToast({ title: '删除失败', icon: 'none' })
        }
      }
    }
  })
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || isStreaming.value) return

  inputText.value = ''

  const userMsgId = `user_${Date.now()}`
  chatStore.addMessage({
    id: userMsgId,
    role: 'user',
    content: text,
    createdAt: Date.now()
  })
  scrollToMsg()

  const aiMsgId = `ai_${Date.now()}`
  chatStore.addMessage({
    id: aiMsgId,
    role: 'assistant',
    content: '',
    createdAt: Date.now(),
    isStreaming: true
  })

  const task = createSseConnection(
    chatStore.currentSessionId,
    text,
    (chunk) => {
      chatStore.updateStreamingMessage(aiMsgId, chunk)
      scrollToMsg()
    },
    async () => {
      chatStore.finishStreaming(aiMsgId)
      chatStore.setActiveRequestTask(null)
      await loadSessions(true)
      if (!chatStore.currentSessionId && chatStore.sessions.length > 0) {
        chatStore.setCurrentSession(chatStore.sessions[0].id)
      }
      if (chatStore.currentSessionId) {
        await refreshLatestMessages(chatStore.currentSessionId)
      }
      scrollToMsg()
    },
    (err) => {
      console.error('SSE error:', err)
      chatStore.finishStreaming(aiMsgId)
      chatStore.setActiveRequestTask(null)
      const msg = (err instanceof Error && err.message) ? err.message : 'AI 回复失败，请重试'
      uni.showToast({ title: msg, icon: 'none', duration: 3000 })
    }
  )
  chatStore.startStreaming(aiMsgId, task)
}

async function handleStopStreaming() {
  try {
    await stopStreaming()
  } catch (e) {
    console.error('Stop streaming failed:', e)
  }
  if (chatStore.activeRequestTask) {
    chatStore.activeRequestTask.abort()
    chatStore.setActiveRequestTask(null)
  }
  if (chatStore.streamingMessageId) {
    chatStore.finishStreaming(chatStore.streamingMessageId)
  }
}

function onTapUserMsg(msgId: string) {
  if (isStreaming.value) return
  if (selectedMsgId.value === msgId) {
    selectedMsgId.value = ''
    return
  }
  if (editingMsgId.value && editingMsgId.value !== msgId) {
    editingMsgId.value = ''
    editText.value = ''
  }
  selectedMsgId.value = msgId
}

function startInlineEdit(msgId: string, content: string) {
  editingMsgId.value = msgId
  editText.value = content
  selectedMsgId.value = ''
}

function cancelInlineEdit() {
  editingMsgId.value = ''
  editText.value = ''
}

async function confirmInlineEdit() {
  const newText = editText.value.trim()
  if (!newText || !editingMsgId.value) return

  const targetMsgId = editingMsgId.value
  editingMsgId.value = ''
  editText.value = ''

  const isTemporaryMsg = targetMsgId.startsWith('user_')

  chatStore.removeMessageFrom(targetMsgId)
  scrollToMsg()

  const newUserMsgId = `user_${Date.now()}`
  chatStore.addMessage({
    id: newUserMsgId,
    role: 'user',
    content: newText,
    createdAt: Date.now()
  })

  const aiMsgId = `ai_${Date.now()}`
  chatStore.addMessage({
    id: aiMsgId,
    role: 'assistant',
    content: '',
    createdAt: Date.now(),
    isStreaming: true
  })
  scrollToMsg()

  const onChunk = (chunk: string) => {
    chatStore.updateStreamingMessage(aiMsgId, chunk)
    scrollToMsg()
  }

  const onDone = async () => {
    chatStore.finishStreaming(aiMsgId)
    chatStore.setActiveRequestTask(null)
    await loadSessions(true)
    if (!chatStore.currentSessionId && chatStore.sessions.length > 0) {
      chatStore.setCurrentSession(chatStore.sessions[0].id)
    }
    if (chatStore.currentSessionId) {
      await refreshLatestMessages(chatStore.currentSessionId)
    }
    scrollToMsg()
  }

  const onError = (err: any) => {
    console.error('Edit SSE error:', err)
    chatStore.finishStreaming(aiMsgId)
    chatStore.setActiveRequestTask(null)
    const msg = (err instanceof Error && err.message) ? err.message : '重新发送失败，请重试'
    uni.showToast({ title: msg, icon: 'none', duration: 3000 })
  }

  let task: UniApp.RequestTask
  if (isTemporaryMsg || !chatStore.currentSessionId) {
    task = createSseConnection(
      chatStore.currentSessionId,
      newText,
      onChunk,
      onDone,
      onError
    )
  } else {
    task = createEditSseConnection(
      targetMsgId,
      chatStore.currentSessionId,
      newText,
      onChunk,
      onDone,
      onError
    )
  }
  chatStore.startStreaming(aiMsgId, task)
}

async function refreshLatestMessages(sessionId: string) {
  try {
    const result = await getMessageList(sessionId, 1, 20)
    if (result.records && result.records.length > 0) {
      const newMsgs = [...result.records].reverse().map(msg => ({
        id: String(msg.id),
        role: msg.role as 'user' | 'assistant',
        content: msg.content,
        emotion: msg.emotion,
        riskLevel: msg.riskLevel,
        createdAt: parseDate(msg.createdTime).getTime()
      }))
      chatStore.replaceTrailingMessages(newMsgs, 1, result.pages ?? 1)
    }
  } catch (e) {
    console.warn('refreshLatestMessages failed:', e)
  }
}

function scrollToMsg() {
  nextTick(() => {
    scrollToggle = !scrollToggle
    scrollToBottom.value = scrollToggle ? 'msg-bottom' : 'msg-bottom-alt'
  })
}

function startNewChat() {
  chatStore.setCurrentSession(null)
  chatStore.clearMessages()
  showSessionPanel.value = false
  loadSessions(true)
}

async function selectPersonality(code: string) {
  userStore.updatePersonality(code)
  showPersonalityPicker.value = false
  startNewChat()
  const found = personalityStore.findByCode(code)
  uni.showToast({
    title: `已切换到 ${found?.name ?? '心屿'}`,
    icon: 'none'
  })
}
</script>

<template>
  <view class="chat-page">
    <!-- 顶部栏 -->
    <view class="chat-header">
      <view class="header-left" @click="showSessionPanel = !showSessionPanel">
        <view class="icon-btn">
          <text class="icon-text">☰</text>
        </view>
      </view>
      <view class="header-center" @click="showPersonalityPicker = true">
        <view class="avatar-sm">
          <text class="avatar-sm-emoji">{{ personality.emoji }}</text>
        </view>
        <text class="personality-name">{{ personality.name }}</text>
        <text class="chevron">›</text>
      </view>
      <view class="header-right" @click="startNewChat">
        <view class="icon-btn new-chat-btn">
          <text class="icon-text">✏</text>
        </view>
      </view>
    </view>

    <!-- 消息列表 -->
    <scroll-view
      class="messages-container"
      scroll-y
      :scroll-into-view="scrollToAnchor || scrollToBottom"
      :scroll-with-animation="true"
      @scrolltoupper="onMsgScrollToUpper"
      upper-threshold="60"
    >
      <view v-if="isLoadingMoreMsg" class="load-more-tip">
        <text class="load-more-text">加载中…</text>
      </view>
      <view v-else-if="chatStore.msgTotalPages !== -1 && !chatStore.hasMoreMessages()" class="load-more-tip">
        <text class="load-more-text">— 已加载全部消息 —</text>
      </view>

      <!-- 欢迎区域 -->
      <view v-if="messages.length === 0" class="welcome-area">
        <view class="welcome-avatar">
          <text class="welcome-emoji">{{ personality.emoji }}</text>
        </view>
        <text class="welcome-name">{{ personality.name }}</text>
        <text class="welcome-desc">今天感觉怎么样？随时可以和我聊聊 🌙</text>
        <view class="quick-replies">
          <view
            v-for="reply in ['我今天心情不好', '我有点焦虑', '能陪我聊聊吗？']"
            :key="reply"
            class="quick-reply-btn"
            @click="inputText = reply"
          >
            <text class="quick-reply-text">{{ reply }}</text>
          </view>
        </view>
      </view>

      <!-- 消息气泡 -->
      <view
        v-for="msg in messages"
        :key="msg.id"
        :id="`msg-${msg.id}`"
        class="message-wrapper"
        :class="{ 'message-user': msg.role === 'user', 'message-ai': msg.role === 'assistant' }"
      >
        <!-- AI 头像 -->
        <view v-if="msg.role === 'assistant'" class="ai-avatar-wrap">
          <view class="ai-avatar">
            <text class="ai-avatar-emoji">{{ personality.emoji }}</text>
          </view>
        </view>

        <!-- 用户消息 -->
        <view v-if="msg.role === 'user'" class="user-msg-col">
          <view v-if="editingMsgId === msg.id" class="inline-edit-wrapper">
            <textarea
              v-model="editText"
              class="inline-edit-textarea"
              :auto-height="true"
              :max-height="160"
              :show-confirm-bar="false"
              :focus="true"
              placeholder="修改你的消息..."
              :placeholder-style="'color: rgba(100,100,120,0.4); font-size: 28rpx'"
            />
            <view class="inline-edit-actions">
              <view class="inline-cancel-btn" @click="cancelInlineEdit">
                <text class="inline-btn-text">取消</text>
              </view>
              <view class="inline-confirm-btn" @click="confirmInlineEdit">
                <text class="inline-btn-text inline-confirm-text">重新发送</text>
              </view>
            </view>
          </view>
          <view v-else>
            <view
              class="bubble-user"
              :class="{ 'bubble-selected': selectedMsgId === msg.id }"
              @tap="onTapUserMsg(msg.id)"
            >
              <text class="msg-text-user">{{ msg.content }}</text>
            </view>
            <view v-if="selectedMsgId === msg.id" class="msg-toolbar">
              <view class="toolbar-btn" @click="startInlineEdit(msg.id, msg.content)">
                <text class="toolbar-icon">✏</text>
                <text class="toolbar-label">编辑</text>
              </view>
            </view>
          </view>
        </view>

        <!-- AI 消息 -->
        <view v-else class="ai-msg-col">
          <!-- AI 消息内容卡片 -->
          <view
            class="bubble-ai"
            :class="{ 'bubble-streaming': msg.isStreaming }"
          >
            <text v-if="msg.isStreaming && !msg.content" class="typing-indicator">
              <text class="dot dot1">●</text><text class="dot dot2">●</text><text class="dot dot3">●</text>
            </text>
            <text v-else class="msg-text-ai">{{ msg.content }}<text v-if="msg.isStreaming" class="cursor">▋</text></text>
          </view>
        </view>
      </view>

      <view id="msg-bottom" class="anchor" />
      <view id="msg-bottom-alt" class="anchor" />
    </scroll-view>

    <!-- 输入区域 -->
    <view class="input-area">
      <view class="input-row">
        <view class="input-box">
          <textarea
            v-model="inputText"
            class="message-input"
            placeholder="Ask me anything..."
            :placeholder-style="'color: rgba(150,150,170,0.55); font-size: 28rpx'"
            :auto-height="true"
            :max-height="120"
            :show-confirm-bar="false"
            :disabled="isStreaming"
            @confirm="sendMessage"
          />
        </view>
        <view v-if="isStreaming" class="send-btn stop-active" @click="handleStopStreaming">
          <view class="stop-icon" />
        </view>
        <view
          v-else
          class="send-btn"
          :class="{ 'send-active': inputText.trim() }"
          @click="sendMessage"
        >
          <text class="send-icon">↑</text>
        </view>
      </view>
    </view>

    <!-- 会话列表面板（左滑抽屉） -->
    <view v-if="showSessionPanel" class="session-overlay" @click="showSessionPanel = false">
      <view class="session-panel" @click.stop>
        <view class="session-panel-header">
          <text class="session-panel-title">历史对话</text>
          <view class="close-btn" @click="showSessionPanel = false">
            <text class="close-icon">✕</text>
          </view>
        </view>

        <view class="new-session-item" @click="startNewChat">
          <view class="new-session-icon-wrap">
            <text class="new-session-icon">✏</text>
          </view>
          <text class="new-session-text">开始新对话</text>
        </view>

        <view v-if="isLoadingSessions" class="session-empty-tip">
          <text>加载中…</text>
        </view>
        <view v-else-if="sessions.length === 0" class="session-empty-tip">
          <text>暂无历史对话</text>
        </view>

        <scroll-view
          v-else
          class="session-list"
          scroll-y
          @scrolltolower="onSessionScrollToLower"
          lower-threshold="60"
        >
          <view
            v-for="session in sessions"
            :key="session.id"
            class="session-item"
            :class="{ 'session-active': chatStore.currentSessionId === session.id }"
            @click="switchSession(session.id)"
          >
            <view class="session-item-info">
              <text class="session-title">{{ session.title || '新对话' }}</text>
              <text class="session-time">{{ session.updatedTime ? session.updatedTime.slice(5, 16) : '' }}</text>
            </view>
            <view class="session-delete-btn" @click.stop="removeSession(session.id)">
              <text class="session-delete-icon">🗑</text>
            </view>
          </view>
          <view v-if="isLoadingMoreSession" class="session-empty-tip">
            <text>加载更多…</text>
          </view>
          <view v-else-if="!chatStore.hasMoreSessions()" class="session-empty-tip">
            <text>没有更多了</text>
          </view>
        </scroll-view>
      </view>
    </view>

    <!-- 人格选择器（底部抽屉） -->
    <view v-if="showPersonalityPicker" class="modal-overlay" @click="showPersonalityPicker = false">
      <view class="personality-picker" @click.stop>
        <view class="picker-handle" />
        <view class="picker-header">
          <text class="picker-title">选择 AI 伴侣</text>
          <view class="close-btn picker-close" @click="showPersonalityPicker = false">
            <text class="close-icon">✕</text>
          </view>
        </view>

        <view v-if="femalePersonalities.length > 0" class="gender-section">
          <text class="gender-label">女性角色</text>
          <view class="personality-grid">
            <view
              v-for="p in femalePersonalities"
              :key="p.code"
              class="personality-card"
              :class="{ 'personality-active': userStore.currentPersonality === p.code }"
              @click="selectPersonality(p.code)"
            >
              <text class="p-emoji">{{ p.emoji }}</text>
              <text class="p-name">{{ p.name }}</text>
              <text class="p-desc">{{ p.description }}</text>
            </view>
          </view>
        </view>

        <view v-if="malePersonalities.length > 0" class="gender-section">
          <text class="gender-label">男性角色</text>
          <view class="personality-grid">
            <view
              v-for="p in malePersonalities"
              :key="p.code"
              class="personality-card"
              :class="{ 'personality-active': userStore.currentPersonality === p.code }"
              @click="selectPersonality(p.code)"
            >
              <text class="p-emoji">{{ p.emoji }}</text>
              <text class="p-name">{{ p.name }}</text>
              <text class="p-desc">{{ p.description }}</text>
            </view>
          </view>
        </view>

        <view v-if="personalityStore.loading" class="picker-loading">
          <text>加载中…</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style>
/* ── 全局页面 ── */
.chat-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f5f7;
}

/* ── 顶部栏 ── */
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 96rpx 28rpx 20rpx;
  background: #ffffff;
  border-bottom: 1rpx solid rgba(0, 0, 0, 0.06);
  flex-shrink: 0;
}

.icon-btn {
  width: 72rpx;
  height: 72rpx;
  border-radius: 18rpx;
  background: #f0f0f3;
  display: flex;
  align-items: center;
  justify-content: center;
}

.new-chat-btn {
  background: #1a1a2e;
}

.icon-text {
  font-size: 32rpx;
  color: #555577;
}

.new-chat-btn .icon-text {
  color: #ffffff;
}

.header-center {
  display: flex;
  align-items: center;
  gap: 12rpx;
  background: #f0f0f3;
  border-radius: 40rpx;
  padding: 10rpx 24rpx 10rpx 14rpx;
}

.avatar-sm {
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea, #764ba2);
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-sm-emoji { font-size: 26rpx; }

.personality-name {
  font-size: 28rpx;
  color: #1a1a2e;
  font-weight: 600;
}

.chevron {
  font-size: 32rpx;
  color: #999;
}

/* ── 消息区域 ── */
.messages-container {
  flex: 1;
  overflow: hidden;
  padding: 24rpx 28rpx 12rpx;
  background: #f5f5f7;
}

.load-more-tip {
  text-align: center;
  padding: 20rpx 0;
}

.load-more-text {
  font-size: 22rpx;
  color: #bbb;
}

/* ── 欢迎区 ── */
.welcome-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80rpx 32rpx 48rpx;
  gap: 20rpx;
}

.welcome-avatar {
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 32rpx rgba(102, 126, 234, 0.35);
}

.welcome-emoji { font-size: 54rpx; }

.welcome-name {
  font-size: 38rpx;
  color: #1a1a2e;
  font-weight: 700;
}

.welcome-desc {
  font-size: 27rpx;
  color: #888;
  text-align: center;
  line-height: 1.7;
}

.quick-replies {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
  justify-content: center;
  margin-top: 12rpx;
}

.quick-reply-btn {
  background: #ffffff;
  border: 1.5rpx solid #e8e8f0;
  border-radius: 30rpx;
  padding: 14rpx 28rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.05);
}

.quick-reply-text {
  font-size: 26rpx;
  color: #555577;
}

/* ── 消息条 ── */
.message-wrapper {
  display: flex;
  margin-bottom: 28rpx;
  align-items: flex-end;
  gap: 16rpx;
}

.message-user { flex-direction: row-reverse; }
.message-ai { flex-direction: row; }

/* AI 头像 */
.ai-avatar-wrap { flex-shrink: 0; align-self: flex-start; margin-top: 4rpx; }

.ai-avatar {
  width: 68rpx;
  height: 68rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea, #764ba2);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4rpx 16rpx rgba(102, 126, 234, 0.3);
}

.ai-avatar-emoji { font-size: 34rpx; }

/* ── 用户气泡 ── */
.user-msg-col {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  max-width: 70%;
}

.bubble-user {
  background: #1a1a2e;
  border-radius: 24rpx 24rpx 6rpx 24rpx;
  padding: 22rpx 28rpx;
  max-width: 100%;
  box-shadow: 0 4rpx 20rpx rgba(26, 26, 46, 0.2);
}

.bubble-selected {
  box-shadow: 0 0 0 3rpx rgba(102, 126, 234, 0.5), 0 4rpx 20rpx rgba(26, 26, 46, 0.2);
}

.msg-text-user {
  font-size: 29rpx;
  color: #ffffff;
  line-height: 1.65;
  word-break: break-word;
}

/* ── AI 气泡 ── */
.ai-msg-col {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  max-width: 74%;
}

.bubble-ai {
  background: #ffffff;
  border-radius: 6rpx 24rpx 24rpx 24rpx;
  padding: 22rpx 28rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.07);
  border: 1rpx solid rgba(0, 0, 0, 0.04);
}

.msg-text-ai {
  font-size: 29rpx;
  color: #1a1a2e;
  line-height: 1.8;
  word-break: break-word;
}

.cursor {
  color: #667eea;
  font-size: 26rpx;
  animation: blink 0.9s infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* 打字动画 */
.typing-indicator {
  display: flex;
  gap: 8rpx;
  align-items: center;
  padding: 4rpx 0;
}

.dot {
  font-size: 14rpx;
  color: #aaa;
  animation: bounce-dot 1.4s ease-in-out infinite;
}
.dot1 { animation-delay: 0s; }
.dot2 { animation-delay: 0.2s; }
.dot3 { animation-delay: 0.4s; }

@keyframes bounce-dot {
  0%, 80%, 100% { transform: translateY(0); opacity: 0.35; }
  40% { transform: translateY(-6rpx); opacity: 1; }
}

/* ── 工具栏 ── */
.msg-toolbar {
  display: flex;
  gap: 8rpx;
  margin-top: 10rpx;
  justify-content: flex-end;
}

.toolbar-btn {
  display: flex;
  align-items: center;
  gap: 6rpx;
  background: #ffffff;
  border: 1rpx solid #e5e5ea;
  border-radius: 20rpx;
  padding: 8rpx 20rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.06);
}

.toolbar-icon { font-size: 22rpx; color: #667eea; }
.toolbar-label { font-size: 22rpx; color: #555577; }

/* ── inline 编辑 ── */
.inline-edit-wrapper {
  width: 100%;
  background: #ffffff;
  border: 2rpx solid #667eea;
  border-radius: 20rpx;
  padding: 20rpx;
  box-shadow: 0 4rpx 20rpx rgba(102, 126, 234, 0.15);
}

.inline-edit-textarea {
  width: 100%;
  font-size: 29rpx;
  color: #1a1a2e;
  line-height: 1.6;
  background: transparent;
  min-height: 60rpx;
  margin-bottom: 16rpx;
}

.inline-edit-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12rpx;
}

.inline-cancel-btn, .inline-confirm-btn {
  padding: 12rpx 28rpx;
  border-radius: 20rpx;
}

.inline-cancel-btn {
  background: #f0f0f3;
}

.inline-confirm-btn {
  background: #1a1a2e;
}

.inline-btn-text { font-size: 25rpx; color: #666; }
.inline-confirm-text { color: #ffffff; font-weight: 600; }

.anchor { height: 20rpx; }

/* ── 输入区域 ── */
.input-area {
  padding: 16rpx 24rpx 52rpx;
  background: #ffffff;
  border-top: 1rpx solid rgba(0, 0, 0, 0.06);
  flex-shrink: 0;
}

.input-row {
  display: flex;
  align-items: flex-end;
  gap: 14rpx;
}

.input-box {
  flex: 1;
  background: #f5f5f7;
  border-radius: 22rpx;
  padding: 16rpx 20rpx;
  min-height: 82rpx;
  display: flex;
  align-items: center;
  border: 1.5rpx solid transparent;
}

.message-input {
  flex: 1;
  font-size: 29rpx;
  color: #1a1a2e;
  line-height: 1.55;
  background: transparent;
  width: 100%;
  min-height: 44rpx;
}

.send-btn {
  width: 82rpx;
  height: 82rpx;
  border-radius: 22rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: #e0e0e8;
  transition: all 0.2s;
}

.send-active {
  background: #1a1a2e;
  box-shadow: 0 6rpx 20rpx rgba(26, 26, 46, 0.3);
}

.stop-active {
  background: #ff4757;
  box-shadow: 0 6rpx 20rpx rgba(255, 71, 87, 0.3);
}

.send-icon {
  font-size: 34rpx;
  color: #999;
  font-weight: 700;
}

.send-active .send-icon { color: #ffffff; }

.stop-icon {
  width: 24rpx;
  height: 24rpx;
  background: #ffffff;
  border-radius: 5rpx;
}

/* ── 会话面板 ── */
.session-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(6rpx);
  z-index: 200;
  display: flex;
}

.session-panel {
  width: 78%;
  max-width: 580rpx;
  height: 100%;
  background: #ffffff;
  border-right: 1rpx solid rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.session-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 96rpx 28rpx 22rpx;
  border-bottom: 1rpx solid #f0f0f3;
  flex-shrink: 0;
}

.session-panel-title {
  font-size: 36rpx;
  color: #1a1a2e;
  font-weight: 700;
}

.close-btn {
  width: 52rpx;
  height: 52rpx;
  border-radius: 14rpx;
  background: #f0f0f3;
  display: flex;
  align-items: center;
  justify-content: center;
}

.close-icon { font-size: 24rpx; color: #888; }

.new-session-item {
  display: flex;
  align-items: center;
  gap: 18rpx;
  padding: 26rpx 28rpx;
  border-bottom: 1rpx solid #f5f5f7;
}

.new-session-icon-wrap {
  width: 48rpx;
  height: 48rpx;
  border-radius: 14rpx;
  background: #1a1a2e;
  display: flex;
  align-items: center;
  justify-content: center;
}

.new-session-icon { font-size: 22rpx; color: #ffffff; }
.new-session-text { font-size: 28rpx; color: #1a1a2e; font-weight: 600; }

.session-empty-tip {
  padding: 40rpx 28rpx;
  text-align: center;
  color: #bbb;
  font-size: 24rpx;
}

.session-list { flex: 1; height: 0; min-height: 0; overflow: hidden; }

.session-item {
  display: flex;
  align-items: center;
  padding: 22rpx 28rpx;
  border-bottom: 1rpx solid #f5f5f7;
}

.session-active {
  background: #f0f0f8;
}

.session-item-info {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.session-title {
  font-size: 28rpx;
  color: #1a1a2e;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.session-time { font-size: 22rpx; color: #bbb; }

.session-delete-btn {
  padding: 8rpx 8rpx 8rpx 20rpx;
}

.session-delete-icon { font-size: 30rpx; color: #ccc; }

/* ── 人格选择器 ── */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(8rpx);
  display: flex;
  align-items: flex-end;
  z-index: 100;
}

.personality-picker {
  background: #ffffff;
  border-top: 1rpx solid rgba(0, 0, 0, 0.06);
  border-top-left-radius: 36rpx;
  border-top-right-radius: 36rpx;
  padding: 16rpx 28rpx 80rpx;
  width: 100%;
  max-height: 82vh;
  overflow-y: auto;
  box-shadow: 0 -8rpx 40rpx rgba(0, 0, 0, 0.12);
}

.picker-handle {
  width: 64rpx;
  height: 7rpx;
  border-radius: 4rpx;
  background: #e0e0e8;
  margin: 0 auto 28rpx;
}

.picker-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 28rpx;
}

.picker-title {
  font-size: 36rpx;
  color: #1a1a2e;
  font-weight: 700;
}

.picker-close {
  background: #f0f0f3;
}

.gender-section { margin-bottom: 28rpx; }

.gender-label {
  font-size: 22rpx;
  color: #aaa;
  display: block;
  margin-bottom: 16rpx;
  letter-spacing: 2rpx;
  text-transform: uppercase;
}

.picker-loading {
  text-align: center;
  color: #ccc;
  font-size: 26rpx;
  padding: 40rpx 0;
}

.personality-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
}

.personality-card {
  background: #f8f8fb;
  border: 1.5rpx solid #ebebf0;
  border-radius: 22rpx;
  padding: 26rpx 16rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
}

.personality-active {
  background: #f0f0f8;
  border-color: #667eea;
  box-shadow: 0 4rpx 16rpx rgba(102, 126, 234, 0.15);
}

.p-emoji { font-size: 48rpx; }
.p-name { font-size: 28rpx; color: #1a1a2e; font-weight: 600; }
.p-desc { font-size: 22rpx; color: #999; text-align: center; line-height: 1.4; }
</style>

