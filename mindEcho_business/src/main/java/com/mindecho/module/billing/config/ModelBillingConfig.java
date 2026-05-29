package com.mindecho.module.billing.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型计费配置
 *
 * <p>维护各 AI 模型的计费倍率（乘以 100 存储为整数，方便 DB 存储和计算）。
 * 例如：倍率 1x = 100，倍率 1.5x = 150，倍率 3x = 300
 *
 * <p>后续可改造为从 DB/配置中心动态加载，现阶段内存硬编码。
 */
@Slf4j
@Component
public class ModelBillingConfig {

    /**
     * 模型倍率表（key=模型名称前缀或完整名称，value=倍率*100）
     * 匹配规则：先精确匹配，再前缀匹配
     */
    private static final Map<String, Integer> MODEL_MULTIPLIER_MAP;

    static {
        MODEL_MULTIPLIER_MAP = new ConcurrentHashMap<>();
        // DeepSeek 系列（本项目当前使用）
        MODEL_MULTIPLIER_MAP.put("deepseek-chat",      100);  // 1x（等同 GPT-4o-mini 级别）
        MODEL_MULTIPLIER_MAP.put("deepseek-reasoner",  150);  // 1.5x
        // OpenAI 系列
        MODEL_MULTIPLIER_MAP.put("gpt-4o-mini",        100);  // 1x
        MODEL_MULTIPLIER_MAP.put("gpt-4.1-mini",       150);  // 1.5x
        MODEL_MULTIPLIER_MAP.put("gpt-4o",             300);  // 3x
        MODEL_MULTIPLIER_MAP.put("gpt-4.1",            300);  // 3x
        MODEL_MULTIPLIER_MAP.put("gpt-4",              300);  // 3x
        // Anthropic 系列
        MODEL_MULTIPLIER_MAP.put("claude-3-haiku",     150);  // 1.5x
        MODEL_MULTIPLIER_MAP.put("claude-3-5-sonnet",  300);  // 3x Claude Sonnet
        MODEL_MULTIPLIER_MAP.put("claude-3-7-sonnet",  300);  // 3x Claude Sonnet
        MODEL_MULTIPLIER_MAP.put("claude-3-opus",      500);  // 5x Claude Opus
        // 默认（未知模型）
        MODEL_MULTIPLIER_MAP.put("default",            150);  // 1.5x
    }

    /**
     * 获取模型倍率（*100 整数）
     * 先精确匹配，再按前缀匹配，最后 fallback 到 default
     *
     * @param modelName 模型名称
     * @return 倍率*100
     */
    public int getMultiplier(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return MODEL_MULTIPLIER_MAP.getOrDefault("default", 150);
        }

        String lower = modelName.toLowerCase();

        // 精确匹配
        if (MODEL_MULTIPLIER_MAP.containsKey(lower)) {
            return MODEL_MULTIPLIER_MAP.get(lower);
        }

        // 前缀匹配（从最长到最短）
        return MODEL_MULTIPLIER_MAP.entrySet().stream()
                .filter(e -> !"default".equals(e.getKey()) && lower.startsWith(e.getKey()))
                .max(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .orElseGet(() -> {
                    log.debug("Unknown model '{}', using default multiplier", modelName);
                    return MODEL_MULTIPLIER_MAP.getOrDefault("default", 150);
                });
    }

    /**
     * 动态更新模型倍率（支持运营后台调用）
     */
    public void updateMultiplier(String modelName, int multiplier) {
        MODEL_MULTIPLIER_MAP.put(modelName.toLowerCase(), multiplier);
        log.info("Model multiplier updated: model={}, multiplier={}", modelName, multiplier);
    }

    /**
     * 计算最终积分（含倍率）
     * finalPoints = (basePoints + contextPoints) * multiplier / 100
     */
    public long calcFinalPoints(long basePoints, long contextPoints, int multiplier) {
        return (basePoints + contextPoints) * multiplier / 100;
    }
}

