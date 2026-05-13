-- Flyway V5: 长期记忆表（pgvector 向量存储）
-- 用途：RETRO 节点复盘内容 embedding 后写入，供后续对话注入历史记忆
-- 依赖：V1 已安装 vector 扩展

CREATE TABLE memory_embeddings (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid        NOT NULL,
    content     text        NOT NULL,               -- 原文（复盘摘要 / 笔记 / artifact）
    embedding   vector(1536),                        -- text-embedding-3-small 维度
    source      varchar(50)  NOT NULL DEFAULT 'RETRO', -- 来源：RETRO / ARTIFACT / RAG
    stage_id    uuid,                               -- 关联阶段（可为 null 表示全局记忆）
    skill_id    varchar(100),                        -- 关联技能（用于按技能过滤召回）
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_memory_embeddings_user ON memory_embeddings(user_id);
CREATE INDEX idx_memory_embeddings_skill ON memory_embeddings(user_id, skill_id);

-- 向量索引（数据量达 500+ 行后再启用）：
-- CREATE INDEX idx_memory_embeddings_vec ON memory_embeddings
--     USING ivfflat (embedding vector_cosine_ops) WITH (lists = 50);
