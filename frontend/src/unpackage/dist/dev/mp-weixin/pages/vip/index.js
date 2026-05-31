"use strict";
const common_vendor = require("../../common/vendor.js");
const api_auth = require("../../api/auth.js");
const api_payment = require("../../api/payment.js");
const store_user = require("../../store/user.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const userStore = store_user.useUserStore();
    const isLoading = common_vendor.ref(false);
    const selectedPlan = common_vendor.ref("quarterly");
    const VIP_PLANS = [
      {
        type: "monthly",
        label: "月度",
        price: "9.9",
        originalPrice: "29.9",
        duration: "1个月",
        badge: "",
        popular: false,
        perDay: "0.33"
      },
      {
        type: "quarterly",
        label: "季度",
        price: "25.9",
        originalPrice: "79.9",
        duration: "3个月",
        badge: "推荐",
        popular: true,
        perDay: "0.29"
      },
      {
        type: "yearly",
        label: "年度",
        price: "88",
        originalPrice: "299",
        duration: "12个月",
        badge: "最划算",
        popular: false,
        perDay: "0.24"
      }
    ];
    const VIP_BENEFITS = [
      { icon: "💬", title: "无限对话", desc: "无频率限制，随时倾诉" },
      { icon: "🧠", title: "长期记忆", desc: "AI 记住你的故事" },
      { icon: "✨", title: "全部人格", desc: "解锁所有角色切换" },
      { icon: "🎯", title: "深度陪伴", desc: "精准情绪理解回复" }
    ];
    common_vendor.onMounted(async () => {
      if (!userStore.isLoggedIn)
        return;
      try {
        const latestInfo = await api_auth.getProfile();
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
        });
      } catch (_) {
        if (!userStore.userInfo) {
          try {
            const cached = common_vendor.index.getStorageSync("userInfo");
            if (cached) {
              userStore.setUserInfo(JSON.parse(cached));
            }
          } catch (__) {
          }
        }
      }
    });
    function pollOrderStatus(orderNo, maxAttempts = 10, intervalMs = 2e3) {
      return new Promise((resolve, reject) => {
        let attempts = 0;
        const poll = async () => {
          attempts++;
          try {
            const order = await api_payment.getOrder(orderNo);
            if (order.status === "paid") {
              resolve();
              return;
            }
            if (attempts >= maxAttempts) {
              reject(new Error("支付确认超时，请稍后在订单记录中查看"));
              return;
            }
            setTimeout(poll, intervalMs);
          } catch (e) {
            reject(e);
          }
        };
        setTimeout(poll, intervalMs);
      });
    }
    function refreshUserVipStatus(expireTime) {
      if (!userStore.userInfo)
        return;
      userStore.setUserInfo({ ...userStore.userInfo, isVip: true, vipExpireTime: expireTime ?? null });
    }
    async function handleBuy() {
      var _a, _b;
      if (isLoading.value)
        return;
      isLoading.value = true;
      try {
        const order = await api_payment.createOrder(selectedPlan.value);
        if (order.wxPayParams) {
          await api_payment.wxPay(order.wxPayParams);
          common_vendor.index.showToast({ title: "支付处理中...", icon: "loading", duration: 15e3 });
          try {
            await pollOrderStatus(order.orderNo);
            const paidOrder = await api_payment.getOrder(order.orderNo);
            refreshUserVipStatus(paidOrder.expireTime);
            common_vendor.index.showToast({ title: "会员已激活！", icon: "success" });
          } catch (_pollErr) {
            common_vendor.index.showToast({ title: "支付已提交，稍后刷新查看会员状态", icon: "none", duration: 3e3 });
          }
        } else {
          common_vendor.index.showToast({ title: "订单创建成功，等待支付接入", icon: "none" });
        }
      } catch (e) {
        if (((_a = e.errMsg) == null ? void 0 : _a.includes("cancel")) || ((_b = e.errMsg) == null ? void 0 : _b.includes("用户取消"))) {
          common_vendor.index.showToast({ title: "已取消支付", icon: "none" });
        } else {
          common_vendor.index.showToast({ title: e.message || "支付失败，请重试", icon: "none" });
        }
      } finally {
        isLoading.value = false;
      }
    }
    const currentPlan = () => VIP_PLANS.find((p) => p.type === selectedPlan.value);
    return (_ctx, _cache) => {
      var _a, _b, _c;
      return common_vendor.e({
        a: common_vendor.o(($event) => common_vendor.index.navigateBack(), "93"),
        b: common_vendor.unref(userStore).isVip
      }, common_vendor.unref(userStore).isVip ? {} : {}, {
        c: common_vendor.f(VIP_BENEFITS, (b, k0, i0) => {
          return {
            a: common_vendor.t(b.icon),
            b: common_vendor.t(b.title),
            c: common_vendor.t(b.desc),
            d: b.title
          };
        }),
        d: common_vendor.f(VIP_PLANS, (plan, k0, i0) => {
          return common_vendor.e({
            a: selectedPlan.value === plan.type
          }, selectedPlan.value === plan.type ? {} : {}, {
            b: selectedPlan.value === plan.type ? 1 : "",
            c: common_vendor.t(plan.label),
            d: plan.badge
          }, plan.badge ? {
            e: common_vendor.t(plan.badge),
            f: plan.popular ? 1 : ""
          } : {}, {
            g: common_vendor.t(plan.duration),
            h: common_vendor.t(plan.price),
            i: common_vendor.t(plan.originalPrice),
            j: common_vendor.t(plan.perDay),
            k: plan.type,
            l: selectedPlan.value === plan.type ? 1 : "",
            m: common_vendor.o(($event) => selectedPlan.value = plan.type, plan.type)
          });
        }),
        e: common_vendor.t((_a = currentPlan()) == null ? void 0 : _a.label),
        f: common_vendor.t((_b = currentPlan()) == null ? void 0 : _b.price),
        g: common_vendor.t((_c = currentPlan()) == null ? void 0 : _c.originalPrice),
        h: common_vendor.t(common_vendor.unref(userStore).isVip ? "已激活" : isLoading.value ? "处理中…" : "立即开通"),
        i: isLoading.value || common_vendor.unref(userStore).isVip ? 1 : "",
        j: common_vendor.o(handleBuy, "e2")
      });
    };
  }
});
wx.createPage(_sfc_main);
//# sourceMappingURL=../../../.sourcemap/mp-weixin/pages/vip/index.js.map
