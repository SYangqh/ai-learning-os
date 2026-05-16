# AI Learning OS

> 一个 **Vibe Coding 实验项目**：先写 [ARCHITECTURE.md](ARCHITECTURE.md) 定好终局蓝图，再让 AI 按文档逐阶段生成代码。

基于 **Spring Boot 3 + Java 21 + Next.js 15** 的自适应学习系统。用户携带自己的 LLM API Key，系统根据背景生成个性化学习路径，强制产出 Artifact、节点状态机推进、支持游客模式与邮箱魔法链接登录。

---

## 环境要求

| 工具 | 最低版本 | 说明 |
|------|---------|------|
| Docker Desktop | 最新版 | 运行 PostgreSQL（含 pgvector）和 Redis |
| Java | 21 | 虚拟线程（`--enable-preview` 不需要，已内置） |
| Maven | 3.9+ | 项目内含 `mvnw` Wrapper，无需单独安装 |
| Node.js | 18+ | 建议 20 LTS |

---

## 一键启动

```bash
# macOS / Linux
./start.sh

# Windows
start.bat
```

两个窗口分别启动后端（`:8080`）和前端（`:3000`）。

---

## 手动启动

### 1. 启动数据库和 Redis

```bash
docker compose up -d
```

PostgreSQL（含 pgvector）和 Redis 会自动启动，Flyway 在后端启动时自动建表，无需手动建库。

### 2. 启动后端

```bash
cd backend-spring
./mvnw spring-boot:run          # macOS/Linux
mvnw.cmd spring-boot:run        # Windows
```

后端就绪后访问 Swagger UI：http://localhost:8080/swagger-ui.html

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

打开 http://localhost:3000 即可使用。

### 停止服务

```bash
docker compose down
```



> 每次 Vibe Coding 后在此更新。格式：✅ 已完成 / 🚧 进行中 / ❌ 未开始

| Phase | 目标 | 状态 | 交付说明 |
|-------|------|------|---------|
| Phase 0 | 打通学习主链路（节点状态初始化、session 恢复） | ✅ | `SessionService.advance()` 读取节点状态；前端展示 `current_node/node_status/awaits_artifact`；刷新后恢复 |
| Phase 1 | Stage 节点状态机（INTRO→…→RETRO 强制推进） | ✅ | `NODE_SEQUENCE` 固定顺序；TASK 门控（`artifact_submitted`）；REVIEW 需 `[PASS]` 关键词 |
| Phase 2 | 最小 Artifact 体系 | ✅ | `artifacts` 表（V3 Flyway）；`POST /api/artifact`（CODE/NOTE）；`GET /api/session/{id}/artifacts`；TASK 节点从 DB 查 artifact 做门控；REVIEW 通过/失败同步 artifact.status；前端代码区独立"提交作品"按钮 + 状态徽章 + 历史列表 |
| Phase 3 | Rubric 评审闭环 | ✅ | `SkillRubricLoader` 从 Skill YAML 读取 `pass_criteria/fail_hints`；REVIEW 节点优先解析 `RUBRIC_JSON::` 结构化输出（passed/score/feedback/hints），fallback 关键词；前端展示分数卡片 + 改进建议列表；`/session/advance` 响应新增 `rubric_*` 字段 |
| Phase 4 | Skill YAML 资源化 + 背景感知教学 | ✅ | `SkillRubricLoader` 新增 `loadStageData()`（读 `task_description`）和 `loadAnalogies()`（读 `analogy_map[background]`）；`buildSystemPrompt` 注入背景感知类比段落；TASK 节点优先使用 YAML `task_description`；background 自由文本自动规范化为 YAML key（frontend/hardware/finance/product/other） |
| Phase 5 | Memory 与 RAG 接入主流程 | ✅ | V5 Flyway 新增 `memory_embeddings` 表（pgvector 1536 维）；新增 `MemoryService`（`remember()` 异步写 + `recall()` 向量召回）；RETRO/ARTIFACT/REVIEW_FAIL 节点完成时自动写入长期记忆；`buildMessages` 注入历史记忆上下文 + 类比桥接段落；RAG retrieve 已接入主流程 |
| Phase 6 | 前端产品工作台完善 | ✅ | 三主题切换系统（暗黑/正常/轻松）—— CSS 变量 Token（`--c-*`）+ `.t-*` 工具类 + `ThemeProvider`（localStorage 持久化）；learn/page.tsx、登录向导、verify 页全量适配；节点进度横向步骤条；空状态 Dashboard（今日目标卡片 + 已完成阶段历史回看）；侧边栏进度统计；Rubric 评审分数卡片 + 改进建议列表；阶段完成内联产出记录 |
| Phase 7 | Artifact 类型可配置化（多学科扩展） | ✅ | 扩展 `artifact_type` 枚举（CODE/NOTE/DIAGRAM/ESSAY/PROOF/NONE）；新增 `english_spoken` & `marxist_philosophy` Skill YAML（NOTE 类型示例）；前端产出面板按类型动态切换（代码编辑器/笔记输入/链接提交）；NONE 类型跳过 artifact 门控；后端 AdvanceResult 全链路携带 artifactType；V6 Flyway 迁移注释 artifacts.type 字段 |
| Phase 8 | 运营、成本与可观测性 | ✅ | V7 Flyway 新增 `token_usage`/`audit_log`/`llm_error_log` 三表；`TraceIdFilter` MDC 全链路 trace_id；`ObservabilityService` 异步写入 token 消耗+操作审计+LLM 错误日志；Redis 用户级限流（60 次/分钟）；`GET /api/usage/session/{id}` token 统计；`GET /api/path/{id}/report` PDF 报告导出；前端 learn 页侧边栏展示 token 消耗与估算费用 |

---

## 技术栈

**后端**
- Spring Boot 3.3.5 + Java 21（虚拟线程）
- Spring Security + JWT（jjwt 0.12.6）
- PostgreSQL + pgvector + Flyway（DDL 自动迁移）
- Redis + Redisson
- AES-256-GCM（API Key 加密存储）
- RestClient（BYOK 动态调用 Anthropic / OpenAI / DeepSeek / Alibaba / Zhipu）

**前端**
- Next.js 15.1.0 + React 19
- Tailwind CSS 3
- JWT Bearer 自动续签

---

## 项目结构

```
ai-learning-os/
├── backend-spring/          ← Spring Boot 后端
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/learningos/
│       │   ├── modules/
│       │   │   ├── auth/    ← 认证（游客/魔法链接/JWT）
│       │   │   ├── user/    ← 用户画像 & 账号合并
│       │   │   ├── path/    ← 学习路径生成
│       │   │   ├── session/ ← 会话推进（advance/chat）
│       │   │   └── llm/     ← 凭据加密存储 & 动态调用
│       │   ├── infrastructure/
│       │   │   ├── crypto/  ← AES-256-GCM
│       │   │   ├── mail/    ← 魔法链接邮件
│       │   │   └── security/← JWT Filter + SecurityConfig
│       │   └── common/      ← Result<T> / 全局异常
│       └── resources/
│           ├── application.yml
│           └── db/migration/V1__init_schema.sql
├── frontend/                ← Next.js 前端
│   └── src/
│       ├── app/
│       │   ├── page.tsx     ← 首页（账号→API Key→画像→生成路径）
│       │   ├── learn/       ← 学习界面
│       │   └── auth/verify/ ← 魔法链接回调页
│       └── lib/api.ts       ← JWT apiFetch 封装
├── start.sh                 ← 一键启动（Linux/macOS）
└── start.bat                ← 一键启动（Windows）
```

---

深入了解架构设计与实施清单，请参考 [ARCHITECTURE.md](ARCHITECTURE.md)。
详细开发文档（接口速查、安全设计、贡献指南）见 [docs/WIKI.md](docs/WIKI.md)。

---

## VS Code 快捷操作 & 自动化测试

详见 [docs/WIKI.md — VS Code 开发自动化](docs/WIKI.md#vs-code-开发自动化) 和 [自动化测试](docs/WIKI.md#自动化测试)。

---

## 未来路线图

Phase 8 之后的战略方向（多智能体、知识库上传、可视化、测验系统、CLI、商业化）详见 [ARCHITECTURE.md § 十六](ARCHITECTURE.md#十六未来路线图2026-年规划)。

---