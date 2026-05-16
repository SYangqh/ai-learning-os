package com.learningos.modules.llm.service;

import com.learningos.common.exception.AppException;
import com.learningos.modules.llm.entity.LlmProvider;
import com.learningos.modules.llm.entity.UserLlmPreference;
import com.learningos.modules.llm.repository.LlmProviderRepository;
import com.learningos.modules.llm.repository.UserLlmCredentialRepository;
import com.learningos.modules.llm.repository.UserLlmPreferenceRepository;
import com.learningos.modules.observability.service.ObservabilityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * 动态 LLM 聊天服务：根据 userId 或直接传入的 apiKey 调用对应 Provider。
 *
 * <p>支持两种模式：
 * <ol>
 *   <li>BYOK-stored：从 DB 解密用户存储的 API Key，按用户偏好选 provider/model</li>
 *   <li>BYOK-inline：前端直接传入 api_key（向下兼容旧流程，不推荐生产使用）</li>
 * </ol>
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicChatService {

    private final LlmProviderRepository providerRepository;
    private final UserLlmPreferenceRepository preferenceRepository;
    private final LlmCredentialService credentialService;
    private final ObjectMapper objectMapper;
    private final ObservabilityService observabilityService;

    // RestClient 实例（无状态，线程安全）
    private final RestClient restClient = RestClient.create();

    /**
     * 核心聊天方法（向下兼容重载），sessionId 为 null。
     */
    public String chat(UUID userId, String inlineKey, List<Map<String, String>> messages) {
        return chat(userId, null, inlineKey, messages);
    }

    /**
     * 核心聊天方法：给定消息列表，返回 assistant 的回复文本，并异步记录 token 消耗。
     *
     * @param userId     当前用户（从 JWT 获取），用于查找偏好和凭据
     * @param sessionId  当前学习会话 ID（可为 null），用于 token 归因
     * @param inlineKey  前端直传的 API Key（可为 null；优先 DB 存储的凭据）
     * @param messages   [{"role":"system",...},{"role":"user",...},...] 格式的消息列表
     */
    @SuppressWarnings("unchecked")
    public String chat(UUID userId, UUID sessionId, String inlineKey, List<Map<String, String>> messages) {
        ChatContext ctx = resolveContext(userId, inlineKey);
        LlmProvider provider = ctx.provider();
        String model = ctx.model();
        String apiKey = ctx.apiKey();

        ChatCallResult result = switch (provider.getType()) {
            case "ANTHROPIC"     -> callAnthropic(apiKey, model, messages, userId, sessionId, provider.getKey());
            case "OPENAI_COMPAT" -> callOpenAiCompat(apiKey, provider.getBaseUrl(), model, messages,
                                                      userId, sessionId, provider.getKey());
            default -> throw AppException.internal("不支持的 provider 类型: " + provider.getType());
        };

        // 异步记录 token 消耗（不阻塞主流程）
        if (result.promptTokens() > 0 || result.completionTokens() > 0) {
            observabilityService.recordTokenUsage(userId, sessionId,
                    provider.getKey(), model,
                    result.promptTokens(), result.completionTokens());
        }

        return result.text();
    }

    // ─── Anthropic Messages API ────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private ChatCallResult callAnthropic(String apiKey, String model,
                                          List<Map<String, String>> messages,
                                          UUID userId, UUID sessionId, String providerKey) {
        String systemPrompt = messages.stream()
                .filter(m -> "system".equals(m.get("role")))
                .map(m -> m.get("content"))
                .findFirst().orElse("");

        List<Map<String, String>> userMessages = messages.stream()
                .filter(m -> !"system".equals(m.get("role")))
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 2048);
        if (!systemPrompt.isEmpty()) body.put("system", systemPrompt);
        body.put("messages", userMessages);

        try {
            Map<String, Object> response = restClient.post()
                    .uri("https://api.anthropic.com/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
            String text = (String) content.get(0).get("text");

            // 提取 token 计数
            int promptTokens = 0, completionTokens = 0;
            Map<String, Object> usage = (Map<String, Object>) response.get("usage");
            if (usage != null) {
                promptTokens     = toInt(usage.get("input_tokens"));
                completionTokens = toInt(usage.get("output_tokens"));
            }
            return new ChatCallResult(text, promptTokens, completionTokens);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Anthropic API call failed: {}", e.getMessage());
            observabilityService.recordLlmError(userId, sessionId, providerKey, model,
                    "HTTP_ERROR", e.getMessage(),
                    Map.of("provider", providerKey, "model", model, "messageCount", messages.size()));
            throw AppException.internal("AI 服务暂时不可用，请稍后重试");
        }
    }

    // ─── OpenAI 兼容 API ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private ChatCallResult callOpenAiCompat(String apiKey, String baseUrl, String model,
                                             List<Map<String, String>> messages,
                                             UUID userId, UUID sessionId, String providerKey) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "max_tokens", 2048
        );

        try {
            Map<String, Object> response = restClient.post()
                    .uri(baseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String text = (String) message.get("content");

            // 提取 token 计数
            int promptTokens = 0, completionTokens = 0;
            Map<String, Object> usage = (Map<String, Object>) response.get("usage");
            if (usage != null) {
                promptTokens     = toInt(usage.get("prompt_tokens"));
                completionTokens = toInt(usage.get("completion_tokens"));
            }
            return new ChatCallResult(text, promptTokens, completionTokens);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("OpenAI compat API call failed: {}", e.getMessage());
            observabilityService.recordLlmError(userId, sessionId, providerKey, model,
                    "HTTP_ERROR", e.getMessage(),
                    Map.of("provider", providerKey, "model", model, "messageCount", messages.size()));
            throw AppException.internal("AI 服务暂时不可用，请稍后重试");
        }
    }

    // ─── 解析调用上下文（provider / model / apiKey）──────────────────────────────

    private ChatContext resolveContext(UUID userId, String inlineKey) {
        if (userId != null) {
            UserLlmPreference pref = preferenceRepository.findById(userId).orElse(null);
            if (pref != null && pref.getChatProviderKey() != null) {
                try {
                    String storedKey = credentialService.decryptApiKey(userId, pref.getChatProviderKey());
                    LlmProvider provider = providerRepository.findByKey(pref.getChatProviderKey())
                            .orElseThrow();
                    String model = pref.getChatModelName() != null
                            ? pref.getChatModelName()
                            : defaultModel(provider.getKey());
                    return new ChatContext(provider, model, storedKey);
                } catch (Exception ignored) {
                }
            }

            for (String provKey : List.of("anthropic", "deepseek", "openai", "alibaba", "zhipu")) {
                String foundKey = tryGetKey(userId, provKey);
                if (foundKey != null) {
                    LlmProvider provider = providerRepository.findByKey(provKey).orElse(null);
                    if (provider != null) {
                        log.debug("Using stored credential: provider={}", provKey);
                        return new ChatContext(provider, defaultModel(provKey), foundKey);
                    }
                }
            }
        }

        if (inlineKey != null && !inlineKey.isBlank()) {
            if (inlineKey.startsWith("sk-ant-")) {
                LlmProvider provider = providerRepository.findByKey("anthropic")
                        .orElseThrow(() -> AppException.internal("Anthropic provider 未配置"));
                return new ChatContext(provider, "claude-sonnet-4-5", inlineKey);
            } else {
                LlmProvider provider = providerRepository.findByKey("openai")
                        .orElseThrow(() -> AppException.internal("OpenAI provider 未配置"));
                return new ChatContext(provider, "gpt-4o", inlineKey);
            }
        }

        throw AppException.badRequest("请先配置 API Key（通过 /api/llm/credentials 或在请求中传入 api_key）");
    }

    /**
     * 测试指定 provider + apiKey 是否连通（发送一条极简消息，不保存凭据）。
     */
    public void testConnection(String providerKey, String apiKey) {
        LlmProvider provider = providerRepository.findByKey(providerKey)
                .orElseThrow(() -> AppException.badRequest("不支持的 provider: " + providerKey));
        List<Map<String, String>> testMessages = List.of(Map.of("role", "user", "content", "Hi"));
        try {
            switch (provider.getType()) {
                case "ANTHROPIC"     -> callAnthropic(apiKey, defaultModel(providerKey), testMessages, null, null, providerKey);
                case "OPENAI_COMPAT" -> callOpenAiCompat(apiKey, provider.getBaseUrl(), defaultModel(providerKey), testMessages, null, null, providerKey);
                default -> throw AppException.internal("不支持的 provider 类型: " + provider.getType());
            }
        } catch (AppException e) {
            log.warn("testConnection failed for provider {}: {}", providerKey, e.getMessage());
            throw AppException.badRequest("API Key 验证失败，请确认服务商选择正确且 Key 有效");
        }
    }

    private String tryGetKey(UUID userId, String provKey) {
        try {
            return credentialService.decryptApiKey(userId, provKey);
        } catch (Exception e) {
            return null;
        }
    }

    private String defaultModel(String providerKey) {
        return switch (providerKey) {
            case "anthropic" -> "claude-sonnet-4-5";
            case "deepseek"  -> "deepseek-chat";
            case "openai"    -> "gpt-4o";
            case "alibaba"   -> "qwen-plus";
            case "zhipu"     -> "glm-4";
            default          -> "gpt-4o";
        };
    }

    private int toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return 0; }
    }

    private record ChatContext(LlmProvider provider, String model, String apiKey) {}

    private record ChatCallResult(String text, int promptTokens, int completionTokens) {}
}