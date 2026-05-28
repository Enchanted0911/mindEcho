package com.mindecho.common.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Spring AI 配置
 */
@Configuration
public class SpringAiConfig {

    /**
     * 配置 ChatClient Bean
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .build();
    }

    /**
     * 自定义 WebClient.Builder，强制底层使用 HTTP/1.1 的 JDK HttpClient
     * <p>
     * 根本原因：Spring AI 的 OpenAiChatAutoConfiguration#openAiApi() 通过
     * {@code ObjectProvider<WebClient.Builder>} 注入 WebClient.Builder，
     * 若容器中存在该 Bean，则直接使用；否则使用默认 Builder（默认走 Reactor Netty 或 JDK HTTP/2）。
     * JDK 内置 HttpClient 的 HTTP/2 实现与 DeepSeek API 存在兼容性问题：
     * 服务端会返回 RST_STREAM (Internal error)，导致流式 SSE 请求中断。
     * 通过此 Bean 将连接器换成 HTTP/1.1 的 JDK HttpClient 即可规避该问题。
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        JdkClientHttpConnector connector = new JdkClientHttpConnector(httpClient);
        return WebClient.builder().clientConnector(connector);
    }
}

