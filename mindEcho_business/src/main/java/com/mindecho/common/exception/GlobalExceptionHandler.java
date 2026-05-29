package com.mindecho.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindecho.common.result.Result;
import com.mindecho.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * <p>对 SSE 请求（Accept: text/event-stream 或 URL 含 /chat/send、/chat/edit）
 * 发生的异常，以 SSE 格式返回错误事件，避免破坏流式协议；
 * 其他请求统一返回 JSON 格式的 Result。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ObjectMapper objectMapper;

    private static final String[] SSE_PATH_PATTERNS = {"/chat/send", "/chat/edit", "/astrology/stream"};

    public GlobalExceptionHandler(@Qualifier("webObjectMapper") ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e,
                                                 HttpServletRequest request,
                                                 HttpServletResponse response) {
        log.warn("Business exception: code={}, message={}", e.getCode(), e.getMessage());
        if (isSseRequest(request)) {
            writeSseError(response, e.getCode(), e.getMessage());
            return null;
        }
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e,
                                                               HttpServletRequest request,
                                                               HttpServletResponse response) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("Validation failed: {}", message);
        if (isSseRequest(request)) {
            writeSseError(response, ResultCode.PARAM_ERROR.getCode(), message);
            return null;
        }
        return Result.error(ResultCode.PARAM_ERROR.getCode(), message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e,
                                                            HttpServletRequest request,
                                                            HttpServletResponse response) {
        Set<ConstraintViolation<?>> violations = e.getConstraintViolations();
        String message = violations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        log.warn("Constraint violation: {}", message);
        if (isSseRequest(request)) {
            writeSseError(response, ResultCode.PARAM_ERROR.getCode(), message);
            return null;
        }
        return Result.error(ResultCode.PARAM_ERROR.getCode(), message);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        log.error("Unexpected error", e);
        if (isSseRequest(request)) {
            writeSseError(response, ResultCode.SYSTEM_ERROR.getCode(), ResultCode.SYSTEM_ERROR.getMessage());
            return null;
        }
        return Result.error(ResultCode.SYSTEM_ERROR);
    }

    // ─── 私有辅助 ─────────────────────────────────────────────────────────────

    private boolean isSseRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            return true;
        }
        String uri = request.getRequestURI();
        for (String pattern : SSE_PATH_PATTERNS) {
            if (uri.contains(pattern)) return true;
        }
        return false;
    }

    private void writeSseError(HttpServletResponse response, int code, String message) {
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setStatus(HttpServletResponse.SC_OK);
        try {
            String errorData = objectMapper.writeValueAsString(Map.of("code", code, "message", message));
            response.getWriter().write("event: error\ndata: " + errorData + "\n\n");
            response.getWriter().flush();
        } catch (IOException ex) {
            log.error("Failed to write SSE error event", ex);
        }
    }
}

