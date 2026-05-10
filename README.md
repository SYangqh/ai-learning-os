# AI Learning OS

基于 **Spring Boot 3 + Java 21 + Next.js 15** 的自适应学习系统。用户携带自己的 LLM API Key，系统根据背景生成个性化学习路径，支持游客模式与邮箱魔法链接登录。

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

*此文档基于详细项目计划自动生成。如需深入了解具体实现，请参考 `plan-aiLearningOs.md` 技术规格文档。*