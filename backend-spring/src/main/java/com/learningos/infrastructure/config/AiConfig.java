package com.learningos.infrastructure.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI ChatClient 配置。
 *
 * <p>使用 auto-configured {@link ChatClient.Builder}，
 * 具体 Provider（OpenAI / Anthropic）由 {@code application.yml} 中的配置决定。</p>
 */
@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
