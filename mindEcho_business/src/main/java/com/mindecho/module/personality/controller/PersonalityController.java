package com.mindecho.module.personality.controller;

import com.mindecho.common.result.Result;
import com.mindecho.module.personality.dto.PersonalityDTO;
import com.mindecho.module.personality.service.PersonalityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 人格接口
 */
@RestController
@RequestMapping("/personality")
@RequiredArgsConstructor
public class PersonalityController {

    private final PersonalityService personalityService;

    /**
     * 获取所有可用的 AI 人格列表
     * GET /api/personality/list
     */
    @GetMapping("/list")
    public Result<List<PersonalityDTO>> list() {
        return Result.success(personalityService.listAll());
    }
}

