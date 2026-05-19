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
19. **动态预制答案**（Phase 9D）：采用混合方案（YAML 优先 + AI 降级），优先匹配 YAML 中的 `trigger_keywords`，无匹配时由 AI 动态生成 `suggestedAnswers`
20. **关键词匹配**：`trigger_keywords` 必须是字符串数组，支持子串匹配（不区分大小写），不允许为 `null`
21. **AI 结构化输出**：AI 生成问题时必须返回 JSON 格式，包含 `question` 和 `suggestedAnswers`（2-5 个答案选项），解析失败时降级为纯文本问题

---

## 文档驱动开发工作流（DDW - Documentation-Driven Workflow）

**核心原则：任何代码改动必须先有对应文档，任何文档改动必须同步对应测试。**

### 改动分级与强制要求

每次 Vibe Coding 开始前，必须先判定改动级别，并按对应要求完成文档 → 代码 → 测试 → Wiki 的闭环。

| 级别 | 改动类型 | 文档要求 | 测试要求 | 验收要求 |
|------|---------|---------|---------|----------|
| **Level 0** | 纯文档/注释/README 更新 | 更新对应文档 | 无需新增测试 | 文档格式校验通过 |
| **Level 1** | Bug 修复、小优化（不改接口签名） | 在当日 `vibe-issues` 记录根因 | 确认现有测试覆盖该逻辑 | `verify.bat` 通过 |
| **Level 2** | 新增接口/Service/Entity/Flyway | 补充 ARCHITECTURE.md 或 WIKI.md | 补充单元测试或集成测试 | `verify.bat` + 测试覆盖率检查 |
| **Level 3** | 新增 Phase/Skill/模块/架构层改动 | 补充 ARCHITECTURE.md + TESTING.md + SKILL_TESTING_STANDARD.md | 补充专项测试 + 更新验收标准 | `verify.bat` + 手工验收 + 文档一致性检查 |

### Level 2/3 强制文档清单

当改动涉及 Level 2 或 Level 3 时，**必须在开始写代码前**完成以下文档：

1. **ARCHITECTURE.md**：如果涉及新模块、新 Phase、新状态机节点，必须先在架构文档中补充设计说明
2. **TESTING.md** 或 **SKILL_TESTING_STANDARD.md**：补充对应的验收标准和测试用例描述
3. **docs/vibe-issues/YYYY-MM-DD.md**：记录本次改动的背景、决策和潜在风险
4. **README.md**：如果改动影响启动流程、技术栈、主要接口，必须同步更新

### Level 3 专项要求：Skill 体系改动

凡涉及以下改动，必须同时更新 `docs/SKILL_TESTING_STANDARD.md`：

- 新增或修改 `backend-spring/src/main/resources/skills/*.skill.yaml`
- 新增 `artifact_type`、`interaction_mode`、`preset_answers`、`analogy_map` 等 Skill 字段
- 修改 SkillLoader、RubricLoader、TemplateSkillMatcher、DynamicSkillGenerationService
- 将新学科、新主题化反馈或新的目标匹配规则纳入正式交付

### 文档与代码同步的强制检查点

**禁止以下行为：**

- ❌ 代码已经写完，事后才补文档（倒挂）
- ❌ 文档描述与代码实现不一致（漂移）
- ❌ 新增测试用例但未在 TESTING.md 中补充验收标准（孤立测试）
- ❌ 修改 Skill YAML 但未在 SKILL_TESTING_STANDARD.md 中补充对应样本（遗漏覆盖）
- ❌ 完成 Phase 后未在 README.md 中标记 ✅ 并补充交付说明（进度失真）

**强制执行顺序：**

```
1. 判定改动级别（Level 0/1/2/3）
2. 补充对应文档（ARCHITECTURE/TESTING/SKILL_TESTING_STANDARD/vibe-issues）
3. 实现代码
4. 补充或更新测试用例
5. 运行 verify.bat 验证
6. 更新 README.md 进度和接口表
7. 声明完成
```

**如果中途发现文档描述与实现不符，必须优先修正文档，再修正代码。**

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

**以下清单必须逐项确认，不允许跳过：**

- [ ] 1. 运行 `verify.bat` / `verify.sh` 并确认全部通过（见上方）
- [ ] 2. 在 README.md 的"当前进度"章节，把刚完成的 Phase 改为 ✅ 并补充交付说明
- [ ] 3. 如果新增了数据库表，在 README 的技术栈章节更新表数量
- [ ] 4. 如果新增了 API 接口，在 README 的"主要接口"表格中补充
- [ ] 5. 在 `docs/vibe-issues/YYYY-MM-DD.md`（当天日期）记录本次 Vibe Coding 中发现的问题清单、根因分析和改进建议
- [ ] 6. 如果改动级别为 Level 2/3，确认已同步更新 ARCHITECTURE.md、TESTING.md 或 SKILL_TESTING_STANDARD.md
- [ ] 7. 如果本次对话中产生了值得长期遵守的实践规则，主动将其提炼后追加到本文件（`copilot-instructions.md`）
- [ ] 8. 检查 `docs/VIBE_CODING_CHECKLIST.md` 是否全部打勾，未完成项必须在当日问题记录中说明原因

**声明"完成"前必须确认以上清单全部打勾，否则视为未完成。**

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
- ❌ 禁止代码已实现但文档未补充就声明"完成"（文档后置）
- ❌ 禁止新增测试用例但未在 TESTING.md 或 SKILL_TESTING_STANDARD.md 中补充验收标准（孤立测试）
- ❌ 禁止修改 Skill YAML 但未在 SKILL_TESTING_STANDARD.md 中补充对应测试样本
- ❌ 禁止跳过 `docs/VIBE_CODING_CHECKLIST.md` 检查就进入下一轮 Vibe Coding

---

## 前端约定

### UI/UX 基础规范

- **详细规范参见 `docs/UI_UX_DESIGN_STANDARD.md`**（包含交互模式、移动端适配、主题化反馈等完整规范）
- 必须把"回答得不错 / 通过评审 / 阶段完成"等正反馈做成可配置的视觉演出，且音效默认可开关并遵守系统静音状态
- 必须让不同主题使用不同反馈语言；可爱风偏轻快，正式/国企风偏克制，禁止只换颜色不换动效和音效
- 必须支持"自由输入 + 预制答案选择"的混合作答模式，并允许按题型、课程和用户设置关闭预制答案
- 数学作业、精确计算、儿童独立练习等场景，必须支持强制自由输入，禁止默认提供可直接代答的预制答案
- 首页"我想学"必须支持预设目标 + 自定义输入，禁止只允许固定枚举
- 后端 Skill 规划必须支持"优先匹配模板 Skill，匹配不上再生成动态 Skill"，禁止把自定义学习目标直接丢给一次性 Prompt 而不做持久化快照

### 预制答案面板规则

- **显示节点**：INTRO（背景调研）、PRACTICE（练习）、REVIEW（确认理解）、RETRO（总结反思）
- **禁止显示**：CONCEPT（只需阅读）、TASK（需要提交作品）
- **前提条件**：`interaction_mode` 为 `PRESET_ONLY` 或 `HYBRID`，且有匹配的 preset_answers
- **stage_index 匹配规则**：
  - `stage_index: null` 的预制答案在所有节点可用（全局）
  - `stage_index: 数字` 的预制答案只在对应 stage 的节点可用
- **交互行为**：点击预制答案后填充到输入框但不自动发送，允许用户修改后再发送

### 移动端强制要求（所有改动必须遵守）

#### 布局适配

- **手机端（<768px）**：
  - ✅ 主内容区占满屏幕（`flex-1`）
  - ✅ 顶部导航固定（`sticky top-0`）
  - ✅ 底部输入区固定（`sticky bottom-0`）
  - ✅ 左侧边栏改为抽屉（滑出/收起）
  - ✅ 右侧产出区改为全屏弹窗（点击"查看产出"按钮打开）
  - ✅ 预制答案面板固定在软键盘上方
  - ❌ **禁止**三列布局（会导致内容区过窄）
  - ❌ **禁止**固定宽度（必须用百分比或 `flex`）
  - ❌ **禁止**过小的点击区域（<44px）

- **平板端（768~1024px）**：
  - ✅ 左侧边栏可选展开/折叠
  - ✅ 主内容区和产出区并排显示（60/40 分割）
  - ✅ 支持横屏模式（landscape）

- **桌面端（≥1024px）**：
  - ✅ 完整三列布局（左 20%、中 50%、右 30%）
  - ✅ 左侧边栏固定展开
  - ✅ 支持快捷键（Ctrl+Enter 发送、Ctrl+K 聚焦输入框）

#### 软键盘适配

- **iOS**：
  - ✅ 监听 `Keyboard.addListener('keyboardWillShow/Hide')` 事件
  - ✅ 输入框上方内容区域高度 = `100vh - topbarHeight - keyboardHeight - inputHeight`
  - ✅ 预制答案面板 `bottom: {keyboardHeight}px`
  - ✅ 输入框聚焦时自动滚动到可见区域

- **Android**：
  - ✅ 使用 `AndroidManifest.xml` 中的 `android:windowSoftInputMode="adjustResize"`
  - ✅ 避免在输入框区域使用 `position: fixed`（会被键盘遮挡）
  - ✅ 使用 `IntersectionObserver` 检测输入框是否被遮挡

#### 触感反馈与声音

- **触感反馈**：
  - ✅ 点击预制答案触发 `light` 触感
  - ✅ 提交作品触发 `medium` 触感
  - ✅ Rubric 通过触发 `success` 触感
  - ✅ 必须有降级策略（不支持的设备静默降级，不影响功能）

- **声音权限**：
  - ✅ 首次播放前必须用户手势触发
  - ✅ 提供全局静音开关（遵守系统静音状态）
  - ✅ 音频文件 ≤50KB
  - ✅ 降级策略：无声音仍可正常使用

#### 改动验收强制检查项

**任何前端 UI 改动在声明"完成"前，必须确认：**

- [ ] 在 iPhone SE（375px）上测试通过
- [ ] 在 iPad（768px）上测试通过
- [ ] 在 1080p 桌面上测试通过
- [ ] 软键盘弹出时输入框不被遮挡
- [ ] 触感反馈有降级策略（`try/catch`）
- [ ] 声音播放有权限处理（首次需用户手势触发）
- [ ] 开启"减少动画"设置后仍可正常使用
- [ ] 使用键盘可完成所有操作（无障碍）
- [ ] 按钮点击区域 ≥44×44px（iOS 人机界面指南）
- [ ] `docs/UI_UX_DESIGN_STANDARD.md` 已补充新增功能的设计规范（如适用）
