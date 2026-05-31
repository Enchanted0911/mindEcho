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
  // 新版：4 种风格 × 男女各一
  gentle_female: { label: "小柔", desc: "温柔陪伴，细腻共情", emoji: "🌸" },
  gentle_male: { label: "阿暖", desc: "温暖守护，踏实陪伴", emoji: "☀️" },
  rational_female: { label: "知微", desc: "冷静分析，清醒引导", emoji: "🎯" },
  rational_male: { label: "林析", desc: "逻辑清晰，引导成长", emoji: "📐" },
  snarky_female: { label: "辣辣", desc: "毒嘴心软，笑中化解", emoji: "😏" },
  snarky_male: { label: "损哥", desc: "损嘴暖心，搞笑减压", emoji: "😤" },
  midnight_female: { label: "夜笙", desc: "深夜守候，静静陪伴", emoji: "🌙" },
  midnight_male: { label: "深渊", desc: "沉默守护，深夜同行", emoji: "🌊" }
};
function getEmotionInfo(code) {
  return EMOTION_MAP[code] || EMOTION_MAP.neutral;
}
function getPersonalityInfo(code) {
  return PERSONALITY_MAP[code] || { label: code, desc: "", emoji: "🤖" };
}
function parseDate(dateStr) {
  if (!dateStr)
    return /* @__PURE__ */ new Date(NaN);
  const normalized = dateStr.includes("T") ? dateStr : dateStr.replace(" ", "T");
  const date = new Date(normalized);
  if (isNaN(date.getTime())) {
    const match = dateStr.match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})/);
    if (match) {
      return new Date(
        parseInt(match[1]),
        parseInt(match[2]) - 1,
        parseInt(match[3]),
        parseInt(match[4]),
        parseInt(match[5]),
        parseInt(match[6])
      );
    }
  }
  return date;
}
exports.getEmotionInfo = getEmotionInfo;
exports.getPersonalityInfo = getPersonalityInfo;
exports.parseDate = parseDate;
//# sourceMappingURL=../../.sourcemap/mp-weixin/utils/emotion.js.map
