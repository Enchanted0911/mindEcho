package com.mindecho.module.personality.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mindecho.module.personality.dto.PersonalityDTO;
import com.mindecho.module.personality.entity.AiPersonality;
import com.mindecho.module.personality.mapper.AiPersonalityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 人格服务（从数据库动态加载）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalityService {

    private final AiPersonalityMapper personalityMapper;

    /**
     * 获取所有启用的人格列表（按 sort_order 排序）
     * 使用 Spring Cache 缓存，人格配置不频繁变化
     */
    @Cacheable(value = "personalities", key = "'all'", unless = "#result.isEmpty()")
    public List<PersonalityDTO> listAll() {
        List<AiPersonality> list = personalityMapper.selectList(
                new LambdaQueryWrapper<AiPersonality>()
                        .eq(AiPersonality::getEnabled, 1)
                        .orderByAsc(AiPersonality::getSortOrder)
        );
        return list.stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * 根据 code 获取人格详情（含 systemPrompt，用于 AI 调用）
     */
    public AiPersonality getByCode(String code) {
        if (code == null || code.isBlank()) {
            return getDefaultPersonality();
        }
        AiPersonality p = personalityMapper.selectOne(
                new LambdaQueryWrapper<AiPersonality>()
                        .eq(AiPersonality::getCode, code)
                        .eq(AiPersonality::getEnabled, 1)
        );
        return p != null ? p : getDefaultPersonality();
    }

    /**
     * 获取默认人格（gentle_female / 小柔）
     */
    private AiPersonality getDefaultPersonality() {
        AiPersonality p = personalityMapper.selectOne(
                new LambdaQueryWrapper<AiPersonality>()
                        .eq(AiPersonality::getCode, "gentle_female")
                        .eq(AiPersonality::getEnabled, 1)
        );
        if (p == null) {
            // 兜底：取排序第一的
            p = personalityMapper.selectOne(
                    new LambdaQueryWrapper<AiPersonality>()
                            .eq(AiPersonality::getEnabled, 1)
                            .orderByAsc(AiPersonality::getSortOrder)
                            .last("LIMIT 1")
            );
        }
        return p;
    }

    /**
     * 获取默认人格 code
     */
    public String getDefaultCode() {
        AiPersonality p = getDefaultPersonality();
        return p != null ? p.getCode() : "gentle_female";
    }

    private PersonalityDTO toDTO(AiPersonality p) {
        return PersonalityDTO.builder()
                .code(p.getCode())
                .name(p.getName())
                .gender(p.getGender())
                .style(p.getStyle())
                .emoji(p.getEmoji())
                .description(p.getDescription())
                .build();
    }
}

