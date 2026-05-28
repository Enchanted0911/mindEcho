package com.mindecho.module.chat.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mindecho.common.result.Result;
import com.mindecho.common.util.UserContext;
import com.mindecho.module.chat.dto.ChatMessageDTO;
import com.mindecho.module.chat.dto.ChatSessionDTO;
import com.mindecho.module.chat.dto.EditMessageRequest;
import com.mindecho.module.chat.dto.SendMessageRequest;
import com.mindecho.module.chat.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
            @RequestParam(name = "page", defaultValue = "1") Integer page,
            @RequestParam(name = "size", defaultValue = "20") Integer size) {
        Long userId = UserContext.getUserId();
        return Result.success(chatService.getSessionList(userId, page, size));
    }

    /**
     * 分页获取会话消息列表（按时间倒序，用于前端懒加载）
     * GET /api/chat/sessions/{sessionId}/messages?page=1&size=20
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<IPage<ChatMessageDTO>> getMessageList(
            @PathVariable("sessionId") Long sessionId,
            @RequestParam(name = "page", defaultValue = "1") Integer page,
            @RequestParam(name = "size", defaultValue = "20") Integer size) {
        Long userId = UserContext.getUserId();
        return Result.success(chatService.getMessageList(userId, sessionId, page, size));
    }

    /**
     * 停止当前流式输出
     * POST /api/chat/stop
     */
    @PostMapping("/stop")
    public Result<Void> stopStreaming() {
        Long userId = UserContext.getUserId();
        log.info("Stop streaming: userId={}", userId);
        chatService.stopStreaming(userId);
        return Result.success();
    }

    /**
     * 编辑用户消息并重新发送（SSE 流式返回）
     * POST /api/chat/edit
     * 后端会删除该消息之后的所有消息，更新该消息内容，然后以新内容重新流式回复
     */
    @PostMapping(value = "/edit", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter editMessage(@Valid @RequestBody EditMessageRequest request) {
        Long userId = UserContext.getUserId();
        log.info("Edit message: userId={}, messageId={}", userId, request.getMessageId());
        return chatService.editMessage(userId, request);
    }

    /**
     * 删除会话
     * DELETE /api/chat/sessions/{sessionId}
     */
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable("sessionId") Long sessionId) {
        Long userId = UserContext.getUserId();
        chatService.deleteSession(userId, sessionId);
        return Result.success();
    }
}

