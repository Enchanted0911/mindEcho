"use strict";
const EMOTION_MAP = {
  anxiety: { label: "焦虑", emoji: "😰", color: "#f59e0b" },
  depression: { label: "抑郁", emoji: "😔", color: "#6366f1" },
  anger: { label: "愤怒", emoji: "😤", color: "#ef4444" },
  loneliness: { label: "孤独", emoji: "😞", color: "#8b5cf6" },
  sadness: { label: "悲伤", emoji: "😢", color: "#3b82f6" },
  happiness: { label: "快乐", emoji: "😊", color: "#22c55e" },
  fear: { label: "恐惧", emoji: "😨", color: "#f97316" },
  neutral: { label: "平静", emoji: "😐", color: "#94a3b8" },
  stress: { label: "压力", emoji: "😩", color: "#dc2626" }
};
const PERSONALITY_MAP = {
  gentle_sister: { label: "温柔姐姐", desc: "温柔陪伴，细腻共情", emoji: "🌸" },
  rational_mentor: { label: "理性导师", desc: "冷静分析，引导成长", emoji: "🎯" },
  snarky_friend: { label: "毒舌朋友", desc: "搞笑吐槽，化解压力", emoji: "😏" },
  midnight_hollow: { label: "深夜树洞", desc: "安静倾听，深夜陪伴", emoji: "🌙" }
};
function getEmotionInfo(code) {
  return EMOTION_MAP[code] || EMOTION_MAP.neutral;
}
function getPersonalityInfo(code) {
  return PERSONALITY_MAP[code] || PERSONALITY_MAP.gentle_sister;
}
function parseDate(dateStr) {
  if (!dateStr)
    return /* @__PURE__ */ new Date(NaN);
  return new Date(dateStr.replace(" ", "T"));
}
exports.getEmotionInfo = getEmotionInfo;
exports.getPersonalityInfo = getPersonalityInfo;
exports.parseDate = parseDate;
//# sourceMappingURL=../../.sourcemap/mp-weixin/utils/emotion.js.map
