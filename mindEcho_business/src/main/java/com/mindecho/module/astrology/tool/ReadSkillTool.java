package com.mindecho.module.astrology.tool;

import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * read_skill 工具实现
 *
 * <p>配合 {@link SkillRegistry} 实现渐进式技能披露（Progressive Disclosure）。
 * 当模型在系统提示中发现可用技能列表后，可通过调用此工具加载某个技能的完整 SKILL.md，
 * 从而了解该技能的详细说明和可用工具列表，再按需调用对应工具。
 *
 * <p>调用示例：
 * <pre>
 * read_skill("astrology")
 * → 返回 skills/astrology/SKILL.md 的完整内容
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReadSkillTool {

    private final SkillRegistry skillRegistry;

    /**
     * 读取指定技能的完整 SKILL.md 内容
     *
     * @param skillName 技能名称（与系统提示中技能列表的 name 字段一致，如 "astrology"）
     * @return SKILL.md 完整内容，或错误提示
     */
    @Tool(name = "read_skill",
          description = "读取指定技能的完整说明文档（SKILL.md），了解该技能的功能、使用方法和可用工具。" +
                        "当你判断用户的请求与某个技能相关时，先调用此工具获取技能详细说明，再使用对应工具完成任务。")
    public String readSkill(
            @ToolParam(description = "技能名称，如 astrology（占星）") String skillName) {
        if (skillName == null || skillName.isBlank()) {
            return "请提供有效的技能名称。可用技能已在系统提示中列出。";
        }

        String name = skillName.trim();
        log.info("[ReadSkillTool] Loading skill: {}", name);

        if (!skillRegistry.contains(name)) {
            return String.format("技能 '%s' 不存在。请检查技能名称是否正确。", name);
        }

        try {
            String content = skillRegistry.readSkillContent(name);
            log.debug("[ReadSkillTool] Skill '{}' loaded, length={}", name, content != null ? content.length() : 0);
            return content != null ? content : "技能文档为空。";
        } catch (Exception e) {
            log.warn("[ReadSkillTool] Failed to read skill '{}': {}", name, e.getMessage());
            return String.format("读取技能 '%s' 失败：%s", name, e.getMessage());
        }
    }
}

