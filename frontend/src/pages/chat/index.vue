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
/** 通过切换 msg-bottom / msg-bottom-alt 强制 scroll-into-view 在值不变时也触发 */
const scrollToBottom = ref('msg-bottom')
let scrollToggle = false
/** 向上加载历史消息后，滚动锚定到这条消息（保持视口位置不跳动） */
const scrollToAnchor = ref('')
const showSessionPanel = ref(false)
const showPersonalityPicker = ref(false)
const isComposing = ref(false)
const isLoadingSessions = ref(false)
/** 编辑浮层：显示状态、目标消息 ID 及编辑文本 */
const showEditPopup = ref(false)
const editTargetMsgId = ref('')
const editText = ref('')

// ── 人格（使用全局 store，自动去重请求）────────────────────
const personalities = computed(() => personalityStore.list)
const femalePersonalities = computed(() => personalityStore.femaleList)
const malePersonalities = computed(() => personalityStore.maleList)

const personality = computed(() => {
  const found = personalityStore.findByCode(userStore.currentPersonality)
  // 兜底：后端 Personality 字段是 name，不是 label
  return found ?? { code: userStore.currentPersonality, name: '心屿', emoji: '🌸', description: '', gender: 'female', style: 'gentle' }
})

// ── 消息 / 会话 ────────────────────────────────────────────
const messages = computed(() => chatStore.messages)
const isStreaming = computed(() => chatStore.isStreaming)
const sessions = computed(() => chatStore.sessions)
const isLoadingMoreMsg = computed(() => chatStore.isLoadingMoreMsg)
const isLoadingMoreSession = computed(() => chatStore.isLoadingMoreSession)

onMounted(async () => {
  // 人格列表：首次调用时请求，已加载则直接复用
  await personalityStore.ensureLoaded()
  await loadSessions(true)
  if (chatStore.currentSessionId) {
    await loadHistory(chatStore.currentSessionId)
  }
})

// ── 会话列表（分页懒加载）─────────────────────────────────

/**
 * 加载会话列表
 * @param reset true = 第一次加载（覆盖），false = 加载更多（追加）
 */
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

/** 会话面板滚动到底：懒加载更多会话 */
function onSessionScrollToLower() {
  loadSessions(false)
}

// ── 消息列表（分页懒加载）─────────────────────────────────

/**
 * 将后端返回的分页消息（倒序）转为正序并写入 store
 * @param records    后端返回的倒序记录
 * @param page       当前页码
 * @param totalPages 总页数
 * @param prepend    true = 插入到头部（加载更早消息），false = 覆盖（首次加载）
 */
function applyMessagePage(
  records: any[],
  page: number,
  totalPages: number,
  prepend: boolean
) {
  // 后端按 created_time DESC 排序，第 1 页是最新消息
  // 转为正序（时间从早到晚）再展示
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

/** 切换会话 / 历史：首次加载最新一页消息 */
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

/** 消息区域滚动到顶：加载更早一页消息 */
async function onMsgScrollToUpper() {
  if (!chatStore.currentSessionId) return
  if (!chatStore.hasMoreMessages() || isLoadingMoreMsg.value) return

  chatStore.isLoadingMoreMsg = true
  const nextPage = chatStore.msgPage + 1
  // 记录当前最早一条消息的 id，加载完后滚回到这里
  const anchorId = messages.value[0]?.id ?? ''

  try {
    const result = await getMessageList(chatStore.currentSessionId, nextPage, 20)
    if (result.records.length > 0) {
      applyMessagePage(result.records, nextPage, result.pages, true)
      // 恢复滚动位置到加载前的第一条消息
      if (anchorId) {
        await nextTick()
        scrollToAnchor.value = `msg-${anchorId}`
        // 滚动完成后清空 anchor，避免影响后续底部滚动
        setTimeout(() => { scrollToAnchor.value = '' }, 300)
      }
    } else {
      // 没有更多了，更新 totalPages 防止继续请求
      chatStore.msgTotalPages = chatStore.msgPage
    }
  } catch (e) {
    console.error('Load more messages failed:', e)
  } finally {
    chatStore.isLoadingMoreMsg = false
  }
}

// ── 会话操作 ─────────────────────────────────────────────

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

// ── 发消息 ───────────────────────────────────────────────

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
      // 发送完成后刷新会话列表（标题可能更新了）
      loadSessions(true)
      // 重新拉取最新一页消息，用后端真实 ID 替换前端临时 ID
      // 确保后续编辑功能可以拿到有效的 messageId
      if (chatStore.currentSessionId) {
        await refreshLatestMessages(chatStore.currentSessionId)
      }
      scrollToMsg()
    },
    (err) => {
      console.error('SSE error:', err)
      chatStore.finishStreaming(aiMsgId)
      chatStore.setActiveRequestTask(null)
      uni.showToast({ title: 'AI 回复失败，请重试', icon: 'none' })
    }
  )
  chatStore.startStreaming(aiMsgId, task)
}

// ── 停止流式输出 ─────────────────────────────────────────

async function handleStopStreaming() {
  // 通知后端停止
  try {
    await stopStreaming()
  } catch (e) {
    console.error('Stop streaming failed:', e)
  }
  // 同时中止前端请求，避免继续等待
  if (chatStore.activeRequestTask) {
    chatStore.activeRequestTask.abort()
    chatStore.setActiveRequestTask(null)
  }
  // 更新流式状态
  if (chatStore.streamingMessageId) {
    chatStore.finishStreaming(chatStore.streamingMessageId)
  }
}

// ── 编辑消息 ─────────────────────────────────────────────

/** 长按用户消息：弹出编辑浮层 */
function onLongPressUserMsg(msgId: string, content: string) {
  if (isStreaming.value) return
  editTargetMsgId.value = msgId
  editText.value = content
  showEditPopup.value = true
}

/** 单击用户消息气泡：弹出操作选项（编辑/重新发送） */
function onTapUserMsg(msgId: string, content: string) {
  if (isStreaming.value) return
  // 防止误触：只有点击自己的消息时才弹出
  uni.showActionSheet({
    itemList: ['编辑并重新发送'],
    success: (res) => {
      if (res.tapIndex === 0) {
        editTargetMsgId.value = msgId
        editText.value = content
        showEditPopup.value = true
      }
    }
  })
}

/** 确认编辑，调用编辑接口并重新流式输出 */
async function confirmEdit() {
  const newText = editText.value.trim()
  if (!newText || !editTargetMsgId.value || !chatStore.currentSessionId) return

  showEditPopup.value = false

  // 前端先同步：删除该消息及其之后的所有消息（与后端 deleteFromTime 语义对齐）
  // 后端会将被编辑的消息连同后续一并删除，再由 sendMessage 重新写入
  chatStore.removeMessageFrom(editTargetMsgId.value)
  scrollToMsg()

  // 先添加用户新消息气泡（占位，后续刷新时会被真实 ID 替换）
  const editUserMsgId = `user_edit_${Date.now()}`
  chatStore.addMessage({
    id: editUserMsgId,
    role: 'user',
    content: newText,
    createdAt: Date.now()
  })

  // 添加 AI 回复占位（流式）
  const aiMsgId = `ai_edit_${Date.now()}`
  chatStore.addMessage({
    id: aiMsgId,
    role: 'assistant',
    content: '',
    createdAt: Date.now(),
    isStreaming: true
  })
  scrollToMsg()

  const task = createEditSseConnection(
    editTargetMsgId.value,
    chatStore.currentSessionId,
    newText,
    (chunk) => {
      chatStore.updateStreamingMessage(aiMsgId, chunk)
      scrollToMsg()
    },
    async () => {
      chatStore.finishStreaming(aiMsgId)
      chatStore.setActiveRequestTask(null)
      loadSessions(true)
      // 重新拉取最新一页消息，用后端真实 ID 替换前端临时 ID
      if (chatStore.currentSessionId) {
        await refreshLatestMessages(chatStore.currentSessionId)
      }
      scrollToMsg()
    },
    (err) => {
      console.error('Edit SSE error:', err)
      chatStore.finishStreaming(aiMsgId)
      chatStore.setActiveRequestTask(null)
      uni.showToast({ title: '重新发送失败，请重试', icon: 'none' })
    }
  )
  chatStore.startStreaming(aiMsgId, task)
}

/**
 * SSE 完成后静默刷新最新一页消息
 * 用后端返回的真实 ID 替换前端发送时生成的临时 ID（如 user_xxx / ai_xxx）
 * 这样后续的编辑操作可以拿到有效的 messageId 传给后端
 */
async function refreshLatestMessages(sessionId: string) {
  try {
    const result = await getMessageList(sessionId, 1, 20)
    if (result.records && result.records.length > 0) {
      // 只替换列表尾部（最新 N 条），避免覆盖用户已向上加载的历史消息
      const newMsgs = [...result.records].reverse().map(msg => ({
        id: String(msg.id),
        role: msg.role as 'user' | 'assistant',
        content: msg.content,
        emotion: msg.emotion,
        riskLevel: msg.riskLevel,
        createdAt: parseDate(msg.createdTime).getTime()
      }))
      // 通过 store 方法更新消息列表，避免直接操作 reactive 数组
      chatStore.replaceTrailingMessages(newMsgs, 1, result.pages ?? 1)
    }
  } catch (e) {
    // 刷新失败不影响主流程，静默忽略
    console.warn('refreshLatestMessages failed:', e)
  }
}

// ── 工具函数 ─────────────────────────────────────────────

function scrollToMsg() {
  nextTick(() => {
    // 通过交替切换两个锚点 id，保证即使上次值相同也能触发 scroll-into-view
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
        <text class="header-icon">☰</text>
      </view>
      <view class="header-center" @click="showPersonalityPicker = true">
        <text class="personality-emoji">{{ personality.emoji }}</text>
        <!-- 修复：后端字段是 name，不是 label -->
        <text class="personality-name">{{ personality.name }}</text>
        <text class="header-chevron">›</text>
      </view>
      <view class="header-right" @click="startNewChat">
        <text class="header-icon">✏️</text>
      </view>
    </view>

    <!-- 消息列表（支持向上滚动懒加载历史消息） -->
    <scroll-view
      class="messages-container"
      scroll-y
      :scroll-into-view="scrollToAnchor || scrollToBottom"
      :scroll-with-animation="true"
      @scrolltoupper="onMsgScrollToUpper"
      upper-threshold="60"
    >
      <!-- 加载更早消息提示 -->
      <view v-if="isLoadingMoreMsg" class="load-more-tip">
        <text>加载中...</text>
      </view>
      <view v-else-if="chatStore.msgTotalPages !== -1 && !chatStore.hasMoreMessages()" class="load-more-tip">
        <text>已加载全部消息</text>
      </view>

      <!-- 欢迎消息 -->
      <view v-if="messages.length === 0" class="welcome-area">
        <text class="welcome-emoji">{{ personality.emoji }}</text>
        <text class="welcome-text">嗨，我是你的{{ personality.name }}</text>
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

      <!-- 消息气泡（id 用于向上加载后恢复滚动位置） -->
      <view
        v-for="msg in messages"
        :key="msg.id"
        :id="`msg-${msg.id}`"
        class="message-wrapper"
        :class="{ 'message-user': msg.role === 'user', 'message-ai': msg.role === 'assistant' }"
      >
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
          @longpress="msg.role === 'user' && !isStreaming ? onLongPressUserMsg(msg.id, msg.content) : undefined"
          @tap="msg.role === 'user' && !isStreaming ? onTapUserMsg(msg.id, msg.content) : undefined"
        >
          <text class="message-text" :user-select="true">{{ msg.content }}</text>
          <text v-if="msg.isStreaming" class="typing-cursor">▋</text>
          <!-- 用户消息编辑提示图标（非流式时显示） -->
          <text v-if="msg.role === 'user' && !isStreaming" class="edit-hint">✏</text>
        </view>
      </view>

      <!-- 底部锚点（双锚点交替，解决 scroll-into-view 值不变时不触发的问题） -->
      <view id="msg-bottom" class="msg-bottom-anchor" />
      <view id="msg-bottom-alt" class="msg-bottom-anchor" />
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
      <!-- 流式输出中：显示停止按钮；空闲时：显示发送按钮 -->
      <view
        v-if="isStreaming"
        class="send-btn stop-btn"
        @click="handleStopStreaming"
      >
        <text class="stop-icon">■</text>
      </view>
      <view
        v-else
        class="send-btn"
        :class="{ 'send-btn-active': inputText.trim() }"
        @click="sendMessage"
      >
        <text>↑</text>
      </view>
    </view>

    <!-- 会话列表侧边面板（支持向下滚动懒加载更多会话） -->
    <view v-if="showSessionPanel" class="session-overlay" @click="showSessionPanel = false">
      <view class="session-panel" @click.stop>
        <view class="session-panel-header">
          <text class="session-panel-title">历史对话</text>
          <text class="session-panel-close" @click="showSessionPanel = false">✕</text>
        </view>

        <view class="new-session-btn" @click="startNewChat">
          <text class="new-session-icon">✏️</text>
          <text class="new-session-text">开始新对话</text>
        </view>

        <view v-if="isLoadingSessions" class="session-loading">
          <text>加载中...</text>
        </view>

        <view v-else-if="sessions.length === 0" class="session-empty">
          <text>暂无历史对话</text>
        </view>

        <!-- 会话列表（滚动到底懒加载更多） -->
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
            <view class="session-item-content">
              <text class="session-title">{{ session.title || '新对话' }}</text>
              <text class="session-time">{{ session.updatedTime ? session.updatedTime.slice(5, 16) : '' }}</text>
            </view>
            <text class="session-delete" @click.stop="removeSession(session.id)">🗑</text>
          </view>

          <!-- 加载更多会话状态 -->
          <view v-if="isLoadingMoreSession" class="session-loading">
            <text>加载更多...</text>
          </view>
          <view v-else-if="!chatStore.hasMoreSessions()" class="session-loading">
            <text>没有更多了</text>
          </view>
        </scroll-view>
      </view>
    </view>

    <!-- 编辑消息弹窗 -->
    <view v-if="showEditPopup" class="modal-overlay" @click="showEditPopup = false">
      <view class="edit-popup" @click.stop>
        <view class="edit-popup-header">
          <text class="edit-popup-title">编辑消息</text>
          <text class="edit-popup-close" @click="showEditPopup = false">✕</text>
        </view>
        <textarea
          v-model="editText"
          class="edit-textarea"
          :auto-height="true"
          :max-height="200"
          :show-confirm-bar="false"
          placeholder="修改你的消息..."
          :placeholder-style="'color: #5a5070; font-size: 28rpx'"
        />
        <view class="edit-popup-actions">
          <view class="edit-cancel-btn" @click="showEditPopup = false">
            <text>取消</text>
          </view>
          <view class="edit-confirm-btn" @click="confirmEdit">
            <text>重新发送</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 人格选择器弹窗 -->
    <view v-if="showPersonalityPicker" class="modal-overlay" @click="showPersonalityPicker = false">
      <view class="personality-picker" @click.stop>
        <view class="picker-header">
          <text class="picker-title">选择你的 AI 伴侣</text>
          <text class="picker-close" @click="showPersonalityPicker = false">✕</text>
        </view>

        <view v-if="femalePersonalities.length > 0" class="gender-group">
          <view class="gender-label">
            <text class="gender-icon">♀</text>
            <text class="gender-text">女性角色</text>
          </view>
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

        <view v-if="malePersonalities.length > 0" class="gender-group">
          <view class="gender-label">
            <text class="gender-icon">♂</text>
            <text class="gender-text">男性角色</text>
          </view>
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
          <text>加载中...</text>
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

/* 加载更多历史消息提示 */
.load-more-tip {
  text-align: center;
  padding: 20rpx 0;
  color: #5a5070;
  font-size: 24rpx;
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

/* 停止按钮 */
.stop-btn {
  background: rgba(220, 80, 80, 0.85);
  box-shadow: 0 4rpx 16rpx rgba(220, 80, 80, 0.4);
}

.stop-icon {
  color: white;
  font-size: 28rpx !important;
}

/* 用户消息编辑提示图标 */
.edit-hint {
  display: inline-block;
  margin-left: 8rpx;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.5);
  vertical-align: middle;
  pointer-events: none;
}

/* 消息气泡 relative 定位（弹出层等后续需要时备用） */
.message-bubble {
  position: relative;
}

/* 会话侧边面板 */
.session-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 200;
  display: flex;
  flex-direction: row;
}

.session-panel {
  width: 75%;
  max-width: 560rpx;
  height: 100%;
  background: #13132a;
  display: flex;
  flex-direction: column;
  padding: 0;
  /* 必须设置 overflow:hidden，子 scroll-view 才能正确继承 flex 剩余高度 */
  overflow: hidden;
}

.session-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 100rpx 32rpx 24rpx;
  border-bottom: 1rpx solid rgba(255, 255, 255, 0.06);
}

.session-panel-title {
  font-size: 36rpx;
  color: #e8d5ff;
  font-weight: 600;
}

.session-panel-close {
  font-size: 36rpx;
  color: #7a6b9a;
  padding: 8rpx;
}

.new-session-btn {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 28rpx 32rpx;
  border-bottom: 1rpx solid rgba(255, 255, 255, 0.06);
  background: rgba(184, 158, 232, 0.08);
}

.new-session-icon { font-size: 32rpx; }
.new-session-text { font-size: 28rpx; color: #b89ee8; font-weight: 500; }

.session-loading, .session-empty {
  padding: 60rpx 32rpx;
  text-align: center;
  color: #5a5070;
  font-size: 26rpx;
}

.session-list {
  flex: 1;
  /* 微信小程序中 scroll-view 必须设定明确高度才能滚动；
     配合父容器 flex: column + overflow:hidden 使用 height:0 + flex:1 */
  height: 0;
  min-height: 0;
  overflow: hidden;
}

.session-item {
  display: flex;
  align-items: center;
  padding: 24rpx 32rpx;
  border-bottom: 1rpx solid rgba(255, 255, 255, 0.04);
}

.session-active {
  background: rgba(184, 158, 232, 0.1);
}

.session-item-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  overflow: hidden;
}

.session-title {
  font-size: 28rpx;
  color: #c4a8f0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-time {
  font-size: 22rpx;
  color: #5a5070;
}

.session-delete {
  font-size: 32rpx;
  padding: 8rpx 8rpx 8rpx 24rpx;
  color: #5a5070;
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
  max-height: 80vh;
  overflow-y: auto;
  border: 1rpx solid rgba(184, 158, 232, 0.15);
}

.picker-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 40rpx;
}

.picker-title {
  font-size: 34rpx;
  color: #e8d5ff;
  font-weight: 600;
}

.picker-close {
  font-size: 32rpx;
  color: #7a6b9a;
  padding: 8rpx;
}

.gender-group {
  margin-bottom: 32rpx;
}

.gender-label {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 20rpx;
}

.gender-icon {
  font-size: 28rpx;
  color: #b89ee8;
}

.gender-text {
  font-size: 26rpx;
  color: #7a6b9a;
  font-weight: 500;
  letter-spacing: 2rpx;
}

.picker-loading {
  text-align: center;
  color: #5a5070;
  font-size: 28rpx;
  padding: 60rpx 0;
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

/* 编辑消息弹窗 */
.edit-popup {
  background: #1a1a2e;
  border-top-left-radius: 40rpx;
  border-top-right-radius: 40rpx;
  padding: 40rpx 32rpx 80rpx;
  width: 100%;
  border: 1rpx solid rgba(184, 158, 232, 0.15);
}

.edit-popup-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 32rpx;
}

.edit-popup-title {
  font-size: 34rpx;
  color: #e8d5ff;
  font-weight: 600;
}

.edit-popup-close {
  font-size: 32rpx;
  color: #7a6b9a;
  padding: 8rpx;
}

.edit-textarea {
  width: 100%;
  font-size: 30rpx;
  color: #e8d5ff;
  line-height: 1.5;
  background: rgba(255, 255, 255, 0.06);
  border: 1rpx solid rgba(184, 158, 232, 0.2);
  border-radius: 20rpx;
  padding: 24rpx 28rpx;
  min-height: 120rpx;
  margin-bottom: 32rpx;
}

.edit-popup-actions {
  display: flex;
  gap: 24rpx;
}

.edit-cancel-btn {
  flex: 1;
  height: 88rpx;
  border-radius: 44rpx;
  background: rgba(255, 255, 255, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
}

.edit-cancel-btn text {
  color: #7a6b9a;
  font-size: 30rpx;
}

.edit-confirm-btn {
  flex: 2;
  height: 88rpx;
  border-radius: 44rpx;
  background: linear-gradient(135deg, #b89ee8 0%, #8b6fd1 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4rpx 20rpx rgba(139, 111, 209, 0.4);
}

.edit-confirm-btn text {
  color: white;
  font-size: 30rpx;
  font-weight: 600;
}
</style>

