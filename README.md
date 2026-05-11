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

---

## VS Code 快捷操作 & 自动化测试

详见 [docs/WIKI.md — VS Code 开发自动化](docs/WIKI.md#vs-code-开发自动化) 和 [自动化测试](docs/WIKI.md#自动化测试)。

---


> 每次 Vibe Coding 后在此更新。格式：✅ 已完成 / 🚧 进行中 / ❌ 未开始

| Phase | 目标 | 状态 | 交付说明 |
|-------|------|------|---------|
| Phase 0 | 打通学习主链路（节点状态初始化、session 恢复） | ✅ | `SessionService.advance()` 读取节点状态；前端展示 `current_node/node_status/awaits_artifact`；刷新后恢复 |
| Phase 1 | Stage 节点状态机（INTRO→…→RETRO 强制推进） | ✅ | `NODE_SEQUENCE` 固定顺序；TASK 门控（`artifact_submitted`）；REVIEW 需 `[PASS]` 关键词 |
| Phase 2 | 最小 Artifact 体系 | ✅ | `artifacts` 表（V3 Flyway）；`POST /api/artifact`（CODE/NOTE）；`GET /api/session/{id}/artifacts`；TASK 节点从 DB 查 artifact 做门控；REVIEW 通过/失败同步 artifact.status；前端代码区独立"提交作品"按钮 + 状态徽章 + 历史列表 |
| Phase 3 | Rubric 评审闭环 | ⚠️ | 靠 `[PASS]` 关键词判断通过；无结构化 `RubricEvaluator`；无 `score/hints` 字段 |
| Phase 4 | Skill YAML 资源化 + 背景感知教学 | ❌ | `backend_basics.skill.yaml` 已创建；SkillLoader/SkillRegistry 未实现；Prompt 仍硬编码 |
| Phase 5 | Memory 与 RAG 接入主流程 | ❌ | `knowledge_chunks` 表存在；`RagService` 接口通；RETRO 不写长期记忆 |
| Phase 6 | 前端产品工作台完善 | ❌ | — |
| Phase 7 | 运营、成本与可观测性 | ❌ | — |

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

## 主要接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/guest` | 游客登录 |
| POST | `/api/auth/magic-link/request` | 发送登录邮件 |
| POST | `/api/auth/magic-link/verify` | 验证魔法链接 |
| POST | `/api/auth/refresh` | 刷新 Token |
| POST | `/api/auth/logout` | 登出 |
| GET  | `/api/me` | 当前用户信息 |
| POST | `/api/profile` | 保存学习画像 |
| POST | `/api/path/generate` | 生成学习路径 |
| GET  | `/api/path` | 获取当前路径及阶段 |
| POST | `/api/stage/{id}/start` | 开始某阶段 |
| POST | `/api/session/advance` | 推进式对话 |
| POST | `/api/chat` | 自由问答 |
| POST | `/api/artifact` | 提交学习产出（CODE / NOTE） |
| GET  | `/api/session/{id}/artifacts` | 查询 session 下所有 Artifact |
| PUT  | `/api/llm/credentials` | 存储 API Key（加密） |
| GET  | `/api/llm/credentials` | 查看已存凭据（脱敏） |
| DELETE | `/api/llm/credentials/{id}` | 删除凭据 |

> 完整接口文档见 [docs/WIKI.md](docs/WIKI.md#api-接口速查) 或 Swagger UI：http://localhost:8080/swagger-ui.html

---

深入了解架构设计与实施清单，请参考 [ARCHITECTURE.md](ARCHITECTURE.md)。
详细开发文档（接口速查、安全设计、贡献指南）见 [docs/WIKI.md](docs/WIKI.md)。
