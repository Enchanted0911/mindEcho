package com.mindecho.common.util;

import java.util.UUID;

/**
 * 用户上下文（ThreadLocal）
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

