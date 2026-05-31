package com.mindecho.module.chat.advisor;

import com.mindecho.module.emotion.service.EmotionService;
import com.mindecho.module.memory.service.MemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;

import java.util.UUID;

/**
 * 记忆提取 Advisor（AFTER_MODEL 阶段）
 *
 * <p>在模型响应返回后，异步执行以下操作：
 * <ol>
 *   <li>调用 {@link MemoryService#extractAndSaveMemoryAsync} 提取并保存长期记忆</li>
 *   <li>调用 {@link EmotionService#analyzeEmotionAndSaveMemoryAsync} 分析情绪（可选）</li>
 * </ol>
 *
 * <p>整个过程异步执行，不阻塞主流程响应。
 *
 * <p>实现 {@link BaseAdvisor}：仅覆写 {@code after()} 方法，{@code before()} 直接透传。
 */
@Slf4j
@RequiredArgsConstructor
public class MemoryExtractionAdvisor implements BaseAdvisor {

    /** Advisor 排序：在所有其他 Advisor 之后执行（包括 MessageChatMemoryAdvisor） */
    public static final int ORDER = 100;

    /** AdvisedRequest 上下文中存储 userId 的 key（由 LongTermMemoryAdvisor 定义，共享使用） */
    public static final String USER_ID_KEY = LongTermMemoryAdvisor.USER_ID_KEY;

    private final MemoryService memoryService;
    private final EmotionService emotionService;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public String getName() {
        return "MemoryExtractionAdvisor";
    }

    // ─── BaseAdvisor 实现 ─────────────────────────────────────────────────────

    /**
     * 模型调用前：直接透传（记忆提取不需要修改请求）
     */
    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        return request;
    }

    /**
     * 模型调用后：异步触发记忆提取与情绪分析
     */
    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        try {
            // 提取 userId
            Object userIdObj = response.context().get(USER_ID_KEY);
            if (userIdObj == null) {
                // 从请求上下文中查找（response.context() 应继承自 request.context()）
                log.debug("MemoryExtractionAdvisor: no userId in response context, skipping memory extraction");
                return response;
            }

            UUID userId = parseUserId(userIdObj);
            if (userId == null) return response;

            // 提取用户输入和 AI 回复文本
            String userMessage = extractUserMessage(response);
            String aiResponse = extractAiResponse(response);

            if (userMessage != null && !userMessage.isBlank()) {
                // 异步提取记忆（不阻塞响应流）
                triggerMemoryExtraction(userId, userMessage, aiResponse);
            }
        } catch (Exception e) {
            log.warn("MemoryExtractionAdvisor: post-processing failed: {}", e.getMessage());
        }
        return response;
    }

    // ─── 异步任务 ─────────────────────────────────────────────────────────────

    /**
     * 异步触发记忆提取
     *
     * <p>MemoryService.extractAndSaveMemoryAsync 本身已标注 @Async，这里直接调用即可。
     */
    private void triggerMemoryExtraction(UUID userId, String userMessage, String aiResponse) {
        try {
            memoryService.extractAndSaveMemoryAsync(userId, userMessage, aiResponse != null ? aiResponse : "");
            log.debug("MemoryExtractionAdvisor: triggered memory extraction for userId={}", userId);
        } catch (Exception e) {
            log.warn("MemoryExtractionAdvisor: memory extraction trigger failed: {}", e.getMessage());
        }
    }

    // ─── 工具方法 ─────────────────────────────────────────────────────────────

    private UUID parseUserId(Object userIdObj) {
        try {
            return userIdObj instanceof UUID u ? u : UUID.fromString(userIdObj.toString());
        } catch (Exception e) {
            log.warn("MemoryExtractionAdvisor: invalid userId={}", userIdObj);
            return null;
        }
    }

    /**
     * 从响应上下文中找到本次对话的用户消息
     *
     * <p>Spring AI 在 Advisor 链执行过程中，response.context() 中包含请求信息，
     * 但用户消息需要从 prompt 中提取。这里通过响应上下文中的用户输入 key 获取。
     */
    private String extractUserMessage(ChatClientResponse response) {
        // 从上下文中读取由 ChatService 预先存储的用户消息
        Object msgObj = response.context().get("mindecho_userMessage");
        return msgObj != null ? msgObj.toString() : null;
    }

    /**
     * 从 ChatResponse 中提取 AI 回复文本
     */
    private String extractAiResponse(ChatClientResponse response) {
        try {
            if (response.chatResponse() != null
                    && response.chatResponse().getResult() != null
                    && response.chatResponse().getResult().getOutput() != null) {
                return response.chatResponse().getResult().getOutput().getText();
            }
        } catch (Exception e) {
            log.debug("MemoryExtractionAdvisor: failed to extract AI response text: {}", e.getMessage());
        }
        return null;
    }
}

