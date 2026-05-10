package com.learningos.modules.llm.service;

import com.learningos.modules.llm.repository.LlmProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 动态 Embedding 服务：使用用户配置的 OpenAI 兼容 embedding 模型将文本向量化。
 *
 * <p>嵌入计算是 RAG 流程的底层操作，失败时静默返回 null（上层应优雅降级）。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicEmbeddingService {

    /** 默认 embedding 模型（text-embedding-3-small，1536 维） */
    private static final String DEFAULT_MODEL = "text-embedding-3-small";

    private final LlmProviderRepository providerRepository;
    private final LlmCredentialService credentialService;

    private final RestClient restClient = RestClient.create();

    /**
     * 将文本嵌入为 float 向量。失败时返回 null（调用方需做空检查）。
     *
     * @param userId 用户 ID，用于查找其 OpenAI 凭据
     * @param text   待嵌入的文本（建议 ≤ 8000 tokens）
     */
    @SuppressWarnings("unchecked")
    public float[] embed(UUID userId, String text) {
        try {
            EmbedContext ctx = resolveContext(userId);
            if (ctx == null) {
                log.debug("No embedding-capable credential found for user={}", userId);
                return null;
            }

            Map<String, Object> body = Map.of("model", ctx.model(), "input", text);

            Map<String, Object> response = restClient.post()
                    .uri(ctx.baseUrl() + "/embeddings")
                    .header("Authorization", "Bearer " + ctx.apiKey())
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            List<Double> embedding = (List<Double>) data.get(0).get("embedding");

            float[] result = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                result[i] = embedding.get(i).floatValue();
            }
            return result;

        } catch (Exception e) {
            log.warn("Embedding failed for userId={}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * 将 float[] 格式化为 PostgreSQL vector 字面量，例如 {@code [0.1,0.2,0.3]}.
     */
    public static String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    // ─── 内部：解析 embedding 调用上下文 ─────────────────────────────────────────

    private EmbedContext resolveContext(UUID userId) {
        // 优先使用用户配置的 OpenAI key
        try {
            String apiKey = credentialService.decryptApiKey(userId, "openai");
            String baseUrl = providerRepository.findByKey("openai")
                    .map(p -> p.getBaseUrl() != null ? p.getBaseUrl() : "https://api.openai.com/v1")
                    .orElse("https://api.openai.com/v1");
            return new EmbedContext(baseUrl, DEFAULT_MODEL, apiKey);
        } catch (Exception e) {
            log.debug("OpenAI credential not found for embedding, userId={}", userId);
            return null;
        }
    }

    private record EmbedContext(String baseUrl, String model, String apiKey) {}
}
