package com.mindecho.module.diary.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mindecho.module.diary.dto.DiaryEntryDTO;
import com.mindecho.module.diary.dto.SaveDiaryRequest;
import com.mindecho.module.diary.entity.DiaryEntry;
import com.mindecho.module.diary.mapper.DiaryEntryMapper;
import com.mindecho.module.memory.service.MemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
    private final MemoryService memoryService;

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
    public DiaryEntryDTO saveDiary(String userId, SaveDiaryRequest request) {
        LocalDate date = request.getDiaryDate() != null ? request.getDiaryDate() : LocalDate.now();

        DiaryEntry existing = diaryEntryMapper.selectOne(
                new LambdaQueryWrapper<DiaryEntry>()
                        .eq(DiaryEntry::getUserId, userId)
                        .eq(DiaryEntry::getDiaryDate, date)
                        .eq(DiaryEntry::getDeleted, 0)
        );

        DiaryEntry saved;
        if (existing != null) {
            LambdaUpdateWrapper<DiaryEntry> updateWrapper = new LambdaUpdateWrapper<DiaryEntry>()
                    .eq(DiaryEntry::getId, existing.getId())
                    .eq(DiaryEntry::getUserId, userId)
                    .set(DiaryEntry::getEmotion, request.getEmotion())
                    .set(DiaryEntry::getEmotionIntensity, request.getEmotionIntensity())
                    .set(DiaryEntry::getContent, request.getContent())
                    .set(DiaryEntry::getWeather, request.getWeather())
                    .set(DiaryEntry::getAiSummary, null);
            diaryEntryMapper.update(null, updateWrapper);
            existing.setEmotion(request.getEmotion());
            existing.setEmotionIntensity(request.getEmotionIntensity());
            existing.setContent(request.getContent());
            existing.setWeather(request.getWeather());
            existing.setAiSummary(null);
            saved = existing;
        } else {
            DiaryEntry entry = new DiaryEntry();
            entry.setUserId(userId);
            entry.setDiaryDate(date);
            entry.setEmotion(request.getEmotion());
            entry.setEmotionIntensity(request.getEmotionIntensity());
            entry.setContent(request.getContent());
            entry.setWeather(request.getWeather());
            diaryEntryMapper.insert(entry);
            saved = entry;
        }

        saveDiaryEmotionMemoryAsync(userId, saved);

        return convertToDTO(saved);
    }

    /**
     * 获取 AI 总结（懒加载）
     */
    public DiaryEntryDTO getAiSummary(String userId, String diaryId) {
        DiaryEntry entry = diaryEntryMapper.selectById(diaryId);
        if (entry == null || !entry.getUserId().equals(userId) || Integer.valueOf(1).equals(entry.getDeleted())) {
            return null;
        }

        if (StringUtils.hasText(entry.getAiSummary())) {
            return convertToDTO(entry);
        }

        try {
            String prompt = String.format(AI_SUMMARY_PROMPT,
                    entry.getEmotion() != null ? entry.getEmotion() : "未记录",
                    entry.getContent() != null ? entry.getContent() : "无内容");

            String summary = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (StringUtils.hasText(summary)) {
                entry.setAiSummary(summary);
                LambdaUpdateWrapper<DiaryEntry> wrapper = new LambdaUpdateWrapper<DiaryEntry>()
                        .eq(DiaryEntry::getId, entry.getId())
                        .set(DiaryEntry::getAiSummary, summary);
                diaryEntryMapper.update(null, wrapper);

                String memContent = buildDiarySummaryMemoryContent(entry, summary);
                memoryService.saveMemory(userId, "summary", memContent, 9);
                log.debug("Diary AI summary saved to memory: userId={}, date={}", userId, entry.getDiaryDate());
            }
        } catch (Exception e) {
            log.error("AI summary generation failed", e);
        }

        return convertToDTO(entry);
    }

    /**
     * 分页查询日记列表
     */
    public IPage<DiaryEntryDTO> getDiaryList(String userId, Integer page, Integer size) {
        Page<DiaryEntry> pageParam = new Page<>(page, size);
        IPage<DiaryEntry> entryPage = diaryEntryMapper.selectPage(pageParam,
                new LambdaQueryWrapper<DiaryEntry>()
                        .eq(DiaryEntry::getUserId, userId)
                        .eq(DiaryEntry::getDeleted, 0)
                        .orderByDesc(DiaryEntry::getDiaryDate)
        );
        return entryPage.convert(this::convertToDTO);
    }

    /**
     * 获取某天的日记
     */
    public DiaryEntryDTO getDiaryByDate(String userId, LocalDate date) {
        DiaryEntry entry = diaryEntryMapper.selectOne(
                new LambdaQueryWrapper<DiaryEntry>()
                        .eq(DiaryEntry::getUserId, userId)
                        .eq(DiaryEntry::getDiaryDate, date)
                        .eq(DiaryEntry::getDeleted, 0)
        );
        return entry != null ? convertToDTO(entry) : null;
    }

    @Async
    public void saveDiaryEmotionMemoryAsync(String userId, DiaryEntry entry) {
        try {
            if (!StringUtils.hasText(entry.getContent())) return;

            String emotion = entry.getEmotion();
            if (!StringUtils.hasText(emotion) || "neutral".equalsIgnoreCase(emotion)) return;

            String dateStr = entry.getDiaryDate() != null ? entry.getDiaryDate().toString() : "";
            String snippet = entry.getContent().length() > 200
                    ? entry.getContent().substring(0, 200) + "…"
                    : entry.getContent();
            String content = String.format("[日记|%s|%s] %s", emotion, dateStr, snippet);

            int score = (entry.getEmotionIntensity() != null && entry.getEmotionIntensity() >= 7) ? 8 : 6;
            memoryService.saveMemory(userId, "emotion", content, score);
            log.debug("Diary emotion memory saved: userId={}, date={}, emotion={}", userId, dateStr, emotion);
        } catch (Exception e) {
            log.warn("Failed to save diary emotion memory: userId={}", userId, e);
        }
    }

    private String buildDiarySummaryMemoryContent(DiaryEntry entry, String aiSummary) {
        String dateStr = entry.getDiaryDate() != null ? entry.getDiaryDate().toString() : "";
        String emotion = StringUtils.hasText(entry.getEmotion()) ? entry.getEmotion() : "未记录";
        return String.format("[日记总结|%s|%s] %s", dateStr, emotion, aiSummary.trim());
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

