package com.mindecho.module.emotion.controller;

import com.mindecho.common.result.Result;
import com.mindecho.common.util.UserContext;
import com.mindecho.module.emotion.dto.EmotionAnalyzeRequest;
import com.mindecho.module.emotion.dto.EmotionAnalyzeResponse;
import com.mindecho.module.emotion.service.EmotionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 情绪分析 Controller
 */
@RestController
@RequestMapping("/emotion")
@RequiredArgsConstructor
public class EmotionController {

    private final EmotionService emotionService;

    /**
     * 情绪分析接口
     * POST /api/emotion/analyze
     */
    @PostMapping("/analyze")
    public Result<EmotionAnalyzeResponse> analyze(@Valid @RequestBody EmotionAnalyzeRequest request) {
        String userId = UserContext.getUserId();
        return Result.success(emotionService.analyze(userId, request));
    }
}

