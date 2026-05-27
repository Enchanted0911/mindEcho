import {del, get} from '../utils/request'

export interface ChatSession {
  id: number
  title: string
  personality: string
  createdTime: string
  updatedTime: string
}

export interface ChatMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  emotion?: string
  riskLevel?: string
  createdTime: string
}

/**
 * 获取会话列表
 */
export function getSessionList(page = 1, size = 20) {
  return get<{ records: ChatSession[]; total: number }>(`/chat/sessions?page=${page}&size=${size}`)
}

/**
 * 获取会话消息列表
 */
export function getMessageList(sessionId: number): Promise<ChatMessage[]> {
  return get<ChatMessage[]>(`/chat/sessions/${sessionId}/messages`)
}

/**
 * 删除会话
 */
export function deleteSession(sessionId: number): Promise<void> {
  return del<void>(`/chat/sessions/${sessionId}`)
}

/**
 * 发送消息（SSE 流式，返回 EventSource）
 * 微信小程序没有 EventSource，使用 uni.request 分块读取
 */
export function createSseConnection(
  sessionId: number | null,
  message: string,
  onChunk: (text: string) => void,
  onDone: () => void,
  onError: (err: any) => void
): void {
  const token = uni.getStorageSync('token')
  const baseUrl = 'http://localhost:8080/api'

  // 微信小程序使用 uni.request 模拟 SSE
  uni.request({
    url: `${baseUrl}/chat/send`,
    method: 'POST',
    data: { sessionId, message },
    header: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
      'Accept': 'text/event-stream'
    },
    enableChunked: true,  // 开启流式接收
    success: () => {
      onDone()
    },
    fail: (err) => {
      onError(err)
    }
  })

  // 监听分块数据
  // @ts-ignore
  uni.onChunkReceived((response: any) => {
    const decoder = new TextDecoder('utf-8')
    const text = decoder.decode(response.data)

    // 解析 SSE 格式
    const lines = text.split('\n')
    for (const line of lines) {
      if (line.startsWith('data:')) {
        const data = line.slice(5).trim()
        if (data === '[DONE]') {
          onDone()
          return
        }
        if (data) {
          onChunk(data)
        }
      } else if (line.startsWith('event:done')) {
        onDone()
      }
    }
  })
}

