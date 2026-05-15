-- Flyway V6: Phase 7 — Artifact 类型可配置化
-- 将 artifacts.type 列注释更新，明确支持所有枚举值

COMMENT ON COLUMN artifacts.type IS
    'Artifact 产出类型：CODE（代码）/ NOTE（笔记/回答）/ DIAGRAM（图表链接）/ ESSAY（论述）/ PROOF（证明）';
