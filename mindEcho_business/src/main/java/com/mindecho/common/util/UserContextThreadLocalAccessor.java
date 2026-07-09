package com.mindecho.common.util;

import io.micrometer.context.ThreadLocalAccessor;

import java.util.UUID;

/**
 * 将 {@link UserContext} 的 userId 注册到 Micrometer Context Propagation 机制中，
 * 实现 Reactor 异步线程切换时 ThreadLocal 值的自动传播。
 *
 * <h3>工作原理</h3>
 * <ol>
 *   <li>当 Reactor 操作链执行 {@code contextWrite} 时，本 Accessor 的
 *       {@link #getValue()} 会被调用，从当前线程的 ThreadLocal 读取 userId，
 *       存入 Reactor Context</li>
 *   <li>当 Reactor 切换到新线程（如 {@code boundedElastic}）执行操作时，
 *       本 Accessor 的 {@link #setValue(UUID)} 会被调用，将 userId 恢复到新线程的
 *       ThreadLocal 中</li>
 *   <li>操作完成后，{@link #reset()} 被调用，清理 ThreadLocal，防止线程池复用时的上下文泄漏</li>
 * </ol>
 *
 * <h3>使用场景</h3>
 * <p>解决 Spring AI Tool 回调在 Reactor {@code boundedElastic} 线程池中执行时，
 * 通过 {@code UserContext.getUserId()} 获取不到 userId 的问题。
 *
 * <h3>前置条件</h3>
 * <p>需要在 {@code META-INF/services/io.micrometer.context.ThreadLocalAccessor} 文件中
 * 注册本类，以便 Reactor 自动发现并使用。
 *
 * @see UserContext
 */
public class UserContextThreadLocalAccessor implements ThreadLocalAccessor<UUID> {

    /** Reactor Context 中存储 userId 的 key */
    public static final String KEY = "mindecho.userId";

    @Override
    public Object key() {
        return KEY;
    }

    @Override
    public UUID getValue() {
        return UserContext.getUserId();
    }

    @Override
    public void setValue(UUID value) {
        UserContext.setUserId(value);
    }

    @Override
    public void reset() {
        UserContext.clear();
    }
}

