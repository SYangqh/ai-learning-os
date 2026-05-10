# AI Learning OS

> **Vibe Coding 项目** — 先写设计文档，再让 AI 根据文档生成代码。
>
> 第一步：阅读 [ARCHITECTURE.md](ARCHITECTURE.md)（技术架构手册），了解系统的终局蓝图、护城河、模块设计和分阶段实施路线。
> 第二步：基于该文档，AI 逐阶段生成后端（Spring Boot）与前端（Next.js）代码。

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

## VS Code 开发自动化

### 方式一：任务面板（日常使用）

按 `Ctrl+Shift+B` 直接运行 **Start All**（自动按顺序启动 Docker → 后端 → 前端）。

按 `Ctrl+Shift+P` → **Run Task** 可选择单个任务：

| 任务名 | 功能 |
|--------|------|
| `🌟 Start All` | 一键启动全部（Docker + Backend + Frontend） |
| `🚀 Backend: Run` | 只启动后端（自动先启 Docker） |
| `🎨 Frontend: Dev` | 只启动前端 |
| `✅ Backend: Verify` | 验收脚本（compile + NodeFsmTest + SmokeTest） |
| `🧪 Backend: Test (NodeFsmTest)` | 只跑状态机单元测试 |
| `🐳 Docker: Start Services` | 只启动 PostgreSQL + Redis |
| `🐳 Docker: Stop Services` | 停止所有容器 |

### 方式二：调试模式（需要打断点）

按 `F5` 或左侧 **Run & Debug** 面板，选择配置：

| 配置名 | 说明 |
|--------|------|
| `🐞 Debug Backend` | 带调试器启动后端，可在 Java 代码打断点 |
| `🐞 Debug Frontend` | 调试 Next.js，自动打开浏览器 |
| `🌟 Full Stack Debug` | 同时启动前后端调试 |

---

## 自动化测试

项目内置两套测试，每次 Vibe Coding 后必须全部通过才能声明"完成"。

### 测试套件

| 测试 | 类型 | 说明 |
|------|------|------|
| `NodeFsmTest` | 纯单元测试 | 验证节点状态机推进规则（无 Spring 上下文，毫秒级） |
| `SmokeTest` | 集成测试 | 验证 Spring 上下文能正常启动、所有关键 Bean 已注册 |
| `ApiTest` | API 冒烟测试 | 用 MockMvc 验证核心接口 HTTP 状态码与响应结构 |

`NodeFsmTest` 覆盖：
- `intro → concept → ... → retro → complete` 完整推进顺序
- TASK 节点门控（无代码提交时必须阻塞）
- REVIEW 节点需 `[PASS]` / `[通过]` 关键词才能放行
- 未知节点默认回退到 `intro`

`ApiTest` 覆盖：
- `POST /api/auth/guest` 返回 200 + access_token
- `GET /api/llm/providers` 返回 provider 列表
- `POST /api/profile` 保存用户画像
- `GET /api/path` 无路径时不报错
- 无 token / 非法 token 返回 401

`SmokeTest` 使用 H2 内嵌数据库 + Mock Redis，**不依赖任何外部服务**，可在任意机器上直接运行。

### 运行方式

**方式一：VS Code 任务**（推荐）

`Ctrl+Shift+P` → Run Task → `✅ Backend: Verify`

**方式二：命令行**

```bash
cd backend-spring

# 只跑状态机单元测试（最快，< 1s）
./mvnw test -Dtest=NodeFsmTest

# 只跑 Spring 上下文测试
./mvnw test -Dtest=SmokeTest -Dspring.profiles.active=test

# 两套一起跑（三套全跑）
./mvnw test -Dtest=NodeFsmTest,SmokeTest,ApiTest -Dspring.profiles.active=test
```

---


> 每次 Vibe Coding 后在此更新。格式：✅ 已完成 / 🚧 进行中 / ❌ 未开始

| Phase | 目标 | 状态 | 交付说明 |
|-------|------|------|---------|
| Phase 0 | 打通学习主链路（节点状态初始化、session 恢复） | ✅ | `SessionService.advance()` 读取节点状态；前端展示 `current_node/node_status/awaits_artifact`；刷新后恢复 |
| Phase 1 | Stage 节点状态机（INTRO→…→RETRO 强制推进） | ✅ | `NODE_SEQUENCE` 固定顺序；TASK 门控（`artifact_submitted`）；REVIEW 需 `[PASS]` 关键词 |
| Phase 2 | 最小 Artifact 体系 | ⚠️ | `progress.artifact_submitted=true` 标记；无独立 Artifact 表；代码内容未持久化 |
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
- Spring AI 1.0.0-M3（路径生成）
- PostgreSQL + Flyway（17 张表，DDL 自动迁移）
- Redis + Redisson
- AES-256-GCM（API Key 加密存储）
- RestClient（BYOK 动态调用 Anthropic / OpenAI 兼容接口）

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
| PUT  | `/api/llm/credentials` | 存储 API Key（加密） |
| GET  | `/api/llm/credentials` | 查看已存凭据（脱敏） |
| DELETE | `/api/llm/credentials/{id}` | 删除凭据 |

## API 接口概览

### 身份认证
- `POST /api/auth/guest`：游客登录
- `POST /api/auth/magic-link/request`：请求魔法链接
- `POST /api/auth/magic-link/verify`：验证登录

### 学习路径
- `POST /api/path/generate`：生成学习路径
- `GET /api/path/{userId}`：获取路径列表
- `POST /api/stage/{stageId}/start`：开始阶段

### 对话与评审
- `POST /api/session/advance`：推进学习
- `POST /api/chat`：自由问答

### 凭据管理
- `PUT /api/llm/credentials`：配置 API Key
- `GET /api/llm/credentials`：列出凭据

## 安全考虑

- API Key 使用 AES-256-GCM 加密存储
- JWT Access Token + Refresh Token 鉴权
- Redis 限流 + Resilience4j 熔断
- RAG 强制租户隔离
- 日志脱敏，禁止明文输出敏感信息

## 贡献指南

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

---

深入了解架构设计与实施清单，请参考 [ARCHITECTURE.md](ARCHITECTURE.md)。
