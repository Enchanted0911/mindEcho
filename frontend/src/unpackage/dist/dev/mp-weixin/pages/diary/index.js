"use strict";
const common_vendor = require("../../common/vendor.js");
const store_diary = require("../../store/diary.js");
const api_diary = require("../../api/diary.js");
const utils_emotion = require("../../utils/emotion.js");
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "index",
  setup(__props) {
    const diaryStore = store_diary.useDiaryStore();
    const isLoading = common_vendor.ref(false);
    const showEditor = common_vendor.ref(false);
    const showDetail = common_vendor.ref(false);
    const selectedDiary = common_vendor.ref(null);
    const isGettingSummary = common_vendor.ref(false);
    const editEmotion = common_vendor.ref("");
    const editIntensity = common_vendor.ref(5);
    const editContent = common_vendor.ref("");
    const editWeather = common_vendor.ref("");
    const EMOTIONS = [
      { code: "happiness", label: "快乐", emoji: "😊" },
      { code: "neutral", label: "平静", emoji: "😐" },
      { code: "sadness", label: "悲伤", emoji: "😢" },
      { code: "anxiety", label: "焦虑", emoji: "😰" },
      { code: "anger", label: "愤怒", emoji: "😤" },
      { code: "loneliness", label: "孤独", emoji: "😞" },
      { code: "stress", label: "压力", emoji: "😩" },
      { code: "fear", label: "恐惧", emoji: "😨" }
    ];
    const WEATHERS = ["☀️ 晴", "🌤 多云", "🌧 雨", "❄️ 雪", "🌙 夜"];
    common_vendor.onMounted(() => {
      loadDiaryList();
    });
    async function loadDiaryList() {
      isLoading.value = true;
      try {
        const result = await api_diary.getDiaryList();
        diaryStore.setDiaryList(result.records || []);
      } catch (e) {
        common_vendor.index.__f__("error", "at pages/diary/index.vue:43", e);
      } finally {
        isLoading.value = false;
      }
    }
    function openEditor() {
      editEmotion.value = "";
      editIntensity.value = 5;
      editContent.value = "";
      editWeather.value = "";
      showEditor.value = true;
    }
    async function saveDiaryEntry() {
      if (!editContent.value.trim() && !editEmotion.value) {
        common_vendor.index.showToast({ title: "请输入内容或选择情绪", icon: "none" });
        return;
      }
      isLoading.value = true;
      try {
        const diary = await api_diary.saveDiary({
          emotion: editEmotion.value || void 0,
          emotionIntensity: editIntensity.value,
          content: editContent.value || void 0,
          weather: editWeather.value || void 0
        });
        diaryStore.updateDiary(diary);
        showEditor.value = false;
        common_vendor.index.showToast({ title: "日记已保存", icon: "success" });
      } catch (e) {
        common_vendor.index.showToast({ title: "保存失败", icon: "none" });
      } finally {
        isLoading.value = false;
      }
    }
    function openDiaryDetail(diary) {
      selectedDiary.value = diary;
      showDetail.value = true;
    }
    async function fetchAiSummary() {
      if (!selectedDiary.value || isGettingSummary.value)
        return;
      isGettingSummary.value = true;
      try {
        const updated = await api_diary.getAiSummary(selectedDiary.value.id);
        selectedDiary.value = updated;
        diaryStore.updateDiary(updated);
      } catch (e) {
        common_vendor.index.showToast({ title: "获取总结失败", icon: "none" });
      } finally {
        isGettingSummary.value = false;
      }
    }
    function formatDate(dateVal) {
      if (Array.isArray(dateVal)) {
        const month = String(dateVal[1]).padStart(2, "0");
        const day = String(dateVal[2]).padStart(2, "0");
        return `${month}月${day}日`;
      }
      if (typeof dateVal === "string" && dateVal.includes("-")) {
        const parts = dateVal.split("-");
        return `${parts[1]}月${parts[2]}日`;
      }
      return String(dateVal);
    }
    function getEmotionColor(code) {
      const colorMap = {
        happiness: "#f0b429",
        neutral: "#9b87d1",
        sadness: "#4a9eff",
        anxiety: "#ff8c42",
        anger: "#ff5252",
        loneliness: "#7c86c8",
        stress: "#e8637a",
        fear: "#b06adf"
      };
      return colorMap[code] || "#9b87d1";
    }
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: common_vendor.o(openEditor, "74"),
        b: isLoading.value && common_vendor.unref(diaryStore).diaryList.length === 0
      }, isLoading.value && common_vendor.unref(diaryStore).diaryList.length === 0 ? {} : common_vendor.unref(diaryStore).diaryList.length === 0 ? {
        d: common_vendor.o(openEditor, "c2")
      } : {}, {
        c: common_vendor.unref(diaryStore).diaryList.length === 0,
        e: common_vendor.f(common_vendor.unref(diaryStore).diaryList, (diary, k0, i0) => {
          return common_vendor.e({
            a: diary.emotion ? getEmotionColor(diary.emotion) : "rgba(155, 135, 209, 0.4)",
            b: common_vendor.t(formatDate(diary.diaryDate)),
            c: diary.weather
          }, diary.weather ? {
            d: common_vendor.t(diary.weather)
          } : {}, {
            e: diary.emotion
          }, diary.emotion ? {
            f: common_vendor.t(common_vendor.unref(utils_emotion.getEmotionInfo)(diary.emotion).emoji),
            g: common_vendor.t(common_vendor.unref(utils_emotion.getEmotionInfo)(diary.emotion).label),
            h: getEmotionColor(diary.emotion),
            i: getEmotionColor(diary.emotion) + "22",
            j: getEmotionColor(diary.emotion) + "44"
          } : {}, {
            k: diary.content
          }, diary.content ? {
            l: common_vendor.t(diary.content)
          } : {}, {
            m: diary.aiSummary
          }, diary.aiSummary ? {
            n: common_vendor.t(diary.aiSummary)
          } : {}, {
            o: diary.id,
            p: common_vendor.o(($event) => openDiaryDetail(diary), diary.id)
          });
        }),
        f: showEditor.value
      }, showEditor.value ? {
        g: common_vendor.o(($event) => showEditor.value = false, "6f"),
        h: common_vendor.f(EMOTIONS, (e, k0, i0) => {
          return {
            a: common_vendor.t(e.emoji),
            b: common_vendor.t(e.label),
            c: common_vendor.s(editEmotion.value === e.code ? {
              color: getEmotionColor(e.code)
            } : {}),
            d: e.code,
            e: editEmotion.value === e.code ? 1 : "",
            f: common_vendor.s(editEmotion.value === e.code ? {
              background: getEmotionColor(e.code) + "22",
              borderColor: getEmotionColor(e.code) + "66"
            } : {}),
            g: common_vendor.o(($event) => editEmotion.value = e.code, e.code)
          };
        }),
        i: common_vendor.f(WEATHERS, (w, k0, i0) => {
          return {
            a: common_vendor.t(w),
            b: w,
            c: editWeather.value === w ? 1 : "",
            d: common_vendor.o(($event) => editWeather.value = w, w)
          };
        }),
        j: editContent.value,
        k: common_vendor.o(($event) => editContent.value = $event.detail.value, "61"),
        l: common_vendor.t(isLoading.value ? "保存中…" : "保存日记"),
        m: isLoading.value ? 1 : "",
        n: common_vendor.o(saveDiaryEntry, "d2"),
        o: common_vendor.o(() => {
        }, "50"),
        p: common_vendor.o(($event) => showEditor.value = false, "13")
      } : {}, {
        q: showDetail.value && selectedDiary.value
      }, showDetail.value && selectedDiary.value ? common_vendor.e({
        r: common_vendor.t(formatDate(selectedDiary.value.diaryDate)),
        s: selectedDiary.value.emotion
      }, selectedDiary.value.emotion ? {
        t: common_vendor.t(common_vendor.unref(utils_emotion.getEmotionInfo)(selectedDiary.value.emotion).emoji),
        v: common_vendor.t(common_vendor.unref(utils_emotion.getEmotionInfo)(selectedDiary.value.emotion).label)
      } : {}, {
        w: common_vendor.o(($event) => showDetail.value = false, "b7"),
        x: selectedDiary.value.content
      }, selectedDiary.value.content ? {
        y: common_vendor.t(selectedDiary.value.content)
      } : {}, {
        z: !selectedDiary.value.aiSummary
      }, !selectedDiary.value.aiSummary ? {
        A: common_vendor.t(isGettingSummary.value ? "生成中…" : "获取总结"),
        B: isGettingSummary.value ? 1 : "",
        C: common_vendor.o(fetchAiSummary, "e7")
      } : {}, {
        D: selectedDiary.value.aiSummary
      }, selectedDiary.value.aiSummary ? {
        E: common_vendor.t(selectedDiary.value.aiSummary)
      } : {}, {
        F: common_vendor.o(() => {
        }, "57"),
        G: common_vendor.o(($event) => showDetail.value = false, "d5")
      }) : {});
    };
  }
});
wx.createPage(_sfc_main);
//# sourceMappingURL=../../../.sourcemap/mp-weixin/pages/diary/index.js.map
