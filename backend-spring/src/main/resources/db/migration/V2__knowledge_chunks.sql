-- Flyway V2: RAG 知识库（pgvector）
-- 依赖：V1 已安装 vector 扩展

CREATE TABLE knowledge_chunks (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    skill_id    varchar(100),                   -- 关联的技能 ID，可为 null 表示全局知识
    title       varchar(300) NOT NULL,
    content     text        NOT NULL,
    embedding   vector(1536),                   -- text-embedding-3-small 维度
    source_ref  varchar(500),                   -- 来源链接或文档路径
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_knowledge_chunks_skill ON knowledge_chunks(skill_id);

-- 注意：ivfflat 向量索引需要至少 `lists` 条行才能发挥效用。
-- 生产环境表数据量达到 1000+ 行后，再执行：
-- CREATE INDEX idx_knowledge_chunks_vec ON knowledge_chunks USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
