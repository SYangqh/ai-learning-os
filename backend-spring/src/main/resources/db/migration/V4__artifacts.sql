-- Flyway V3: Artifact 学习产出体系
-- Phase 2: 支持 CODE / NOTE 两类产出，与 session/stage/node 三层关联

CREATE TABLE artifacts (
    id          uuid        PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id  uuid        NOT NULL REFERENCES learning_sessions(id),
    stage_id    uuid        NOT NULL REFERENCES stages(id),
    user_id     uuid        NOT NULL REFERENCES users(id),
    node_key    varchar(50) NOT NULL,                  -- intro/concept/practice/task/review/retro
    type        varchar(20) NOT NULL,                  -- CODE / NOTE
    content     text        NOT NULL,
    status      varchar(20) NOT NULL DEFAULT 'submitted',  -- submitted / passed / needs_revision
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_artifacts_session ON artifacts(session_id);
CREATE INDEX idx_artifacts_user    ON artifacts(user_id);
