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
        common_vendor.index.__f__("error", "at pages/diary/index.vue:44", e);
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
    return (_ctx, _cache) => {
      return common_vendor.e({
        a: common_vendor.o(openEditor, "94"),
        b: isLoading.value && common_vendor.unref(diaryStore).diaryList.length === 0
      }, isLoading.value && common_vendor.unref(diaryStore).diaryList.length === 0 ? {} : common_vendor.unref(diaryStore).diaryList.length === 0 ? {} : {}, {
        c: common_vendor.unref(diaryStore).diaryList.length === 0,
        d: common_vendor.f(common_vendor.unref(diaryStore).diaryList, (diary, k0, i0) => {
          return common_vendor.e({
            a: common_vendor.t(formatDate(diary.diaryDate)),
            b: diary.weather
          }, diary.weather ? {
            c: common_vendor.t(diary.weather)
          } : {}, {
            d: diary.emotion
          }, diary.emotion ? {
            e: common_vendor.t(common_vendor.unref(utils_emotion.getEmotionInfo)(diary.emotion).emoji),
            f: common_vendor.t(common_vendor.unref(utils_emotion.getEmotionInfo)(diary.emotion).label)
          } : {}, {
            g: diary.content
          }, diary.content ? {
            h: common_vendor.t(diary.content)
          } : {}, {
            i: diary.aiSummary
          }, diary.aiSummary ? {
            j: common_vendor.t(diary.aiSummary)
          } : {}, {
            k: diary.id,
            l: common_vendor.o(($event) => openDiaryDetail(diary), diary.id)
          });
        }),
        e: showEditor.value
      }, showEditor.value ? {
        f: common_vendor.o(($event) => showEditor.value = false, "f4"),
        g: common_vendor.f(EMOTIONS, (e, k0, i0) => {
          return {
            a: common_vendor.t(e.emoji),
            b: common_vendor.t(e.label),
            c: e.code,
            d: editEmotion.value === e.code ? 1 : "",
            e: common_vendor.o(($event) => editEmotion.value = e.code, e.code)
          };
        }),
        h: common_vendor.f(WEATHERS, (w, k0, i0) => {
          return {
            a: common_vendor.t(w),
            b: w,
            c: editWeather.value === w ? 1 : "",
            d: common_vendor.o(($event) => editWeather.value = w, w)
          };
        }),
        i: editContent.value,
        j: common_vendor.o(($event) => editContent.value = $event.detail.value, "a2"),
        k: isLoading.value,
        l: common_vendor.o(saveDiaryEntry, "45"),
        m: common_vendor.o(() => {
        }, "cb"),
        n: common_vendor.o(($event) => showEditor.value = false, "f2")
      } : {}, {
        o: showDetail.value && selectedDiary.value
      }, showDetail.value && selectedDiary.value ? common_vendor.e({
        p: common_vendor.t(formatDate(selectedDiary.value.diaryDate)),
        q: selectedDiary.value.emotion
      }, selectedDiary.value.emotion ? {
        r: common_vendor.t(common_vendor.unref(utils_emotion.getEmotionInfo)(selectedDiary.value.emotion).emoji),
        s: common_vendor.t(common_vendor.unref(utils_emotion.getEmotionInfo)(selectedDiary.value.emotion).label)
      } : {}, {
        t: common_vendor.o(($event) => showDetail.value = false, "e0"),
        v: selectedDiary.value.content
      }, selectedDiary.value.content ? {
        w: common_vendor.t(selectedDiary.value.content)
      } : {}, {
        x: !selectedDiary.value.aiSummary
      }, !selectedDiary.value.aiSummary ? {
        y: common_vendor.t(isGettingSummary.value ? "生成中..." : "获取总结"),
        z: common_vendor.o(fetchAiSummary, "e4")
      } : {}, {
        A: selectedDiary.value.aiSummary
      }, selectedDiary.value.aiSummary ? {
        B: common_vendor.t(selectedDiary.value.aiSummary)
      } : {}, {
        C: common_vendor.o(() => {
        }, "91"),
        D: common_vendor.o(($event) => showDetail.value = false, "c7")
      }) : {});
    };
  }
});
wx.createPage(_sfc_main);
//# sourceMappingURL=../../../.sourcemap/mp-weixin/pages/diary/index.js.map
