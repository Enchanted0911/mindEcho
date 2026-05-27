"use strict";
const common_vendor = require("../../common/vendor.js");
const api_payment = require("../../api/payment.js");
const store_user = require("../../store/user.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const userStore = store_user.useUserStore();
    const isLoading = common_vendor.ref(false);
    const selectedPlan = common_vendor.ref("monthly");
    const VIP_PLANS = [
      {
        type: "monthly",
        label: "月度会员",
        price: "9.9",
        originalPrice: "29.9",
        duration: "1个月",
        badge: "",
        popular: false
      },
      {
        type: "quarterly",
        label: "季度会员",
        price: "25.9",
        originalPrice: "79.9",
        duration: "3个月",
        badge: "推荐",
        popular: true
      },
      {
        type: "yearly",
        label: "年度会员",
        price: "88",
        originalPrice: "299",
        duration: "12个月",
        badge: "最划算",
        popular: false
      }
    ];
    const VIP_BENEFITS = [
      { emoji: "💬", title: "无限对话", desc: "无限次 AI 聊天，无频率限制" },
      { emoji: "🧠", title: "长期记忆", desc: "AI 记住你的故事和性格" },
      { emoji: "✨", title: "全部人格", desc: "解锁所有 AI 人格切换" },
      { emoji: "🎯", title: "深度陪伴", desc: "更精准的情绪理解和回复" }
    ];
    async function handleBuy() {
      var _a;
      if (isLoading.value)
        return;
      isLoading.value = true;
      try {
        const order = await api_payment.createOrder(selectedPlan.value);
        if (order.wxPayParams) {
          await api_payment.wxPay(order.wxPayParams);
          common_vendor.index.showToast({ title: "支付成功，会员已激活！", icon: "success" });
        } else {
          common_vendor.index.showToast({ title: "订单创建成功，等待支付接入", icon: "none" });
        }
      } catch (e) {
        if ((_a = e.errMsg) == null ? void 0 : _a.includes("cancel")) {
          common_vendor.index.showToast({ title: "已取消支付", icon: "none" });
        } else {
          common_vendor.index.showToast({ title: "支付失败，请重试", icon: "none" });
        }
      } finally {
        isLoading.value = false;
      }
    }
    return (_ctx, _cache) => {
      var _a, _b;
      return common_vendor.e({
        a: common_vendor.o(($event) => common_vendor.index.navigateBack(), "1e"),
        b: common_vendor.unref(userStore).isVip
      }, common_vendor.unref(userStore).isVip ? {} : {}, {
        c: common_vendor.f(VIP_BENEFITS, (b, k0, i0) => {
          return {
            a: common_vendor.t(b.emoji),
            b: common_vendor.t(b.title),
            c: common_vendor.t(b.desc),
            d: b.title
          };
        }),
        d: common_vendor.f(VIP_PLANS, (plan, k0, i0) => {
          return common_vendor.e({
            a: plan.badge
          }, plan.badge ? {
            b: common_vendor.t(plan.badge)
          } : {}, {
            c: common_vendor.t(plan.label),
            d: common_vendor.t(plan.duration),
            e: common_vendor.t(plan.price),
            f: common_vendor.t(plan.originalPrice),
            g: plan.type,
            h: selectedPlan.value === plan.type ? 1 : "",
            i: plan.popular ? 1 : "",
            j: common_vendor.o(($event) => selectedPlan.value = plan.type, plan.type)
          });
        }),
        e: common_vendor.t((_a = VIP_PLANS.find((p) => p.type === selectedPlan.value)) == null ? void 0 : _a.label),
        f: common_vendor.t((_b = VIP_PLANS.find((p) => p.type === selectedPlan.value)) == null ? void 0 : _b.price),
        g: common_vendor.t(common_vendor.unref(userStore).isVip ? "已激活" : "立即开通"),
        h: isLoading.value,
        i: isLoading.value || common_vendor.unref(userStore).isVip,
        j: common_vendor.o(handleBuy, "92")
      });
    };
  }
});
wx.createPage(_sfc_main);
//# sourceMappingURL=../../../.sourcemap/mp-weixin/pages/vip/index.js.map
