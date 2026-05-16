# AI Learning OS — Copilot 编码规则

本文件是 Vibe Coding 的核心约束文件。每次让 AI 生成代码前，先确认它读取了这份规则。

---

## 项目定位

这是一个 **Vibe Coding 项目**：先写架构文档（ARCHITECTURE.md），再让 AI 按文档逐阶段生成代码。
每次编码后必须更新 README.md 中的"当前进度"章节。

---

## 必读文件（每次开始前 AI 必须阅读）

1. `ARCHITECTURE.md` — 终局蓝图、模块规格、Phase 清单
2. `README.md` — 当前进度（进度章节）
3. `backend-spring/src/main/resources/application.yml` — 配置结构
4. `backend-spring/src/main/resources/db/migration/` — 数据库现状

---

## 代码生成规则（必须遵守）

### 架构规则

1. **Controller 只做协议转换**，不写任何业务逻辑
2. **禁止 Controller 直接调用 Repository**，必须经过 Service
3. **所有状态必须持久化**，节点状态写 DB（`learning_sessions.progress` jsonb），不存内存
4. **API Key 禁止明文存储**，必须通过 `CryptoService`（AES-256-GCM）加密
5. **禁止明文日志输出** API Key、token、密码等敏感字段

### 数据库规则

6. 主键统一使用 `UUID`（`@Column(columnDefinition = "uuid")`）
7. 时间字段统一使用 `OffsetDateTime`（`columnDefinition = "timestamptz"`）
8. 新表必须写 Flyway 迁移文件（`V{n}__description.sql`），**禁止** `spring.jpa.hibernate.ddl-auto=create`
9. pgvector 操作（`<=>` 余弦相似度）必须用 `JdbcTemplate` 原生 SQL，禁止用 JPA 类型映射

### 模块规则

10. 模块包名当前使用 `modules/path`（对应文档的 curriculum），**禁止**在未完成当前 Phase 前做大规模重命名
11. `common.Result<T>` 是统一响应包装类，不要引入第二套（文档叫 `ApiResponse`，代码叫 `Result`，两者等价）
12. 安全上下文中的 userId 必须从 `@AuthenticationPrincipal UUID userId` 获取，**禁止**从 RequestBody 获取

### 节点状态机规则

13. 节点顺序固定：`intro → concept → practice → task → review → retro`
14. TASK 节点：没有 `artifact_submitted=true` 不能 advance
15. REVIEW 节点：回复必须包含 `[PASS]` 或 `[通过]` 才能 advance
16. RETRO 节点完成后：必须写入长期记忆（现阶段写 `knowledge_chunks`，后续迁移到 `memory_embeddings`）

### Skill 规则

17. 教学策略必须放在 `src/main/resources/skills/*.skill.yaml`，**禁止**硬编码进 Java Prompt 字符串
18. 每个 Skill YAML 必须包含 `analogy_map`，支持 frontend/hardware/finance/product/other 五类背景

---

## 每次 Vibe Coding 后必须运行验收脚本（强制）

**在声明"完成"之前，必须先运行验收脚本，且全部通过：**

```bat
# Windows
verify.bat

# macOS / Linux
./verify.sh
```

验收脚本执行以下三步，**任意一步失败都必须修复后重新运行**：

| 步骤 | 命令 | 目的 |
|------|------|------|
| 1 | `mvnw compile` | 确认无编译错误（方法签名/import/类型等） |
| 2 | `mvnw test -Dtest=NodeFsmTest` | 确认节点状态机逻辑无回归 |
| 3 | `mvnw test -Dtest=SmokeTest -Dspring.profiles.active=test` | 确认 Spring 上下文能启动（Bean/配置/依赖注入） |

> 典型失败场景：修改了方法签名但未同步调用处 → 步骤 1 失败。这类错误**不应该**在人工 review 时才被发现。

**禁止在验收脚本失败时报告"已完成"。**

如果验收脚本失败，执行以下操作：
1. 仔细查看错误日志，定位具体的失败原因
2. 根据错误信息修复代码
3. 重新运行验收脚本直到全部通过
4. 如果失败原因复杂无法快速修复，提供详细日志、失败原因分析和建议的解决方案，并标明需要人工 review

---

## 每次 Vibe Coding 后必须做的事

1. 运行 `verify.bat` / `verify.sh` 并确认全部通过（见上方）
2. 在 README.md 的"当前进度"章节，把刚完成的 Phase 改为 ✅ 并补充交付说明
3. 如果新增了数据库表，在 README 的技术栈章节更新表数量
4. 如果新增了 API 接口，在 README 的"主要接口"表格中补充
5. 在 `docs/vibe-issues/YYYY-MM-DD.md`（当天日期）记录本次 Vibe Coding 中发现的问题清单、根因分析和改进建议（参考 `docs/vibe-issues/2026-05-11.md` 格式）
6. 如果本次对话中产生了值得长期遵守的实践规则，主动将其提炼后追加到本文件（`copilot-instructions.md`）

---

## 当前技术栈版本（不允许随意升级）

| 组件 | 版本 |
|------|------|
| Spring Boot | 3.3.5 |
| Java | 21 |
| Spring AI | 1.0.0-M3 |
| Next.js | 15.1.0 |
| React | 19 |
| PostgreSQL | 16（pgvector/pgvector:pg16 镜像） |
| Redis | 7 |

---

## 文件命名约定

| 类型 | 规范 |
|------|------|
| Controller | `{Module}Controller.java` |
| Service | `{Module}Service.java` |
| Entity | 单数名词，如 `LearningSession.java` |
| Repository | `{Entity}Repository.java` |
| Flyway | `V{n}__{snake_case_description}.sql` |
| Skill YAML | `{skill_id}.skill.yaml` |

---

## 对话驱动的 Skill 提炼规则

**当与用户的对话中出现以下任一情况，AI 必须主动将其提炼为规则并更新本文件：**

| 触发情况 | 动作 |
|---------|------|
| 用户指出一个反复踩坑的问题（如枚举值前后端不同步） | 在对应规则章节追加条目 |
| 用户提出一个新的工作流约定（如按日期保存问题记录） | 在"每次 Vibe Coding 后必须做的事"中追加 |
| 某个 bug 的根因值得系统性防范（如 `@Modifying` 缺 `@Transactional`） | 追加到 `backend_basics.skill.yaml` 的 `pitfalls` 节 |
| 用户对 UI/UX 有明确的偏好约定 | 在本文件末尾新增"前端约定"章节并记录 |

**提炼原则：**
- 只记录**可操作**的规则，不记录描述性叙述
- 一条规则一行，用祈使句（"必须…" / "禁止…" / "每次…"）
- 不创建冗余条目：先搜索本文件是否已存在类似规则

---

## 禁止事项清单

- ❌ 不允许 `@Transactional` 加在 Controller 上
- ❌ 不允许在 Service 层 catch 所有异常然后静默忽略
- ❌ 不允许在 Prompt 字符串里拼接用户的 API Key 明文
- ❌ 不允许跳过 Flyway 直接改表结构
- ❌ 不允许在 `verify.bat` 失败时仍声明"已完成"
- ❌ 不允许在没有对应单元测试的情况下修改节点状态机逻辑（`NodeFsmTest` 必须覆盖变更）
- ❌ 不允许用 `System.out.println` 调试，只用 `log.info/debug/warn/error`
- ❌ 不允许 hardcode localhost URL（用配置项）
- ❌ 不允许在没有通过当前 Phase 验收标准前，开始下一个 Phase
- ❌ 禁止在任何场景向用户展示 "Internal Server Error"、"null"、原始 HTTP 状态码文本；后端 AppException message 必须是用户可直接阅读的中文，前端取不到 message 时展示预设业务友好文案
- ❌ 禁止后端 Service 层在对外 AppException 中拼接内部异常的 message（`e.getMessage()`），内部原因只写日志
- ❌ 用 PowerShell 写 Java/YAML/JSON/Properties 文件时，禁止使用默认的 `Set-Content`/`Out-File`（会产生 UTF-8 BOM），必须用 `[System.IO.File]::WriteAllText(path, content, New-Object System.Text.UTF8Encoding $false)`
- ❌ 禁止在单次 `replace_string_in_file` 中追加新类定义到文件末尾（旧类未删除），需要重写大文件时优先用 `create_file` 或完整 PowerShell 重写
