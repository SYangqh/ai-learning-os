-- 添加阿里通义千问、智谱 AI、Moonshot 等国内主流 OpenAI 兼容 Provider
INSERT INTO llm_providers (key, display_name, type, base_url, supports_stream, supports_embeddings)
VALUES
    ('alibaba', '通义千问 (DashScope)', 'OPENAI_COMPAT', 'https://dashscope.aliyuncs.com/compatible-mode/v1', true, true),
    ('zhipu',   '智谱 AI (GLM)',        'OPENAI_COMPAT', 'https://open.bigmodel.cn/api/paas/v4',              true, false)
ON CONFLICT (key) DO UPDATE
    SET display_name = EXCLUDED.display_name,
        base_url     = EXCLUDED.base_url;
