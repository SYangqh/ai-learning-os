package com.learningos.modules.rag.service;

import com.learningos.modules.llm.service.DynamicEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * RAG（检索增强生成）服务。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>Ingest：将文本块向量化并存入 {@code knowledge_chunks} 表</li>
 *   <li>Retrieve：给定查询文本，用余弦相似度召回 top-k 相关块</li>
 * </ul>
 *
 * <p>向量操作通过 JdbcTemplate + pgvector 原生 SQL 完成，避免 JPA 类型映射问题。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RagService {

    private final DynamicEmbeddingService embeddingService;
    private final JdbcTemplate jdbc;

    // ─── Ingest ───────────────────────────────────────────────────────────────

    /**
     * 将一段文本嵌入后存入知识库。
     *
     * @param userId    用户 ID（用于获取其 OpenAI 凭据做 embedding）
     * @param skillId   关联技能 ID（可为 null，表示全局通用知识）
     * @param title     块标题（摘要，用于调试/管理）
     * @param content   原始文本内容
     * @param sourceRef 来源引用（URL 或文件路径），可为 null
     */
    public void ingest(UUID userId, String skillId, String title, String content, String sourceRef) {
        float[] emb = embeddingService.embed(userId, content);
        if (emb == null) {
            log.warn("Embedding unavailable, skipping ingest: skillId={} title={}", skillId, title);
            return;
        }
        String vecStr = DynamicEmbeddingService.toVectorString(emb);
        jdbc.update(
            "INSERT INTO knowledge_chunks (id, skill_id, title, content, embedding, source_ref) " +
            "VALUES (gen_random_uuid(), ?, ?, ?, ?::vector, ?)",
            skillId, title, content, vecStr, sourceRef
        );
        log.info("Ingested knowledge chunk: skillId={} title={}", skillId, title);
    }

    // ─── Retrieve ─────────────────────────────────────────────────────────────

    /**
     * 召回与查询文本最相关的 top-k 知识块内容。
     *
     * <p>若 {@code skillId} 不为空，则限定在该技能的知识块内检索；否则全库检索。</p>
     *
     * @param userId  用户 ID（用于 embedding 查询向量）
     * @param query   查询文本（通常是当前节点指令或用户问题）
     * @param skillId 技能 ID 过滤（可为 null）
     * @param topK    返回条数（建议 2~5）
     * @return 内容字符串列表，embedding 不可用或无匹配时返回空列表
     */
    public List<String> retrieve(UUID userId, String query, String skillId, int topK) {
        float[] emb = embeddingService.embed(userId, query);
        if (emb == null) return List.of();

        String vecStr = DynamicEmbeddingService.toVectorString(emb);
        try {
            if (skillId != null && !skillId.isBlank()) {
                return jdbc.queryForList(
                    "SELECT content FROM knowledge_chunks " +
                    "WHERE skill_id = ? " +
                    "ORDER BY embedding <=> ?::vector LIMIT ?",
                    String.class, skillId, vecStr, topK
                );
            }
            return jdbc.queryForList(
                "SELECT content FROM knowledge_chunks " +
                "ORDER BY embedding <=> ?::vector LIMIT ?",
                String.class, vecStr, topK
            );
        } catch (Exception e) {
            log.warn("RAG retrieve failed: {}", e.getMessage());
            return List.of();
        }
    }
}
