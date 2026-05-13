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
Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| 测试类 | 用例数 | 覆盖内容 |
|--------|--------|---------|
| `NodeFsmTest` | 13 | 节点顺序推进、TASK 门控、REVIEW [PASS] 关键词、retro 完成后 stage_complete=true |
| `SmokeTest` | 2 | Spring 上下文启动、所有关键 Bean 注册成功 |

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

