import {get} from '../utils/request'

export interface Personality {
  code: string
  name: string
  gender: 'male' | 'female'
  style: string
  emoji: string
  description: string
}

/**
 * 获取所有可用的 AI 人格列表
 */
export function getPersonalityList(): Promise<Personality[]> {
  return get<Personality[]>('/personality/list')
}

