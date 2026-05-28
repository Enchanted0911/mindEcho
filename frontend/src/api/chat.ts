import {del, get} from '../utils/request'

export interface ChatSession {
  id: string   // 后端 Long 序列化为字符串，防止 JS 精度丢失
  title: string
  personality: string
  createdTime: string
  updatedTime: string
}

export interface ChatMessage {
  id: string   // 后端 Long 序列化为字符串，防止 JS 精度丢失
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
export function getMessageList(sessionId: string): Promise<ChatMessage[]> {
  return get<ChatMessage[]>(`/chat/sessions/${sessionId}/messages`)
}

/**
 * 删除会话
 */
export function deleteSession(sessionId: string): Promise<void> {
  return del<void>(`/chat/sessions/${sessionId}`)
}

/**
 * 发送消息（SSE 流式）
 * 微信小程序使用 RequestTask.onChunkReceived 接收分块数据
 */
export function createSseConnection(
  sessionId: string | null,
  message: string,
  onChunk: (text: string) => void,
  onDone: () => void,
  onError: (err: any) => void
): void {
  const token = uni.getStorageSync('token')
  const baseUrl = 'http://localhost:8080/api'

  // uni.request 返回 RequestTask，通过它注册 onChunkReceived
  const requestTask = uni.request({
    url: `${baseUrl}/chat/send`,
    method: 'POST',
    data: { sessionId, message },
    header: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
      'Accept': 'text/event-stream'
    },
    enableChunked: true,
    responseType: 'arraybuffer',
    success: () => {
      onDone()
    },
    fail: (err) => {
      onError(err)
    }
  })

  // 通过 RequestTask 实例监听分块数据
  requestTask.onChunkReceived((response: any) => {
    try {
      const decoder = new TextDecoder('utf-8')
      const text = decoder.decode(response.data)

      // 解析 SSE 格式：每行可能是 "data: xxx" 或 "event:done"
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
        } else if (line.startsWith('event:done') || line.includes('[DONE]')) {
          onDone()
        }
      }
    } catch (e) {
      console.error('Chunk parse error:', e)
    }
  })
}

