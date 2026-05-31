<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {getProfile} from '../../api/auth'
import {createOrder, getOrder, wxPay} from '../../api/payment'
import {useUserStore} from '../../store/user'

const userStore = useUserStore()
const isLoading = ref(false)
const selectedPlan = ref('quarterly')

const VIP_PLANS = [
  {
    type: 'monthly',
    label: '月度',
    price: '9.9',
    originalPrice: '29.9',
    duration: '1个月',
    badge: '',
    popular: false,
    perDay: '0.33'
  },
  {
    type: 'quarterly',
    label: '季度',
    price: '25.9',
    originalPrice: '79.9',
    duration: '3个月',
    badge: '推荐',
    popular: true,
    perDay: '0.29'
  },
  {
    type: 'yearly',
    label: '年度',
    price: '88',
    originalPrice: '299',
    duration: '12个月',
    badge: '最划算',
    popular: false,
    perDay: '0.24'
  }
]

const VIP_BENEFITS = [
  { icon: '💬', title: '无限对话', desc: '无频率限制，随时倾诉' },
  { icon: '🧠', title: '长期记忆', desc: 'AI 记住你的故事' },
  { icon: '✨', title: '全部人格', desc: '解锁所有角色切换' },
  { icon: '🎯', title: '深度陪伴', desc: '精准情绪理解回复' }
]

onMounted(async () => {
  if (!userStore.isLoggedIn) return
  try {
    const latestInfo = await getProfile()
    userStore.setUserInfo({
      id: String(latestInfo.id),
      nickname: latestInfo.nickname,
      avatar: latestInfo.avatar,
      isVip: latestInfo.isVip,
      vipExpireTime: latestInfo.vipExpireTime ?? null,
      aiPersonality: latestInfo.aiPersonality,
      birthCity: latestInfo.birthCity ?? null,
      birthLat: latestInfo.birthLat ?? null,
      birthLng: latestInfo.birthLng ?? null,
      birthTime: latestInfo.birthTime ?? null
    })
  } catch (_) {
    if (!userStore.userInfo) {
      try {
        const cached = uni.getStorageSync('userInfo')
        if (cached) {
          userStore.setUserInfo(JSON.parse(cached))
        }
      } catch (__) { /* ignore */ }
    }
  }
})

function pollOrderStatus(orderNo: string, maxAttempts = 10, intervalMs = 2000): Promise<void> {
  return new Promise((resolve, reject) => {
    let attempts = 0
    const poll = async () => {
      attempts++
      try {
        const order = await getOrder(orderNo)
        if (order.status === 'paid') { resolve(); return }
        if (attempts >= maxAttempts) { reject(new Error('支付确认超时，请稍后在订单记录中查看')); return }
        setTimeout(poll, intervalMs)
      } catch (e) { reject(e) }
    }
    setTimeout(poll, intervalMs)
  })
}

function refreshUserVipStatus(expireTime?: string) {
  if (!userStore.userInfo) return
  userStore.setUserInfo({ ...userStore.userInfo, isVip: true, vipExpireTime: expireTime ?? null })
}

async function handleBuy() {
  if (isLoading.value) return
  isLoading.value = true
  try {
    const order = await createOrder(selectedPlan.value)
    if (order.wxPayParams) {
      await wxPay(order.wxPayParams)
      uni.showToast({ title: '支付处理中...', icon: 'loading', duration: 15000 })
      try {
        await pollOrderStatus(order.orderNo)
        const paidOrder = await getOrder(order.orderNo)
        refreshUserVipStatus(paidOrder.expireTime)
        uni.showToast({ title: '会员已激活！', icon: 'success' })
      } catch (_pollErr: any) {
        uni.showToast({ title: '支付已提交，稍后刷新查看会员状态', icon: 'none', duration: 3000 })
      }
    } else {
      uni.showToast({ title: '订单创建成功，等待支付接入', icon: 'none' })
    }
  } catch (e: any) {
    if (e.errMsg?.includes('cancel') || e.errMsg?.includes('用户取消')) {
      uni.showToast({ title: '已取消支付', icon: 'none' })
    } else {
      uni.showToast({ title: e.message || '支付失败，请重试', icon: 'none' })
    }
  } finally {
    isLoading.value = false
  }
}

const currentPlan = () => VIP_PLANS.find(p => p.type === selectedPlan.value)
</script>

<template>
  <view class="vip-page">
    <!-- 顶部导航 -->
    <view class="vip-header">
      <view class="back-btn" @click="uni.navigateBack()">
        <text class="back-icon">‹</text>
      </view>
      <text class="header-title">开通会员</text>
      <view style="width: 48rpx" />
    </view>

    <scroll-view class="vip-scroll" scroll-y>
      <!-- Hero 区域 -->
      <view class="hero-area">
        <view class="hero-bg-glow" />
        <view class="hero-icon-wrap">
          <text class="hero-icon">👑</text>
          <view class="hero-ring r1" />
          <view class="hero-ring r2" />
        </view>
        <text class="hero-title">心屿会员</text>
        <text class="hero-subtitle">解锁完整 AI 情绪陪伴体验</text>
        <view v-if="userStore.isVip" class="vip-active-badge">
          <text class="vip-active-dot" />
          <text class="vip-active-text">会员已激活</text>
        </view>
      </view>

      <!-- 权益列表 -->
      <view class="benefits-area">
        <text class="area-label">会员专属权益</text>
        <view class="benefits-grid">
          <view v-for="b in VIP_BENEFITS" :key="b.title" class="benefit-card">
            <text class="benefit-icon">{{ b.icon }}</text>
            <text class="benefit-title">{{ b.title }}</text>
            <text class="benefit-desc">{{ b.desc }}</text>
          </view>
        </view>
      </view>

      <!-- 套餐选择 -->
      <view class="plans-area">
        <text class="area-label">选择套餐</text>
        <view class="plans-list">
          <view
            v-for="plan in VIP_PLANS"
            :key="plan.type"
            class="plan-card"
            :class="{ 'plan-selected': selectedPlan === plan.type }"
            @click="selectedPlan = plan.type"
          >
            <!-- 选中指示器 -->
            <view class="plan-radio" :class="{ 'radio-selected': selectedPlan === plan.type }">
              <view v-if="selectedPlan === plan.type" class="radio-dot" />
            </view>

            <view class="plan-main">
              <view class="plan-name-row">
                <text class="plan-label">{{ plan.label }}</text>
                <view v-if="plan.badge" class="plan-badge"
                  :class="{ 'badge-recommend': plan.popular }">
                  <text class="plan-badge-text">{{ plan.badge }}</text>
                </view>
              </view>
              <text class="plan-duration">{{ plan.duration }}</text>
            </view>

            <view class="plan-price-block">
              <view class="plan-price-row">
                <text class="plan-price-unit">¥</text>
                <text class="plan-price">{{ plan.price }}</text>
              </view>
              <text class="plan-original">¥{{ plan.originalPrice }}</text>
              <text class="plan-per-day">约 ¥{{ plan.perDay }}/天</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 免费限制说明 -->
      <view class="free-note">
        <text class="free-note-text">免费版每日对话次数有限 · 升级后无限使用</text>
      </view>

      <view style="height: 200rpx" />
    </scroll-view>

    <!-- 底部购买栏 -->
    <view class="buy-bar">
      <view class="buy-price-info">
        <view class="buy-plan-name">
          <text class="buy-plan-text">{{ currentPlan()?.label }}会员</text>
        </view>
        <view class="buy-price-row">
          <text class="buy-price-unit">¥</text>
          <text class="buy-price">{{ currentPlan()?.price }}</text>
          <text class="buy-original">原价¥{{ currentPlan()?.originalPrice }}</text>
        </view>
      </view>
      <view
        class="buy-btn"
        :class="{ 'btn-disabled': isLoading || userStore.isVip }"
        @click="handleBuy"
      >
        <text class="buy-btn-text">{{ userStore.isVip ? '已激活' : isLoading ? '处理中…' : '立即开通' }}</text>
      </view>
    </view>
  </view>
</template>

<style>
.vip-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #0d0b1a;
}

/* ── 顶部栏 ── */
.vip-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 96rpx 24rpx 18rpx;
  background: rgba(15, 12, 28, 0.95);
  backdrop-filter: blur(20rpx);
  border-bottom: 1rpx solid rgba(255, 255, 255, 0.06);
  flex-shrink: 0;
}

.back-btn {
  width: 48rpx;
  height: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.back-icon { font-size: 44rpx; color: rgba(180, 150, 240, 0.8); line-height: 1; }
.header-title { font-size: 34rpx; color: rgba(230, 225, 255, 0.95); font-weight: 600; }

.vip-scroll { flex: 1; }

/* ── Hero ── */
.hero-area {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60rpx 32rpx 48rpx;
  overflow: hidden;
}

.hero-bg-glow {
  position: absolute;
  top: -100rpx;
  left: 50%;
  transform: translateX(-50%);
  width: 500rpx;
  height: 500rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(200, 160, 40, 0.12) 0%, transparent 65%);
  pointer-events: none;
}

.hero-icon-wrap {
  position: relative;
  width: 120rpx;
  height: 120rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24rpx;
}

.hero-icon { font-size: 64rpx; z-index: 1; }

.hero-ring {
  position: absolute;
  border-radius: 50%;
  border: 1rpx solid rgba(220, 180, 50, 0.2);
  animation: spin-hero linear infinite;
}

.r1 { width: 100rpx; height: 100rpx; animation-duration: 10s; border-style: dashed; }
.r2 { width: 120rpx; height: 120rpx; animation-duration: 16s; animation-direction: reverse; opacity: 0.5; }

@keyframes spin-hero { to { transform: rotate(360deg); } }

.hero-title {
  font-size: 52rpx;
  color: rgba(230, 225, 255, 0.96);
  font-weight: 700;
  letter-spacing: 4rpx;
  margin-bottom: 10rpx;
}

.hero-subtitle { font-size: 25rpx; color: rgba(180, 170, 210, 0.55); }

.vip-active-badge {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-top: 20rpx;
  background: rgba(50, 200, 100, 0.1);
  border: 1rpx solid rgba(50, 200, 100, 0.3);
  border-radius: 30rpx;
  padding: 10rpx 24rpx;
}

.vip-active-dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background: #32c864;
  box-shadow: 0 0 8rpx rgba(50, 200, 100, 0.6);
}

.vip-active-text { font-size: 24rpx; color: rgba(60, 220, 110, 0.9); font-weight: 600; }

/* ── 权益 ── */
.benefits-area { padding: 0 24rpx 32rpx; }

.area-label {
  font-size: 21rpx;
  color: rgba(180, 160, 220, 0.4);
  display: block;
  margin-bottom: 16rpx;
  letter-spacing: 2rpx;
}

.benefits-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14rpx;
}

.benefit-card {
  background: rgba(255, 255, 255, 0.04);
  border: 1rpx solid rgba(255, 255, 255, 0.07);
  border-radius: 20rpx;
  padding: 24rpx 20rpx;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.benefit-icon { font-size: 36rpx; }
.benefit-title { font-size: 26rpx; color: rgba(220, 215, 245, 0.9); font-weight: 600; }
.benefit-desc { font-size: 21rpx; color: rgba(180, 170, 210, 0.5); line-height: 1.5; }

/* ── 套餐 ── */
.plans-area { padding: 0 24rpx 24rpx; }

.plans-list { display: flex; flex-direction: column; gap: 14rpx; }

.plan-card {
  display: flex;
  align-items: center;
  gap: 16rpx;
  background: rgba(255, 255, 255, 0.04);
  border: 1.5rpx solid rgba(255, 255, 255, 0.08);
  border-radius: 20rpx;
  padding: 24rpx 22rpx;
  position: relative;
  overflow: hidden;
}

.plan-selected {
  border-color: rgba(220, 180, 50, 0.5);
  background: rgba(220, 170, 30, 0.07);
  box-shadow: 0 4rpx 20rpx rgba(200, 150, 20, 0.15);
}

/* 选择框 */
.plan-radio {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  border: 2rpx solid rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.radio-selected { border-color: rgba(220, 180, 50, 0.8); background: rgba(220, 170, 30, 0.15); }

.radio-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: #d4a520;
}

/* 套餐信息 */
.plan-main { flex: 1; }

.plan-name-row {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-bottom: 6rpx;
}

.plan-label { font-size: 30rpx; color: rgba(225, 218, 245, 0.92); font-weight: 600; }

.plan-badge {
  background: rgba(180, 170, 210, 0.12);
  border: 1rpx solid rgba(180, 170, 210, 0.2);
  border-radius: 10rpx;
  padding: 3rpx 12rpx;
}

.badge-recommend {
  background: rgba(220, 170, 30, 0.15);
  border-color: rgba(220, 180, 50, 0.4);
}

.plan-badge-text { font-size: 19rpx; color: rgba(200, 185, 100, 0.9); font-weight: 600; }
.badge-recommend .plan-badge-text { color: rgba(230, 195, 60, 0.95); }

.plan-duration { font-size: 22rpx; color: rgba(180, 170, 210, 0.5); }

/* 价格 */
.plan-price-block { text-align: right; }

.plan-price-row { display: flex; align-items: baseline; gap: 2rpx; justify-content: flex-end; }
.plan-price-unit { font-size: 22rpx; color: #d4a520; font-weight: 600; }
.plan-price { font-size: 44rpx; color: #d4a520; font-weight: 700; line-height: 1; }
.plan-original { font-size: 21rpx; color: rgba(180, 170, 210, 0.3); text-decoration: line-through; display: block; margin-top: 4rpx; }
.plan-per-day { font-size: 19rpx; color: rgba(180, 170, 210, 0.4); display: block; }

/* 免费说明 */
.free-note { padding: 8rpx 24rpx 0; text-align: center; }
.free-note-text { font-size: 21rpx; color: rgba(180, 170, 210, 0.3); }

/* ── 底部购买栏 ── */
.buy-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  padding: 18rpx 24rpx;
  padding-bottom: calc(18rpx + env(safe-area-inset-bottom, 24rpx));
  background: rgba(15, 12, 28, 0.98);
  border-top: 1rpx solid rgba(255, 255, 255, 0.07);
  backdrop-filter: blur(24rpx);
  flex-shrink: 0;
}

.buy-price-info { display: flex; flex-direction: column; gap: 6rpx; }

.buy-plan-name { display: flex; align-items: center; }
.buy-plan-text { font-size: 22rpx; color: rgba(180, 170, 210, 0.55); }

.buy-price-row { display: flex; align-items: baseline; gap: 3rpx; }
.buy-price-unit { font-size: 22rpx; color: #d4a520; font-weight: 600; }
.buy-price { font-size: 44rpx; color: #d4a520; font-weight: 700; line-height: 1; }
.buy-original { font-size: 21rpx; color: rgba(180, 170, 210, 0.3); text-decoration: line-through; margin-left: 8rpx; }

.buy-btn {
  flex: 1;
  height: 90rpx;
  background: linear-gradient(135deg, #e8a820, #c48010);
  border-radius: 18rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6rpx 24rpx rgba(200, 140, 20, 0.4);
}

.btn-disabled { opacity: 0.5; }

.buy-btn-text {
  font-size: 30rpx;
  color: rgba(255, 248, 220, 0.95);
  font-weight: 700;
  letter-spacing: 2rpx;
}
</style>

