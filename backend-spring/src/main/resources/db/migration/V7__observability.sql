-- ─────────────────────────────────────────────────────────────────────────────
-- V7: Phase 8 — 运营、成本与可观测性
--   token_usage    : LLM Token 消耗明细
--   audit_log      : 关键操作审计记录
--   llm_error_log  : LLM 调用失败记录
-- ─────────────────────────────────────────────────────────────────────────────

-- Token 消耗明细表
CREATE TABLE token_usage (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id          UUID         REFERENCES learning_sessions(id) ON DELETE SET NULL,
    provider_key        VARCHAR(50),
    model               VARCHAR(100),
    prompt_tokens       INT          NOT NULL DEFAULT 0,
    completion_tokens   INT          NOT NULL DEFAULT 0,
    total_tokens        INT          NOT NULL DEFAULT 0,
    -- 按 CNY 估算成本（¥），精度足够展示给用户
    estimated_cost_cny  NUMERIC(10,6) DEFAULT 0,
    trace_id            VARCHAR(36),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_token_usage_user_id    ON token_usage(user_id);
CREATE INDEX idx_token_usage_session_id ON token_usage(session_id);
CREATE INDEX idx_token_usage_created_at ON token_usage(created_at);

-- 操作审计日志表
CREATE TABLE audit_log (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         REFERENCES users(id) ON DELETE SET NULL,
    action        VARCHAR(100) NOT NULL,   -- e.g. CREDENTIAL_SAVE, PATH_GENERATE, ARTIFACT_SUBMIT
    resource_type VARCHAR(50),            -- e.g. CREDENTIAL, PATH, ARTIFACT
    resource_id   VARCHAR(100),
    detail        JSONB,
    ip_address    VARCHAR(45),
    trace_id      VARCHAR(36),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_user_id    ON audit_log(user_id);
CREATE INDEX idx_audit_log_action     ON audit_log(action);
CREATE INDEX idx_audit_log_created_at ON audit_log(created_at);

-- LLM 调用失败记录表
CREATE TABLE llm_error_log (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID         REFERENCES users(id) ON DELETE SET NULL,
    session_id       UUID         REFERENCES learning_sessions(id) ON DELETE SET NULL,
    provider_key     VARCHAR(50),
    model            VARCHAR(100),
    error_type       VARCHAR(100),  -- e.g. HTTP_ERROR, PARSE_ERROR, RATE_LIMIT
    error_message    TEXT,
    -- 请求快照（去掉 api_key 敏感字段）
    request_snapshot JSONB,
    trace_id         VARCHAR(36),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_llm_error_log_user_id    ON llm_error_log(user_id);
CREATE INDEX idx_llm_error_log_created_at ON llm_error_log(created_at);
