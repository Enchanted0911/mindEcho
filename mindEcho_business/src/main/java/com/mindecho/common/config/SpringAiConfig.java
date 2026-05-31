package com.mindecho.common.config;

import com.alibaba.cloud.ai.graph.skills.SpringAiSkillAdvisor;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.classpath.ClasspathSkillRegistry;
import com.mindecho.module.astrology.service.UserAstrologyContextBuilder;
import com.mindecho.module.astrology.tool.ReadSkillTool;
import com.mindecho.module.chat.advisor.LongTermMemoryAdvisor;
import com.mindecho.module.chat.advisor.MemoryExtractionAdvisor;
import com.mindecho.module.chat.advisor.MindEchoChatMemoryRepository;
import com.mindecho.module.emotion.service.EmotionService;
import com.mindecho.module.memory.service.MemoryService;
import com.mindecho.module.memory.service.MemoryVectorService;
import com.mindecho.module.memory.service.RerankService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Spring AI 配置
 *
 * <p>使用 Advisor 链实现以下功能：
 * <ol>
 *   <li>{@link LongTermMemoryAdvisor}：BEFORE_MODEL，注入长期记忆（BM25 + 向量召回）</li>
 *   <li>{@link MessageChatMemoryAdvisor}：注入/保存短期会话历史（最近 20 轮）</li>
 *   <li>{@link MemoryExtractionAdvisor}：AFTER_MODEL，异步提取并保存长期记忆</li>
 *   <li>{@link SpringAiSkillAdvisor}：技能渐进式披露（Skills）</li>
 * </ol>
 *
 * <p>Advisor 执行顺序（order 从小到大）：
 * <pre>
 * LongTermMemoryAdvisor (order=-10)
 *   → MessageChatMemoryAdvisor  (order=0，默认)
 *     → SpringAiSkillAdvisor    (order=...)
 *       → MemoryExtractionAdvisor (order=100)
 *         → LLM 调用
 * </pre>
 */
@Configuration
public class SpringAiConfig {

    /**
     * Skills 注册表：从 classpath:skills/ 加载所有技能定义
     *
     * <p>技能目录结构（src/main/resources/skills/）：
     * <pre>
     * skills/
     * └── astrology/
     *     └── SKILL.md  ← 占星技能定义
     * </pre>
     */
    @Bean
    public SkillRegistry skillRegistry() {
        return ClasspathSkillRegistry.builder()
                .classpathPath("skills")
                .build();
    }

    /**
     * 长期记忆注入 Advisor（BEFORE_MODEL）
     *
     * <p>在注入长期记忆的同时，额外从 user_astrology 表加载用户星盘背景，
     * 使主聊天模型能感知用户的占星基础信息。
     */
    @Bean
    public LongTermMemoryAdvisor longTermMemoryAdvisor(MemoryService memoryService,
                                                        MemoryVectorService memoryVectorService,
                                                        RerankService rerankService,
                                                        UserAstrologyContextBuilder astrologyContextBuilder) {
        return new LongTermMemoryAdvisor(memoryService, memoryVectorService, rerankService, astrologyContextBuilder);
    }

    /**
     * 记忆提取 Advisor（AFTER_MODEL）
     */
    @Bean
    public MemoryExtractionAdvisor memoryExtractionAdvisor(MemoryService memoryService,
                                                            EmotionService emotionService) {
        return new MemoryExtractionAdvisor(memoryService, emotionService);
    }

    /**
     * 基于 PostgreSQL 的短期会话记忆存储（使用现有 chat_message 表）
     *
     * <p>{@link MessageWindowChatMemory} 封装了消息窗口策略（最多保留 20 条消息），
     * 底层存储委托给 {@link MindEchoChatMemoryRepository}，实际读取来自 {@code chat_message} 表。
     */
    @Bean
    public MessageWindowChatMemory chatMemory(MindEchoChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(20)
                .build();
    }

    /**
     * 聊天功能专用 ChatClient，挂载完整的 Advisor 链。
     *
     * <p>Advisor 链设计：
     * <ul>
     *   <li>{@link LongTermMemoryAdvisor} (order=-10)：BEFORE 阶段注入长期记忆上下文</li>
     *   <li>{@link MessageChatMemoryAdvisor} (order=0)：注入最近 20 轮会话历史</li>
     *   <li>{@link MemoryExtractionAdvisor} (order=100)：AFTER 阶段异步提取记忆</li>
     *   <li>{@link SpringAiSkillAdvisor}：技能列表注入（用于渐进式工具披露）</li>
     * </ul>
     *
     * <p>默认工具：{@code read_skill}（始终可用，引导模型按需加载技能说明）。
     * 占星工具（astrology_calculate_natal 等）由 ChatService 在检测到占星关键词时动态附加。
     *
     * <p>注意：conversationId 在每次请求时通过 {@code chatClient.prompt().advisors(...)} 动态传入，
     * 不设置 defaultConversationId（每个用户会话有独立的 conversationId）。
     *
     * @see #astrologyChatClient(ChatModel) 占星解读专用 ChatClient（无 Advisor）
     */
    @Bean
    public ChatClient chatClient(@Qualifier("openAiChatModel") ChatModel chatModel,
                                 SkillRegistry skillRegistry,
                                 ReadSkillTool readSkillTool,
                                 LongTermMemoryAdvisor longTermMemoryAdvisor,
                                 MemoryExtractionAdvisor memoryExtractionAdvisor,
                                 MessageWindowChatMemory chatMemory) {

        // SpringAiSkillAdvisor：在系统提示中注入可用技能列表，引导模型按需调用 read_skill
        SpringAiSkillAdvisor skillAdvisor = SpringAiSkillAdvisor.builder()
                .skillRegistry(skillRegistry)
                .build();

        // MessageChatMemoryAdvisor：加载/保存短期会话历史
        // conversationId 在请求时动态传入（通过 AdvisorParams.CHAT_MEMORY_CONVERSATION_ID_KEY）
        MessageChatMemoryAdvisor chatMemoryAdvisor = MessageChatMemoryAdvisor
                .builder(chatMemory)
                .build();

        return ChatClient.builder(chatModel)
                .defaultAdvisors(
                        longTermMemoryAdvisor,   // order=-10: 注入长期记忆
                        chatMemoryAdvisor,        // order=0:   注入/保存会话历史
                        memoryExtractionAdvisor,  // order=100: 异步提取记忆
                        skillAdvisor              // 技能披露
                )
                // read_skill 工具（默认工具，始终可用）
                .defaultTools(readSkillTool)
                .build();
    }

    /**
     * 占星解读专用 ChatClient（裸客户端，不挂载任何 Advisor）。
     *
     * <p>占星解读（本命盘/和盘/流运）是一次性、无状态的 LLM 调用，不需要：
     * <ul>
     *   <li>会话历史注入（MessageChatMemoryAdvisor）</li>
     *   <li>记忆提取写入（MemoryExtractionAdvisor）</li>
     *   <li>长期记忆上下文（LongTermMemoryAdvisor）</li>
     *   <li>技能披露（SpringAiSkillAdvisor）</li>
     * </ul>
     * 上述上下文由 {@link com.mindecho.module.astrology.service.AstrologyAiService} 自行拼装进 systemPrompt。
     *
     * @see #chatClient(ChatModel, SkillRegistry, ReadSkillTool, LongTermMemoryAdvisor, MemoryExtractionAdvisor, MessageWindowChatMemory)
     *      聊天功能专用 ChatClient（带完整 Advisor 链）
     */
    @Bean
    public ChatClient astrologyChatClient(@Qualifier("openAiChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    /**
     * 自定义 WebClient.Builder，强制底层使用 HTTP/1.1 的 JDK HttpClient
     *
     * <p>解决 DeepSeek API 与 JDK HTTP/2 的兼容性问题（RST_STREAM Internal error）。
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

