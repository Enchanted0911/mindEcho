package com.mindecho.module.diary.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mindecho.module.diary.dto.DiaryEntryDTO;
import com.mindecho.module.diary.dto.SaveDiaryRequest;
import com.mindecho.module.diary.entity.DiaryEntry;
import com.mindecho.module.diary.mapper.DiaryEntryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 情绪日记服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryService {

    private final DiaryEntryMapper diaryEntryMapper;
    private final ChatClient chatClient;

    private static final String AI_SUMMARY_PROMPT = """
            请为以下情绪日记生成一段温柔的 AI 总结（100字以内），帮助用户理解自己的情绪状态，
            并给出一句温暖的鼓励：

            情绪：%s
            日记内容：%s

            请以"今天，你..." 开头：
            """;

    /**
     * 保存或更新日记
     */
    public DiaryEntryDTO saveDiary(Long userId, SaveDiaryRequest request) {
        LocalDate date = request.getDiaryDate() != null ? request.getDiaryDate() : LocalDate.now();

        // 查找是否已有当天日记
        DiaryEntry existing = diaryEntryMapper.findByUserIdAndDate(userId, date);

        if (existing != null) {
            // 更新
            existing.setEmotion(request.getEmotion());
            existing.setEmotionIntensity(request.getEmotionIntensity());
            existing.setContent(request.getContent());
            existing.setWeather(request.getWeather());
            existing.setAiSummary(null); // 内容变化，重置 AI 总结
            diaryEntryMapper.updateById(existing);
            return convertToDTO(existing);
        }

        // 新建
        DiaryEntry entry = new DiaryEntry();
        entry.setUserId(userId);
        entry.setDiaryDate(date);
        entry.setEmotion(request.getEmotion());
        entry.setEmotionIntensity(request.getEmotionIntensity());
        entry.setContent(request.getContent());
        entry.setWeather(request.getWeather());
        diaryEntryMapper.insert(entry);

        return convertToDTO(entry);
    }

    /**
     * 获取 AI 总结（懒加载）
     */
    public DiaryEntryDTO getAiSummary(Long userId, Long diaryId) {
        DiaryEntry entry = diaryEntryMapper.selectById(diaryId);
        if (entry == null || !entry.getUserId().equals(userId)) {
            return null;
        }

        // 已有总结直接返回
        if (entry.getAiSummary() != null && !entry.getAiSummary().isEmpty()) {
            return convertToDTO(entry);
        }

        // 生成 AI 总结
        try {
            String prompt = String.format(AI_SUMMARY_PROMPT,
                    entry.getEmotion() != null ? entry.getEmotion() : "未记录",
                    entry.getContent() != null ? entry.getContent() : "无内容");

            String summary = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            entry.setAiSummary(summary);
            diaryEntryMapper.updateById(entry);
        } catch (Exception e) {
            log.error("AI summary generation failed", e);
        }

        return convertToDTO(entry);
    }

    /**
     * 分页查询日记列表
     */
    public IPage<DiaryEntryDTO> getDiaryList(Long userId, Integer page, Integer size) {
        Page<DiaryEntry> pageParam = new Page<>(page, size);
        IPage<DiaryEntry> entryPage = diaryEntryMapper.pageByUserId(pageParam, userId);
        return entryPage.convert(this::convertToDTO);
    }

    /**
     * 获取某天的日记
     */
    public DiaryEntryDTO getDiaryByDate(Long userId, LocalDate date) {
        DiaryEntry entry = diaryEntryMapper.findByUserIdAndDate(userId, date);
        return entry != null ? convertToDTO(entry) : null;
    }

    private DiaryEntryDTO convertToDTO(DiaryEntry entry) {
        return DiaryEntryDTO.builder()
                .id(entry.getId())
                .diaryDate(entry.getDiaryDate() != null ? entry.getDiaryDate().toString() : null)
                .emotion(entry.getEmotion())
                .emotionIntensity(entry.getEmotionIntensity())
                .content(entry.getContent())
                .aiSummary(entry.getAiSummary())
                .weather(entry.getWeather())
                .createdTime(entry.getCreatedTime())
                .updatedTime(entry.getUpdatedTime())
                .build();
    }
}

