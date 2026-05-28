"use strict";
const common_vendor = require("../common/vendor.js");
const api_personality = require("../api/personality.js");
const usePersonalityStore = common_vendor.defineStore("personality", () => {
  const list = common_vendor.ref([]);
  const loaded = common_vendor.ref(false);
  const loading = common_vendor.ref(false);
  const femaleList = common_vendor.computed(() => list.value.filter((p) => p.gender === "female"));
  const maleList = common_vendor.computed(() => list.value.filter((p) => p.gender === "male"));
  function findByCode(code) {
    return list.value.find((p) => p.code === code) ?? null;
  }
  async function ensureLoaded() {
    if (loaded.value || loading.value)
      return;
    loading.value = true;
    try {
      list.value = await api_personality.getPersonalityList();
      loaded.value = true;
    } catch (e) {
      common_vendor.index.__f__("error", "at store/personality.ts:34", "Load personalities failed:", e);
    } finally {
      loading.value = false;
    }
  }
  async function refresh() {
    loaded.value = false;
    await ensureLoaded();
  }
  return {
    list,
    loaded,
    loading,
    femaleList,
    maleList,
    findByCode,
    ensureLoaded,
    refresh
  };
});
exports.usePersonalityStore = usePersonalityStore;
//# sourceMappingURL=../../.sourcemap/mp-weixin/store/personality.js.map
