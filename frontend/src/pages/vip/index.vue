<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {getProfile} from '../../api/auth'
import {createOrder, getOrder, wxPay} from '../../api/payment'
import {useUserStore} from '../../store/user'

const userStore = useUserStore()
const isLoading = ref(false)
const selectedPlan = ref('monthly')

const VIP_PLANS = [
  {
    type: 'monthly',
    label: '月度会员',
    price: '9.9',
    originalPrice: '29.9',
    duration: '1个月',
    badge: '',
    popular: false
  },
  {
    type: 'quarterly',
    label: '季度会员',
    price: '25.9',
    originalPrice: '79.9',
    duration: '3个月',
    badge: '推荐',
    popular: true
  },
  {
    type: 'yearly',
    label: '年度会员',
    price: '88',
    originalPrice: '299',
    duration: '12个月',
    badge: '最划算',
    popular: false
  }
]

const VIP_BENEFITS = [
  { emoji: '💬', title: '无限对话', desc: '无限次 AI 聊天，无频率限制' },
  { emoji: '🧠', title: '长期记忆', desc: 'AI 记住你的故事和性格' },
  { emoji: '✨', title: '全部人格', desc: '解锁所有 AI 人格切换' },
  { emoji: '🎯', title: '深度陪伴', desc: '更精准的情绪理解和回复' }
]

// 页面加载时从后端拉取最新用户信息（确保 VIP 状态与到期时间是最新的）
// 若接口失败（网络异常等），降级读取本地缓存，避免页面白屏
onMounted(async () => {
  if (!userStore.isLoggedIn) return
  try {
    const latestInfo = await getProfile()
    // 后端返回的是 UserInfoDTO（id 为 Long，序列化为字符串），与 UserInfo 结构兼容
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
    // 后端请求失败时降级：尝试用本地缓存填充 store（仅在 store 为空时执行）
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

/**
 * 轮询订单状态，最多等待 maxAttempts * intervalMs 毫秒
 * 当订单变为 paid 时 resolve，超时或出错时 reject
 * 使用 setTimeout 递归而非 setInterval，确保前一次请求完成后再发下一次，避免请求堆积
 */
function pollOrderStatus(orderNo: string, maxAttempts = 10, intervalMs = 2000): Promise<void> {
  return new Promise((resolve, reject) => {
    let attempts = 0

    const poll = async () => {
      attempts++
      try {
        const order = await getOrder(orderNo)
        if (order.status === 'paid') {
          resolve()
          return
        }
        if (attempts >= maxAttempts) {
          reject(new Error('支付确认超时，请稍后在订单记录中查看'))
          return
        }
        // 前一次请求完成后再等待 intervalMs 发起下一次
        setTimeout(poll, intervalMs)
      } catch (e) {
        reject(e)
      }
    }

    // 首次延迟 intervalMs 后开始轮询（给微信支付回调一点处理时间）
    setTimeout(poll, intervalMs)
  })
}

/**
 * 支付成功后更新本地 userStore 的 VIP 状态
 */
function refreshUserVipStatus(expireTime?: string) {
  if (!userStore.userInfo) return
  const updated = {
    ...userStore.userInfo,
    isVip: true,
    vipExpireTime: expireTime ?? null
  }
  userStore.setUserInfo(updated)
}

async function handleBuy() {
  if (isLoading.value) return
  isLoading.value = true

  try {
    const order = await createOrder(selectedPlan.value)

    if (order.wxPayParams) {
      // 调起微信支付
      await wxPay(order.wxPayParams)

      // 微信支付 success 回调仅代表用户操作完成，需轮询服务端确认真正到账
      uni.showToast({ title: '支付处理中...', icon: 'loading', duration: 15000 })
      try {
        await pollOrderStatus(order.orderNo)
        // 轮询到 paid，刷新 VIP 状态
        const paidOrder = await getOrder(order.orderNo)
        refreshUserVipStatus(paidOrder.expireTime)
        uni.showToast({ title: '会员已激活！', icon: 'success' })
      } catch (_pollErr: any) {
        // 轮询超时不代表支付失败，可能是回调延迟
        uni.showToast({ title: '支付已提交，稍后刷新查看会员状态', icon: 'none', duration: 3000 })
      }
    } else {
      // 开发环境：微信支付 SDK 未接入，订单创建成功即视为完成
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
</script>

<template>
  <view class="vip-page">
    <!-- 顶部 -->
    <view class="vip-header">
      <view class="back-btn" @click="uni.navigateBack()">← 返回</view>
      <text class="header-title">开通会员</text>
      <view style="width: 80rpx" />
    </view>

    <scroll-view class="vip-scroll" scroll-y>
      <!-- VIP 标题 -->
      <view class="vip-hero">
        <text class="hero-emoji">👑</text>
        <text class="hero-title">心屿会员</text>
        <text class="hero-subtitle">解锁完整 AI 情绪陪伴体验</text>
        <view v-if="userStore.isVip" class="vip-status-badge">
          <text>✓ 已是会员</text>
        </view>
      </view>

      <!-- 会员权益 -->
      <view class="benefits-section">
        <text class="section-title">会员专属权益</text>
        <view class="benefits-grid">
          <view v-for="b in VIP_BENEFITS" :key="b.title" class="benefit-card">
            <text class="benefit-emoji">{{ b.emoji }}</text>
            <text class="benefit-title">{{ b.title }}</text>
            <text class="benefit-desc">{{ b.desc }}</text>
          </view>
        </view>
      </view>

      <!-- 套餐选择 -->
      <view class="plans-section">
        <text class="section-title">选择套餐</text>
        <view class="plans-list">
          <view
            v-for="plan in VIP_PLANS"
            :key="plan.type"
            class="plan-card"
            :class="{ 'plan-selected': selectedPlan === plan.type, 'plan-popular': plan.popular }"
            @click="selectedPlan = plan.type"
          >
            <view v-if="plan.badge" class="plan-badge">{{ plan.badge }}</view>
            <view class="plan-info">
              <text class="plan-label">{{ plan.label }}</text>
              <text class="plan-duration">{{ plan.duration }}</text>
            </view>
            <view class="plan-price-area">
              <text class="plan-price">¥{{ plan.price }}</text>
              <text class="plan-original">¥{{ plan.originalPrice }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 免费版限制提示 -->
      <view class="free-tip">
        <text class="free-tip-text">免费版每日对话次数有限，升级后无限制</text>
      </view>

      <view style="height: 200rpx" />
    </scroll-view>

    <!-- 购买按钮（固定底部） -->
    <view class="buy-footer">
      <view class="buy-price-info">
        <text class="buy-label">{{ VIP_PLANS.find(p => p.type === selectedPlan)?.label }}</text>
        <text class="buy-price">¥{{ VIP_PLANS.find(p => p.type === selectedPlan)?.price }}</text>
      </view>
      <button
        class="buy-btn"
        :loading="isLoading"
        :disabled="isLoading || userStore.isVip"
        @click="handleBuy"
      >
        {{ userStore.isVip ? '已激活' : '立即开通' }}
      </button>
    </view>
  </view>
</template>

<style>
.vip-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #1a0a2e 0%, #0f0f1a 100%);
}

.vip-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 100rpx 32rpx 24rpx;
}

.back-btn { font-size: 28rpx; color: #b89ee8; }
.header-title { font-size: 36rpx; color: #e8d5ff; font-weight: bold; }

.vip-scroll { flex: 1; }

.vip-hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60rpx 32rpx 40rpx;
  gap: 16rpx;
}

.hero-emoji { font-size: 100rpx; }
.hero-title { font-size: 56rpx; color: #f0d060; font-weight: bold; letter-spacing: 4rpx; }
.hero-subtitle { font-size: 28rpx; color: #b89ee8; }

.vip-status-badge {
  background: rgba(240, 208, 96, 0.2);
  border: 1rpx solid #f0d060;
  padding: 12rpx 32rpx;
  border-radius: 40rpx;
  margin-top: 8rpx;
}

.vip-status-badge text { font-size: 26rpx; color: #f0d060; }

.section-title {
  font-size: 32rpx;
  color: #e8d5ff;
  font-weight: 600;
  display: block;
  margin-bottom: 24rpx;
}

.benefits-section {
  padding: 0 32rpx;
  margin-bottom: 40rpx;
}

.benefits-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20rpx;
}

.benefit-card {
  background: rgba(255,255,255,0.04);
  border: 1rpx solid rgba(184,158,232,0.12);
  border-radius: 20rpx;
  padding: 28rpx 24rpx;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.benefit-emoji { font-size: 44rpx; }
.benefit-title { font-size: 28rpx; color: #e8d5ff; font-weight: 600; }
.benefit-desc { font-size: 22rpx; color: #7a6b9a; line-height: 1.5; }

.plans-section { padding: 0 32rpx; }

.plans-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.plan-card {
  background: rgba(255,255,255,0.04);
  border: 2rpx solid rgba(184,158,232,0.1);
  border-radius: 20rpx;
  padding: 28rpx 32rpx;
  display: flex;
  align-items: center;
  position: relative;
  overflow: hidden;
}

.plan-selected {
  border-color: #b89ee8;
  background: rgba(184,158,232,0.1);
}

.plan-popular {
  border-color: rgba(240, 208, 96, 0.3);
}

.plan-badge {
  position: absolute;
  top: 0;
  right: 0;
  background: linear-gradient(135deg, #f0d060, #e0a030);
  color: #1a0a2e;
  font-size: 22rpx;
  padding: 6rpx 20rpx;
  border-bottom-left-radius: 12rpx;
  font-weight: bold;
}

.plan-info { flex: 1; }
.plan-label { font-size: 30rpx; color: #e8d5ff; font-weight: 600; display: block; }
.plan-duration { font-size: 24rpx; color: #7a6b9a; }

.plan-price-area { text-align: right; }
.plan-price { font-size: 44rpx; color: #f0d060; font-weight: bold; display: block; }
.plan-original { font-size: 24rpx; color: #5a5070; text-decoration: line-through; }

.free-tip {
  padding: 24rpx 32rpx;
  text-align: center;
}

.free-tip-text { font-size: 24rpx; color: #5a5070; }

.buy-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 32rpx 60rpx;
  background: rgba(15,15,26,0.97);
  border-top: 1rpx solid rgba(255,255,255,0.06);
  gap: 24rpx;
}

.buy-price-info { display: flex; flex-direction: column; }
.buy-label { font-size: 24rpx; color: #7a6b9a; }
.buy-price { font-size: 44rpx; color: #f0d060; font-weight: bold; }

.buy-btn {
  flex: 1;
  height: 96rpx;
  background: linear-gradient(135deg, #f0d060, #e0a030);
  color: #1a0a2e;
  font-size: 32rpx;
  font-weight: bold;
  border-radius: 48rpx;
  border: none;
}

.buy-btn[disabled] { opacity: 0.5; }
</style>

