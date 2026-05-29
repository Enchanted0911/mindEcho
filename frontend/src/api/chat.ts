import {BASE_URL, del, get, post} from '../utils/request'

export interface ChatSession {
  id: string   // 后端 Long 序列化为字符串，防止 JS 精度丢失
  title: string
  aiPersonality: string
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

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

/**
 * 获取会话列表
 */
export function getSessionList(page = 1, size = 20): Promise<PageResult<ChatSession>> {
  return get<PageResult<ChatSession>>(`/chat/sessions?page=${page}&size=${size}`)
}

/**
 * 分页获取会话消息列表（按时间倒序，第 1 页是最新消息）
 */
export function getMessageList(sessionId: string, page = 1, size = 20): Promise<PageResult<ChatMessage>> {
  return get<PageResult<ChatMessage>>(`/chat/sessions/${sessionId}/messages?page=${page}&size=${size}`)
}

/**
 * 删除会话
 */
export function deleteSession(sessionId: string): Promise<void> {
  return del<void>(`/chat/sessions/${sessionId}`)
}

/**
 * 停止当前流式输出
 */
export function stopStreaming(): Promise<void> {
  return post<void>('/chat/stop')
}

/**
 * 编辑消息并重新发送（SSE 流式）
 * 后端会删除该消息之后的所有消息，更新内容后重新流式回复
 */
export function createEditSseConnection(
  messageId: string,
  sessionId: string,
  message: string,
  onChunk: (text: string) => void,
  onDone: () => void,
  onError: (err: any) => void
): UniApp.RequestTask {
  return createSseRequest('/chat/edit', { messageId, sessionId, message }, onChunk, onDone, onError)
}

/**
 * 发送消息（SSE 流式）
 * 微信小程序使用 RequestTask.onChunkReceived 接收分块数据
 * 返回 RequestTask，外部可调用 .abort() 中止连接
 */
export function createSseConnection(
  sessionId: string | null,
  message: string,
  onChunk: (text: string) => void,
  onDone: () => void,
  onError: (err: any) => void
): UniApp.RequestTask {
  return createSseRequest('/chat/send', { sessionId, message }, onChunk, onDone, onError)
}

/**
 * 通用 SSE 请求（内部复用）
 */
function createSseRequest(
  path: string,
  data: Record<string, any>,
  onChunk: (text: string) => void,
  onDone: () => void,
  onError: (err: any) => void
): UniApp.RequestTask {
  const token = uni.getStorageSync('token')

  // ⚠️ TextDecoder 必须在整个连接生命周期内共享同一个实例，并开启 stream:true。
  // 中文字符在 UTF-8 下占 3 个字节，网络分包时可能被截断到相邻 chunk 边界。
  // 若每次 onChunkReceived 都 new TextDecoder()，则跨 chunk 截断的字节会被强制
  // 解码为替换字符（U+FFFD），导致字符位置错乱。stream:true 告知 decoder
  // "数据尚未结束，保留末尾不完整字节等待下一个 chunk 拼合"。
  const decoder = new TextDecoder('utf-8', { fatal: false })

  // ⚠️ SSE 帧缓冲区：SSE 协议以 \n\n 作为帧分隔符。
  // 网络分包时，一个 TCP/HTTP chunk 不保证恰好对应一个完整的 SSE 帧，存在两种情况：
  //   - 拆包：一帧被切成多个 chunk（如 "data: 你好" 和 "世界\n\n" 分两次到达）
  //   - 粘包：多帧合并在一个 chunk 里（如 "data: 你\n\ndata: 好\n\n"）
  // 若直接对每个 chunk 做 split('\n') 处理，拆包场景下截断行会被提前投递，
  // 下一个 chunk 里的续行因不以 "data:" 开头而被丢弃，造成丢字符。
  // 解决方案：维护跨 chunk 的字符串缓冲区，只在遇到完整帧分隔符 \n\n 时才处理。
  let sseBuffer = ''

  // 标记 onDone 是否已触发，防止 success 回调与 [DONE] 事件重复调用
  let doneTriggered = false
  const triggerDone = () => {
    if (!doneTriggered) {
      doneTriggered = true
      onDone()
    }
  }

  /**
   * 处理缓冲区中所有已完整的 SSE 帧（以 \n\n 分隔）
   * 处理完成后将未完整的帧残留保留在 sseBuffer 中等待后续数据
   */
  const flushBuffer = () => {
    // 以 \n\n 为帧边界切割，最后一段可能是不完整帧，保留回缓冲区
    const frames = sseBuffer.split('\n\n')
    // 最后一个元素：若末尾有 \n\n 则为空字符串（完整），否则为截断帧
    sseBuffer = frames.pop() ?? ''

    for (const frame of frames) {
      // 一帧可能包含多行（event: / data: / id: 等），逐行解析
      const lines = frame.split('\n')
      for (const line of lines) {
        if (line.startsWith('data:')) {
          const data = line.slice(5).trim()
          if (data === '[DONE]') {
            triggerDone()
            return
          }
          if (data) {
            onChunk(data)
          }
        } else if (line.startsWith('event:')) {
          // 精确匹配 "event: done"，避免误匹配含 "done" 的其他事件名（如 "event: not-done"）
          const eventName = line.slice(6).trim()
          if (eventName === 'done') {
            triggerDone()
          }
        }
      }
    }
  }

  // uni.request 返回 RequestTask，通过它注册 onChunkReceived
  const requestTask = uni.request({
    url: `${BASE_URL}${path}`,
    method: 'POST',
    data,
    header: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
      'Accept': 'text/event-stream'
    },
    enableChunked: true,
    responseType: 'arraybuffer',
    success: () => {
      // 请求结束：flush decoder 剩余字节（不传 stream:true，触发最终解码），再处理缓冲区残留帧，最后触发 done
      // 注意：decode() 不带参数或传空 Uint8Array（不带 stream:true）会 flush 内部缓冲区
      const remaining = decoder.decode(new Uint8Array(0))
      if (remaining) {
        sseBuffer += remaining
        flushBuffer()
      }
      triggerDone()
    },
    fail: (err) => {
      onError(err)
    }
  })

  // 通过 RequestTask 实例监听分块数据
  requestTask.onChunkReceived((response: any) => {
    try {
      // stream:true：保留末尾不完整的多字节序列，等待下一个 chunk 补全
      const text = decoder.decode(response.data, { stream: true })
      // 追加到帧缓冲区，再尝试提取完整帧
      sseBuffer += text
      flushBuffer()
    } catch (e) {
      console.error('Chunk parse error:', e)
    }
  })

  return requestTask
}

