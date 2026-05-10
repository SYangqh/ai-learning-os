-- Flyway V1: 基础表结构（身份体系 + 学习路径 + 自适应学习核心表）
-- 依赖：PostgreSQL 15+ 并已安装 citext 扩展

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "citext";
CREATE EXTENSION IF NOT EXISTS "vector";  -- pgvector，RAG 阶段使用

-- ─── 用户主表 ─────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id                  uuid        PRIMARY KEY DEFAULT uuid_generate_v4(),
    kind                varchar(20) NOT NULL DEFAULT 'guest',   -- guest / user
    status              varchar(20) NOT NULL DEFAULT 'active',  -- active / disabled
    created_at          timestamptz NOT NULL DEFAULT now(),
    merged_into_user_id uuid        REFERENCES users(id)
);

CREATE INDEX idx_users_kind ON users(kind);
CREATE INDEX idx_users_merged_into ON users(merged_into_user_id);

-- ─── 账号身份绑定（邮箱 / OAuth）─────────────────────────────────────────────
CREATE TABLE auth_identities (
    id                  uuid        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id             uuid        NOT NULL REFERENCES users(id),
    type                varchar(20) NOT NULL,       -- email / oauth
    provider            varchar(50),                -- google/github (type=oauth)
    provider_user_id    varchar(255),
    email               citext,
    email_verified_at   timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uidx_auth_identities_email
    ON auth_identities(type, email) WHERE type = 'email';
CREATE UNIQUE INDEX uidx_auth_identities_oauth
    ON auth_identities(type, provider, provider_user_id) WHERE provider_user_id IS NOT NULL;

-- ─── 匿名设备码（游客入口）────────────────────────────────────────────────────
CREATE TABLE guest_devices (
    device_id       uuid        PRIMARY KEY,
    user_id         uuid        NOT NULL REFERENCES users(id),
    first_seen_at   timestamptz NOT NULL DEFAULT now(),
    last_seen_at    timestamptz NOT NULL DEFAULT now(),
    user_agent      text,
    ip_hash         varchar(64)     -- sha256(ip)，用于风控
);

-- ─── 魔法链接一次性 Token（只存 hash）────────────────────────────────────────
CREATE TABLE magic_link_tokens (
    id          uuid        PRIMARY KEY DEFAULT uuid_generate_v4(),
    email       citext      NOT NULL,
    token_hash  varchar(64) NOT NULL,   -- sha256(raw_token)
    expires_at  timestamptz NOT NULL,
    consumed_at timestamptz,
    device_id   uuid,
    ip_hash     varchar(64),
    user_agent  text,
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_magic_link_tokens_email ON magic_link_tokens(email);
CREATE INDEX idx_magic_link_tokens_expires ON magic_link_tokens(expires_at);

-- ─── 会话/刷新令牌（多端登录）────────────────────────────────────────────────
CREATE TABLE user_sessions (
    id                  uuid        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id             uuid        NOT NULL REFERENCES users(id),
    device_id           uuid,
    refresh_token_hash  varchar(64) NOT NULL UNIQUE,    -- sha256(raw_refresh)
    created_at          timestamptz NOT NULL DEFAULT now(),
    expires_at          timestamptz NOT NULL,
    revoked_at          timestamptz,
    last_seen_at        timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_sessions_user ON user_sessions(user_id);

-- ─── 用户画像 ─────────────────────────────────────────────────────────────────
CREATE TABLE user_profiles (
    id              uuid        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         uuid        NOT NULL REFERENCES users(id),
    background      varchar(100) NOT NULL,
    skills          jsonb,
    target          varchar(200) NOT NULL,
    learning_style  varchar(50) DEFAULT 'project',
    daily_time      int         DEFAULT 60,
    analogy_basis   varchar(500),
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uidx_user_profiles_user ON user_profiles(user_id);

-- ─── 学习路径 ─────────────────────────────────────────────────────────────────
CREATE TABLE learning_paths (
    id          uuid        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     uuid        NOT NULL REFERENCES users(id),
    title       varchar(200) NOT NULL,
    description text,
    status      varchar(20) NOT NULL DEFAULT 'ongoing',  -- ongoing / completed / archived
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_learning_paths_user ON learning_paths(user_id);

-- ─── 学习阶段 ─────────────────────────────────────────────────────────────────
CREATE TABLE stages (
    id          uuid        PRIMARY KEY DEFAULT uuid_generate_v4(),
    path_id     uuid        NOT NULL REFERENCES learning_paths(id),
    stage_index int         NOT NULL,
    title       varchar(200) NOT NULL,
    goal        text,
    skill_id    varchar(100),   -- 关联 Skill 资源文件
    graph_name  varchar(100),   -- 兼容旧版
    status      varchar(20) NOT NULL DEFAULT 'locked',  -- locked / active / completed
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_stages_path ON stages(path_id);
CREATE UNIQUE INDEX uidx_stages_path_index ON stages(path_id, stage_index);

-- ─── 学习会话 ─────────────────────────────────────────────────────────────────
CREATE TABLE learning_sessions (
    id          uuid        PRIMARY KEY DEFAULT uuid_generate_v4(),
    stage_id    uuid        NOT NULL REFERENCES stages(id),
    user_id     uuid        NOT NULL REFERENCES users(id),
    started_at  timestamptz NOT NULL DEFAULT now(),
    finished_at timestamptz,
    progress    jsonb       DEFAULT '{}'::jsonb
);

CREATE INDEX idx_learning_sessions_stage ON learning_sessions(stage_id);
CREATE INDEX idx_learning_sessions_user ON learning_sessions(user_id);

-- ─── 会话消息 ─────────────────────────────────────────────────────────────────
CREATE TABLE session_messages (
    id          uuid        PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id  uuid        NOT NULL REFERENCES learning_sessions(id),
    role        varchar(20) NOT NULL,   -- user / assistant / system
    content     text        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_session_messages_session ON session_messages(session_id);

-- ─── 代码快照 ─────────────────────────────────────────────────────────────────
CREATE TABLE code_snapshots (
    id          uuid        PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id  uuid        NOT NULL REFERENCES learning_sessions(id),
    filename    varchar(200) NOT NULL,
    code        text        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now()
);

-- ─── 节点图谱模板（自适应学习 - Phase 3）─────────────────────────────────────
CREATE TABLE stage_node_templates (
    id               uuid        PRIMARY KEY DEFAULT uuid_generate_v4(),
    skill_id         varchar(100) NOT NULL,
    node_key         varchar(50)  NOT NULL,   -- intro/concept/practice/task/review/retro
    next_node_keys   jsonb,                   -- 可流转的后续节点列表
    require_artifact boolean NOT NULL DEFAULT false,
    rubric_ref       varchar(200),
    sort_order       int NOT NULL DEFAULT 0
);

CREATE INDEX idx_snt_skill ON stage_node_templates(skill_id);

-- ─── 会话 FSM 游标（自适应学习 - Phase 3）────────────────────────────────────
CREATE TABLE learning_session_state (
    session_id          uuid        PRIMARY KEY REFERENCES learning_sessions(id),
    current_node_key    varchar(50) NOT NULL DEFAULT 'intro',
    node_status         varchar(20) NOT NULL DEFAULT 'pending',  -- pending/running/done/failed
    artifacts_required  jsonb       DEFAULT '[]'::jsonb,
    artifacts_submitted jsonb       DEFAULT '[]'::jsonb,
    evaluator_result    jsonb,
    updated_at          timestamptz NOT NULL DEFAULT now()
);

-- ─── 用户掌握度（自适应难度调度 - Phase 3）───────────────────────────────────
CREATE TABLE user_mastery (
    user_id         uuid        NOT NULL REFERENCES users(id),
    concept_key     varchar(100) NOT NULL,
    mastery_score   int         NOT NULL DEFAULT 0 CHECK (mastery_score BETWEEN 0 AND 100),
    last_tested_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, concept_key)
);

-- ─── LLM Provider 配置 ───────────────────────────────────────────────────────
CREATE TABLE llm_providers (
    id                  uuid        PRIMARY KEY DEFAULT uuid_generate_v4(),
    key                 varchar(50) NOT NULL UNIQUE,    -- anthropic / openai / dashscope
    display_name        varchar(100) NOT NULL,
    type                varchar(30) NOT NULL,           -- ANTHROPIC / OPENAI_COMPAT
    base_url            varchar(300),
    default_headers     jsonb,
    supports_stream     boolean NOT NULL DEFAULT true,
    supports_embeddings boolean NOT NULL DEFAULT true,
    enabled             boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now()
);

-- 内置 Provider 初始数据
INSERT INTO llm_providers (key, display_name, type, supports_stream, supports_embeddings) VALUES
    ('anthropic', 'Anthropic Claude', 'ANTHROPIC', true, false),
    ('openai',    'OpenAI',           'OPENAI_COMPAT', true, true),
    ('deepseek',  'DeepSeek',         'OPENAI_COMPAT', true, false);

UPDATE llm_providers SET base_url = 'https://api.openai.com/v1'  WHERE key = 'openai';
UPDATE llm_providers SET base_url = 'https://api.deepseek.com/v1' WHERE key = 'deepseek';

-- ─── LLM 模型目录 ─────────────────────────────────────────────────────────────
CREATE TABLE llm_models (
    id              uuid        PRIMARY KEY DEFAULT uuid_generate_v4(),
    provider_key    varchar(50) NOT NULL REFERENCES llm_providers(key),
    model_name      varchar(100) NOT NULL,
    task            varchar(20) NOT NULL,   -- chat / embeddings
    enabled         boolean NOT NULL DEFAULT true,
    context_window  int,
    pricing         jsonb
);

CREATE UNIQUE INDEX uidx_llm_models ON llm_models(provider_key, model_name, task);

INSERT INTO llm_models (provider_key, model_name, task, context_window) VALUES
    ('anthropic', 'claude-opus-4-5',    'chat', 200000),
    ('anthropic', 'claude-sonnet-4-5',  'chat', 200000),
    ('openai',    'gpt-4o',             'chat', 128000),
    ('openai',    'text-embedding-3-small', 'embeddings', 8191),
    ('deepseek',  'deepseek-chat',      'chat', 64000);

-- ─── 用户 LLM 凭据（AES-256-GCM 加密存储）────────────────────────────────────
CREATE TABLE user_llm_credentials (
    id              uuid        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         uuid        NOT NULL REFERENCES users(id),
    provider_key    varchar(50) NOT NULL,
    enc_ciphertext  text        NOT NULL,   -- base64
    enc_iv          text        NOT NULL,   -- base64 (12 bytes)
    enc_tag         text        NOT NULL,   -- base64 (16 bytes)
    key_id          varchar(50) NOT NULL,   -- 使用哪把主密钥加密
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    last_used_at    timestamptz,
    revoked_at      timestamptz
);

CREATE UNIQUE INDEX uidx_user_llm_credentials
    ON user_llm_credentials(user_id, provider_key) WHERE revoked_at IS NULL;

-- ─── 用户 LLM 偏好 ────────────────────────────────────────────────────────────
CREATE TABLE user_llm_preferences (
    user_id                 uuid        PRIMARY KEY REFERENCES users(id),
    chat_provider_key       varchar(50),
    chat_model_name         varchar(100),
    embedding_provider_key  varchar(50),
    embedding_model_name    varchar(100),
    updated_at              timestamptz NOT NULL DEFAULT now()
);
