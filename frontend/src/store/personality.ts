import {defineStore} from 'pinia'
import {computed, ref} from 'vue'
import {getPersonalityList, type Personality} from '../api/personality'

/**
 * 人格 Store
 * 全局缓存人格列表，避免每次进入页面都重新请求。
 * 首次调用 ensureLoaded() 时发起网络请求，之后直接复用内存缓存。
 */
export const usePersonalityStore = defineStore('personality', () => {
  const list = ref<Personality[]>([])
  const loaded = ref(false)
  const loading = ref(false)

  const femaleList = computed(() => list.value.filter(p => p.gender === 'female'))
  const maleList = computed(() => list.value.filter(p => p.gender === 'male'))

  /** 根据 code 查找人格，找不到返回 null */
  function findByCode(code: string): Personality | null {
    return list.value.find(p => p.code === code) ?? null
  }

  /**
   * 确保人格列表已加载（幂等）。
   * 已加载或正在加载时直接返回，避免重复请求。
   */
  async function ensureLoaded() {
    if (loaded.value || loading.value) return
    loading.value = true
    try {
      list.value = await getPersonalityList()
      loaded.value = true
    } catch (e) {
      console.error('Load personalities failed:', e)
    } finally {
      loading.value = false
    }
  }

  /** 强制刷新（如后台修改了人格配置后调用） */
  async function refresh() {
    loaded.value = false
    await ensureLoaded()
  }

  return {
    list,
    loaded,
    loading,
    femaleList,
    maleList,
    findByCode,
    ensureLoaded,
    refresh,
  }
})

