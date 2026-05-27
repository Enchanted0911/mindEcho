"use strict";
const common_vendor = require("../common/vendor.js");
const useDiaryStore = common_vendor.defineStore("diary", () => {
  const diaryList = common_vendor.ref([]);
  const currentDiary = common_vendor.ref(null);
  function setDiaryList(list) {
    diaryList.value = list;
  }
  function setCurrentDiary(diary) {
    currentDiary.value = diary;
  }
  function updateDiary(diary) {
    const index = diaryList.value.findIndex((d) => d.id === diary.id);
    if (index !== -1) {
      diaryList.value[index] = diary;
    } else {
      diaryList.value.unshift(diary);
    }
  }
  return {
    diaryList,
    currentDiary,
    setDiaryList,
    setCurrentDiary,
    updateDiary
  };
});
exports.useDiaryStore = useDiaryStore;
//# sourceMappingURL=../../.sourcemap/mp-weixin/store/diary.js.map
