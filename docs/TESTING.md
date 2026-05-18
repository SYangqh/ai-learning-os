# AI Learning OS — 功能验收测试手册

> 本文档说明如何逐阶段验收已交付的功能。每个 Phase 包含：自动化测试命令 + 手动验收步骤 + 预期结果。

---

## 目录

1. [前置条件](#前置条件)
2. [自动化测试（必须全部通过）](#自动化测试必须全部通过)
3. [Phase 0 — 主链路打通](#phase-0--主链路打通)
4. [Phase 1 — 节点状态机](#phase-1--节点状态机)
5. [Phase 2 — Artifact 体系](#phase-2--artifact-体系)
6. [Phase 3 — Rubric 评审闭环](#phase-3--rubric-评审闭环)
7. [Phase 4 — Skill YAML 背景感知教学](#phase-4--skill-yaml-背景感知教学)
8. [前端 UX 功能](#前端-ux-功能)
9. [Skill 体系专项测试](#skill-体系专项测试)

---

## 前置条件

1. Docker Desktop 正在运行（`docker compose up -d`）
2. 后端已启动（`cd backend-spring && ./mvnw spring-boot:run`）
3. 前端已启动（`cd frontend && npm run dev`）
4. 已准备一个有效的 LLM API Key（OpenAI / Anthropic / 兼容接口）
5. 浏览器访问 http://localhost:3000

---

## 自动化测试（必须全部通过）

在 `backend-spring` 目录下运行：

```bash
# Windows
.\mvnw test "-Dtest=NodeFsmTest,SmokeTest" "-Dspring.profiles.active=test"

# macOS/Linux
./mvnw test -Dtest=NodeFsmTest,SmokeTest -Dspring.profiles.active=test
```

**预期输出：**
```
Tests run: 19, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| 测试类 | 用例数 | 覆盖内容 |
|--------|--------|---------|
| `NodeFsmTest` | 17 | 节点顺序推进、TASK 门控、REVIEW 结构化/关键词回退、AI `[ADVANCE]` 标记控制 |
| `SmokeTest` | 2 | Spring 上下文启动、所有关键 Bean 注册成功 |

> 与当前 Skill YAML 直接相关的结构校验、学科差异校验、模板匹配校验，统一补充到 [docs/SKILL_TESTING_STANDARD.md](docs/SKILL_TESTING_STANDARD.md)。本文件继续负责 Phase 验收主线。

---

## Phase 0 — 主链路打通

> 验收目标：完整走通"登录 → 填画像 → 生成路径 → 进入阶段 → 看到 AI 消息"流程。

### 步骤 1：游客登录

1. 打开 http://localhost:3000
2. 点击「以游客身份继续」
3. ✅ 预期：跳转到下一步（API Key 填写页）

### 步骤 2：填写 API Key

1. 选择 Provider（如 OpenAI），输入 API Key
2. 点击保存
3. ✅ 预期：进入学习画像填写页，API Key 保存成功无报错

### 步骤 3：填写学习画像

填写以下内容并提交：
- 背景：前端工程师（或其他）
- 学习目标：掌握 Spring Boot 后端开发
- 类比基础：我熟悉 React 和 fetch

✅ 预期：AI 开始生成个性化学习路径（页面出现 loading 状态）

### 步骤 4：进入学习阶段

1. 路径生成后，左侧侧边栏显示阶段列表（至少 1 个阶段状态为 `active`）
2. 点击第一个阶段
3. ✅ 预期：
   - 右侧出现聊天记录（AI 发送了开场消息）
   - 顶部显示节点徽章（如 `● 引入`）
   - 底部输入框可用

### 步骤 5：刷新恢复

1. 刷新页面（F5）
2. ✅ 预期：
   - 之前的阶段和节点状态自动恢复
   - 聊天记录完整显示，不丢失

---

## Phase 1 — 节点状态机

> 验收目标：6 个节点按顺序推进，TASK 门控有效，REVIEW 通过机制正常。

### 步骤 1：推进 INTRO → CONCEPT → PRACTICE

1. 在 INTRO 节点发送任意消息（如"我了解一些基础"）
2. ✅ 预期：AI 回复后，顶部节点徽章变为 `● 概念`（CONCEPT）
3. 在 CONCEPT 节点发送消息
4. ✅ 预期：节点变为 `● 练习`（PRACTICE）
5. 在 PRACTICE 节点发送消息
6. ✅ 预期：节点变为 `● 任务`（TASK）

### 步骤 2：TASK 门控验证

1. 在 TASK 节点直接发送消息（不提交代码）
2. ✅ 预期：AI 拒绝推进，底部出现提示：
   > "⚠ 下一步需要提交代码作品。请在右侧代码区写好代码，点击「提交作品」后再发送消息推进。"
3. 节点徽章**仍停留在** `● 任务`，不推进

### 步骤 3：提交 Artifact 后推进

1. 点击「打开代码区」，在右侧编辑器写任意代码（如 `// test`）
2. 点击「提交作品」
3. ✅ 预期：聊天区立即出现确认消息："✅ 代码已收到！请发送一条消息…"
4. 发送任意消息（如"请评审"）
5. ✅ 预期：节点推进到 `● 评审`（REVIEW）

### 步骤 4：REVIEW 未通过时不推进

1. 在 REVIEW 节点发送消息
2. 若 AI 回复**不包含** `[PASS]` 或 `[通过]`
3. ✅ 预期：节点仍停留在 `● 评审`

### 步骤 5：REVIEW 通过

1. 继续与 AI 对话改进代码
2. 当 AI 回复包含 `[PASS]` 或 `[通过]`
3. ✅ 预期：节点推进到 `● 复盘`（RETRO）

### 步骤 6：RETRO 完成 → Stage 完成

1. 在 RETRO 节点发送消息
2. ✅ 预期：
   - 出现绿色🎓「阶段完成！」卡片
   - 输入框消失
   - 侧边栏该阶段状态变为 `completed`（✅图标）

---

## Phase 2 — Artifact 体系

> 验收目标：代码可提交、可查询、状态正确同步。

### 步骤 1：提交代码 Artifact

1. 进入 TASK 节点，点击「打开代码区」
2. 输入以下示例代码：
   ```java
   @RestController
   public class HelloController {
       @GetMapping("/hello")
       public String hello() { return "Hello!"; }
   }
   ```
3. 点击「提交作品」
4. ✅ 预期：
   - 聊天区出现确认消息
   - 代码区底部出现已提交的 Artifact 列表（显示截断预览）
   - 进度徽章变为「✓ 已提交 · 等待评审」（amber 色）

### 步骤 2：多次提交覆盖

1. 修改代码区内容
2. 再次点击「提交作品」
3. ✅ 预期：Artifact 列表出现新条目（时间戳更新），旧条目仍保留

### 步骤 3：REVIEW 通过后状态更新

1. 完成 REVIEW 节点（AI 回复含 `[PASS]`）
2. ✅ 预期：进度徽章变为「✓ 评审通过」（emerald 绿色）

### 步骤 4：REVIEW 未通过状态

1. 若 AI 评审未通过（含 fail 反馈）
2. ✅ 预期：进度徽章变为「✗ 需要修改」（红色）

### 步骤 5：API 直接验证（可选，需 curl）

```bash
# 替换 TOKEN 为实际 JWT，SESSION_ID 为当前 session UUID
curl -H "Authorization: Bearer TOKEN" \
  http://localhost:8080/api/session/SESSION_ID/artifacts
```

✅ 预期：返回包含 `id`、`type`、`content`、`status`、`node_key` 的 JSON 数组

---

## Phase 3 — Rubric 评审闭环

> 验收目标：REVIEW 节点输出结构化评分卡片，passed/score/feedback/hints 字段正确显示。

### 步骤 1：触发 Rubric 评审

1. 完成 TASK 节点（提交代码 + 进入 REVIEW）
2. 在 REVIEW 节点发送消息（如"请开始评审"）
3. ✅ 预期 AI 回复后：
   - 聊天记录下方出现评审卡片
   - 卡片显示「✅ Rubric 评审通过」或「❌ Rubric 评审未通过」
   - 显示分数（如 `85 分`）

### 步骤 2：验证 feedback 和 hints

1. 检查评审卡片内容
2. ✅ 预期：
   - `feedback` 字段：一句话总结（如"你的 Controller 结构正确…"）
   - `hints` 列表（未通过时）：具体改进建议（amber 色 ▸ 列表项）

### 步骤 3：评审通过场景

1. 提交一份满足 YAML rubric `pass_criteria` 的代码
   > 对于 `backend_basics` Stage 1，通过标准为：GET /articles 路径、200 状态码、JSON 数组返回
2. ✅ 预期：
   - 卡片显示「✅ Rubric 评审通过」（绿色边框）
   - 分数 ≥ 80
   - 聊天区出现🏆庆祝卡片（金色边框）
   - 节点自动推进到 RETRO

### 步骤 4：评审未通过场景

1. 提交一份明显缺少要求的代码（如空文件）
2. ✅ 预期：
   - 卡片显示「❌ Rubric 评审未通过」（红色边框）
   - 分数 < 80
   - hints 列表显示对应 `fail_hints` 内容（来自 YAML）
   - 节点**停留在 REVIEW**，不推进

### 步骤 5：进度条持久化

1. 完成 REVIEW 通过后，不要立即切换阶段
2. 刷新页面
3. ✅ 预期：进度徽章仍显示「✓ 评审通过」，不重置

---

## Phase 4 — Skill YAML 背景感知教学

> 验收目标：AI 回复中使用了来自 `backend_basics.skill.yaml` 的类比和任务描述，而非硬编码文本。

### 步骤 1：验证背景感知类比（前端背景）

**准备：** 在学习画像中填写背景为"前端工程师"（或含 "react" / "前端" / "js" 等关键词）

1. 进入任意阶段的 CONCEPT 节点
2. 发送消息，触发 AI 讲解
3. ✅ 预期：AI 回复中出现类似以下类比（来自 YAML `analogy_map.frontend`）：
   - "类比 fetch()" / "类比 localStorage" / "类比 Redux reducer" 等
   - **不应该**出现硬件/金融相关类比

### 步骤 2：对比不同背景的类比差异

**方法：** 分别用以下背景创建账号（游客即可），观察 CONCEPT 节点的 AI 回复：

## Skill 体系专项测试

当前仓库已有 3 个模板 Skill：`backend_basics`、`english_spoken`、`marxist_philosophy`。

后续凡涉及以下改动时，除本文件的主链路验收外，还必须补跑 [docs/SKILL_TESTING_STANDARD.md](docs/SKILL_TESTING_STANDARD.md) 中定义的专项测试：

1. 新增或修改 `backend-spring/src/main/resources/skills/*.skill.yaml`
2. 新增 `TemplateSkillMatcher` / `DynamicSkillGenerationService` / SkillLoader / RubricLoader 相关逻辑
3. 新增 `artifact_type`、`interaction_mode`、`preset_answers`、`analogy_map` 等 Skill 字段
4. 将新学科、新主题化反馈或新的目标匹配规则纳入正式交付范围

| 背景关键词 | 期望类比风格 |
|-----------|------------|
| 前端工程师 / react | fetch() / localStorage / Redux |
| 硬件工程师 / 嵌入式 | 总线请求 / Flash 存储 / 中断 |
| 金融 / 量化 | 交易指令 / 台账 / 数字签名 |
| 产品经理 | 表单提交 / Excel / Cookie |

✅ 预期：不同背景的用户，AI 在讲解同一概念时使用了明显不同的类比措辞。

### 步骤 3：验证 TASK 节点使用 YAML task_description

1. 推进到 TASK 节点（Stage 1 的 backend_basics）
2. 观察 AI 的任务说明消息
3. ✅ 预期：AI 的任务描述包含 YAML 中的具体要求：
   > "用 Spring Boot 实现 GET /articles 接口，返回文章列表（固定假数据即可）。
   > 要求：状态码正确，响应体为 JSON 数组，每条包含 id、title、createdAt。"
4. **不应该**只是泛化的"请完成本阶段任务"

### 步骤 4：日志验证（开发者验证）

在后端日志中搜索，确认 YAML 数据被加载：

```bash
# 后端日志中不应出现以下错误：
"Skill YAML not found: skills/backend_basics.skill.yaml"
"Failed to load skill rubric"
"Failed to load stage data"
"Failed to load analogies"
```

✅ 预期：日志中无上述警告，说明 YAML 加载正常。

### 步骤 5：fallback 验证（无 YAML 时降级）

将 Skill YAML 文件临时重命名，重启后端，进入 TASK 节点：

✅ 预期：AI 仍然能正常回复（降级到通用 task 指令），不报 500 错误。

> 验证后恢复文件名。

---

## Phase 9A — 主题化正反馈演出

> 验收目标：正反馈演出能按主题切换文案/动效/音效，支持静音降级，播放失败不影响主流程。

### 步骤 1：验证主题差异

1. 在学习页完成一个正向事件（如 PRACTICE 口头回答被 AI 认可、REVIEW 通过、完成 Stage）
2. 观察反馈演出的文案、动画效果和音效
3. 切换到另一套主题（设置 → 主题切换）
4. 重复触发同一正向事件
5. ✅ 预期：
   - 文案语气明显不同（如暗黑主题偏克制，轻松主题偏活泼）
   - 动画节奏明显不同（如暗黑主题淡入淡出，轻松主题弹跳动效）
   - 音效风格明显不同（如暗黑主题低频提示音，轻松主题清脆音效）

### 步骤 2：验证静音跟随

1. 将系统设置为静音模式（或关闭音效总开关）
2. 触发正向事件
3. ✅ 预期：
   - 不播放音效
   - 仍然显示文案和动画
   - 不弹出"播放失败"错误提示

### 步骤 3：验证低动效模式

1. 开启无障碍低动效模式（设置 → 无障碍）
2. 触发正向事件
3. ✅ 预期：
   - 不触发强烈弹跳或闪烁动画
   - 改为平滑淡入淡出或静态文本提示
   - 音效保持正常（除非同时静音）

### 步骤 4：验证播放失败降级

1. 清空浏览器缓存或模拟音效文件加载失败
2. 触发正向事件
3. ✅ 预期：
   - 页面不报错，不中断学习流程
   - 至少显示普通文本提示（如"回答不错！"）
   - 节点状态正常推进，不受影响

### 步骤 5：验证配置新增生效

1. 在 FeedbackEffectManifest 配置中新增一个主题的反馈条目（开发者操作）
2. 不修改业务代码，仅刷新页面
3. 触发对应事件
4. ✅ 预期：新主题的文案、动效、音效自动生效，无需重新部署后端

---

## 前端 UX 功能

> 以下功能为本项目额外交付的 UX 改进，逐项验收。

### 复制按钮

1. 鼠标悬停在任意 AI 回复气泡或用户消息气泡上
2. ✅ 预期：气泡右上角出现「复制」按钮（半透明）
3. 点击复制按钮
4. ✅ 预期：按钮文字变为「✓」持续约 1.5 秒，内容已写入剪贴板

### 导出聊天记录

1. 进入任意有聊天记录的阶段
2. 点击顶部「⬇ 导出记录」按钮
3. ✅ 预期：浏览器弹出文件下载，文件名格式为 `{阶段名}-聊天记录.md`
4. 打开下载的文件
5. ✅ 预期：包含导出时间、**你：** / **AI 导师：** 格式的完整对话内容，Markdown 格式

### 重新生成回复

1. 在非 stageComplete 状态下，找到最后一条 AI 消息
2. ✅ 预期：消息气泡下方显示「↺ 重新生成」按钮
3. 点击该按钮
4. ✅ 预期：
   - 最后一条 AI 消息原地替换为新的 AI 回复
   - 节点状态不变（不推进）
   - 历史消息条数不增加

### 节点操作提示

1. 进入不同节点时，观察底部输入区
2. ✅ 预期：在输入框下方显示对应提示（sky 蓝色字体）：

| 节点 | 期望提示内容 |
|------|------------|
| intro | 💬 回答引导问题，让 AI 了解你的现有认知即可 |
| concept | 📖 阅读 AI 讲解，随时提问深入理解概念 |
| practice | ✏️ 口头回答练习题即可，无需写代码 |
| task | 💻 在右侧代码区完成任务，写好后点击「提交作品」 |
| review | 📬 发送一条消息，AI 将立刻开始评审你的代码 |
| retro | 🧠 与 AI 一起回顾本阶段收获，可自由提问 |

### 庆祝卡片不跨阶段残留

1. 完成任意阶段的 REVIEW（通过），出现🏆庆祝卡片
2. 在侧边栏点击另一个阶段
3. ✅ 预期：庆祝卡片消失，新阶段的聊天记录干净显示

### 完成阶段的历史记录

1. 找到侧边栏中状态为 completed 的阶段
2. 点击该阶段
3. ✅ 预期：
   - 聊天区展示该阶段的**完整历史对话**（不是空白，不是新开场消息）
   - 显示绿色🎓「阶段完成！」卡片
   - 输入框不显示（只读模式）

---

## 快速验收 Checklist

完成一轮完整的"从游客到阶段完成"后，确认以下所有项均通过：

- [ ] 自动化测试 15 条全部 PASS
- [ ] 游客登录 → 填画像 → 生成路径：全程无 500 错误
- [ ] 6 个节点顺序推进正确（不可跳过）
- [ ] TASK 节点未提交 Artifact 时拒绝推进
- [ ] 提交 Artifact 后进度徽章更新
- [ ] REVIEW 节点出现结构化 Rubric 卡片（分数 + feedback + hints）
- [ ] REVIEW 通过后出现庆祝卡片，节点推进到 RETRO
- [ ] RETRO 完成后阶段标记为 completed，输入框消失
- [ ] 刷新页面后状态完整恢复
- [ ] AI 回复中出现与用户背景匹配的类比措辞
- [ ] 消息气泡 hover 显示复制按钮，点击有效
- [ ] 导出聊天记录文件格式正确
- [ ] 重新进入 completed 阶段能看到历史聊天记录
- [ ] 切换阶段时庆祝卡片正确清除


---

## Phase 9B — 混合作答模式与预制答案

> 验收目标：PRACTICE 节点支持"自由输入"和"预制答案选择"两种作答方式，且可按 Skill 配置控制。

### 步骤 1：验证 HYBRID 混合模式（默认）

1. 进入任意 PRACTICE 节点
2. ✅ 预期：聊天区底部同时出现：
   - 自由输入框（用户可手动输入答案）
   - 预制答案卡片区（显示 2-4 个候选答案按钮）
3. 点击任一预制答案卡片
4. ✅ 预期：
   - 该答案自动填入输入框（可编辑）
   - 点击「发送」后，AI 收到该答案并回复
5. 清空输入框，手动输入自定义答案并发送
6. ✅ 预期：AI 正常接收并回复

### 步骤 2：验证 FREE_INPUT_ONLY 强制手输模式

1. 修改任一 Skill YAML 文件（如 `backend_basics.skill.yaml`），添加：
   \\\yaml
   interaction_mode: FREE_INPUT_ONLY
   \\\
2. 重启后端，重新进入该 Skill 对应的 PRACTICE 节点
3. ✅ 预期：
   - **不显示**预制答案卡片
   - 仅显示自由输入框
   - 提示文案："请手动输入答案（本题禁用预制答案）"

### 步骤 3：验证 PRESET_ONLY 预制答案模式

1. 修改 Skill YAML 为：
   \\\yaml
   interaction_mode: PRESET_ONLY
   preset_answers:
     - text: "使用 @RestController 注解"
       confidence: high
     - text: "不确定，需要再学习"
       confidence: low
   \\\
2. 重启后端，进入该节点
3. ✅ 预期：
   - 显示预制答案按钮
   - 自由输入框被禁用或隐藏
   - 必须点击预制答案才能继续

### 步骤 4：验证答案来源记录（后端审计）

1. 分别提交一次自由输入答案和一次预制答案
2. 检查后端日志或数据库 `session_messages` 表
3. ✅ 预期：
   - 自由输入的消息：`answer_source=FREE_INPUT`
   - 预制答案的消息：`answer_source=PRESET`，且包含 `preset_answer_id`

### 步骤 5：验证学科差异场景约束

1. 新增一个数学类 Skill YAML：
   \\\yaml
   skill_id: elementary_math
   interaction_mode: FREE_INPUT_ONLY  # 禁用预制答案
   \\\
2. 进入该 Skill 的 PRACTICE 节点
3. ✅ 预期：
   - 不显示预制答案（儿童数学必须手输）
   - 提示文案："请独立计算并输入结果"

### 步骤 6：验证用户偏好覆盖（可选）

1. 在设置面板中添加"总是使用自由输入"开关
2. 开启后，即使 Skill 配置为 HYBRID
3. ✅ 预期：不显示预制答案卡片（用户偏好优先于 Skill 配置）

---

## Phase 9C — 移动端 Web 与 iOS / Android 验证

> 验收目标：在真机或移动浏览器上，全流程（登录→学习→提交→评审）操作流畅，安全区/软键盘/横竖屏均正常适配；离线草稿不丢失；推送/触感降级不中断学习。

### 步骤 1：安全区适配验证（iOS 刘海 / 底部 Home Indicator）

**设备要求：** iPhone X 或更新机型（有刘海/灵动岛 + Home Indicator）

1. 用 Safari 或 Capacitor 打包的 App 打开学习页
2. ✅ 预期：
   - 顶部导航栏不被刘海/状态栏遮挡（safe-area-inset-top 生效）
   - 底部输入框不被 Home Indicator 遮挡（safe-area-inset-bottom 生效）
   - 横屏时两侧内容不被圆角或刘海遮挡
3. 旋转至横屏，再旋转回竖屏
4. ✅ 预期：布局正确重排，无内容溢出或错位

### 步骤 2：软键盘适配验证

1. 在手机浏览器（Safari / Chrome Mobile）中打开学习页
2. 点击聊天输入框，触发软键盘弹起
3. ✅ 预期：
   - 输入框不被键盘遮挡（视图自动上移或 padding-bottom 动态增加）
   - 聊天消息区域仍可滚动
   - 收起键盘后输入框恢复原始位置
4. 在 Artifact 代码输入区重复上述操作
5. ✅ 预期：代码编辑区可见，不被键盘遮挡

### 步骤 3：Sidebar 抽屉验证（移动端）

1. 在手机浏览器（屏幕宽度 < 768px）打开学习页
2. ✅ 预期：侧边栏默认隐藏（不占用屏幕空间）
3. 点击 Topbar 左侧汉堡菜单按钮（≥ 44×44px 触控区域）
4. ✅ 预期：侧边栏从左侧滑入，背景出现半透明遮罩
5. 点击遮罩区域
6. ✅ 预期：侧边栏滑出收起
7. 点击侧边栏内任意学习阶段
8. ✅ 预期：侧边栏自动收起，主内容区切换到对应阶段

### 步骤 4：ArtifactPanel 底部 Sheet 验证（移动端）

1. 进入一个有 Artifact 的学习阶段（TASK 节点）
2. ✅ 预期：屏幕右下角显示 FAB（💻 按钮），不遮挡主内容
3. 点击 FAB
4. ✅ 预期：底部 Sheet 从底部滑入，显示代码/笔记编辑区
5. 在代码区输入内容（等待约 1 秒）
6. ✅ 预期：底部状态栏出现"草稿已保存"提示（带时间）
7. 刷新页面，重新进入同一阶段
8. ✅ 预期：代码区自动恢复上次输入内容（IndexedDB 草稿恢复）
9. 提交作品后检查草稿
10. ✅ 预期：草稿被清除（提交后 clearDraft 调用成功）

### 步骤 5：离线草稿降级验证

1. 开启浏览器隐私/无痕模式（IndexedDB 受限）
2. 进入学习页的 TASK 节点，输入一些代码
3. ✅ 预期：
   - 页面不报错，学习流程正常进行
   - 不显示"草稿已保存"（降级静默，不显示错误提示）
4. 关闭无痕模式，恢复正常浏览器
5. ✅ 预期：IndexedDB 草稿功能恢复正常

### 步骤 6：推送/触感权限降级验证

1. 在浏览器设置中**拒绝**该站点的通知权限
2. 完成一个学习阶段（RETRO 节点完成）
3. ✅ 预期：
   - 不弹出通知权限请求对话框（不再次打扰）
   - 页面正常显示完成反馈演出
   - 控制台无未处理的权限错误
4. 在不支持 Vibration API 的设备（iOS Safari）上点击按钮
5. ✅ 预期：无报错，触感功能静默降级
