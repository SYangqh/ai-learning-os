# AI Learning OS — Wiki

> 面向开发者和使用者的详细说明。技术架构见 [ARCHITECTURE.md](../ARCHITECTURE.md)，启动方式见 [README.md](../README.md)。

---

## 目录

1. [核心概念](#核心概念)
2. [用户流程](#用户流程)
3. [学习节点状态机](#学习节点状态机)
4. [Skill YAML 编写指南](#skill-yaml-编写指南)
5. [API 接口速查](#api-接口速查)
6. [数据库表说明](#数据库表说明)
7. [身份体系](#身份体系)
8. [BYOK 凭据管理](#byok-凭据管理)
9. [RAG 知识库](#rag-知识库)
10. [常见问题](#常见问题)

---

## 核心概念

| 概念 | 说明 |
|------|------|
| **Learning Path** | 针对用户目标生成的学习路线，包含多个 Stage |
| **Stage** | 一个学习单元（如"理解 HTTP"），包含 6 个节点 |
| **Node** | 节点：INTRO / CONCEPT / PRACTICE / TASK / REVIEW / RETRO |
| **Artifact** | 学习产出：代码、笔记、项目产物 |
| **Skill YAML** | 教学策略配置文件，定义阶段、任务、Rubric、类比词典 |
| **BYOK** | Bring Your Own Key，用户携带自己的 LLM API Key |
| **Mastery** | 掌握度分数（0-100），决定教学难度档位 |

---

## 用户流程

```
打开首页
  ↓
以游客身份登录（无需注册）
  ↓
填写 LLM API Key（OpenAI / Anthropic）
  ↓
填写学习画像（背景 / 目标 / 类比基础）
  ↓
AI 生成个性化学习路径
  ↓
进入 Stage → 按节点推进
  ├── INTRO    了解本阶段目标
  ├── CONCEPT  学习核心概念
  ├── PRACTICE 做小练习
  ├── TASK     完成核心任务（必须提交代码/笔记）
  ├── REVIEW   AI 评审产出（未通过 → 打回重做）
  └── RETRO    复盘总结
  ↓
Stage 完成 → 解锁下一 Stage
```

---

## 学习节点状态机

每个 Stage 包含固定的 6 个节点，顺序不可跳过：

```
INTRO → CONCEPT → PRACTICE → TASK → REVIEW → RETRO
```

### 推进规则

| 节点 | 推进条件 | 阻塞说明 |
|------|---------|---------|
| INTRO | 用户发送任意消息 | — |
| CONCEPT | 用户发送任意消息 | — |
| PRACTICE | 用户发送任意消息 | — |
| TASK | 用户提交 artifact（代码或笔记） | 未提交则返回 `awaits_artifact=true` |
| REVIEW | 回复包含 `[PASS]` 或 `[通过]` | 未通过则停留在 REVIEW |
| RETRO | 用户发送任意消息 | 完成后 Stage 标记为 complete |

### 前端状态字段

`POST /api/session/advance` 返回：

```json
{
  "content": "AI 回复内容",
  "current_node": "task",
  "node_status": "running",
  "awaits_artifact": true,
  "stage_complete": false,
  "awaits_input": true
}
```

---

## Skill YAML 编写指南

Skill 文件放在 `backend-spring/src/main/resources/skills/` 目录下，文件名格式：`{skill_id}.skill.yaml`。

### 最小结构

```yaml
id: my_skill
display_name: 技能显示名
target_audience: [frontend, hardware, finance, product, other]
project_blueprint: "最终交付物描述"

stages:
  - index: 1
    goal: 本阶段目标（一句话）
    nodes: [INTRO, CONCEPT, PRACTICE, TASK, REVIEW, RETRO]
    task_description: |
      具体任务说明，告诉用户要做什么
    artifact_type: CODE  # CODE / NOTE / PROJECT / QUIZ
    rubric:
      pass_criteria:
        - 验收标准 1
        - 验收标准 2
      fail_hints:
        - 常见错误提示 1
        - 常见错误提示 2

analogy_map:
  frontend:
    concept_key: "面向前端工程师的类比说法"
  hardware:
    concept_key: "面向硬件工程师的类比说法"
```

### 目前已有的 Skill

| Skill ID | 显示名 | Stage 数 | 最终产物 |
|----------|--------|---------|---------|
| `backend_basics` | 后端基础 | 4 | 博客 API（CRUD + JWT + Docker） |

---

## API 接口速查

### 认证

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/api/auth/guest` | 游客登录 | 无 |
| POST | `/api/auth/magic-link/request` | 发送登录邮件 | 无 |
| POST | `/api/auth/magic-link/verify` | 验证魔法链接 | 无 |
| POST | `/api/auth/refresh` | 刷新 token | 无 |
| POST | `/api/auth/logout` | 登出 | Bearer |

### 用户

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET | `/api/me` | 当前用户信息 | Bearer |
| POST | `/api/profile` | 保存学习画像 | Bearer |
| GET | `/api/profile` | 获取当前画像 | Bearer |
| GET | `/api/mastery` | 查看掌握度列表 | Bearer |

### 学习路径

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/api/path/generate` | AI 生成学习路径 | Bearer |
| GET | `/api/path` | 获取当前路径 | Bearer |
| POST | `/api/stage/{id}/start` | 开始某阶段 | Bearer |

### 会话

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/api/session/advance` | 推进式对话（节点约束） | Bearer |
| POST | `/api/chat` | 自由问答（不影响进度） | Bearer |

### LLM 凭据

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| PUT | `/api/llm/credentials` | 存储 API Key（加密） | Bearer |
| GET | `/api/llm/credentials` | 查看凭据（脱敏） | Bearer |
| DELETE | `/api/llm/credentials/{id}` | 删除凭据 | Bearer |
| GET | `/api/llm/providers` | 获取 Provider 列表 | Bearer |
| GET | `/api/llm/models` | 获取模型列表 | Bearer |

### RAG 知识库

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/api/rag/ingest` | 向量化并存入知识块 | Bearer |
| POST | `/api/rag/retrieve` | 语义检索知识块 | Bearer |

---

## 数据库表说明

| 表名 | 说明 | 关键字段 |
|------|------|---------|
| `users` | 用户主表 | `kind(guest/user)`, `status` |
| `auth_identities` | 邮箱/OAuth 身份 | `type`, `email`, `user_id` |
| `guest_devices` | 游客设备 | `device_id`, `user_id` |
| `magic_link_tokens` | 魔法链接 token | `token_hash`, `expires_at` |
| `user_sessions` | JWT refresh 会话 | `refresh_token_hash`, `expires_at` |
| `user_profiles` | 学习画像 | `background`, `target`, `analogy_basis` |
| `user_mastery` | 掌握度追踪 | `(user_id, concept_key)`, `mastery_score` |
| `llm_providers` | Provider 目录 | `key`, `base_url`, `type` |
| `llm_models` | 模型目录 | `provider_key`, `model_name`, `task` |
| `user_llm_credentials` | 加密凭据 | `encrypted_payload`, `provider_key` |
| `user_llm_preferences` | 模型偏好 | `provider_key`, `model_name` |
| `learning_paths` | 学习路径 | `user_id`, `status`, `skill_id` |
| `stages` | 学习阶段 | `path_id`, `stage_index`, `status` |
| `learning_sessions` | 会话状态 | `stage_id`, `progress jsonb` |
| `session_messages` | 消息历史 | `session_id`, `role`, `content` |
| `knowledge_chunks` | RAG 知识块 | `embedding vector(1536)`, `skill_id` |

`learning_sessions.progress` jsonb 结构：
```json
{
  "current_node": "task",
  "node_status": "running",
  "awaits_artifact": true,
  "artifact_submitted": false,
  "review_passed": false
}
```

---

## 身份体系

### 游客登录

```bash
curl -X POST http://localhost:8080/api/auth/guest \
  -H "Content-Type: application/json" \
  -d '{"deviceId": null}'
```

返回 `access_token` 和 `refresh_token`，之后所有请求带 `Authorization: Bearer {access_token}`。

### 游客 → 真实账号合并

1. 用游客 token 调用 `POST /api/auth/magic-link/request`（带邮箱）
2. 点击邮件中的魔法链接
3. 系统自动将游客数据（路径/会话/mastery）迁移到真实账号

---

## BYOK 凭据管理

### 存储 API Key

```bash
curl -X PUT http://localhost:8080/api/llm/credentials \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "providerKey": "openai",
    "apiKey": "sk-..."
  }'
```

Key 使用 **AES-256-GCM** 加密后存储，明文永不落库。

### 支持的 Provider

| providerKey | 说明 |
|-------------|------|
| `openai` | OpenAI (gpt-4o 等) |
| `anthropic` | Anthropic (claude-3-5-sonnet 等) |
| `openai_compatible` | 任意 OpenAI 兼容接口（Qwen、DashScope 等） |

---

## RAG 知识库

### 向量化一段文本

```bash
curl -X POST http://localhost:8080/api/rag/ingest \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "skillId": "backend_basics",
    "title": "RESTful 设计原则",
    "content": "REST 是一种架构风格...",
    "sourceRef": "https://example.com/rest-guide"
  }'
```

### 语义检索

```bash
curl -X POST http://localhost:8080/api/rag/retrieve \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "HTTP 状态码有哪些",
    "skillId": "backend_basics",
    "topK": 3
  }'
```

> 注意：ingest 和 retrieve 需要用户已配置支持 embedding 的 API Key（OpenAI text-embedding-3-small）。

---

## 常见问题

**Q: 不想注册邮箱，可以用吗？**
A: 可以。点击"以游客身份继续"，直接开始学习。数据存在本地会话中，注册后可合并。

**Q: 支持哪些 LLM？**
A: OpenAI（gpt-4o/gpt-4o-mini）、Anthropic（claude-3-5-sonnet）、任意 OpenAI 兼容接口（如 Qwen）。

**Q: API Key 安全吗？**
A: 使用 AES-256-GCM 加密存储，密钥来自服务器环境变量，日志中不会出现明文。

**Q: TASK 节点卡住了怎么办？**
A: 必须在聊天框输入代码或笔记，然后勾选"作为任务提交"（`artifact_submitted=true`）才能推进。普通聊天不算提交。

**Q: REVIEW 怎么通过？**
A: AI 评审后回复中出现 `[PASS]` 或 `[通过]` 时自动标记通过。如果 AI 没有给出这个标记，说明你的产出还需要改进。

**Q: 前端只有英文提示？**
A: 在画像页填写"学习目标"和"类比基础"时用中文，AI 会用中文回复。

**Q: 怎么新增一个学习路线？**
A: 在 `backend-spring/src/main/resources/skills/` 目录下新建一个 `{id}.skill.yaml`，按照格式填写即可，无需修改 Java 代码（Phase 4 完成后生效）。
