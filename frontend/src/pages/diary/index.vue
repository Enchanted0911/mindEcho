<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {useDiaryStore} from '../../store/diary'
import {getAiSummary, getDiaryList, saveDiary} from '../../api/diary'
import {getEmotionInfo} from '../../utils/emotion'

const diaryStore = useDiaryStore()

const isLoading = ref(false)
const showEditor = ref(false)
const showDetail = ref(false)
const selectedDiary = ref<any>(null)
const isGettingSummary = ref(false)

// 编辑器状态
const editEmotion = ref('')
const editIntensity = ref(5)
const editContent = ref('')
const editWeather = ref('')

const EMOTIONS = [
  { code: 'happiness', label: '快乐', emoji: '😊' },
  { code: 'neutral', label: '平静', emoji: '😐' },
  { code: 'sadness', label: '悲伤', emoji: '😢' },
  { code: 'anxiety', label: '焦虑', emoji: '😰' },
  { code: 'anger', label: '愤怒', emoji: '😤' },
  { code: 'loneliness', label: '孤独', emoji: '😞' },
  { code: 'stress', label: '压力', emoji: '😩' },
  { code: 'fear', label: '恐惧', emoji: '😨' }
]

const WEATHERS = ['☀️ 晴', '🌤 多云', '🌧 雨', '❄️ 雪', '🌙 夜']

onMounted(() => {
  loadDiaryList()
})

async function loadDiaryList() {
  isLoading.value = true
  try {
    const result = await getDiaryList()
    diaryStore.setDiaryList(result.records || [])
  } catch (e) {
    console.error(e)
  } finally {
    isLoading.value = false
  }
}

function openEditor() {
  editEmotion.value = ''
  editIntensity.value = 5
  editContent.value = ''
  editWeather.value = ''
  showEditor.value = true
}

async function saveDiaryEntry() {
  if (!editContent.value.trim() && !editEmotion.value) {
    uni.showToast({ title: '请输入内容或选择情绪', icon: 'none' })
    return
  }

  isLoading.value = true
  try {
    const diary = await saveDiary({
      emotion: editEmotion.value || undefined,
      emotionIntensity: editIntensity.value,
      content: editContent.value || undefined,
      weather: editWeather.value || undefined
    })
    diaryStore.updateDiary(diary)
    showEditor.value = false
    uni.showToast({ title: '日记已保存', icon: 'success' })
  } catch (e) {
    uni.showToast({ title: '保存失败', icon: 'none' })
  } finally {
    isLoading.value = false
  }
}

function openDiaryDetail(diary: any) {
  selectedDiary.value = diary
  showDetail.value = true
}

async function fetchAiSummary() {
  if (!selectedDiary.value || isGettingSummary.value) return
  isGettingSummary.value = true
  try {
    const updated = await getAiSummary(selectedDiary.value.id)
    selectedDiary.value = updated
    diaryStore.updateDiary(updated)
  } catch (e) {
    uni.showToast({ title: '获取总结失败', icon: 'none' })
  } finally {
    isGettingSummary.value = false
  }
}

function formatDate(dateStr: string) {
  const parts = dateStr.split('-')
  return `${parts[1]}月${parts[2]}日`
}
</script>

<template>
  <view class="diary-page">
    <!-- 顶部 -->
    <view class="diary-header">
      <text class="header-title">情绪日记</text>
      <view class="add-btn" @click="openEditor">
        <text>+ 记录今天</text>
      </view>
    </view>

    <!-- 日记列表 -->
    <scroll-view class="diary-list" scroll-y>
      <view v-if="isLoading && diaryStore.diaryList.length === 0" class="loading-tip">
        <text>加载中...</text>
      </view>

      <view v-else-if="diaryStore.diaryList.length === 0" class="empty-tip">
        <text class="empty-emoji">📖</text>
        <text class="empty-text">还没有日记</text>
        <text class="empty-desc">记录今天的心情，让 AI 陪你整理情绪</text>
      </view>

      <view
        v-for="diary in diaryStore.diaryList"
        :key="diary.id"
        class="diary-card"
        @click="openDiaryDetail(diary)"
      >
        <view class="diary-card-header">
          <view class="diary-date-area">
            <text class="diary-date">{{ formatDate(diary.diaryDate) }}</text>
            <text v-if="diary.weather" class="diary-weather">{{ diary.weather }}</text>
          </view>
          <view v-if="diary.emotion" class="emotion-badge">
            <text>{{ getEmotionInfo(diary.emotion).emoji }}</text>
            <text class="emotion-label">{{ getEmotionInfo(diary.emotion).label }}</text>
          </view>
        </view>

        <text v-if="diary.content" class="diary-preview" :numberOfLines="2">
          {{ diary.content }}
        </text>

        <view v-if="diary.aiSummary" class="ai-summary-preview">
          <text class="ai-badge">✨ AI 总结</text>
          <text class="ai-summary-text" :numberOfLines="1">{{ diary.aiSummary }}</text>
        </view>
      </view>
    </scroll-view>

    <!-- 日记编辑器 -->
    <view v-if="showEditor" class="editor-overlay" @click.self="showEditor = false">
      <view class="editor-panel">
        <view class="editor-header">
          <text class="editor-title">记录今天</text>
          <text class="editor-close" @click="showEditor = false">✕</text>
        </view>

        <!-- 情绪选择 -->
        <text class="section-label">今天的情绪</text>
        <scroll-view class="emotion-scroll" scroll-x>
          <view class="emotion-row">
            <view
              v-for="e in EMOTIONS"
              :key="e.code"
              class="emotion-pill"
              :class="{ 'emotion-selected': editEmotion === e.code }"
              @click="editEmotion = e.code"
            >
              <text class="emotion-pill-emoji">{{ e.emoji }}</text>
              <text class="emotion-pill-label">{{ e.label }}</text>
            </view>
          </view>
        </scroll-view>

        <!-- 天气 -->
        <text class="section-label">今天的天气</text>
        <view class="weather-row">
          <text
            v-for="w in WEATHERS"
            :key="w"
            class="weather-pill"
            :class="{ 'weather-selected': editWeather === w }"
            @click="editWeather = w"
          >{{ w }}</text>
        </view>

        <!-- 日记内容 -->
        <text class="section-label">写下来...</text>
        <textarea
          v-model="editContent"
          class="diary-textarea"
          placeholder="今天发生了什么？有什么想说的？"
          :placeholder-style="'color: #5a5070; font-size: 28rpx'"
          :auto-height="true"
          :max-height="200"
        />

        <!-- 保存 -->
        <button class="save-btn" :loading="isLoading" @click="saveDiaryEntry">
          保存日记
        </button>
      </view>
    </view>

    <!-- 日记详情 -->
    <view v-if="showDetail && selectedDiary" class="detail-overlay" @click.self="showDetail = false">
      <view class="detail-panel">
        <view class="detail-header">
          <view class="detail-date-emotion">
            <text class="detail-date">{{ formatDate(selectedDiary.diaryDate) }}</text>
            <text v-if="selectedDiary.emotion" class="detail-emotion">
              {{ getEmotionInfo(selectedDiary.emotion).emoji }} {{ getEmotionInfo(selectedDiary.emotion).label }}
            </text>
          </view>
          <text @click="showDetail = false">✕</text>
        </view>

        <scroll-view class="detail-content" scroll-y>
          <text v-if="selectedDiary.content" class="detail-text">{{ selectedDiary.content }}</text>

          <view class="ai-section">
            <view class="ai-section-header">
              <text class="ai-section-title">✨ AI 情绪总结</text>
              <text
                v-if="!selectedDiary.aiSummary"
                class="get-summary-btn"
                @click="fetchAiSummary"
              >{{ isGettingSummary ? '生成中...' : '获取总结' }}</text>
            </view>
            <text v-if="selectedDiary.aiSummary" class="ai-summary-full">
              {{ selectedDiary.aiSummary }}
            </text>
            <text v-else class="ai-summary-placeholder">
              点击「获取总结」，让 AI 帮你梳理今天的情绪 🌙
            </text>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<style>
.diary-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #0f0f1a;
}

.diary-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 100rpx 32rpx 24rpx;
  background: rgba(15, 15, 26, 0.95);
}

.header-title {
  font-size: 40rpx;
  color: #e8d5ff;
  font-weight: bold;
}

.add-btn {
  background: linear-gradient(135deg, #b89ee8, #8b6fd1);
  color: white;
  padding: 16rpx 32rpx;
  border-radius: 40rpx;
  font-size: 28rpx;
}

.diary-list {
  flex: 1;
  padding: 24rpx;
}

.loading-tip, .empty-tip {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 120rpx 0;
  gap: 16rpx;
}

.empty-emoji { font-size: 80rpx; }
.empty-text { font-size: 34rpx; color: #e8d5ff; }
.empty-desc { font-size: 26rpx; color: #5a5070; text-align: center; }

.diary-card {
  background: rgba(255, 255, 255, 0.04);
  border: 1rpx solid rgba(184, 158, 232, 0.1);
  border-radius: 24rpx;
  padding: 28rpx;
  margin-bottom: 20rpx;
}

.diary-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16rpx;
}

.diary-date { font-size: 28rpx; color: #b89ee8; font-weight: 600; }
.diary-weather { font-size: 26rpx; color: #7a6b9a; margin-left: 12rpx; }

.emotion-badge {
  display: flex;
  align-items: center;
  gap: 8rpx;
  background: rgba(184, 158, 232, 0.12);
  padding: 8rpx 20rpx;
  border-radius: 30rpx;
}

.emotion-label { font-size: 24rpx; color: #b89ee8; }

.diary-preview { font-size: 28rpx; color: #9a8aaa; line-height: 1.6; }

.ai-summary-preview {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 16rpx;
  background: rgba(184, 158, 232, 0.06);
  padding: 12rpx 16rpx;
  border-radius: 12rpx;
}

.ai-badge { font-size: 24rpx; color: #b89ee8; white-space: nowrap; }
.ai-summary-text { font-size: 24rpx; color: #7a6b9a; flex: 1; }

/* 编辑器 */
.editor-overlay, .detail-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0,0,0,0.6);
  display: flex;
  align-items: flex-end;
  z-index: 100;
}

.editor-panel, .detail-panel {
  background: #1a1a2e;
  border-top-left-radius: 40rpx;
  border-top-right-radius: 40rpx;
  padding: 40rpx 32rpx 80rpx;
  width: 100%;
  max-height: 85vh;
  overflow-y: auto;
}

.editor-header, .detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32rpx;
}

.editor-title, .detail-date-emotion { }

.editor-title {
  font-size: 36rpx;
  color: #e8d5ff;
  font-weight: 600;
}

.editor-close, .detail-panel text:last-child {
  font-size: 36rpx;
  color: #7a6b9a;
}

.section-label {
  font-size: 26rpx;
  color: #7a6b9a;
  display: block;
  margin: 24rpx 0 16rpx;
}

.emotion-scroll { margin-bottom: 8rpx; }

.emotion-row {
  display: flex;
  gap: 16rpx;
  white-space: nowrap;
  padding-bottom: 8rpx;
}

.emotion-pill {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  background: rgba(255,255,255,0.05);
  border: 1rpx solid rgba(184,158,232,0.1);
  padding: 16rpx 20rpx;
  border-radius: 16rpx;
  flex-shrink: 0;
}

.emotion-selected {
  background: rgba(184,158,232,0.2);
  border-color: #b89ee8;
}

.emotion-pill-emoji { font-size: 36rpx; }
.emotion-pill-label { font-size: 22rpx; color: #b89ee8; }

.weather-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-bottom: 8rpx;
}

.weather-pill {
  background: rgba(255,255,255,0.05);
  border: 1rpx solid rgba(184,158,232,0.1);
  padding: 12rpx 24rpx;
  border-radius: 30rpx;
  font-size: 26rpx;
  color: #9a8aaa;
}

.weather-selected {
  background: rgba(184,158,232,0.2);
  border-color: #b89ee8;
  color: #e8d5ff;
}

.diary-textarea {
  width: 100%;
  min-height: 160rpx;
  background: rgba(255,255,255,0.04);
  border: 1rpx solid rgba(184,158,232,0.12);
  border-radius: 16rpx;
  padding: 20rpx;
  font-size: 28rpx;
  color: #e8d5ff;
  line-height: 1.6;
  margin-top: 8rpx;
}

.save-btn {
  width: 100%;
  height: 96rpx;
  background: linear-gradient(135deg, #b89ee8, #8b6fd1);
  color: white;
  font-size: 32rpx;
  border-radius: 48rpx;
  margin-top: 32rpx;
  border: none;
}

/* 详情 */
.detail-date { font-size: 32rpx; color: #b89ee8; font-weight: 600; }
.detail-emotion { font-size: 28rpx; color: #9a8aaa; margin-left: 16rpx; }
.detail-content { max-height: 60vh; }
.detail-text { font-size: 30rpx; color: #c4a8f0; line-height: 1.8; white-space: pre-wrap; }

.ai-section { margin-top: 40rpx; padding-top: 32rpx; border-top: 1rpx solid rgba(255,255,255,0.08); }
.ai-section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20rpx; }
.ai-section-title { font-size: 30rpx; color: #b89ee8; font-weight: 600; }
.get-summary-btn { font-size: 26rpx; color: #8b6fd1; background: rgba(139,111,209,0.1); padding: 10rpx 24rpx; border-radius: 30rpx; }
.ai-summary-full { font-size: 28rpx; color: #c4a8f0; line-height: 1.8; }
.ai-summary-placeholder { font-size: 26rpx; color: #5a5070; }
</style>

