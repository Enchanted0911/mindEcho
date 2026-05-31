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

function formatDate(dateVal: any) {
  if (Array.isArray(dateVal)) {
    const month = String(dateVal[1]).padStart(2, '0')
    const day = String(dateVal[2]).padStart(2, '0')
    return `${month}月${day}日`
  }
  if (typeof dateVal === 'string' && dateVal.includes('-')) {
    const parts = dateVal.split('-')
    return `${parts[1]}月${parts[2]}日`
  }
  return String(dateVal)
}

function getEmotionColor(code: string): string {
  const colorMap: Record<string, string> = {
    happiness: '#f0b429',
    neutral: '#9b87d1',
    sadness: '#4a9eff',
    anxiety: '#ff8c42',
    anger: '#ff5252',
    loneliness: '#7c86c8',
    stress: '#e8637a',
    fear: '#b06adf'
  }
  return colorMap[code] || '#9b87d1'
}
</script>

<template>
  <view class="diary-page">
    <!-- 顶部栏 -->
    <view class="diary-header">
      <view class="header-title-wrap">
        <text class="header-title">情绪日记</text>
        <text class="header-subtitle">记录每一天的心情</text>
      </view>
      <view class="add-btn" @click="openEditor">
        <text class="add-btn-icon">+</text>
        <text class="add-btn-text">记录</text>
      </view>
    </view>

    <!-- 日记列表 -->
    <scroll-view class="diary-list" scroll-y>
      <view v-if="isLoading && diaryStore.diaryList.length === 0" class="state-tip">
        <text class="state-text">加载中…</text>
      </view>

      <view v-else-if="diaryStore.diaryList.length === 0" class="empty-state">
        <view class="empty-icon-wrap">
          <text class="empty-icon">📖</text>
        </view>
        <text class="empty-title">还没有日记</text>
        <text class="empty-desc">记录今天的心情，让 AI 陪你整理情绪</text>
        <view class="empty-action" @click="openEditor">
          <text class="empty-action-text">+ 写第一篇日记</text>
        </view>
      </view>

      <view
        v-for="diary in diaryStore.diaryList"
        :key="diary.id"
        class="diary-card"
        @click="openDiaryDetail(diary)"
      >
        <!-- 左侧情绪色条 -->
        <view
          class="emotion-bar"
          :style="{ background: diary.emotion ? getEmotionColor(diary.emotion) : 'rgba(155, 135, 209, 0.4)' }"
        />

        <view class="diary-card-body">
          <view class="diary-card-top">
            <view class="diary-meta">
              <text class="diary-date">{{ formatDate(diary.diaryDate) }}</text>
              <text v-if="diary.weather" class="diary-weather">{{ diary.weather }}</text>
            </view>
            <view v-if="diary.emotion" class="emotion-badge"
              :style="{ background: getEmotionColor(diary.emotion) + '22', borderColor: getEmotionColor(diary.emotion) + '44' }">
              <text class="emotion-emoji">{{ getEmotionInfo(diary.emotion).emoji }}</text>
              <text class="emotion-label" :style="{ color: getEmotionColor(diary.emotion) }">
                {{ getEmotionInfo(diary.emotion).label }}
              </text>
            </view>
          </view>

          <text v-if="diary.content" class="diary-preview" :numberOfLines="2">
            {{ diary.content }}
          </text>

          <view v-if="diary.aiSummary" class="ai-preview">
            <text class="ai-preview-badge">AI</text>
            <text class="ai-preview-text" :numberOfLines="1">{{ diary.aiSummary }}</text>
          </view>
        </view>
      </view>

      <view style="height: 80rpx" />
    </scroll-view>

    <!-- 日记编辑器弹层 -->
    <view v-if="showEditor" class="sheet-overlay" @click="showEditor = false">
      <view class="sheet-panel" @click.stop>
        <view class="sheet-handle" />
        <view class="sheet-header">
          <text class="sheet-title">记录今天</text>
          <view class="close-btn" @click="showEditor = false">
            <text class="close-icon">✕</text>
          </view>
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
              :style="editEmotion === e.code ? { background: getEmotionColor(e.code) + '22', borderColor: getEmotionColor(e.code) + '66' } : {}"
              @click="editEmotion = e.code"
            >
              <text class="emotion-pill-emoji">{{ e.emoji }}</text>
              <text class="emotion-pill-label"
                :style="editEmotion === e.code ? { color: getEmotionColor(e.code) } : {}">
                {{ e.label }}
              </text>
            </view>
          </view>
        </scroll-view>

        <!-- 天气选择 -->
        <text class="section-label">今天的天气</text>
        <view class="weather-row">
          <view
            v-for="w in WEATHERS"
            :key="w"
            class="weather-pill"
            :class="{ 'weather-selected': editWeather === w }"
            @click="editWeather = w"
          >
            <text class="weather-text">{{ w }}</text>
          </view>
        </view>

        <!-- 内容 -->
        <text class="section-label">写下来…</text>
        <textarea
          v-model="editContent"
          class="diary-textarea"
          placeholder="今天发生了什么？有什么想说的？"
          :placeholder-style="'color: rgba(180,170,200,0.3); font-size: 28rpx'"
          :auto-height="true"
          :max-height="200"
        />

        <view class="save-btn" :class="{ 'btn-loading': isLoading }" @click="saveDiaryEntry">
          <text class="save-btn-text">{{ isLoading ? '保存中…' : '保存日记' }}</text>
        </view>
      </view>
    </view>

    <!-- 日记详情弹层 -->
    <view v-if="showDetail && selectedDiary" class="sheet-overlay" @click="showDetail = false">
      <view class="sheet-panel detail-panel" @click.stop>
        <view class="sheet-handle" />
        <view class="detail-header">
          <view class="detail-meta">
            <text class="detail-date">{{ formatDate(selectedDiary.diaryDate) }}</text>
            <text v-if="selectedDiary.emotion" class="detail-emotion-badge">
              {{ getEmotionInfo(selectedDiary.emotion).emoji }}
              {{ getEmotionInfo(selectedDiary.emotion).label }}
            </text>
          </view>
          <view class="close-btn" @click="showDetail = false">
            <text class="close-icon">✕</text>
          </view>
        </view>

        <scroll-view class="detail-scroll" scroll-y>
          <text v-if="selectedDiary.content" class="detail-text">{{ selectedDiary.content }}</text>

          <view class="ai-section">
            <view class="ai-section-header">
              <view class="ai-section-title-wrap">
                <view class="ai-dot" />
                <text class="ai-section-title">AI 情绪总结</text>
              </view>
              <view
                v-if="!selectedDiary.aiSummary"
                class="get-summary-btn"
                :class="{ 'btn-loading': isGettingSummary }"
                @click="fetchAiSummary"
              >
                <text class="get-summary-text">{{ isGettingSummary ? '生成中…' : '获取总结' }}</text>
              </view>
            </view>
            <text v-if="selectedDiary.aiSummary" class="ai-summary-text">{{ selectedDiary.aiSummary }}</text>
            <text v-else class="ai-placeholder">点击「获取总结」，让 AI 帮你梳理今天的情绪 🌙</text>
          </view>

          <view style="height: 40rpx" />
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
  background: #0d0b1a;
}

/* ── 顶部栏 ── */
.diary-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 96rpx 28rpx 20rpx;
  background: rgba(15, 12, 28, 0.95);
  backdrop-filter: blur(20rpx);
  border-bottom: 1rpx solid rgba(255, 255, 255, 0.06);
  flex-shrink: 0;
}

.header-title-wrap {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.header-title {
  font-size: 40rpx;
  color: rgba(230, 225, 255, 0.95);
  font-weight: 700;
}

.header-subtitle {
  font-size: 22rpx;
  color: rgba(180, 170, 210, 0.45);
}

.add-btn {
  display: flex;
  align-items: center;
  gap: 8rpx;
  background: linear-gradient(135deg, #7c4dff, #5c35cc);
  border-radius: 16rpx;
  padding: 14rpx 24rpx;
  box-shadow: 0 4rpx 16rpx rgba(100, 60, 220, 0.35);
}

.add-btn-icon {
  font-size: 28rpx;
  color: white;
  font-weight: 300;
  line-height: 1;
}

.add-btn-text {
  font-size: 26rpx;
  color: white;
  font-weight: 500;
}

/* ── 列表 ── */
.diary-list {
  flex: 1;
  padding: 24rpx 24rpx 0;
}

/* ── 状态 ── */
.state-tip {
  padding: 80rpx 0;
  text-align: center;
}

.state-text { font-size: 26rpx; color: rgba(180, 170, 210, 0.4); }

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 100rpx 40rpx;
  gap: 16rpx;
}

.empty-icon-wrap {
  width: 100rpx;
  height: 100rpx;
  border-radius: 28rpx;
  background: rgba(120, 80, 200, 0.12);
  border: 1rpx solid rgba(150, 100, 250, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8rpx;
}

.empty-icon { font-size: 48rpx; }
.empty-title { font-size: 32rpx; color: rgba(220, 215, 245, 0.85); font-weight: 600; }
.empty-desc { font-size: 25rpx; color: rgba(180, 170, 210, 0.5); text-align: center; line-height: 1.6; }

.empty-action {
  margin-top: 8rpx;
  background: rgba(120, 80, 200, 0.15);
  border: 1rpx solid rgba(150, 100, 250, 0.3);
  border-radius: 20rpx;
  padding: 14rpx 32rpx;
}

.empty-action-text { font-size: 26rpx; color: rgba(160, 120, 240, 0.9); }

/* ── 日记卡片 ── */
.diary-card {
  display: flex;
  align-items: stretch;
  background: rgba(255, 255, 255, 0.04);
  border: 1rpx solid rgba(255, 255, 255, 0.07);
  border-radius: 20rpx;
  margin-bottom: 16rpx;
  overflow: hidden;
}

.emotion-bar {
  width: 5rpx;
  flex-shrink: 0;
  border-radius: 0;
  opacity: 0.7;
}

.diary-card-body {
  flex: 1;
  padding: 22rpx 22rpx 22rpx 18rpx;
}

.diary-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12rpx;
}

.diary-meta {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.diary-date { font-size: 26rpx; color: rgba(180, 150, 240, 0.85); font-weight: 600; }
.diary-weather { font-size: 22rpx; color: rgba(180, 170, 210, 0.45); }

.emotion-badge {
  display: flex;
  align-items: center;
  gap: 6rpx;
  border: 1rpx solid;
  border-radius: 20rpx;
  padding: 5rpx 14rpx;
}

.emotion-emoji { font-size: 22rpx; }
.emotion-label { font-size: 21rpx; font-weight: 500; }

.diary-preview {
  font-size: 26rpx;
  color: rgba(200, 195, 225, 0.7);
  line-height: 1.65;
  display: block;
}

.ai-preview {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-top: 12rpx;
  background: rgba(120, 80, 200, 0.08);
  border: 1rpx solid rgba(150, 100, 250, 0.15);
  border-radius: 10rpx;
  padding: 8rpx 12rpx;
}

.ai-preview-badge {
  font-size: 18rpx;
  color: rgba(160, 120, 240, 0.8);
  background: rgba(120, 80, 200, 0.2);
  border-radius: 6rpx;
  padding: 3rpx 8rpx;
  font-weight: 600;
  flex-shrink: 0;
}

.ai-preview-text {
  font-size: 21rpx;
  color: rgba(180, 170, 210, 0.6);
  flex: 1;
}

/* ── 弹层通用 ── */
.sheet-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.65);
  backdrop-filter: blur(10rpx);
  display: flex;
  align-items: flex-end;
  z-index: 100;
}

.sheet-panel {
  background: #130e24;
  border-top: 1rpx solid rgba(255, 255, 255, 0.08);
  border-top-left-radius: 36rpx;
  border-top-right-radius: 36rpx;
  padding: 16rpx 28rpx 80rpx;
  width: 100%;
  max-height: 88vh;
  overflow-y: auto;
  box-shadow: 0 -8rpx 40rpx rgba(0, 0, 0, 0.4);
}

.detail-panel { padding-bottom: 60rpx; }

.sheet-handle {
  width: 60rpx;
  height: 6rpx;
  border-radius: 3rpx;
  background: rgba(255, 255, 255, 0.12);
  margin: 0 auto 24rpx;
}

.sheet-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 28rpx;
}

.sheet-title {
  font-size: 34rpx;
  color: rgba(230, 225, 255, 0.95);
  font-weight: 700;
}

.close-btn {
  width: 48rpx;
  height: 48rpx;
  border-radius: 12rpx;
  background: rgba(255, 255, 255, 0.06);
  display: flex;
  align-items: center;
  justify-content: center;
}

.close-icon { font-size: 24rpx; color: rgba(180, 170, 210, 0.5); }

.section-label {
  font-size: 22rpx;
  color: rgba(180, 160, 220, 0.5);
  display: block;
  margin: 22rpx 0 14rpx;
  letter-spacing: 1rpx;
}

/* 情绪选择 */
.emotion-scroll { margin-bottom: 4rpx; }

.emotion-row {
  display: flex;
  gap: 12rpx;
  white-space: nowrap;
  padding-bottom: 4rpx;
}

.emotion-pill {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 7rpx;
  background: rgba(255, 255, 255, 0.04);
  border: 1rpx solid rgba(255, 255, 255, 0.08);
  padding: 14rpx 18rpx;
  border-radius: 16rpx;
  flex-shrink: 0;
}

.emotion-pill-emoji { font-size: 30rpx; }
.emotion-pill-label { font-size: 21rpx; color: rgba(180, 170, 210, 0.6); }

/* 天气选择 */
.weather-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-bottom: 4rpx;
}

.weather-pill {
  background: rgba(255, 255, 255, 0.04);
  border: 1rpx solid rgba(255, 255, 255, 0.08);
  padding: 11rpx 22rpx;
  border-radius: 30rpx;
}

.weather-selected {
  background: rgba(120, 80, 200, 0.15);
  border-color: rgba(150, 100, 250, 0.4);
}

.weather-text { font-size: 24rpx; color: rgba(200, 190, 230, 0.75); }

/* 文本区域 */
.diary-textarea {
  width: 100%;
  min-height: 160rpx;
  background: rgba(255, 255, 255, 0.04);
  border: 1rpx solid rgba(255, 255, 255, 0.08);
  border-radius: 16rpx;
  padding: 18rpx 20rpx;
  font-size: 27rpx;
  color: rgba(225, 218, 245, 0.9);
  line-height: 1.7;
  margin-top: 4rpx;
  box-sizing: border-box;
}

/* 保存按钮 */
.save-btn {
  width: 100%;
  height: 92rpx;
  background: linear-gradient(135deg, #7c4dff, #5c35cc);
  border-radius: 18rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 28rpx;
  box-shadow: 0 6rpx 24rpx rgba(100, 60, 220, 0.35);
}

.save-btn.btn-loading { opacity: 0.65; }

.save-btn-text {
  font-size: 30rpx;
  color: white;
  font-weight: 600;
  letter-spacing: 2rpx;
}

/* 详情 */
.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
}

.detail-meta {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.detail-date { font-size: 30rpx; color: rgba(180, 150, 240, 0.9); font-weight: 600; }
.detail-emotion-badge { font-size: 26rpx; color: rgba(200, 190, 230, 0.65); }

.detail-scroll { max-height: 60vh; }

.detail-text {
  font-size: 28rpx;
  color: rgba(220, 215, 240, 0.88);
  line-height: 2;
  white-space: pre-wrap;
  display: block;
  margin-bottom: 32rpx;
}

/* AI 总结区域 */
.ai-section {
  border-top: 1rpx solid rgba(255, 255, 255, 0.07);
  padding-top: 24rpx;
}

.ai-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18rpx;
}

.ai-section-title-wrap {
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.ai-dot {
  width: 8rpx;
  height: 8rpx;
  border-radius: 50%;
  background: rgba(160, 120, 240, 0.8);
  box-shadow: 0 0 8rpx rgba(160, 120, 240, 0.6);
}

.ai-section-title { font-size: 26rpx; color: rgba(180, 150, 240, 0.85); font-weight: 600; }

.get-summary-btn {
  background: rgba(120, 80, 200, 0.15);
  border: 1rpx solid rgba(150, 100, 250, 0.3);
  border-radius: 16rpx;
  padding: 10rpx 22rpx;
}

.get-summary-text { font-size: 24rpx; color: rgba(160, 120, 240, 0.9); }

.ai-summary-text {
  font-size: 26rpx;
  color: rgba(210, 200, 235, 0.85);
  line-height: 1.85;
}

.ai-placeholder { font-size: 24rpx; color: rgba(180, 170, 210, 0.35); line-height: 1.6; }
</style>

