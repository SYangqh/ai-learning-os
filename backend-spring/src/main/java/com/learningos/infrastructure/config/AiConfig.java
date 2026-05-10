package com.learningos.infrastructure.config;

import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 相关配置占位符。
 * ChatClient 已移除，所有 LLM 调用通过 DynamicChatService 的 RestClient 完成（BYOK 架构）。
 */
@Configuration
public class AiConfig {
}
