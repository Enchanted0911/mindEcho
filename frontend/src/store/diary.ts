import {defineStore} from 'pinia'
import {ref} from 'vue'

export interface DiaryEntry {
  id: number
  diaryDate: string
  emotion: string | null
  emotionIntensity: number | null
  content: string | null
  aiSummary: string | null
  weather: string | null
  createdTime: string
  updatedTime: string
}

export const useDiaryStore = defineStore('diary', () => {
  const diaryList = ref<DiaryEntry[]>([])
  const currentDiary = ref<DiaryEntry | null>(null)

  function setDiaryList(list: DiaryEntry[]) {
    diaryList.value = list
  }

  function setCurrentDiary(diary: DiaryEntry | null) {
    currentDiary.value = diary
  }

  function updateDiary(diary: DiaryEntry) {
    const index = diaryList.value.findIndex(d => d.id === diary.id)
    if (index !== -1) {
      diaryList.value[index] = diary
    } else {
      diaryList.value.unshift(diary)
    }
  }

  return {
    diaryList,
    currentDiary,
    setDiaryList,
    setCurrentDiary,
    updateDiary
  }
})

