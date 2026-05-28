import {get, post} from '../utils/request'
import type {DiaryEntry} from '../store/diary'

export interface SaveDiaryRequest {
  diaryDate?: string
  emotion?: string
  emotionIntensity?: number
  content?: string
  weather?: string
}

/**
 * 保存日记
 */
export function saveDiary(data: SaveDiaryRequest): Promise<DiaryEntry> {
  return post<DiaryEntry>('/diary/save', data)
}

/**
 * 获取日记列表
 */
export function getDiaryList(page = 1, size = 20) {
  return get<{ records: DiaryEntry[]; total: number }>(`/diary/list?page=${page}&size=${size}`)
}

/**
 * 获取某日日记
 */
export function getDiaryByDate(date: string): Promise<DiaryEntry | null> {
  return get<DiaryEntry>(`/diary/date/${date}`)
}

/**
 * 获取 AI 总结
 */
export function getAiSummary(id: string): Promise<DiaryEntry> {
  return get<DiaryEntry>(`/diary/${id}/ai-summary`)
}

