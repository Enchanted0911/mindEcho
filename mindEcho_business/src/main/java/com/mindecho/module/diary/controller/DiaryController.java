package com.mindecho.module.diary.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mindecho.common.result.Result;
import com.mindecho.common.result.ResultCode;
import com.mindecho.common.util.UserContext;
import com.mindecho.module.diary.dto.DiaryEntryDTO;
import com.mindecho.module.diary.dto.SaveDiaryRequest;
import com.mindecho.module.diary.service.DiaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 情绪日记 Controller
 */
@RestController
@RequestMapping("/diary")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;

    /**
     * 保存日记
     * POST /api/diary/save
     */
    @PostMapping("/save")
    public Result<DiaryEntryDTO> saveDiary(@Valid @RequestBody SaveDiaryRequest request) {
        String userId = UserContext.getUserId();
        return Result.success(diaryService.saveDiary(userId, request));
    }

    /**
     * 分页查询日记列表
     * GET /api/diary/list
     */
    @GetMapping("/list")
    public Result<IPage<DiaryEntryDTO>> getDiaryList(
            @RequestParam(name = "page", defaultValue = "1") Integer page,
            @RequestParam(name = "size", defaultValue = "20") Integer size) {
        String userId = UserContext.getUserId();
        return Result.success(diaryService.getDiaryList(userId, page, size));
    }

    /**
     * 查询某日日记
     * GET /api/diary/date/{date}
     */
    @GetMapping("/date/{date}")
    public Result<DiaryEntryDTO> getDiaryByDate(
            @PathVariable("date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        String userId = UserContext.getUserId();
        DiaryEntryDTO diary = diaryService.getDiaryByDate(userId, date);
        return Result.success(diary);
    }

    /**
     * 获取 AI 总结
     * GET /api/diary/{id}/ai-summary
     */
    @GetMapping("/{id}/ai-summary")
    public Result<DiaryEntryDTO> getAiSummary(@PathVariable("id") String id) {
        String userId = UserContext.getUserId();
        DiaryEntryDTO dto = diaryService.getAiSummary(userId, id);
        if (dto == null) {
            return Result.error(ResultCode.DIARY_NOT_FOUND);
        }
        return Result.success(dto);
    }
}

