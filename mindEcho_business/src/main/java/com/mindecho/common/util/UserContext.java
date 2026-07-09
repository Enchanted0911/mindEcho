package com.mindecho.common.util;

import java.util.UUID;

/**
 * 用户上下文（ThreadLocal）
 *
 * <p>配合 {@link UserContextThreadLocalAccessor} 实现 Reactor 异步线程的自动上下文传播。
 * 当 Reactor 线程切换时（如 {@code boundedElastic} 线程池），micrometer context-propagation
 * 会自动将 userId 从 Reactor Context 恢复到当前线程的 ThreadLocal 中，
 * 确保 Spring AI Tool 回调等方法仍能通过 {@link #getUserId()} 获取正确的用户 ID。
 *
 * @see UserContextThreadLocalAccessor
 */
public class UserContext {

    private static final ThreadLocal<UUID> USER_ID_HOLDER = new ThreadLocal<>();

    public static void setUserId(UUID userId) {
        USER_ID_HOLDER.set(userId);
    }

    public static UUID getUserId() {
        return USER_ID_HOLDER.get();
    }

    public static void clear() {
        USER_ID_HOLDER.remove();
    }
}

