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

---

## 八、Phase 9D：动态预制答案测试标准

### 适用范围

Phase 9D 引入动态预制答案机制，采用混合方案（YAML 优先 + AI 动态生成降级）。本节定义该机制的测试标准。

### 结构层测试标准

#### 新增 YAML 字段

每个 Skill YAML 的 `preset_answers` 可新增以下字段：

1. `trigger_keywords`（可选）：关键词列表，当 AI 生成的问题包含任一关键词时，该预制答案会被加入候选列表
2. 如果 `trigger_keywords` 为空或不存在，该预制答案只在初始背景调研时显示（传统逻辑）

**测试要点**：

1. `trigger_keywords` 字段必须是字符串数组
2. `trigger_keywords` 可以为空数组或不存在，但不允许为 `null`
3. 同一个 `preset_answer` 可以同时指定 `stage_index` 和 `trigger_keywords`
4. 关键词匹配必须是子串匹配（不区分大小写），而不是全词匹配

#### 测试类建议

1. `PresetAnswerTriggerKeywordTest` - 验证关键词字段结构合法性
2. `PresetAnswerKeywordMatchingTest` - 验证关键词匹配逻辑

### 逻辑层测试标准

#### 后端关键词匹配逻辑

**必须覆盖的测试场景**：

| 场景 | AI 问题 | YAML 中的 trigger_keywords | 预期结果 |
|------|---------|---------------------------|----------|
| 完全匹配 | "你用过模块化开发吗？" | `["模块"]` | 匹配成功 |
| 部分匹配 | "有没有用 import 或 require？" | `["import", "require"]` | 匹配成功（任一关键词命中即可） |
| 大小写不敏感 | "你了解 CommonJS 吗？" | `["commonjs"]` | 匹配成功 |
| 子串匹配 | "模块化开发是前端的基础" | `["模块"]` | 匹配成功 |
| 无匹配 | "你喜欢编程吗？" | `["模块", "包"]` | 匹配失败 |
| 空关键词列表 | "任意问题" | `[]` | 不进行关键词匹配（仅初始问题显示） |

**测试要点**：

1. 关键词匹配必须在 `loadInteractionConfig` 方法中实现
2. 匹配优先级：全局预制答案（无 trigger_keywords） → 关键词触发预制答案 → 阶段级预制答案 → AI 动态生成
3. 多个预制答案匹配同一个关键词时，全部返回（不去重）
4. 关键词匹配失败时，返回空列表（触发 AI 动态生成降级）

#### AI 动态生成预制答案

**Prompt 结构化输出要求**：

AI 必须返回符合以下 JSON Schema 的结构化输出：

```json
{
  "question": "string (AI 生成的问题)",
  "suggestedAnswers": [
    {
      "text": "string (答案选项文本)",
      "confidence": "HIGH | MEDIUM | LOW"
    }
  ]
}
```

**测试要点**：

1. AI 返回的 JSON 必须能被 Jackson 成功解析
2. `suggestedAnswers` 数组长度必须在 2-5 之间（少于 2 个不显示预制答案）
3. `confidence` 枚举值必须是 `HIGH / MEDIUM / LOW` 之一（大小写不敏感）
4. 解析失败时必须降级为纯文本问题（不显示预制答案）

**测试类建议**：

1. `AiSuggestedAnswersParserTest` - 验证 JSON 解析逻辑
2. `AiSuggestedAnswersValidationTest` - 验证答案选项数量和质量

### 流程层测试标准

#### 端到端流程测试

**场景 1：YAML 全局预制答案（初始问题）**

```
User → AI 提问："你之前有没有用过 Node.js？"
      → 后端匹配到全局预制答案（stage_index=null, trigger_keywords=[]）
      → 前端显示：
        [用过浏览器端 JS，写过交互页面]
        [跑过 node xxx.js，知道 console.log]
        [没接触过 JS / Node，完全从零开始]
```

**场景 2：YAML 关键词触发预制答案**

```
User 回答 → "用过浏览器端 JS"
AI 追问 → "你有没有用过模块化开发？"
后端匹配 → trigger_keywords 包含"模块"，返回对应预制答案
前端显示 → [用过 import/require] [听说过，但不清楚区别] [没用过]
```

**场景 3：AI 动态生成预制答案**

```
User 回答 → "用过 import"
AI 追问 → "你在使用 import 时，有没有遇到过循环依赖的问题？"
后端匹配 → trigger_keywords 无匹配（"循环依赖"不在关键词库）
AI 生成 → 返回 JSON，包含 3 个 suggestedAnswers
前端显示 → [遇到过，通过重构解决了] [听说过，但没实际遇到] [不知道循环依赖是什么]（标记为"AI 智能生成"）
```

**测试类建议**：

1. `DynamicPresetAnswersFlowTest` - 端到端集成测试
2. `PresetAnswerSourceTrackingTest` - 验证 `source` 字段（YAML / AI_GENERATED）

### 体验层测试标准

#### 前端显示要求

**必须验证的UI表现**：

1. **来源标记**：AI 动态生成的预制答案必须有视觉区分（如"💡 快速选择（AI 智能生成）"）
2. **降级策略**：AI 未返回 suggestedAnswers 时，不显示预制答案面板，允许自由输入
3. **去重逻辑**：如果 YAML 和 AI 生成的答案文本完全相同，前端自动去重
4. **移动端适配**：预制答案面板在移动端必须固定在软键盘上方
5. **快捷交互**：点击预制答案后填充到输入框，不自动发送（允许用户修改）

#### 桌面端 vs 移动端

| 设备类型 | 布局 | 按钮尺寸 | 交互 |
|---------|------|---------|------|
| 桌面端（≥1024px） | 横向流式布局 | `px-3 py-1.5` | 鼠标 hover 高亮 |
| 平板端（768~1024px） | 横向流式或纵向堆叠 | `px-3 py-2` | 触摸反馈 |
| 手机端（<768px） | 纵向堆叠或横向滚动 | `min-h-[44px]` | 触感反馈 + 长按显示完整文本 |

### 性能测试标准

#### 缓存策略

1. **YAML 预制答案缓存**：SkillRubricLoader 必须在首次加载后缓存 Skill YAML，避免重复解析
2. **AI 响应解析缓存**：同一个问题的 AI 响应不应该重复解析（使用 Session 级缓存）

#### 性能基准

| 操作 | 性能目标 | 测试方法 |
|------|---------|---------|
| YAML 关键词匹配 | <10ms | JMH 微基准测试 |
| AI JSON 解析 | <50ms | 单元测试 + 计时 |
| 端到端流程（含 AI 调用） | <3s | 集成测试 + 模拟 AI 响应 |

### 降级策略测试

#### 异常情况处理

| 异常情况 | 处理方式 | 验收标准 |
|---------|---------|---------|
| AI 未返回 `suggestedAnswers` | 不显示预制答案面板 | 用户可正常自由输入，不影响流程 |
| AI 返回格式错误（非 JSON） | 解析失败时降级为纯文本问题 | 日志记录错误，前端不显示预制答案 |
| AI 返回的答案选项 <2 个 | 不显示预制答案面板 | 质量不足，避免干扰用户 |
| AI 返回的答案选项 >5 个 | 只取前 5 个 | 避免选项过多，保持界面简洁 |
| 关键词匹配返回 >10 个预制答案 | 只取前 5 个（按 confidence 排序） | 避免选项过多 |

**测试类建议**：

1. `PresetAnswersDegradationTest` - 验证降级策略
2. `PresetAnswersErrorHandlingTest` - 验证异常处理

### 质量保证清单

**在声明"Phase 9D 完成"前，必须确认：**

- [ ] `verify.bat` 全部通过（编译 + NodeFsmTest + SmokeTest）
- [ ] YAML 新增 `trigger_keywords` 字段后，现有 3 个 Skill 仍能正常加载
- [ ] 关键词匹配逻辑在至少 5 个不同场景下测试通过（见"逻辑层测试标准"）
- [ ] AI JSON 解析在格式正确和格式错误两种情况下都能正确处理
- [ ] 前端在桌面端、平板端、手机端三种尺寸下测试通过
- [ ] 降级策略在至少 4 种异常情况下测试通过（见"降级策略测试"）
- [ ] `docs/UI_UX_DESIGN_STANDARD.md` 已补充动态预制答案规范
- [ ] `docs/vibe-issues/2026-05-19.md` 已记录设计决策和实现要点

---

## 变更历史

| 日期 | 版本 | 变更说明 |
|------|------|----------|
| 2026-05-19 | v1.1 | 新增"八、Phase 9D：动态预制答案测试标准" |
| 2026-05-11 | v1.0 | 初始版本，定义 Skill 体系测试标准（基于当前 3 个模板 Skill） |