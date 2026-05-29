package com.mindecho.module.astrology.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 占星解读响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AstrologyInterpretResponseDTO {

    /** AI 生成的占星解读文本（自然语言，带陪伴感） */
    private String interpretation;

    /** 解读焦点（回显请求中的 focus） */
    private String focus;

    /** 解读类型：NATAL / SYNASTRY / TRANSIT */
    private String interpretType;

    /**
     * 是否使用了用户 Memory（用于前端展示"已融合你的情绪历史"等提示）
     */
    private boolean memoryFused;

    /**
     * 是否使用了 RAG 知识（用于前端展示"已检索占星知识库"等提示）
     */
    private boolean ragFused;
}

