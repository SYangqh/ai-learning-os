# AI Learning OS — 技术开发手册（Agent-Ready）

> 基于 **Spring Boot 4 + Java 21 虚拟线程 + Spring AI 2 + pgvector + 多模态 Agent** 的学习操作系统

---

## 一、系统本质与核心竞争力

这不是 Web App，也不是 ChatGPT 包装器。

> **这是一个强制你产出真实成果的、带持久化记忆的 AI 学习引擎。**

### 与直接问 ChatGPT 的根本区别

| 维度 | 直接问 ChatGPT | AI Learning OS |
|------|----------------|----------------|
| 学习验证 | 聊完即结束，无法验证是否真的学会 | **不产出 Artifact 不能推进**（掌握度门控） |
| 个性化 | 通用回答，不了解你的背景 | **背景感知教学**：前端/硬件/金融用不同类比讲同一个概念 |
| 学习路径 | 每次对话独立，无记忆 | **持久化阶段状态**，跨会话续学，记住你走到哪里 |
| 知识来源 | 仅模型内部知识 | **RAG 注入**：可接入你自己的文档、笔记、规范 |
| 学习成果 | 对话记录，无可携带物 | **Artifact 作品集**：代码快照、项目产物、可导出 PDF 报告 |
| 教学方法 | 随机 | **Skill 资源化**：教学策略可编辑、可迭代、可插件化 |

### 护城河在哪里

1. **强制产出闭环**：`task → artifact 提交 → rubric 评审 → 通过才解锁下一阶段`
2. **背景感知类比引擎**：同一概念，前端工程师听 React/fetch 类比，硬件工程师听中断/状态机类比
3. **Skill 资源化教学法**：教学策略不在代码里，在 YAML 文件里，可持续迭代
4. **跨会话记忆**：pgvector 向量记忆，每次对话都基于你的历史上下文
5. **BYOK + 多端同步**：用户自带 API Key，后端加密存储，支持异步任务

### 这份文档的定位

这份文档**不是最终 UI、最终流程、最终实现细节的冻结版**。

它应该同时承担 3 个角色：

1. **终局蓝图**：定义这个产品最终必须具备的护城河与架构边界
2. **当前仓库的演进约束**：重构时不打碎已有前后端接口和数据库基础
3. **逐阶段实施清单**：每次开发都能知道现在是在补“理想态”里的哪一层

换句话说：

> 这不是“最终形态说明书”，而是“从当前项目演进到完美产品的总设计图”。

---

## 二、学习核心引擎：Stage 节点状态机

这是系统与 ChatGPT 最本质的区别。每个学习阶段不是一段对话，而是一个**强制推进的节点状态机**：

```
Stage（阶段）
  │
  ├── [INTRO]     → 讲背景与目标，激活学习动机
  ├── [CONCEPT]   → 概念讲解（背景感知类比注入）
  ├── [PRACTICE]  → 小练习（口头回答 / 代码片段）
  ├── [TASK]      → 核心任务（必须提交 Artifact）
  ├── [REVIEW]    → Rubric 评审（AI 打分，不通过打回）
  └── [RETRO]     → 复盘 + 向量化存入长期记忆
```

**规则：节点顺序不可跳过，REVIEW 未通过不能进入 RETRO，RETRO 未完成不能进入下一 Stage。**

用户当前所在节点和节点状态必须持久化到 DB（`learning_sessions.progress jsonb`）。

### 2.1 TASK 节点产出类型可配置（多学科扩展关键）

**核心设计原则**：并非所有学习场景都需要写代码。TASK 节点要求的"产出类型"必须由 Skill YAML 声明，而不是由代码硬判断。

```
artifact_type 枚举：
  CODE     → 代码片段（学后端/前端/Python 等编程类）
  NOTE     → 文字作答 / Markdown 笔记（学英文、政治、历史等）
  QUIZ     → 结构化问答（选择题 / 判断题，自动评分）
  DIAGRAM  → 上传图片 / 链接（手绘架构图、思维导图）
  NONE     → 无产出要求（纯口头练习的 PRACTICE 节点）
```

**对前端的影响**：
- `artifact_type = CODE`：展示代码编辑区 + 「提交作品」按钮
- `artifact_type = NOTE`：展示富文本输入区 + 「提交笔记」按钮
- `artifact_type = QUIZ`：渲染选项卡片，点击即提交
- `artifact_type = NONE`：不显示任何提交区，节点由 AI 对话推进

**对 Skill YAML 的要求**：每个 Stage 的 `artifact_type` 字段必须明确声明；若不声明，默认为 `CODE`（向后兼容当前系统）。

**典型多学科配置示例**：

```yaml
# skills/english_spoken.skill.yaml   英语口语
stages:
  - index: 1
    goal: 掌握"问路"场景对话
    artifact_type: NOTE   # 写出一段对话，AI 点评语法和表达
    rubric:
      pass_criteria:
        - 包含问路的核心短语（Excuse me / Could you tell me...）
        - 语法无重大错误

# skills/math_calculus.skill.yaml    微积分
stages:
  - index: 3
    goal: 理解链式法则
    artifact_type: QUIZ   # 3 道选择题验证理解
    rubric:
      pass_score: 2       # 答对 2/3 即通过

# skills/politics_philosophy.skill.yaml  马克思主义哲学
stages:
  - index: 1
    goal: 理解物质与意识的关系
    artifact_type: NOTE   # 用自己的话写 200 字论述
    rubric:
      pass_criteria:
        - 包含"客观实在性"核心概念
        - 有具体举例
```

**实施路径**（当前 → 未来）：
1. **现阶段**：Skill YAML 已有 `artifact_type: CODE` 字段，前端通过该字段显示/隐藏代码区（最小改动）
2. **P1**：前端根据 `artifact_type` 渲染不同提交 UI（代码区 / 文本区 / 选项卡）
3. **P2**：后端 Rubric 评审逻辑按类型分支（代码走语法检查，NOTE/QUIZ 走语义评分）

### 2.2 PRACTICE / QUIZ 节点的作答交互必须可配置

**核心设计原则**：系统不能把“用户必须自己打字”当成唯一交互方式。对概念判断题、信心建立题、口语表达题，前端必须支持“空白作答区 + 预制答案快捷选择”的混合模式；但对于数学作业、精确计算、儿童独立练习等场景，必须允许关闭预制答案。

建议配置：

```yaml
interaction_mode:
  answer_mode: HYBRID        # FREE_INPUT_ONLY / PRESET_ONLY / HYBRID
  preset_answer_enabled: true
  preset_answer_source: skill
  allow_skip_typing: true

practice:
  prompt: "你觉得“开始读文件了”和“文件读完了”这两条打印信息，会按照什么顺序出现？随便猜一个答案就行。"
  preset_answers:
    - id: unsure_hint
      label: "我没把握，想先看看提示"
      answer: "我现在还不确定顺序，想先看一下同步/异步的提示。"
      mastery_level: low
    - id: guess_sync
      label: "我猜先开始读文件，再文件读完"
      answer: "我猜会先出现“开始读文件了”，再出现“文件读完了”。"
      mastery_level: medium
    - id: depends_mode
      label: "我觉得顺序和同步/异步实现有关"
      answer: "我猜顺序取决于实现方式；如果是同步读文件，大概率先开始读文件，再读完。"
      mastery_level: high
```

前端交互要求：
- 默认展示空白作答区，但不强迫用户先输入文字
- 同屏展示预制答案卡片，用户点击即可作为本次提交内容
- 预制答案应该体现不同掌握程度，而不只是“标准答案”
- 题型、Skill、Stage 以及用户个人设置都可以覆盖 `answer_mode`
- 对儿童数学、精确计算、默写类任务，默认 `FREE_INPUT_ONLY`

这类设计的目标不是“替用户答题”，而是降低开口门槛，提升弱信心用户的启动意愿。

### 2.3 正反馈必须具备主题感知演出

当用户出现“回答得不错”“Rubric 通过”“阶段完成”“连续学习达成”等正向事件时，系统应触发可配置的反馈演出，而不是只返回一行文本。

要求：
- 反馈由 `visual_effect + sound_effect + copy_tone` 三部分组成
- 音效必须支持全局开关、系统静音跟随和无障碍降噪模式
- 不同主题不能只换颜色，必须同时改变动效节奏、文案语气和音效风格
- 例如：可爱风偏轻快、弹跳、柔和提示音；正式/国企风偏克制、稳重、弱动效、短提示音

### 2.4 “我想学”必须支持预设 + 自定义

首页的“我想学”不能只做成固定枚举。系统必须同时支持：
- 预设目标卡片：降低选择成本，适合大多数常见路线
- 自定义输入：允许用户直接描述真正想学的内容，例如“我想学 NestJS 做后台管理系统”“我想学儿童数学应用题讲解”“我想学 STM32 + FreeRTOS”

核心原则：
- `target` 是用户意图，不应被前端枚举硬限制
- 前端负责收集“目标文本”，后端负责把目标解析为可执行的 Skill
- 自定义目标不等于每次实时让 AI 临场发挥；一旦生成路径，必须把解析后的 Skill 快照固定下来，避免学习中途漂移

推荐交互：
- 保留常见目标推荐卡片
- 额外提供“自定义我想学”输入框
- 用户可以先点推荐项，再补一句更细的描述

后端解析流程：
1. 先用 `TemplateSkillMatcher` 将用户目标匹配到已有模板 Skill
2. 若匹配置信度足够高，直接复用现有 Skill YAML
3. 若无合适模板，则由 `DynamicSkillGenerationService` 生成用户专属 Skill 草案
4. 生成结果必须落库或落对象存储，作为 `GENERATED` Skill，而不是写回 classpath
5. 路径创建时把最终采用的 Skill 版本快照绑定到 `learning_path`，保证后续 Stage 稳定

这意味着：系统未来应当从“静态 Skill 列表”升级为“模板 Skill + 动态 Skill 实例”的混合模型。

---

## 三、背景感知教学法（Analogy Engine）

用户画像中必须包含 `background` 字段（前端/硬件/金融/产品/其他）。

每个 Skill YAML 中包含**类比词典**：

```yaml
# skills/backend_basics.skill.yaml
analogy_map:
  frontend:
    http_request: "类比 fetch()，你熟悉的 Promise 链"
    state_machine: "类比 Redux reducer，action 驱动状态转换"
  hardware:
    http_request: "类比总线请求，主控发起，外设响应"
    state_machine: "类比 FSM 电路，时钟驱动状态跳转"
  finance:
    http_request: "类比交易指令，发出→确认→结算"
    state_machine: "类比风控规则引擎，条件触发状态迁移"
```

`AnalogyAdvisor` 在注入 `AgentContext` 时根据用户 background 自动选择类比词条。

---

## 四、Artifact 体系（学习成果第一等公民）

每个 TASK 节点必须有对应的 Artifact 提交，否则 Agent 拒绝推进：

```
artifacts/
├── CodeSnapshot    代码提交（关联 session_id）
├── NoteArtifact    笔记/回答（Markdown）
├── ProjectArtifact 项目产物（README / 截图 / 链接）
└── QuizResult      小测评分（结构化 JSON）
```

Artifact 用途：
- 作为 Rubric 评审的输入
- 向量化存入 memory，供后续 Agent 检索
- 汇总导出 PDF 学习报告
- 作为用户可携带的**作品集**

---

## 五、总体架构（AI 必须遵守）

```
Browser / Mobile App / WebSocket / Audio
          │
          ▼
     Controller 层（只做协议转换）
          │
          ▼
      Agent 层（核心决策）
   ┌──────────────────────────┐
   │ UserProfileAdvisor        │  注入画像 + background
   │ AnalogyAdvisor            │  注入背景感知类比
   │ StageStateAdvisor         │  注入当前节点状态
   │ MemoryAdvisor             │  注入向量长期记忆
   │ RagAdvisor                │  注入 RAG 知识片段
   │ SkillAdvisor              │  注入当前 Skill 定义
   └──────────────────────────┘
          │
          ▼
      LLM（ChatClient）
          │
          ▼
      Skill 执行 / Artifact 验收 / 节点推进
          │
          ▼
 Repository / pgvector / Redis Stream / S3
```

**原则：所有业务必须经过 Agent 层，禁止 Controller 直接调用 Service。**

---

## 六、核心模块拆分

```
modules/
├── agent/          ★ 核心（Agent + Advisor 链 + 节点状态机）
├── curriculum/     学习路径 + Stage + 节点状态（StageNode）
├── artifact/       Artifact 体系（提交 / 存储 / 检索）
├── memory/         长期记忆（pgvector 向量存储）
├── rag/            文档知识库（Tika + pgvector + S3）
├── skill/          Skill 插件系统（YAML 资源化）
├── session/        学习会话推进（节点状态机驱动）
├── interview/      面试模式（Skill 驱动）
├── voice/          WebSocket + ASR/TTS
├── report/         PDF 学习报告（iText）
├── auth/           游客 / 魔法链接 / JWT / 账号合并
└── llm/            BYOK + 动态模型路由 + AES-256-GCM 加密存储
```

## 补充：当前项目基线与演进原则

这份设计必须建立在**当前仓库已经存在的实现基线**上，而不是推倒重来。

### 当前基线已经具备的能力

1. `auth` 已经实现游客登录、魔法链接、refresh、logout、游客合并接口
2. `llm` 已经实现 provider、model、credential、preference 的基础接口
3. `path` 已经实现路径生成、当前路径查询、阶段启动接口
4. `session` 已经实现两类对话：`/session/advance` 与 `/chat`
5. 前端学习页已经具备：阶段侧栏、消息区、代码区、推进式提交、自由问答
6. 数据库已经具备 `learning_session_state` 与 `user_mastery` 这两个“状态机 / 掌握度”雏形表

### 因此重构必须遵守的演进原则

1. **先兼容，再替换**：优先保留现有 API 外形，内部再逐步切到 Agent 驱动
2. **先落状态机，再改命名**：先让 `path -> stage -> session` 真正按节点推进，再考虑把 `path` 模块重命名为 `curriculum`
3. **先复用现有表，再抽象新表**：已有 `code_snapshots`、`learning_session_state` 能承载第一阶段的 Artifact/FSM，就不要一开始扩大迁移面
4. **前端先保持交互骨架**：学习页当前“阶段列表 + 聊天区 + 代码区”的布局可以继续用，先补节点感知与产出门控
5. **所有理想态都必须有迁移路径**：文档中写出的每个模块，都要能映射到当前仓库已有文件或下一步可新增的最小实现

### 当前模块与目标模块的映射关系

| 当前模块 | 目标模块 | 处理策略 |
|---------|----------|----------|
| `modules/path` | `modules/curriculum` | 第一阶段先保留 `path` 包名，内部补 StageNode/FSM；稳定后再重命名 |
| `learning_sessions.progress` + `learning_session_state` | Stage 节点状态机 | 直接复用为当前状态存储，不先设计第二套状态字段 |
| `code_snapshots` | `artifact` | 先作为 CODE 类 Artifact 落地，后续再扩 Note/Project/Quiz |
| `/api/chat` 与 `/api/session/advance` | 自由问答 / 推进式对话 | 已有接口语义正确，优先保持 |
| `frontend/src/app/learn/page.tsx` | 学习工作台 | 保持页面骨架，新增节点提示、提交要求、评审反馈区 |

---

## 七、Agent 调用流程（固定范式）

```
User Input（含 sessionId / stageNodeId）
   ↓
UserProfileAdvisor   → 注入用户画像 + background
AnalogyAdvisor       → 根据 background 选择类比词条
StageStateAdvisor    → 注入当前 StageNode 状态（INTRO/CONCEPT/...）
MemoryAdvisor        → 向量检索历史记忆注入
RagAdvisor           → 向量检索知识库注入
SkillAdvisor         → 注入当前 Skill 的阶段定义 + Rubric
   ↓
LLM
   ↓
Tool Call：
  - advance_node()      推进到下一节点（需满足前置条件）
  - submit_artifact()   提交 Artifact（TASK 节点必须）
  - evaluate_rubric()   Rubric 评审（REVIEW 节点触发）
  - recall_memory()     主动回忆历史知识点
  - search_rag()        检索知识库
```

AI 必须实现：

```java
LearningAgent.run(userId, sessionId, input)
```

---

## 八、Memory 模块（pgvector 向量长期记忆）

使用：PostgreSQL + pgvector

```sql
memory_embeddings
├── id uuid
├── user_id uuid
├── content text          -- 原文（对话/复盘/笔记）
├── embedding vector(1536)
├── source varchar        -- 来源：RETRO / ARTIFACT / REVIEW_FAIL / RAG
├── stage_id uuid null
└── created_at timestamptz
```

流程：

```
RETRO 节点完成      → 复盘内容 embedding         → 写入 memory_embeddings (source=RETRO)
REVIEW 节点未通过   → feedback/hints embedding  → 写入 memory_embeddings (source=REVIEW_FAIL)
TASK 节点完成       → Artifact 摘要 embedding   → 写入 memory_embeddings (source=ARTIFACT)
每次对话开始        → 向量检索相关历史           → 注入 MemoryAdvisor
```

**这是"学到的内容不会消失"的关键机制。**

---

## 九、RAG 文档知识库

使用：

* Apache Tika 解析文档
* Redis Stream 异步向量化
* pgvector 存向量
* S3（MinIO）存原文

流程：

```
上传 PDF/MD/DOCX
 → Tika 解析
 → Redis Stream（异步任务）
 → 切块 + Embedding
 → pgvector 存储
```

RAG 的三类用途（不仅是问答）：
1. **规划时**：生成路径时检索课程模板/最佳实践
2. **讲解时**：CONCEPT 节点检索权威片段 + 最小必要阅读
3. **评审时**：REVIEW 节点检索规范/示例/反例，让反馈可追溯

---

## 十、Skill 插件系统（教学策略资源化）

每个 Skill YAML 包含完整教学策略，教学方法不在代码里：

```yaml
# skills/backend_basics.skill.yaml
id: backend_basics
display_name: 后端基础
target_audience: [frontend, hardware, finance]
project_blueprint: "做一个博客 API：CRUD + JWT 鉴权 + 部署"

stages:
  - index: 1
    goal: 理解 HTTP 与 RESTful
    nodes: [INTRO, CONCEPT, PRACTICE, TASK, REVIEW, RETRO]
    task_description: "用 Spring Boot 实现 GET /articles 接口"
    artifact_type: CODE
    rubric:
      pass_criteria:
        - 接口可正常返回 JSON 列表
        - 状态码使用正确
      fail_hints:
        - 检查是否遗漏 @RestController
        - 检查返回类型是否为 List<ArticleDTO>

analogy_map:
  frontend:
    http_request: "类比 fetch()，你熟悉的 Promise 链"
  hardware:
    http_request: "类比总线请求，主控发起，外设响应"
  finance:
    http_request: "类比交易指令，发出→确认→结算"
```

运行时动态加载：

```java
SkillLoader.loadAll()  // 从 classpath:/skills/*.yaml
```

但长期规划中，Skill 不能只来自 `classpath:/skills/*.yaml`。应区分两类来源：

1. **TEMPLATE**：仓库内置的稳定 Skill YAML，适用于高频公共路线
2. **GENERATED**：基于用户自定义“我想学”动态生成的 Skill 实例，按用户隔离存储

推荐策略：
- 模板 Skill 继续使用 YAML 资源化，便于人工维护与版本管理
- 动态 Skill 使用统一 Skill Schema 存数据库或对象存储，避免运行期改写仓库文件
- 无论来源如何，运行时都转换成同一个 `Skill` 对象供 `SkillAdvisor` 和 `RubricEvaluator` 消费
- 一条学习路径一旦生成，就绑定一份 Skill 快照；后续模板更新或二次生成，不影响已开始的路径

---

## 十一、LLM 动态路由（BYOK + AES-256-GCM 加密）

用户可配置：OpenAI 兼容 / Anthropic / Qwen / DashScope

**安全要求**：API Key 绝不存明文，AES-256-GCM 加密存储，主密钥来自环境变量。

```java
// modules/llm/service/LlmClientFactory.java
public interface LlmClientFactory {
    ChatClient createChatClient(UUID userId);
    EmbeddingClient createEmbeddingClient(UUID userId);
}

// modules/llm/service/CredentialService.java
void saveCredential(UUID userId, CredentialRequest req);  // 加密存储
String decryptKey(UserLlmCredential cred);               // 解密（仅内部调用）
```

---

## 十二、Redis 的真正用途

**不是缓存，是事件流 + 异步任务总线：**

```
Redis Stream: learning-events
├── STAGE_STARTED      → 触发 Skill 初始化
├── NODE_ADVANCED      → 触发节点状态持久化
├── ARTIFACT_SUBMITTED → 触发 Rubric 异步评审
├── STAGE_COMPLETED    → 触发 memory embedding + 进度统计
└── RAG_INGEST_QUEUED  → 触发文档异步向量化任务
```

---

## 十三、身份体系（游客 → 魔法链接 → 账号合并）

```
游客访问（device_id）
   → POST /api/auth/guest → access_token（kind=guest）
   → 开始学习（所有数据归属 guest_user）

邮箱登录
   → POST /api/auth/magic-link/request → 发邮件
   → POST /api/auth/magic-link/verify  → 触发游客数据合并
   → 游客数据（路径/会话/Artifact）迁移到真实账号
```

合并规则：
- `learning_paths`、`learning_sessions`、`artifacts` 全部迁移
- 冲突路径（to_user 已有进行中路径）→ 游客路径状态改为 `archived`，数据保留可回放
- 合并操作用 DB 事务 + advisory lock 保证幂等

---

## 十四、开发顺序（严格按此执行）

1. **auth**（游客 + 魔法链接 + JWT + 账号合并）
2. **llm**（BYOK + AES-256-GCM 加密存储 + LlmClientFactory）
3. **curriculum**（学习路径 + Stage + StageNode 状态机）
4. **agent 核心**（LearningAgent + Advisor 链）
5. **skill**（SkillLoader + SkillRegistry + YAML 解析）
6. **artifact**（Artifact 体系 + Rubric 评审）
7. **memory**（pgvector + MemoryService）
8. **rag**（Tika + Redis Stream + pgvector）
9. **session**（会话推进 + 节点驱动）
10. **interview**（面试 Skill + 追问 Advisor）
11. **voice**（WebSocket + DashScope ASR/TTS）
12. **report**（iText PDF 导出）

## 补充：从当前项目到完美产品的三层目标

### P0：把现有系统从“能聊”升级成“能学”

这是最近的目标，必须直接落在当前代码结构上：

1. 打通 `learning_session_state`，真正按 `INTRO → CONCEPT → PRACTICE → TASK → REVIEW → RETRO` 推进
2. 在 `session/advance` 中加入 Artifact 提交门控，不提交代码/回答就不能进入下一节点
3. 把当前 `path/start + learn page` 串成真正可重复使用的学习流程
4. 把 Rubric 结果结构化返回给前端，而不是只返回自然语言

### P1：把“教学法”做成系统能力

1. Skill YAML 支持 `analogy_map`、`rubric`、`artifact_type`、`stage nodes`
2. Agent Advisor 链补齐 `AnalogyAdvisor`、`StageStateAdvisor`
3. 形成基于 background 的教学分流，不同背景用户看到不同解释和任务描述
4. 把 `user_mastery` 真正用于难度调度和复习推荐

### P2：把它变成完整产品，而不是开发者 Demo

1. 学习首页要有“今天做什么”的 dashboard，而不只是进入学习页
2. 每个阶段要有可见的完成条件、预估时长、失败重试建议
3. 要有成本控制与配额保护，避免 BYOK 被错误调用消耗
4. 要有事件观测、报错回放、模型调用审计，能运营而不是只能开发
5. 要有成果展示页、报告页、历史复盘页，形成用户留存与传播闭环

### P3：多学科扩展（编程以外的领域）

当前系统面向编程学习，但框架本身是通用的。以下学科可直接通过新增 Skill YAML 接入，无需改动核心引擎：

| 学科 | artifact_type | Skill YAML 关键字段 | 备注 |
|------|--------------|-------------------|------|
| **英语口语/写作** | NOTE | `pass_criteria: 含核心短语, 语法无重大错误` | AI 点评表达，不跑代码 |
| **数学（高数/线代）** | QUIZ | `pass_score: 答对 X/Y 题` | 结构化题目，自动评分 |
| **政治/历史** | NOTE | `pass_criteria: 含核心概念, 有举例` | 开放性作答，AI 语义评分 |
| **前端开发** | CODE | `artifact_type: CODE` | CODE 类型原生支持，无需额外适配 |
| **后端开发** | CODE | `artifact_type: CODE` | CODE 类型原生支持，无需额外适配 |
| **产品设计** | DIAGRAM | `artifact_type: DIAGRAM` | 提交原型图链接或截图 |

**前提条件**：前端「代码区」需改造为按 `artifact_type` 动态切换的「产出区」（P1 任务）。在此之前，非 CODE 类 Skill 中的 TASK 节点可临时设为 `artifact_type: NOTE`，用文本框接收回答。

## 补充：可直接开工的实施任务清单与验收标准

这一节的目标不是描述理想架构，而是把工作拆成**可以直接建分支、写代码、提测**的任务。

原则：

1. 每个阶段都必须有明确输入、输出、改动边界、验收标准
2. 没有通过验收的阶段，不进入下一个阶段
3. 所有任务默认以“兼容当前仓库”为前提，不做无意义的大规模重命名

### Phase 0：打通当前学习主链路

目标：让当前项目从“页面能跑”变成“学习链路真正闭环”。

实施任务：

1. 为 `learning_sessions` 和 `learning_session_state` 建立一一对应的初始化流程
2. 在阶段启动时初始化默认节点 `INTRO`
3. 在 `SessionService.advance()` 中读取当前节点，而不是把整段学习流程当作普通聊天处理
4. 把当前节点信息返回前端，包括 `node_key`、`node_status`、`awaits_artifact`
5. 在前端学习页展示当前节点和当前阶段要求

验收标准：

1. 用户点击一个 stage 后，后端一定能返回当前 session 对应的节点状态
2. 新 session 首次进入时，节点固定为 `INTRO`
3. 前端能稳定展示“当前阶段 + 当前节点 + 是否等待用户提交”
4. 页面刷新后，用户能恢复到上一次 session 和节点

交付结果：

1. 一个真实可恢复的 stage/session/node 主链路
2. 当前 learn 页面从“聊天页”升级为“学习工作台”

### Phase 1：实现 Stage 节点状态机

目标：把 `INTRO → CONCEPT → PRACTICE → TASK → REVIEW → RETRO` 变成真实状态机。

实施任务：

1. 定义节点枚举和节点流转规则
2. 在 `learning_session_state` 中记录 `current_node_key`、`node_status`、`artifacts_required`、`evaluator_result`
3. 实现 `advanceNode()` 规则校验，禁止跨节点跳转
4. 为 `TASK` 节点增加“必须有 Artifact 才能继续”的门控逻辑
5. 为 `REVIEW` 节点增加“未通过回退 TASK 或停留 REVIEW”的规则
6. 为 `RETRO` 节点增加“完成后写 memory”的后置动作

验收标准：

1. 同一个 session 不能跳过 `TASK` 直接进入 `REVIEW`
2. `TASK` 节点没有产出时，接口返回明确的阻塞原因
3. `REVIEW` 不通过时，session 不会进入下一个 stage
4. `RETRO` 完成后，stage 状态会变为完成，下一 stage 解锁
5. 任意节点失败后，重新请求不会破坏状态一致性

交付结果：

1. 后端具备确定性的节点推进能力
2. 学习流程第一次真正具备“必须学完才能过”的约束

### Phase 2：先落地最小可用 Artifact 体系

目标：让系统第一次拥有“用户真实产出”，而不是只存对话。

实施任务：

1. 复用 `code_snapshots` 作为第一类 Artifact 存储
2. 定义统一 Artifact 提交入口，至少支持 `CODE` 和 `NOTE`
3. 在 `TASK` 节点将提交结果回写到 `learning_session_state.artifacts_submitted`
4. 前端代码区提交后，显示“已提交 / 待评审 / 已通过 / 需修改”状态
5. 为 Artifact 增加与 stage、node、session 的关联关系

验收标准：

1. 用户提交代码后，后端能查询到对应 session 的最新代码快照
2. `TASK` 节点可以准确判断“是否已经有合格产出”
3. 前端能区分“只是输入文本”和“真正提交了任务产出”
4. stage 完成后能回看该阶段的历史代码或笔记

交付结果：

1. 系统首次具备作品集雏形
2. 后续 Rubric 评审与报告导出有了稳定输入

### Phase 3：补齐 Rubric 评审闭环

目标：让系统从“有产出”升级为“有评判标准”。

实施任务：

1. 为 Skill YAML 增加 `rubric.pass_criteria`、`rubric.fail_hints` 结构
2. 实现 `RubricEvaluator`，统一输出 `passed / score / feedback / hints`
3. 在 `REVIEW` 节点调用 RubricEvaluator，而不是只让大模型自由发挥
4. 评审结果写入 `learning_session_state.evaluator_result`
5. 前端增加评审反馈展示区，展示通过状态和修改建议

验收标准：

1. 同一个 Skill 的同类任务能返回结构一致的评审结果
2. 前端可以依据 `passed` 布尔值决定是否允许继续
3. 评审失败时用户可以看到具体的 fail hints，而不是笼统地“请继续改进”
4. 后端能保存最近一次 Rubric 结果供后续回看

交付结果：

1. 学习流程形成“提交 - 评审 - 修改 - 再评审”的真实闭环
2. 系统开始形成自己的教学标准，而不是只靠 prompt 话术

### Phase 4：Skill 资源化与背景感知教学

目标：把教学法从代码逻辑中抽离出来，变成可配置能力。

实施任务：

1. 设计 Skill YAML 结构，至少支持 `project_blueprint`、`stages`、`nodes`、`rubric`、`analogy_map`
2. 实现 `SkillLoader` 和 `SkillRegistry`
3. 新增 `AnalogyAdvisor`，按用户 `background` 提供类比词条
4. 新增 `StageStateAdvisor`，注入当前 node 与当前 Rubric
5. 把路径生成和节点文案逐步切换为“读 Skill 资源”而不是写死在 Java 代码里

验收标准：

1. 新增一个 Skill YAML 后，不改 Java 主逻辑就能接入新学习路线
2. 同一个知识点面对不同 background，模型收到的上下文明确不同
3. 当前至少有 1 个完整 Skill 可以从路径生成一直跑到 stage 完成
4. 移除某个 Skill 后，系统不会影响其他学习路线运行

交付结果：

1. 教学法进入可持续迭代阶段
2. 后续新增课程/路线的成本显著下降

### Phase 5：Memory 与 RAG 接入主学习流程

目标：让系统真的“记住用户”，也真的“引用知识”。

实施任务：

1. 接通 `MemoryService.save()` 与 `MemoryService.recall()`
2. 在 `RETRO` 节点把复盘内容向量化入库（source=RETRO）
3. 在 `REVIEW` 节点未通过时，将 feedback/hints 向量化入库（source=REVIEW_FAIL）；在 `TASK` 节点完成后，将 Artifact 摘要向量化入库（source=ARTIFACT），供后续节点召回
4. 在 `CONCEPT` 和 `REVIEW` 节点引入 RAG 检索
5. 实现文档上传、解析、切块、异步向量化链路
6. 区分用户私有知识库与平台公共知识库

验收标准：

1. 用户完成复盘后，下一次相关对话能检索到历史学习记录
2. RAG 检索结果能稳定进入模型上下文，而不是只停留在接口层
3. 上传一份文档后，可以在学习过程中被检索引用
4. 不同用户之间的私有知识库互相隔离

交付结果：

1. 系统拥有长期记忆和知识增强能力
2. 学习体验从“单轮教学”升级为“连续教学”

### Phase 6：完善前端产品工作台

目标：把当前学习页打磨成真正的产品工作台。

实施任务：

1. 增加当前节点说明区、完成条件区、当前任务区
2. 增加 Artifact 提交状态、Rubric 反馈区、下一步行动建议区
3. 增加恢复学习、查看历史提交、切换自由问答/推进模式的交互
4. 增加阶段完成态和下一阶段引导
5. 设计“今日任务 / 本周进度 / 已完成产出”摘要区域

验收标准：

1. 用户不看日志、不看接口，也能知道自己当前该做什么
2. 用户可以清楚区分“普通聊天”和“任务提交”
3. 任意已完成 stage 都能回看该阶段的产出和评审
4. 页面在桌面端可以连续完成一个完整 stage，而不需要开发者辅助

交付结果：

1. 当前 learn 页面从工程验证页升级为可交付产品页

### Phase 7：Artifact 类型可配置化（多学科扩展）

目标：打破"只教编程"的限制，支持英语、数学、政治等多种学科。

实施任务：

1. V6 Flyway Migration：扩展 `artifacts.type` 支持 `CODE/NOTE/QUIZ/DIAGRAM/ESSAY/PROOF/NONE`
2. 新增学科 Skill YAML 配置文件（`subjects/math.yaml`、`subjects/english.yaml`、`subjects/politics.yaml`），包含学科特定评分维度（LaTeX 支持、语法检查、论述题结构化评分等）
3. 前端产出区按 `artifact_type` 动态切换：
   - `CODE` → Monaco Editor + 语法高亮
   - `QUIZ` → 选择题/判断题/填空题动态生成
   - `DIAGRAM` → Mermaid/Excalidraw 绘图区
   - `NOTE/ESSAY` → Markdown 编辑器 + AI 批改面板
4. Rubric 扩展为多维度评分：内容 / 结构 / 创新 / 规范性

验收标准：

1. 新增一个非编程 Skill YAML（如 `english_writing.skill.yaml`）后，不改 Java 主逻辑即可跑通完整学习流程
2. `QUIZ` 类型：答对 pass_score 题自动通过，不需要 AI 人工判断
3. `NOTE/ESSAY` 类型：前端展示 Markdown 编辑器，AI 返回结构化多维度评分
4. `DIAGRAM` 类型：支持提交 Mermaid 代码或图片链接

交付结果：

1. 系统从"编程专项工具"升级为"通用学习引擎"
2. 后续新增课程的成本降至：写一个 YAML + 可选新增前端组件

### Phase 8：运营、成本与可观测性

目标：让系统能真实上线、可运维、可持续演进。

实施任务：

1. **全链路 Trace ID**：每个请求生成唯一 `trace_id`，贯穿前端→后端→LLM 调用，日志中统一记录便于故障排查
2. **Token 消耗统计**：新增 `token_usage` 表（`user_id, session_id, model, prompt_tokens, completion_tokens, cost`），前端展示"本次会话消耗 XXX tokens，约 ¥X.XX"
3. **用户级限流**：基于 Redis 的用户级限流（如每分钟最多 10 次请求），防止 API Key 被误操作消耗
4. **操作审计日志**：记录关键操作（凭据修改、路径生成、Artifact 提交），支持安全审查
5. **错误回放**：记录失败的 LLM 调用（请求/响应/错误堆栈），提供重新执行能力，方便调试
6. **PDF 报告导出**：学习路径 + 各阶段 Artifact + 评分 + 改进建议 → 生成精美 PDF，可分享给老师/HR 作为能力证明

验收标准：

1. 出现错误时能通过 `trace_id` 定位到具体用户、session、stage、node
2. 可以统计单个用户或单个 provider 的成本消耗
3. 安全敏感接口具备限流和脱敏日志
4. 产品上线后可以基于事件数据观察学习完成率与流失点
5. 完成至少 1 个 stage 的用户可以导出 PDF 报告

交付结果：

1. 系统具备上线和持续优化的基础能力
2. 用户有可携带、可分享的学习成果证明

### Phase 9A：主题化正反馈演出

目标：先把“反馈特效/音效”从概念变成一套独立能力，避免和移动端适配、混合作答模式绑在一个大阶段里同时推进。

实施任务：

1. 增加统一的 `FeedbackEffectManifest` 配置，按 `theme + event_type` 描述文案、动效、音效、触感反馈和降级策略
2. 在前端实现正反馈演出引擎，覆盖“回答不错”“通过评审”“阶段完成”“连续学习达成”等正向事件
3. 将主题反馈与现有主题系统绑定，确保不同主题不仅换色，也切换文案语气、动效节奏和音效风格
4. 增加静音跟随、音效总开关、无障碍低动效模式、播放失败降级文本提示

验收标准：

1. 同一正向事件在至少两套主题下呈现出明显不同的文案、动画和音效风格
2. 音效可关闭，系统静音或低动效模式下不会强制播放或触发强动画
3. 反馈演出失败时不影响节点推进，页面至少回落为普通文本提示
4. 配置新增一个主题反馈条目后，不改业务逻辑即可在前端生效

交付结果：

1. 系统获得可配置、可主题化、可降级的正反馈能力

### Phase 9B：混合作答模式与预制答案

目标：把 PRACTICE / QUIZ 的“自由输入 + 预制答案选择”做成 Skill 可配置能力，而不是前端写死的某一种交互。

实施任务：

1. 为 Skill YAML 扩展 `interaction_mode`、`preset_answers`、`allow_skip_typing` 等结构
2. 前端实现 `FREE_INPUT_ONLY / PRESET_ONLY / HYBRID` 三种作答模式切换，并允许按题型、Skill、Stage、用户设置覆盖
3. 预制答案支持掌握程度分层和来源标记，提交时记录 `answer_source`、`preset_answer_id` 等审计信息
4. 对数学作业、精确计算、儿童独立练习等场景提供强制手输模式，禁止默认可直接代答

验收标准：

1. 同一道 PRACTICE 题既可以自由输入，也可以按配置选择预制答案提交
2. `FREE_INPUT_ONLY` 场景下不会渲染可直接提交的预制答案卡片
3. 后端可以区分本次作答来自自由输入还是预制答案
4. 当前 NOTE 类 Skill 接入混合作答后，不破坏 TASK / REVIEW / RETRO 主链路

交付结果：

1. 系统具备按学科和题型切换作答交互的基础能力

### Phase 9C：移动端 Web 与 iOS / Android 验证

目标：在不改变核心后端协议的前提下，先让 Web 窄屏完整可用，再验证 iOS / Android App 落地路径。

实施任务：

1. 完成首页、登录、learn 工作台的移动端响应式改造，处理安全区、软键盘遮挡、长文本输入、横竖屏切换
2. 为移动端补充离线草稿、恢复学习入口、声音权限管理、触感反馈与推送提醒的接口约束
3. 提供 iOS / Android App 方案，优先复用 React 生态；先用 Capacitor 做真机验证，再决定是否演进到 React Native / Expo
4. 定义移动端降级策略：无推送、无触感、无音频权限时仍可完成学习主流程

验收标准：

1. 学习页在手机竖屏下可完成一个完整 Stage，不依赖桌面端
2. 输入框、产出区、阶段导航在软键盘弹起后仍可操作
3. 至少一套 App 形态可在 iOS 和 Android 真机上完成登录、开始学习、提交产出、继续会话
4. 声音权限、通知权限、触感反馈都具备显式降级路径

交付结果：

1. 系统从桌面 Web 工具升级为具备移动端交付基线的学习产品

### Phase 10A：首页“我想学”自定义输入

目标：先解决目标采集入口，让首页从固定枚举升级为“推荐目标 + 自定义输入”的混合模式。

实施任务：

1. 首页“我想学”区域支持推荐目标卡片 + 自定义输入框 + 补充说明输入
2. 用户画像保存 `target_text`、`target_mode`（PRESET / CUSTOM）和补充说明，而不是只保存固定枚举值
3. 支持“先点推荐，再补充一句更细描述”的交互，避免用户被迫二选一
4. 对过空、过短、过模糊的目标输入提供业务友好的校验文案

验收标准：

1. 用户既可以点击推荐目标，也可以输入自定义“我想学”文本
2. 推荐目标和自定义输入最终都进入同一条路径生成链路
3. 用户刷新或重新进入首页后，可以看到最近一次保存的目标文本
4. 首页不再用固定枚举硬限制用户意图

交付结果：

1. 首页“我想学”从静态推荐升级为真实的目标输入入口

### Phase 10B：模板 Skill 匹配层

目标：在真正引入动态生成前，先建立稳定的模板 Skill 匹配层，优先复用现有高频路线。

实施任务：

1. 新增 `TemplateSkillMatcher`，对用户目标做归一化、别名匹配、关键词匹配和置信度评分
2. 为模板 Skill 建立可检索元数据：别名、适用场景、学科标签、artifact 类型、目标人群
3. 将匹配结果写入路径生成审计信息，记录命中的模板、置信度和回退原因
4. 先基于当前模板 Skill 建立最小样本集：`backend_basics`、`english_spoken`、`marxist_philosophy`

验收标准：

1. 常见目标文本可以稳定命中当前模板 Skill，而不是重复生成新 Skill
2. 低置信度输入不会被错误地硬匹配到不相关 Skill
3. 模板匹配结果对前端和日志都是可解释的，而不是黑盒路由
4. 新增一个模板 Skill 后，只需补充元数据和测试样本即可参与匹配

交付结果：

1. 后端获得“模板优先”的目标解析入口

### Phase 10C：动态 Skill 生成与快照治理

目标：在模板无法覆盖时，再引入动态 Skill 生成，并保证生成结果可持久化、可回放、不会漂移。

实施任务：

1. 新增 `DynamicSkillGenerationService`，对匹配失败的目标生成用户专属 Skill 草案
2. 新增 `generated_skills` 存储层，保存结构化定义、来源 prompt、版本、状态、归属用户和审核信息
3. 路径生成时写入 `skill_source` 与 `skill_snapshot_id`，运行期始终绑定固定快照
4. 为动态 Skill 增加基本审核与防漂移机制：至少支持 `draft / active / archived`
5. 明确边界：动态 Skill 只允许写 DB 或对象存储，禁止回写 `classpath:/skills/*.yaml`

验收标准：

1. 对新目标，后端能够生成一份结构合法、可用于路径生成的动态 Skill
2. 同一条学习路径在生成后不会因为模板更新或再次生成而改变已有 Stage 内容
3. 不同用户生成的动态 Skill 相互隔离，默认不对其他用户可见
4. 动态 Skill 生成失败时可回退到草案待确认态，而不是直接中断整条链路

交付结果：

1. 后端 Skill 系统从静态 YAML 插件升级为模板 + 动态实例 + 快照治理的混合体系

### 补充：Phase 9-10 的测试文档约束

1. 凡涉及 Skill 结构、匹配、作答模式、反馈特效、移动端交互的改动，都必须同步更新 `docs/TESTING.md` 和 `docs/SKILL_TESTING_STANDARD.md`
2. 当前模板 Skill 基线为 `backend_basics`、`english_spoken`、`marxist_philosophy`，新增能力必须至少覆盖这三条样本路线
3. 在 Phase 10B 之前，模板匹配测试必须直接基于当前 Skill 清单编写，禁止只用虚构样本证明功能可用

## 补充：近期两周开工建议

如果现在立刻开始开发，建议只做以下顺序，不要分散：

### 第 1 周

1. 打通 `Phase 0` 的 session/node 基线
2. 完成 `Phase 1` 的节点状态机
3. 先用 `code_snapshots` 做最小 Artifact 提交链路

### 第 2 周

1. 完成 `Phase 3` 的 RubricEvaluator
2. 补前端工作台中的节点信息和评审反馈区
3. 开始 Skill YAML 结构设计，但只先跑通 1 条路线

### 两周结束时的验收口径

1. 用户可以从首页进入一条学习路径
2. 进入一个 stage 后，会按节点推进
3. 到 `TASK` 节点时必须提交代码或笔记
4. 系统能给出结构化评审结果
5. 通过评审后能完成该阶段并解锁下一阶段

---

## 十五、AI 生成代码必须遵守的规则

1. 禁止 Controller 写业务逻辑
2. 所有业务走 `LearningAgent.run()`
3. 所有教学策略必须 Skill 资源化（YAML），不硬编码进 Prompt
4. 所有状态必须持久化（Stage 节点状态写 DB，不存内存）
5. 不产出 Artifact 不能推进节点（由 Agent 工具调用强制执行）
6. 所有 API Key 必须加密存储，禁止明文日志

---

# 详细规格清单

目标：**精确到包名 → 文件名 → 类名 → 方法签名 → 依赖关系**

同时必须满足一个前提：

> **规格设计要优先兼容当前仓库结构，新增抽象必须服务于下一步实现，而不是为了概念完整性。**

---

# 一、顶层包结构（固定，不允许改）

```
com.learningos
├── LearningOsApplication.java
├── common/
├── modules/
│   ├── auth/
│   ├── llm/
│   ├── agent/
│   ├── curriculum/       ← 新增：学习路径 + StageNode 状态机
│   ├── artifact/         ← 新增：Artifact 体系
│   ├── memory/
│   ├── rag/
│   ├── skill/
│   ├── session/
│   ├── interview/
│   ├── voice/
│   └── report/
└── infrastructure/
    ├── config/
    ├── crypto/           ← AES-256-GCM
    ├── mail/
    └── security/
```

兼容说明：
- 当前仓库已经存在 `modules/path/`，短期内允许它承担 `curriculum` 责任
- 若未到模块稳定期，**不要先为了命名美观做大规模 package rename**
- 允许采用“`path` 对外、`curriculum` 对内文档命名”的过渡策略

---

# 二、common（全局）

## `common/api/ApiResponse.java`

```java
public record ApiResponse<T>(boolean success, T data, String error) {
    public static <T> ApiResponse<T> ok(T data);
    public static <T> ApiResponse<T> fail(String error);
}
```

## `common/exception/GlobalExceptionHandler.java`

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(AppException.class)
    public ApiResponse<Void> handleApp(AppException e);

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleGeneral(Exception e);
}
```

---

# 三、auth 模块

## `modules/auth/controller/AuthController.java`

```java
@PostMapping("/api/auth/guest")
ApiResponse<TokenResponse> guestLogin(@RequestBody GuestRequest req);

@PostMapping("/api/auth/magic-link/request")
ApiResponse<Void> requestMagicLink(@RequestBody EmailRequest req);

@PostMapping("/api/auth/magic-link/verify")
ApiResponse<TokenResponse> verify(@RequestBody VerifyRequest req);

@PostMapping("/api/auth/refresh")
ApiResponse<TokenResponse> refresh(@RequestBody RefreshRequest req);

@PostMapping("/api/auth/logout")
ApiResponse<Void> logout();
```

## `modules/auth/service/AuthService.java`

```java
TokenResponse guestLogin(String deviceId, String userAgent);
void sendMagicLink(String email, String deviceId);
TokenResponse verifyMagicToken(String token, String deviceId);  // 含游客合并
TokenResponse refresh(String refreshToken);
void logout(UUID sessionId);
```

游客合并规则（`mergeGuestToUser`）：
- 迁移 `learning_paths`、`learning_sessions`、`artifacts`
- 冲突路径 → 游客路径改为 `archived`
- DB 事务 + advisory lock 保证幂等

---

# 四、llm（BYOK + 加密）

## `modules/llm/service/LlmClientFactory.java`

```java
public interface LlmClientFactory {
    ChatClient createChatClient(UUID userId);
    EmbeddingClient createEmbeddingClient(UUID userId);
}
```

## `modules/llm/service/CredentialService.java`

```java
void saveCredential(UUID userId, CredentialRequest req);  // AES-256-GCM 加密
UserLlmCredential getCredential(UUID userId, String providerKey);
String decryptKey(UserLlmCredential cred);  // 包内可见，不对外暴露
```

## `modules/llm/controller/LlmController.java`

```java
@PostMapping("/api/llm/credentials")
ApiResponse<Void> saveCredential(@RequestBody CredentialRequest req);

@GetMapping("/api/llm/credentials")
ApiResponse<List<CredentialSummary>> listCredentials();  // 只返回脱敏信息

@GetMapping("/api/llm/providers")
ApiResponse<List<LlmProvider>> listProviders();

@GetMapping("/api/llm/models")
ApiResponse<List<LlmModel>> listModels(@RequestParam String providerKey);
```

---

# 五、curriculum（学习路径 + StageNode 状态机）

实现备注：
- 当前代码里对应的是 `modules/path`
- 第一阶段可以在 `PathService` 基础上补 `StageNode` 与节点推进逻辑
- `learning_session_state` 应视为现阶段状态机主表，而不是过渡废表

## `modules/curriculum/entity/LearningPath.java`
字段：`id, userId, title, description, status(ACTIVE/PAUSED/ARCHIVED/COMPLETED), skillId, skillSource(TEMPLATE/GENERATED), skillSnapshotId, createdAt`

## `modules/curriculum/entity/Stage.java`
字段：`id, pathId, stageIndex, title, goal, status(LOCKED/ACTIVE/COMPLETED)`

## `modules/curriculum/entity/StageNode.java`
字段：`id, stageId, nodeType(INTRO/CONCEPT/PRACTICE/TASK/REVIEW/RETRO), status(PENDING/ACTIVE/PASSED/FAILED), artifactId(nullable)`

## `modules/curriculum/service/CurriculumService.java`

```java
LearningPath generatePath(UUID userId, UserProfile profile);  // 调用 LearningAgent，解析模板或动态 Skill
Stage getActiveStage(UUID userId);
StageNode getActiveNode(UUID stageId);
void advanceNode(UUID nodeId);                                  // 状态机转换
void lockNodeUntilArtifact(UUID nodeId);                       // TASK 节点门控
```

---

# 六、artifact（学习产出体系）

实现备注：
- 第一阶段不必立刻引入完整 `artifact` 大表
- 可以先复用 `code_snapshots` 作为 `CODE` 类 Artifact 存储
- `NOTE / PROJECT / QUIZ` 可以先存在 `learning_sessions.progress` 或新增轻量表，待工作流稳定后再统一抽象

## `modules/artifact/entity/Artifact.java`
字段：`id, sessionId, userId, stageId, nodeId, type(CODE/NOTE/PROJECT/QUIZ), content, s3Key(nullable), createdAt`

## `modules/artifact/service/ArtifactService.java`

```java
Artifact submit(UUID userId, UUID nodeId, ArtifactSubmitRequest req);
List<Artifact> listByStage(UUID stageId);
void vectorizeArtifact(UUID artifactId);  // 发布 Redis Stream 异步向量化
```

## `modules/artifact/service/RubricEvaluator.java`

```java
EvaluationResult evaluate(Artifact artifact, Skill skill);
// 返回：{ passed: boolean, score: int, feedback: String, hints: List<String> }
```

---

# 七、agent（系统核心）

## `modules/agent/LearningAgent.java`

```java
public interface LearningAgent {
    AgentResult run(UUID userId, UUID sessionId, String input);
}
```

## `modules/agent/impl/LearningAgentImpl.java`

依赖：

* `UserProfileAdvisor`
* `AnalogyAdvisor`
* `StageStateAdvisor`
* `MemoryAdvisor`
* `RagAdvisor`
* `SkillAdvisor`
* `LlmClientFactory`
* `CurriculumService`
* `ArtifactService`

## `modules/agent/advisor/`

```
UserProfileAdvisor.java  → 注入 background + profile
AnalogyAdvisor.java      → 根据 background 选择类比词条（从 Skill YAML）
StageStateAdvisor.java   → 注入当前 StageNode 类型 + 状态 + Rubric
MemoryAdvisor.java       → 向量检索历史记忆
RagAdvisor.java          → 向量检索知识库
SkillAdvisor.java        → 注入当前 Skill 的阶段定义
```

每个 Advisor：

```java
public interface AgentAdvisor {
    void apply(AgentContext context);
}
```

## `modules/agent/model/AgentContext.java`

```java
public class AgentContext {
    UUID userId;
    UUID sessionId;
    String input;
    UserProfile profile;
    StageNode currentNode;
    Map<String, String> analogies;
    Skill currentSkill;
    List<String> memories;
    List<String> ragChunks;
    String rubric;
}
```

## `modules/agent/tools/`（Agent 工具调用）

```java
AdvanceNodeTool.java     // advance_node()：推进节点（含前置条件检查）
SubmitArtifactTool.java  // submit_artifact()：提交产出
EvaluateRubricTool.java  // evaluate_rubric()：触发 Rubric 评审
RecallMemoryTool.java    // recall_memory()：主动检索历史记忆
SearchRagTool.java       // search_rag()：检索知识库
```

---

# 八、memory（pgvector 向量长期记忆）

## `modules/memory/service/MemoryService.java`

```java
void save(UUID userId, String content, String source, UUID stageId);
List<String> recall(UUID userId, String query, int topK);
```

## `modules/memory/repository/MemoryEmbeddingRepository.java`

```java
// 原生 SQL：SELECT content FROM memory_embeddings
// WHERE user_id = :userId
// ORDER BY embedding <=> :queryVector LIMIT :k
List<String> findSimilar(UUID userId, float[] queryVector, int k);
```

---

# 九、rag（文档知识库）

## `modules/rag/service/RagService.java`

```java
String ingest(UUID userId, MultipartFile file);  // 返回 jobId
List<String> search(String query, int topK);
List<String> searchWithScope(UUID userId, String query, int topK);
```

## `modules/rag/parser/TikaDocumentParser.java`

```java
String parse(InputStream inputStream, String mimeType);
List<String> chunk(String content, int chunkSize, int overlap);
```

## `modules/rag/async/RagIngestConsumer.java`

消费 Redis Stream `rag-ingest-jobs`，执行：解析 → 切块 → Embedding → 写 pgvector

---

# 十、skill（插件系统）

## `modules/skill/model/Skill.java`

```java
public record Skill(
    String id,
    String displayName,
  SkillSource source,
  UUID ownerUserId,
    List<String> targetAudience,
  String targetIntent,
    String projectBlueprint,
    List<StageDefinition> stages,
    Map<String, Map<String, String>> analogyMap
) {}
```

## `modules/skill/SkillRegistry.java`

```java
List<Skill> getAllTemplates();
Optional<Skill> find(String skillId);
Optional<Skill> findForUser(UUID userId, String skillId);
Skill resolve(UUID userId, UserProfile profile, String targetText);
Map<String, String> getAnalogies(String skillId, String background);
```

## `modules/skill/loader/SkillLoader.java`

```java
@PostConstruct
void loadAll();  // 仅加载 TEMPLATE 类 Skill
```

## `modules/skill/service/DynamicSkillGenerationService.java`

```java
Skill generate(UUID userId, UserProfile profile, String targetText);
SkillSnapshot snapshot(Skill skill);
```

---

# 十一、session（学习推进）

## `modules/session/controller/SessionController.java`

```java
@PostMapping("/api/session/advance")
ApiResponse<AgentResult> advance(@RequestBody ChatRequest req);

@PostMapping("/api/session/chat")
ApiResponse<AgentResult> freeChat(@RequestBody ChatRequest req);  // 自由问答，不影响进度
```

两类对话模式：
- **推进式**（`/advance`）：受节点状态机约束，必须产出才能推进
- **自由问答**（`/chat`）：随时解释概念，不影响阶段进度

实现备注：
- 当前 `SessionController` 已经基本符合这个边界，不应重写成新的入口
- 下一步重点不是改接口名，而是让 `SessionService.advance()` 真正读取当前节点、检查 Artifact、触发 Rubric、推进状态

---

# 十二、interview（面试模式）

## `modules/interview/service/InterviewService.java`

```java
Question nextQuestion(UUID userId);
Evaluation evaluate(UUID userId, String answer);
InterviewReport finish(UUID userId);
```

面试模式本质是特殊 Skill：`interview.skill.yaml`，节点类型为 `QUESTION → EVALUATE → NEXT_QUESTION`。

---

# 十三、voice（语音 WebSocket）

## `modules/voice/ws/VoiceWebSocketHandler.java`

流程：

```
浏览器音频 → WS → ASR（DashScope）→ LearningAgent.run() → TTS（DashScope）→ 音频返回
```

---

# 十四、report（PDF 报告）

## `modules/report/service/ReportService.java`

```java
byte[] generate(UUID userId);  // iText PDF
```

报告内容：
- 完成的学习路径与阶段列表
- 每阶段 Artifact（代码快照 / 笔记）
- Rubric 评分记录
- 长期记忆摘要（从 memory_embeddings 提取）

---

# 十五、完美产品还必须具备的非功能能力

如果目标是“完美软件产品”，除了架构与模块，还必须补足以下能力：

1. **可观测性**：每次 Agent 调用、模型调用、节点推进、Rubric 失败都要有 trace id
2. **容错恢复**：浏览器关闭、刷新、网络抖动后，用户能回到上一次节点，不丢失输入
3. **成本治理**：按用户、provider、模型维度统计 token 消耗，并允许限制或降级
4. **运营能力**：可配置推荐 Skill、默认学习路径模板、问题阶段的热修复文案
5. **内容迭代能力**：教学法以 YAML 为主，代码只做执行器，保证非开发也能参与调优
6. **安全基线**：API Key 加密、日志脱敏、邮件限流、游客合并幂等、RAG 文档隔离

---

# 十六、唯一调用链（AI 必须遵守）

```
Controller
   ↓
LearningAgent.run(userId, sessionId, input)
   ↓
Advisor 链（注入上下文）
   ↓
LLM
   ↓
Agent 工具调用（advance_node / submit_artifact / evaluate_rubric）
   ↓
CurriculumService / ArtifactService / MemoryService
   ↓
Repository / pgvector / Redis Stream / S3
```

**禁止任何层级的越级调用。**

---

## 十六、未来路线图（2026 年规划）

> 本节记录 Phase 8 之后的战略方向，不作为近期开发约束，供架构演进参考。

### 16.1 多智能体协作（Multi-TutorBot）

**当前**：一个会话一个 AI 角色。

**目标**：支持创建多个 TutorBot 实例并行工作，例如"代码审查 Bot + 思维导图 Bot + 面试模拟 Bot"同时介入一次学习会话。

**实现路径**：
- 基于 Java 21 虚拟线程 + `CompletableFuture` 实现并行 Agent Loop
- 新增 `AgentOrchestrator`，按场景路由到对应 TutorBot 实例
- 每个 TutorBot 有独立的 Skill 定义和 Advisor 链

### 16.2 知识库管理（Knowledge Base）

**当前**：RAG 只读检索平台公共知识库。

**目标**：支持用户上传 PDF/DOCX/PPTX，自动切片 + 向量化 + 索引，形成用户私有知识库。

**实现路径**：
- 复用现有 pgvector，新增 `DocumentUploadController`
- 引入 MinIO 对象存储保存原始文档
- 用 Apache Tika 解析多格式文档，Redis Stream 异步向量化

### 16.3 交互式可视化（Visualize Mode）

**当前**：纯文本 + 代码块展示。

**目标**：支持 Mermaid 流程图、Chart.js 数据图表、SVG 动画，让抽象概念可视化。

**实现路径**：
- 前端新增 `@mermaid-js/mermaid` + `recharts` 依赖
- 后端新增图表/流程图生成接口，Skill YAML 中可声明 `visualization_type`
- CONCEPT 节点可自动生成配套可视化内容

### 16.4 测验系统（Quiz Mode）

**当前**：无独立测验机制，练习题通过对话进行。

**目标**：基于知识库和 Artifact 自动生成选择题/判断题/简答题，形成独立测验模式。

**实现路径**：
- 新增 `QuizGeneratorService`，从 Skill YAML + 历史记忆自动生成题目
- 前端新增测验面板，支持计时、提交、即时评分
- 测验结果写入 `artifacts`（type = QUIZ），纳入 Rubric 评分体系

### 16.5 CLI 接口（AI 可操作）

**当前**：仅 Web 界面。

**目标**：提供命令行接口，让 AI Agent 可自主配置和操作系统（适用于批量课程导入、自动化测试等场景）。

**实现路径**：
- 引入 Spring Shell 模块，暴露关键 REST API 的 CLI 封装
- 支持 `los path generate`、`los skill list`、`los session advance` 等命令

---

### 16.6 商业化路径

#### SaaS 多租户版

**目标**：学校/培训机构可批量采购，每个机构有独立的数据隔离和配置空间。

**实现路径**：基于 Schema 隔离的多租户架构（`TenantContext` + 动态数据源切换）。

#### 企业内训版

**目标**：针对企业定制化学习内容（如"Java 岗前培训"、"产品经理训练营"）。

**特点**：支持私有化部署 + 企业内部知识库导入 + 定制 Skill YAML。

#### 开源社区版 + 商业增值服务

**策略**：
- 基础功能开源（MIT/Apache-2.0）
- 增值服务收费：多租户管理、企业定制、专属模型微调、7×24 技术支持

---

> 上述路线图均以"不破坏当前仓库接口契约"为前提。任何涉及 DB Schema 变更的功能必须通过 Flyway Migration 实现，任何涉及新 API 的功能必须保持向后兼容。