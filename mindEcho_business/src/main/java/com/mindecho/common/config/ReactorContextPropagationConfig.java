package com.mindecho.common.config;

import io.micrometer.context.ContextRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Hooks;

/**
 * Reactor Context 自动传播配置
 *
 * <p>启用 Reactor 的自动上下文传播机制，使 ThreadLocal 值（如 {@link com.mindecho.common.util.UserContext}
 * 中的 userId）能够在 Reactor 异步线程切换时自动传播到新线程。
 *
 * <h3>解决的问题</h3>
 * <p>Spring AI 的 Tool 回调在 Reactor {@code boundedElastic} 线程池中执行，
 * 而 {@code UserContext} 基于 ThreadLocal 存储 userId。
 * 默认情况下，Reactor 线程切换不会自动传播 ThreadLocal 值，
 * 导致 Tool 方法中 {@code UserContext.getUserId()} 返回 null。
 *
 * <h3>工作原理</h3>
 * <ol>
 *   <li>{@link Hooks#enableAutomaticContextPropagation()} 开启自动传播</li>
 *   <li>当 {@code Mono/Flux.subscribe()} 在 HTTP 请求线程上调用时，
 *       Reactor 会通过 {@link ContextRegistry} 找到所有注册的
 *       {@link io.micrometer.context.ThreadLocalAccessor}（包括我们的
 *       {@link com.mindecho.common.util.UserContextThreadLocalAccessor}），
 *       将当前线程的 ThreadLocal 值捕获并存入 Reactor Context</li>
 *   <li>当 Reactor 操作切换到 {@code boundedElastic} 等异步线程时，
 *       Reactor 会自动将 Context 中的值恢复到新线程的 ThreadLocal 中</li>
 *   <li>操作完成后自动清理 ThreadLocal，防止线程池复用时的上下文泄漏</li>
 * </ol>
 *
 * @see com.mindecho.common.util.UserContextThreadLocalAccessor
 * @see com.mindecho.common.util.UserContext
 */
@Slf4j
@Configuration
public class ReactorContextPropagationConfig {

    @PostConstruct
    public void init() {
        // 1. 确保 ContextRegistry 已加载所有 ThreadLocalAccessor（通过 SPI）
        ContextRegistry.getInstance().loadThreadLocalAccessors();

        // 2. 开启 Reactor 自动上下文传播
        //    此后所有 Mono/Flux 的操作链会自动捕获当前线程的 ThreadLocal 值
        //    并在异步线程切换时恢复
        Hooks.enableAutomaticContextPropagation();

        log.info("Reactor automatic context propagation enabled");
    }
}

