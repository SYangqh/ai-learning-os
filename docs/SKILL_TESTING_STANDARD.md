# AI Learning OS — Skill 体系测试标准

> 本文档基于当前仓库真实存在的 Skill 清单编写，目标是把“Skill YAML 能不能稳定驱动学习流程”从口头要求变成可执行测试口径。

---

## 适用范围

适用于以下改动：

1. 新增或修改 `backend-spring/src/main/resources/skills/*.skill.yaml`
2. 修改 SkillLoader、SkillRubricLoader、TemplateSkillMatcher、DynamicSkillGenerationService
3. 修改 `artifact_type`、`interaction_mode`、`preset_answers`、`analogy_map`、`rubric` 结构
4. 将新学科、新目标匹配逻辑、主题化反馈或移动端作答模式纳入正式交付

本文件与 `docs/TESTING.md` 的分工：

- `docs/TESTING.md`：负责主链路 Phase 验收
- `docs/SKILL_TESTING_STANDARD.md`：负责 Skill 结构、学科差异、匹配规则、未来 Skill 扩展能力的专项测试

---

## 当前 Skill 基线

当前仓库的模板 Skill 只有以下 3 个，后续 Skill 相关能力必须至少覆盖这 3 条样本路线：

| Skill ID | 学科 | Stage 数 | 主要 artifact_type | 当前定位 |
|----------|------|----------|--------------------|----------|
| `backend_basics` | 后端开发 | 4 | CODE | 当前主流程基线，覆盖代码产出、Rubric 评审、背景感知类比 |
| `english_spoken` | 英语口语 | 2 | NOTE | NOTE 类型基线，覆盖对话写作、词数/句数规则、语言类 Rubric |
| `marxist_philosophy` | 政治/哲学 | 2 | NOTE | 长文本论述基线，覆盖概念关键词、论述结构、案例分析 |

### 当前基线审计结果

| 检查项 | backend_basics | english_spoken | marxist_philosophy | 说明 |
|--------|----------------|----------------|--------------------|------|
| `id/display_name/project_blueprint` | 通过 | 通过 | 通过 | 具备基本元数据 |
| `stages[*].nodes` 固定 6 节点 | 通过 | 通过 | 通过 | 与当前 FSM 一致 |
| `artifact_type` 显式声明 | 通过 | 通过 | 通过 | 当前已覆盖 CODE 和 NOTE |
| `rubric.pass_criteria/fail_hints` | 通过 | 通过 | 通过 | 都有最小评分规则 |
| `analogy_map` 覆盖 `frontend/hardware/finance/product/other` | 未通过 | 未通过 | 未通过 | 当前 3 个 Skill 均缺少 `other` |
| 模板匹配测试样本可用 | 通过 | 通过 | 通过 | 可作为 Phase 10B 的首批样本 |

**结论**：当前 Skill 清单足够支撑“模板 Skill + 文档化测试标准”的起步，但 `analogy_map.other` 仍是明确待补缺口，后续不能在测试里默认视为已满足。

---

## 发布门槛

每次涉及 Skill 体系的开发，至少分 5 层验证：

1. 结构层：YAML 结构、字段完整性、枚举合法性
2. 逻辑层：Skill 读取、Rubric 加载、背景归一化、模板匹配
3. 流程层：Skill 驱动 session/path 主链路，不破坏节点状态机
4. 体验层：前端是否按 Skill 渲染正确产出区、反馈区、作答模式
5. 规划层：未来 Phase 9/10 的新增字段和能力是否补齐了验收口径

在正式声明“完成”前，以下命令仍是强制门槛：

```bash
# Windows
verify.bat

# 或至少逐项确认
cd backend-spring
.\mvnw.cmd compile
.\mvnw.cmd test -Dtest=NodeFsmTest
.\mvnw.cmd test -Dtest=SmokeTest -Dspring.profiles.active=test
```

---

## 一、结构层测试标准

### 必检字段

每个 Skill YAML 必须检查以下内容：

1. 根字段存在：`id`、`display_name`、`target_audience`、`project_blueprint`、`stages`、`analogy_map`
2. `stages` 非空，且 `index` 连续递增
3. 每个 stage 的 `nodes` 必须严格等于 `INTRO/CONCEPT/PRACTICE/TASK/REVIEW/RETRO`
4. 每个 stage 必须显式声明 `artifact_type`
5. 每个 stage 必须包含 `task_description`
6. 每个 stage 必须包含 `rubric.pass_criteria` 与 `rubric.fail_hints`
7. `target_audience` 与 `analogy_map` 至少覆盖 `frontend/hardware/finance/product/other`
8. 不允许在 YAML 中出现明文 API Key、token、密码、私有 URL

### 结构层自动化建议

建议新增以下测试类：

1. `SkillYamlSchemaTest`
2. `SkillAnalogyCoverageTest`
3. `SkillStageOrderingTest`
4. `SkillArtifactTypeContractTest`

### 结构层通过标准

1. 所有 Skill 文件都能被 Loader 成功解析
2. 任一 Skill 缺字段、字段拼错、枚举非法时，测试必须直接失败
3. `analogy_map` 缺背景分类时，测试必须显式指出缺的是哪一类，而不是只返回泛化报错

---

## 二、逻辑层测试标准

### 现有后端必须覆盖的逻辑点

1. SkillLoader 能加载全部模板 Skill
2. RubricLoader 能正确读取 `pass_criteria` 与 `fail_hints`
3. 背景归一化逻辑能把自由文本稳定归到 `frontend/hardware/finance/product/other`
4. NOTE 和 CODE 两类 `artifact_type` 在服务层返回值中可正确透传
5. REVIEW 节点仍优先解析 `RUBRIC_JSON::`，无结构化输出时再回退关键词判断

### 推荐新增测试类

1. `SkillLoaderTest`
2. `SkillRubricLoaderTest`
3. `BackgroundNormalizationTest`
4. `TemplateSkillMatcherTest`
5. `GeneratedSkillValidationTest`

### 逻辑层通过标准

1. 修改 Skill 解析逻辑后，现有 3 个 Skill 的读取结果保持稳定
2. 任意一个 stage 的 `artifact_type`、`task_description`、`rubric` 改动都能在测试里被准确感知
3. 模板匹配逻辑必须能解释“为什么命中/为什么没命中”，不能只输出一个最终 skill id

---

## 三、流程层测试标准

### 当前必须保住的主链路

1. `backend_basics` 继续走 CODE 提交流程
2. `english_spoken` 和 `marxist_philosophy` 继续走 NOTE 提交流程
3. TASK 节点门控、REVIEW 节点通过逻辑、RETRO 节点结束写记忆的规则不回归
4. 不同 Skill 的 stage 数不同，但节点序列规则一致

### 当前自动化覆盖现状

| 测试类 | 当前状态 | 已覆盖 | 未覆盖 |
|--------|----------|--------|--------|
| `NodeFsmTest` | 已有 | 节点顺序、TASK 门控、REVIEW 解析、AI `[ADVANCE]` 标记 | Skill YAML 读取、NOTE 类型流程、模板匹配 |
| `SmokeTest` | 已有 | Spring 上下文与关键 Bean 启动 | Skill 文件内容正确性 |
| Skill 专项集成测试 | 缺失 | 无 | CODE/NOTE 按 Skill 路由、YAML 字段驱动行为 |

### 推荐新增流程测试

1. `SkillDrivenSessionFlowTest`
2. `NoteArtifactFlowTest`
3. `TemplateSkillPathGenerationTest`
4. `DynamicSkillSnapshotBindingTest`

### 流程层通过标准

1. 至少覆盖 1 条 CODE Skill 和 2 条 NOTE Skill 的端到端流程
2. 修改 Skill 字段后，如果会影响 path/session 行为，必须有对应测试失败或更新
3. 模板匹配引入后，路径生成必须稳定记录 `skill_source` 和 `skill_snapshot_id`

---

## 四、体验层测试标准

### 当前 3 个 Skill 的前端验收重点

#### `backend_basics`

1. TASK 节点展示代码编辑区
2. 提交作品后能看到 artifact 提交状态和历史列表
3. REVIEW 节点展示 Rubric 分数卡片和改进建议

#### `english_spoken`

1. TASK 节点展示 NOTE 输入区，而不是默认代码编辑器
2. Stage 1 可输入 6 到 8 句英语对话并附中文说明
3. Stage 2 可输入 150 到 200 词自我介绍，评审反馈偏语言表达维度

#### `marxist_philosophy`

1. TASK 节点展示长文本 NOTE 输入区
2. Stage 1 能承载 300 到 400 字论述，不发生内容截断
3. REVIEW 反馈能指出概念遗漏、例子不足、论述结构问题

### 体验层通过标准

1. 不允许 NOTE 类 Skill 退化成“只显示代码编辑器”
2. 不允许不同学科共用完全相同的任务提示和评审文案而没有学科差异
3. 如果新增 `interaction_mode` 或 `preset_answers`，必须同时验证移动端和桌面端表现

---

## 五、模板匹配测试样本

在 `TemplateSkillMatcher` 开工后，首批最小样本必须直接使用当前 3 个模板 Skill。

| 用户输入 | 预期命中 Skill | 说明 |
|----------|----------------|------|
| “我想学 Spring Boot 后端开发，做博客接口” | `backend_basics` | 命中后端开发 + 博客 API 语义 |
| “我想练职场英语口语，自我介绍和会议表达” | `english_spoken` | 命中英语口语场景 |
| “我想学考研政治里的马克思主义哲学” | `marxist_philosophy` | 命中哲学/政治路线 |
| “我想做儿童数学应用题训练” | 不命中模板，进入动态 Skill 候选 | 当前无数学模板，不能误命中 |

通过标准：

1. 命中模板时返回可解释的匹配原因和置信度
2. 未命中模板时明确进入动态 Skill 生成候选，而不是静默回退到错误模板
3. 新增模板 Skill 后必须补样本，不允许只改 matcher 逻辑不补测试集

---

## 六、当前 Skill 的逐项验收矩阵

### `backend_basics`

1. Stage 1：验证 `GET /articles`、`200`、JSON 数组、`id/title` 字段
2. Stage 2：验证 JPA 实体、Repository、POST `201`、DB 持久化
3. Stage 3：验证 JWT 登录返回 token，受保护接口 `401/200/201` 分流
4. Stage 4：验证作者权限 `403`、删除 `204`、Dockerfile 可构建启动
5. 背景类比：至少抽查 `frontend/hardware/finance/product` 四类，`other` 记录为待补

### `english_spoken`

1. Stage 1：验证礼貌开场、问路表达、方向词、致谢、句数不少于 6 句
2. Stage 2：验证 150 到 200 词、自我介绍结构、技术方向、项目成果、英语学习动机
3. 背景类比：抽查时态/语法类比是否因背景不同而变化
4. 前端：确认使用 NOTE 输入区，不显示代码相关提示词

### `marxist_philosophy`

1. Stage 1：验证“客观实在性”“物质决定意识”“主观能动性”、具体例子、字数门槛
2. Stage 2：验证主要矛盾/次要矛盾、两点论/重点论、结论是否有行动指导
3. 背景类比：抽查辩证法和矛盾分析法的背景差异表达
4. 前端：确认长文本输入和评审反馈不会把哲学论述误当代码或短问答处理

---

## 七、Phase 9 / 10 扩展测试口径

### Phase 9A：主题化正反馈演出

1. 测试 `FeedbackEffectManifest` 结构合法性
2. 测试同一事件在不同主题下的文案、动效、音效差异
3. 测试静音、低动效、播放失败时的降级行为

### Phase 9B：混合作答模式

1. 测试 `interaction_mode` 与 `preset_answers` 的 YAML 结构
2. 测试 `FREE_INPUT_ONLY / PRESET_ONLY / HYBRID` 三种前端渲染差异
3. 测试后端对 `answer_source`、`preset_answer_id` 的记录

### Phase 9C：移动端 Web 与 App

1. 测试首页、登录、learn 页面在窄屏和安全区设备上的行为
2. 测试软键盘、离线草稿、触感反馈、权限被拒时的降级路径
3. 测试至少一套 App 容器形态可走通学习主流程

### Phase 10A：首页目标输入升级

1. 测试推荐目标、自定义输入、混合输入三条路径
2. 测试目标为空、过短、过于模糊时的校验文案
3. 测试刷新后目标文本回填

### Phase 10B：模板 Skill 匹配层

1. 测试首批 3 个模板 Skill 的命中样本和误判样本
2. 测试低置信度时不误命中
3. 测试匹配结果审计字段落库

### Phase 10C：动态 Skill 生成与快照治理

1. 测试生成结果结构是否合法
2. 测试 `skill_source` 和 `skill_snapshot_id` 绑定是否稳定
3. 测试不同用户隔离和 `draft/active/archived` 生命周期

---

## 八、当前缺口清单

以下缺口在本轮文档梳理后已明确，但尚未自动化补齐：

1. 缺少 Skill YAML 结构测试
2. 缺少 NOTE 类型主链路自动化测试
3. 缺少模板 Skill 匹配测试
4. 当前 3 个 Skill 的 `analogy_map` 都缺少 `other`
5. 缺少面向主题化反馈、混合作答、移动端能力的专项测试基线

这些缺口不影响本轮文档落地，但会直接影响 Phase 9A-10C 的可验证性，应作为下一轮开发的首批配套测试任务。
