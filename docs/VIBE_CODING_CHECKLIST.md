# Vibe Coding 检查清单

> **强制规则**：每次让 AI 开始写代码前，必须先完成本清单的前置检查；每次声明"完成"前，必须确认所有后置检查项全部打勾。

---

## 使用方法

1. 每次开始新一轮 Vibe Coding 时，复制本文件到当天的 `docs/vibe-issues/YYYY-MM-DD.md` 的末尾
2. 逐项确认前置检查清单，判定本次改动级别
3. 按对应级别完成文档 → 代码 → 测试 → Wiki 的闭环
4. 运行 `verify.bat` / `verify.sh`
5. 逐项确认后置检查清单
6. 全部打勾后才可声明"完成"

---

## 前置检查清单（开始写代码前）

### 第一步：判定改动级别

- [ ] 已确认本次改动级别为：
  - [ ] **Level 0** - 纯文档/注释/README 更新
  - [ ] **Level 1** - Bug 修复、小优化（不改接口签名）
  - [ ] **Level 2** - 新增接口/Service/Entity/Flyway
  - [ ] **Level 3** - 新增 Phase/Skill/模块/架构层改动

### 第二步：确认已读取必读文件

- [ ] 已读取 `ARCHITECTURE.md`（终局蓝图与当前 Phase 清单）
- [ ] 已读取 `README.md`（当前进度与主要接口）
- [ ] 已读取 `backend-spring/src/main/resources/application.yml`（配置结构）
- [ ] 已读取 `backend-spring/src/main/resources/db/migration/`（数据库现状）
- [ ] 如果涉及 Skill 改动，已读取当前所有 `skills/*.skill.yaml`

### 第三步：Level 2/3 强制文档补充

**仅当改动级别为 Level 2 或 Level 3 时填写：**

- [ ] **ARCHITECTURE.md**：已补充设计说明（模块/Phase/状态机节点/Skill 字段）
- [ ] **TESTING.md** 或 **SKILL_TESTING_STANDARD.md**：已补充验收标准和测试用例描述
- [ ] **docs/vibe-issues/YYYY-MM-DD.md**：已创建当天问题记录文件并写明改动背景、决策和潜在风险
- [ ] 如果改动影响启动流程、技术栈、主要接口，已同步更新 **README.md**

### 第四步：Skill 体系专项检查

**仅当改动涉及以下内容时填写：**

- [ ] 已确认本次改动是否涉及：
  - [ ] 新增或修改 `skills/*.skill.yaml`
  - [ ] 新增 `artifact_type`、`interaction_mode`、`preset_answers`、`analogy_map` 等 Skill 字段
  - [ ] 修改 SkillLoader、RubricLoader、TemplateSkillMatcher、DynamicSkillGenerationService
  - [ ] 将新学科、新主题化反馈或新的目标匹配规则纳入正式交付

- [ ] 如果涉及上述改动，已在 `docs/SKILL_TESTING_STANDARD.md` 中补充：
  - [ ] 结构层测试要求（新增字段是否必检）
  - [ ] 逻辑层测试要求（Skill 读取、Rubric 加载、背景归一化）
  - [ ] 流程层测试要求（CODE/NOTE 按 Skill 路由）
  - [ ] 体验层测试要求（前端是否按 Skill 渲染正确产出区）
  - [ ] 模板匹配测试样本（如果涉及 TemplateSkillMatcher）

---

## 后置检查清单（声明"完成"前）

### 第一步：强制验收脚本

- [ ] 已运行 `verify.bat` / `verify.sh`
- [ ] **步骤 1（编译）**：✅ `mvnw compile` 通过
- [ ] **步骤 2（节点状态机）**：✅ `NodeFsmTest` 全部通过
- [ ] **步骤 3（Spring 上下文）**：✅ `SmokeTest` 全部通过

### 第二步：文档同步确认

- [ ] **README.md**：
  - [ ] 如果完成了某个 Phase，已标记为 ✅ 并补充交付说明
  - [ ] 如果新增了数据库表，已更新技术栈章节的表数量
  - [ ] 如果新增了 API 接口，已在"主要接口"表格中补充
- [ ] **docs/vibe-issues/YYYY-MM-DD.md**：
  - [ ] 已记录本次 Vibe Coding 中发现的问题清单
  - [ ] 已补充根因分析和改进建议
  - [ ] 如有决策，已在"决策"章节记录
- [ ] **docs/TESTING.md** 或 **SKILL_TESTING_STANDARD.md**：
  - [ ] 如果新增了测试用例，已补充对应验收标准
  - [ ] 如果修改了自动化测试数量，已同步修正文档中的用例数

### 第三步：测试覆盖确认

- [ ] 如果本次改动为 **Level 1**：
  - [ ] 已确认现有测试覆盖该逻辑
  - [ ] 运行 `verify.bat` 后，NodeFsmTest 或 SmokeTest 至少有一个能验证本次改动
- [ ] 如果本次改动为 **Level 2/3**：
  - [ ] 已新增单元测试或集成测试
  - [ ] 新增的测试已在 TESTING.md 或 SKILL_TESTING_STANDARD.md 中补充验收标准
  - [ ] 测试命名清晰，能从名称直接理解测试意图

### 第四步：文档与代码一致性检查

- [ ] **架构文档一致性**：
  - [ ] ARCHITECTURE.md 中的描述与当前代码实现一致
  - [ ] 如果实现过程中发现文档描述不合理，已优先修正文档再修正代码
- [ ] **测试文档一致性**：
  - [ ] TESTING.md 中的验收标准与当前测试用例一致
  - [ ] 如果新增了测试类或测试方法，测试文档中已补充说明
- [ ] **Skill 文档一致性**：
  - [ ] 如果修改了 Skill YAML，SKILL_TESTING_STANDARD.md 中已补充或更新对应测试样本
  - [ ] 当前 3 个模板 Skill（`backend_basics`、`english_spoken`、`marxist_philosophy`）仍可正常加载和使用

### 第五步：规则提炼与更新

- [ ] 如果本次对话中产生了值得长期遵守的实践规则：
  - [ ] 已主动提炼为可操作的规则（祈使句格式）
  - [ ] 已追加到 `.github/copilot-instructions.md` 对应章节
  - [ ] 在当天的 `docs/vibe-issues/YYYY-MM-DD.md` 中记录了规则补充说明

---

## 禁止行为（强制检查）

- [ ] ✅ **未出现**代码已实现但文档未补充的情况（文档后置）
- [ ] ✅ **未出现**文档描述与代码实现不一致的情况（文档漂移）
- [ ] ✅ **未出现**新增测试用例但未在 TESTING.md 中补充验收标准的情况（孤立测试）
- [ ] ✅ **未出现**修改 Skill YAML 但未在 SKILL_TESTING_STANDARD.md 中补充对应样本的情况（遗漏覆盖）
- [ ] ✅ **未出现**完成 Phase 后未在 README.md 中标记 ✅ 并补充交付说明的情况（进度失真）
- [ ] ✅ **未出现**`verify.bat` 失败仍声明"完成"的情况

---

## 完成判定

**当且仅当以上所有检查项（根据改动级别）全部打勾时，才可声明本次 Vibe Coding "已完成"。**

**如有任何未完成项，必须在当天的 `docs/vibe-issues/YYYY-MM-DD.md` 中说明原因和后续计划。**

---

## 检查清单模板（复制到每日问题记录）

```markdown
## Vibe Coding 检查清单（YYYY-MM-DD）

### 前置检查
- [ ] 改动级别：Level X
- [ ] 必读文件已读取
- [ ] Level 2/3 文档已补充
- [ ] Skill 体系专项检查已完成

### 后置检查
- [ ] verify.bat 全部通过
- [ ] 文档已同步更新
- [ ] 测试覆盖已确认
- [ ] 文档与代码一致性已检查
- [ ] 规则提炼已完成

### 禁止行为检查
- [ ] 所有禁止行为均未出现

**完成判定**：□ 已完成 / □ 未完成（原因：___）
```
