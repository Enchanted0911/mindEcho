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
  id: number
  title: string
  personality: string
  createdTime: string
  updatedTime: string
}

export const useChatStore = defineStore('chat', () => {
  const currentSessionId = ref<number | null>(null)
  const messages = ref<ChatMessage[]>([])
  const sessions = ref<ChatSession[]>([])
  const isLoading = ref(false)
  const isStreaming = ref(false)
  const streamingMessageId = ref<string | null>(null)

  function setCurrentSession(sessionId: number | null) {
    currentSessionId.value = sessionId
  }

  function addMessage(message: ChatMessage) {
    messages.value.push(message)
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

  function clearMessages() {
    messages.value = []
  }

  function setSessions(list: ChatSession[]) {
    sessions.value = list
  }

  function startStreaming(messageId: string) {
    isStreaming.value = true
    streamingMessageId.value = messageId
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
  }
})

