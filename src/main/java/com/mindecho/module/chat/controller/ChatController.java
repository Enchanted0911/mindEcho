package com.mindecho.module.chat.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mindecho.common.result.Result;
import com.mindecho.common.util.UserContext;
import com.mindecho.module.chat.dto.ChatMessageDTO;
import com.mindecho.module.chat.dto.ChatSessionDTO;
import com.mindecho.module.chat.dto.SendMessageRequest;
import com.mindecho.module.chat.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 聊天 Controller
 */
@Slf4j
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 发送消息（SSE 流式返回）
     * POST /api/chat/send
     */
    @PostMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(@Valid @RequestBody SendMessageRequest request) {
        Long userId = UserContext.getUserId();
        log.info("Chat send: userId={}, message={}", userId, request.getMessage());
        return chatService.sendMessage(userId, request);
    }

    /**
     * 获取会话列表
     * GET /api/chat/sessions
     */
    @GetMapping("/sessions")
    public Result<IPage<ChatSessionDTO>> getSessionList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Long userId = UserContext.getUserId();
        return Result.success(chatService.getSessionList(userId, page, size));
    }

    /**
     * 获取会话消息列表
     * GET /api/chat/sessions/{sessionId}/messages
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ChatMessageDTO>> getMessageList(@PathVariable Long sessionId) {
        Long userId = UserContext.getUserId();
        return Result.success(chatService.getMessageList(userId, sessionId));
    }

    /**
     * 删除会话
     * DELETE /api/chat/sessions/{sessionId}
     */
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable Long sessionId) {
        Long userId = UserContext.getUserId();
        chatService.deleteSession(userId, sessionId);
        return Result.success();
    }
}

