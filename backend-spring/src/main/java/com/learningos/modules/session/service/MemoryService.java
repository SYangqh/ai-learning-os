package com.learningos.modules.session.service;

import com.learningos.modules.llm.service.DynamicEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 长期记忆服务（memory_embeddings）。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>{@link #remember}：将复盘/笔记内容向量化后异步写入长期记忆</li>
 *   <li>{@link #recall}：检索与当前话题最相关的历史记忆，格式化后注入 System Prompt</li>
 * </ul>
 *
 * <p>Embedding 不可用时静默降级，不抛出异常，保证主流程不受影响。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryService {

    private final DynamicEmbeddingService embeddingService;
    private final JdbcTemplate jdbc;

    // ─── Write ────────────────────────────────────────────────────────────────

    /**
     * 异步将内容向量化并持久化到 memory_embeddings。
     * 在 RETRO 节点完成时调用，不阻塞主流程。
     *
     * @param userId  用户 ID
     * @param content 复盘原文（通常是 RETRO 节点的 AI 总结回复）
     * @param source  来源标识（RETRO / ARTIFACT）
     * @param stageId 所属阶段 ID，可为 null
     * @param skillId 所属技能 ID，可为 null
     */
    @Async
    public void remember(UUID userId, String content, String source, UUID stageId, String skillId) {
        if (content == null || content.isBlank()) return;
        try {
            float[] emb = embeddingService.embed(userId, content);
            if (emb == null) {
                log.debug("Embedding unavailable, skipping memory write: userId={}", userId);
                return;
            }
            String vecStr = DynamicEmbeddingService.toVectorString(emb);
            jdbc.update(
                "INSERT INTO memory_embeddings (id, user_id, content, embedding, source, stage_id, skill_id) " +
                "VALUES (gen_random_uuid(), ?, ?, ?::vector, ?, ?, ?)",
                userId, content, vecStr, source, stageId, skillId
            );
            log.info("Memory written: userId={} source={} skillId={}", userId, source, skillId);
        } catch (Exception e) {
            log.warn("Failed to write memory for userId={}: {}", userId, e.getMessage());
        }
    }

    // ─── Read ─────────────────────────────────────────────────────────────────

    /**
     * 检索与当前话题最相关的历史记忆（余弦相似度 top-K）。
     *
     * <p>优先按 skillId 过滤；若 skillId 为 null 则全库检索。</p>
     * <p>Embedding 不可用或无记忆时返回空字符串（调用方无需判空）。</p>
     *
     * @param userId  用户 ID
     * @param query   当前话题/节点描述（用于向量检索）
     * @param skillId 技能 ID 过滤，可为 null
     * @param topK    返回条数（建议 2~3）
     * @return 格式化好的记忆注入片段，空时返回 ""
     */
    public String recall(UUID userId, String query, String skillId, int topK) {
        try {
            float[] emb = embeddingService.embed(userId, query);
            if (emb == null) return "";

            String vecStr = DynamicEmbeddingService.toVectorString(emb);
            List<String> items;
            if (skillId != null && !skillId.isBlank()) {
                items = jdbc.queryForList(
                    "SELECT content FROM memory_embeddings " +
                    "WHERE user_id = ? AND (skill_id = ? OR skill_id IS NULL) " +
                    "ORDER BY embedding <=> ?::vector LIMIT ?",
                    String.class, userId, skillId, vecStr, topK
                );
            } else {
                items = jdbc.queryForList(
                    "SELECT content FROM memory_embeddings " +
                    "WHERE user_id = ? " +
                    "ORDER BY embedding <=> ?::vector LIMIT ?",
                    String.class, userId, vecStr, topK
                );
            }

            if (items.isEmpty()) return "";

            StringBuilder sb = new StringBuilder(
                "\n【你的历史学习记忆（仅供参考，建立新旧知识连接）】\n");
            items.forEach(item -> sb.append("- ").append(item.replace("\n", " ").strip()).append("\n"));
            return sb.toString();

        } catch (Exception e) {
            log.warn("Memory recall failed for userId={}: {}", userId, e.getMessage());
            return "";
        }
    }
}
