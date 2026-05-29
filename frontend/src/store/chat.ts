import {defineStore} from 'pinia'
import {ref} from 'vue'

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  emotion?: string
  riskLevel?: string
  createdAt: number
  isStreaming?: boolean
}

export interface ChatSession {
  id: string   // 后端 Long 序列化为字符串，防止 JS 精度丢失
  title: string
  aiPersonality: string
  createdTime: string
  updatedTime: string
}

export const useChatStore = defineStore('chat', () => {
  const currentSessionId = ref<string | null>(null)
  const messages = ref<ChatMessage[]>([])
  const isLoading = ref(false)
  const isStreaming = ref(false)
  const streamingMessageId = ref<string | null>(null)
  /** 当前正在进行的 SSE RequestTask，用于前端中止请求 */
  const activeRequestTask = ref<UniApp.RequestTask | null>(null)
  /** 正在编辑的消息 ID（null 表示未处于编辑状态） */
  const editingMessageId = ref<string | null>(null)

  // ── 消息分页状态 ──────────────────────────────────────────
  /** 当前已加载到第几页（1 = 最新一页） */
  const msgPage = ref(1)
  /** 消息总页数，-1 表示未知（未加载过） */
  const msgTotalPages = ref(-1)
  /** 是否正在加载更多历史消息 */
  const isLoadingMoreMsg = ref(false)

  // ── 会话分页状态 ──────────────────────────────────────────
  const sessions = ref<ChatSession[]>([])
  /** 当前已加载到第几页 */
  const sessionPage = ref(1)
  /** 会话总页数，-1 表示未知 */
  const sessionTotalPages = ref(-1)
  /** 是否正在加载更多会话 */
  const isLoadingMoreSession = ref(false)

  // ── 消息操作 ────────────────────────────────────────────
  function setCurrentSession(sessionId: string | null) {
    currentSessionId.value = sessionId
  }

  function addMessage(message: ChatMessage) {
    messages.value.push(message)
  }

  /**
   * 将更早的消息批量插入到列表头部（向上加载历史时使用）
   * 需要先过滤掉已存在的 id，防止重复
   */
  function prependMessages(older: ChatMessage[]) {
    const existingIds = new Set(messages.value.map(m => m.id))
    const deduped = older.filter(m => !existingIds.has(m.id))
    messages.value = [...deduped, ...messages.value]
  }

  function updateStreamingMessage(messageId: string, chunk: string) {
    const msg = messages.value.find(m => m.id === messageId)
    if (msg) {
      msg.content += chunk
    }
  }

  function finishStreaming(messageId: string) {
    const msg = messages.value.find(m => m.id === messageId)
    if (msg) {
      msg.isStreaming = false
    }
    isStreaming.value = false
    streamingMessageId.value = null
  }

  /** 清空消息列表并重置分页状态（切换会话时使用） */
  function clearMessages() {
    messages.value = []
    msgPage.value = 1
    msgTotalPages.value = -1
    isLoadingMoreMsg.value = false
  }

  function startStreaming(messageId: string, task?: UniApp.RequestTask) {
    isStreaming.value = true
    streamingMessageId.value = messageId
    if (task) {
      activeRequestTask.value = task
    }
  }

  function setActiveRequestTask(task: UniApp.RequestTask | null) {
    activeRequestTask.value = task
  }

  function setEditingMessageId(id: string | null) {
    editingMessageId.value = id
  }

  /** 更新指定消息内容（用于编辑回显） */
  function updateMessageContent(messageId: string, newContent: string) {
    const msg = messages.value.find(m => m.id === messageId)
    if (msg) {
      msg.content = newContent
    }
  }

  /** 删除指定消息之后的所有消息（不含自身，历史兼容保留） */
  function removeMessagesAfter(messageId: string) {
    const idx = messages.value.findIndex(m => m.id === messageId)
    if (idx !== -1) {
      messages.value = messages.value.slice(0, idx + 1)
    }
  }

  /** 删除指定消息本身及之后的所有消息（与后端 deleteFromTime >= 语义对齐） */
  function removeMessageFrom(messageId: string) {
    const idx = messages.value.findIndex(m => m.id === messageId)
    if (idx !== -1) {
      messages.value = messages.value.slice(0, idx)
    }
  }

  /** 是否还有更多历史消息可加载 */
  function hasMoreMessages(): boolean {
    if (msgTotalPages.value === -1) return true   // 未知，允许尝试
    return msgPage.value < msgTotalPages.value
  }

  /**
   * 用真实消息列表替换消息尾部（SSE 完成后刷新时使用）
   * 保留早于最早真实消息的历史记录，用新消息列表替换其余部分
   */
  function replaceTrailingMessages(newMsgs: ChatMessage[], newPage: number, newTotalPages: number) {
    if (newMsgs.length === 0) return
    const firstRealId = newMsgs[0]?.id
    // 找到当前列表中第一个已有真实 ID（数字字符串）且与新数据对应的切割点
    const cutIdx = messages.value.findIndex(
      m => m.id === firstRealId || (!m.id.startsWith('user_') && !m.id.startsWith('ai_') && !m.id.startsWith('ai_edit_'))
    )
    const olderMsgs = cutIdx > 0 ? messages.value.slice(0, cutIdx) : []
    // 合并旧历史 + 新真实消息，去重
    const existingIds = new Set(newMsgs.map(m => m.id))
    const deduped = olderMsgs.filter(m => !existingIds.has(m.id))
    messages.value = [...deduped, ...newMsgs]
    msgPage.value = newPage
    msgTotalPages.value = newTotalPages
  }

  // ── 会话操作 ────────────────────────────────────────────
  /** 首次加载（覆盖）会话列表，重置分页 */
  function setSessions(list: ChatSession[], totalPages = 1) {
    sessions.value = list
    sessionPage.value = 1
    sessionTotalPages.value = totalPages
    isLoadingMoreSession.value = false
  }

  /** 追加更多会话到列表末尾（向下懒加载时使用） */
  function appendSessions(more: ChatSession[], newPage: number, totalPages: number) {
    const existingIds = new Set(sessions.value.map(s => s.id))
    const deduped = more.filter(s => !existingIds.has(s.id))
    sessions.value = [...sessions.value, ...deduped]
    sessionPage.value = newPage
    sessionTotalPages.value = totalPages
    isLoadingMoreSession.value = false
  }

  /** 是否还有更多会话可加载 */
  function hasMoreSessions(): boolean {
    if (sessionTotalPages.value === -1) return true
    return sessionPage.value < sessionTotalPages.value
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
    removeMessageFrom,
    hasMoreMessages,
    replaceTrailingMessages,
    setSessions,
    appendSessions,
    hasMoreSessions,
  }
})

