# AI Learning OS — UI/UX 设计标准

> 本文档定义 AI Learning OS 的交互设计规范，覆盖桌面端与移动端（iOS/Android），确保所有功能改动遵循统一的体验标准。

---

## 目录

1. [设计原则](#设计原则)
2. [交互模式体系](#交互模式体系)
3. [预制答案面板规范](#预制答案面板规范)
4. [移动端适配强制要求](#移动端适配强制要求)
5. [主题化反馈演出规范](#主题化反馈演出规范)
6. [无障碍与包容性设计](#无障碍与包容性设计)
7. [验收检查清单](#验收检查清单)

---

## 设计原则

### 核心原则

| 原则 | 说明 | 示例 |
|------|------|------|
| **移动优先** | 任何新功能必须先考虑移动端体验，再扩展到桌面端 | 按钮最小点击区域 44×44 px |
| **渐进增强** | 基础功能在低端设备可用，高级特性按能力降级 | 触感反馈在不支持的设备静默降级 |
| **语义化交互** | 用户操作必须有即时反馈（视觉/声音/触感） | 提交作品后立刻显示加载状态 |
| **包容性** | 支持无障碍、支持不同背景用户、支持不同学习偏好 | 儿童练习强制手输，专业学习可用预制答案 |
| **文档先行** | UI 改动前必须先更新本文档，代码实现后必须通过验收清单 | Level 2/3 改动必须补充本文档 |

### 反模式（禁止）

❌ **只在桌面端测试就发布**  
❌ **预制答案在所有场景都可用**（数学题、儿童练习必须强制手输）  
❌ **正反馈只换颜色不换文案/动效/音效**  
❌ **用纯文本展示错误信息**（必须业务友好）  
❌ **硬编码交互逻辑**（必须由 Skill YAML 驱动）  

---

## 交互模式体系

### 三种模式定义

| 模式 | `interaction_mode` | 适用场景 | UI 表现 |
|------|-------------------|---------|---------|
| **自由输入** | `FREE_INPUT_ONLY` | 数学计算、儿童练习、创意写作、精确推导 | 只显示输入框，不显示预制答案面板 |
| **预制答案** | `PRESET_ONLY` | 背景调研、快速确认、选择题、引导型问答 | 只显示预制答案按钮，不显示输入框 |
| **混合模式** | `HYBRID`（默认） | 技术学习、概念理解、开放讨论 | 同时显示输入框和预制答案面板 |

### 模式切换规则

1. **Skill 级默认值**：在 `*.skill.yaml` 中定义 `interaction_mode`
2. **节点级覆盖**：INTRO 节点背景调研可临时改为 `PRESET_ONLY`
3. **题型级强制**：数学题、儿童练习必须在 Skill 中显式声明 `FREE_INPUT_ONLY`
4. **用户级偏好**：允许用户在设置中关闭预制答案（未来 Phase）

### 在 Skill YAML 中配置

```yaml
id: math_basics
display_name: 数学基础
interaction_mode: FREE_INPUT_ONLY  # 全局强制自由输入

preset_answers: []  # 数学题禁用预制答案
```

```yaml
id: nodejs_basics
display_name: Node.js 基础
interaction_mode: HYBRID  # 默认混合模式

preset_answers:
  # 全局预制答案（适用于 INTRO 节点的背景调研）
  - id: pa_nodejs_exp_browser
    text: "用过浏览器端 JS，写过交互页面"
    confidence: HIGH
    # 无 stage_index，全局可用

  # 阶段级预制答案（适用于具体技术点的 PRACTICE 节点）
  - id: pa_nodejs_s1_require
    text: "用 require() 引入模块"
    confidence: HIGH
    stage_index: 1
```

---

## 预制答案面板规范

### 显示逻辑

| 节点类型 | 是否显示 | 规则 |
|---------|---------|------|
| **INTRO** | 是 | `interaction_mode` 为 `PRESET_ONLY` 或 `HYBRID` 且有匹配的全局 preset_answers（`stage_index` 为 null） |
| **CONCEPT** | 否 | 概念讲解节点，用户只需阅读，不显示预制答案 |
| **PRACTICE** | 是 | `interaction_mode` 为 `PRESET_ONLY` 或 `HYBRID` 且有匹配的阶段 preset_answers（`stage_index` 匹配当前 stage） |
| **TASK** | 否 | 任务节点需要提交作品，不显示预制答案 |
| **REVIEW** | 是 | `interaction_mode` 为 `PRESET_ONLY` 或 `HYBRID` 且有预制答案（用于快速确认理解） |
| **RETRO** | 是 | `interaction_mode` 为 `PRESET_ONLY` 或 `HYBRID` 且有预制答案（用于总结反思） |

**核心规则**：
- `stage_index` 为 `null` 的预制答案在所有节点可用（全局）
- `stage_index` 为数字的预制答案只在对应 stage 的节点可用
- `FREE_INPUT_ONLY` 模式下，任何节点都不显示预制答案面板

### UI 布局

#### 桌面端（≥768px）

```
┌─────────────────────────────────────┐
│ 预制答案（点击快速回复）              │
├─────────────────────────────────────┤
│ [用过浏览器 JS] [跑过 node] [从零开始] │
│ [不太确定，需要看示例]                │
└─────────────────────────────────────┘
```

- 位置：聊天输入框上方，消息区域下方
- 布局：横向流式布局（`flex-wrap`）
- 按钮尺寸：`px-3 py-1.5`（12×6 最小点击区域）
- 按钮样式：
  - `confidence: HIGH` → 深色边框、清晰文字、hover 时强调色
  - `confidence: LOW` → 浅色边框、淡化文字、hover 时轻微提亮

#### 移动端（<768px）

```
┌─────────────────────────────────────┐
│ 预制答案（点击快速回复）              │
├─────────────────────────────────────┤
│ [用过浏览器 JS]                      │
│ [跑过 node xxx.js]                   │
│ [从零开始]                           │
│ [不太确定，需要看示例]                │
└─────────────────────────────────────┘
```

- 位置：固定在软键盘上方（`bottom: {keyboardHeight}px`）
- 布局：纵向堆叠（`flex-col`）或横向滚动（`overflow-x-auto`）
- 按钮尺寸：`min-h-[44px]`（iOS 人机界面指南最小点击区域）
- 按钮样式：增加内边距（`px-4 py-3`），确保手指易点击

### 交互行为

1. **点击预制答案**：
   - 立刻将答案文本填充到输入框
   - 光标定位到输入框末尾
   - 自动聚焦输入框（桌面端）
   - 不自动发送（允许用户修改后再发送）

2. **长按预制答案**（移动端）：
   - 触发触感反馈（`Haptics.impact({ style: 'light' })`）
   - 显示完整答案文本（若按钮文本被截断）

3. **滑动关闭**（移动端）：
   - 支持向下滑动隐藏预制答案面板
   - 滑动超过 50px 时触发关闭动画
   - 关闭后在输入框旁显示"展开预制答案"按钮

### 可访问性要求

- 每个按钮必须有 `aria-label`（描述答案内容和置信度）
- 支持键盘导航（Tab 键切换，Enter 键选择）
- 支持屏幕阅读器（读出答案文本和置信度）
- 按钮之间对比度 ≥4.5:1（WCAG AA 标准）

---

## 动态预制答案规范（Phase 9D）

### 设计背景

**问题**：传统的人工预制答案只能覆盖"可预见的问题模式"，AI 生成的深入追问无法提前在 Skill YAML 中定义。

**解决方案**：混合方案 —— YAML 优先 + AI 动态生成降级

### 工作机制

```
┌─────────────────────────────────────────────┐
│ 1. 用户回答问题                              │
└─────────────────┬───────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────┐
│ 2. 后端检查 Skill YAML 中是否有匹配的预制答案│
│    - 优先匹配全局预制答案（stage_index=null）│
│    - 其次匹配关键词触发的预制答案             │
│    - 最后匹配阶段级预制答案（stage_index=N）  │
└─────────────────┬───────────────────────────┘
                  │
        ┌─────────┴─────────┐
        │                   │
        ▼                   ▼
  有匹配的预制答案      无匹配的预制答案
        │                   │
        │                   ▼
        │     ┌─────────────────────────────┐
        │     │ 3. AI 生成问题时，同时生成   │
        │     │    3-5 个建议答案选项       │
        │     │    （结构化 JSON 输出）      │
        │     └─────────────┬───────────────┘
        │                   │
        └─────────┬─────────┘
                  │
                  ▼
┌─────────────────────────────────────────────┐
│ 4. 前端显示预制答案面板                      │
│    - YAML 预制答案（质量高、稳定）           │
│    - 或 AI 动态生成答案（全覆盖、零维护）     │
└─────────────────────────────────────────────┘
```

### Skill YAML 扩展字段

#### 关键词触发的预制答案（新增）

```yaml
preset_answers:
  # 全局背景调研（无 trigger_keywords）
  - id: pa_nodejs_exp_browser
    text: "用过浏览器端 JS，写过交互页面"
    confidence: HIGH
    stage_index: null  # 全局可用

  # 关键词触发的预制答案（新增 trigger_keywords 字段）
  - id: pa_nodejs_module_yes
    text: "用过 import/require，引入过第三方库"
    confidence: HIGH
    trigger_keywords: ["模块", "包", "import", "require", "CommonJS", "ES Module"]
    stage_index: null  # 或指定 stage

  - id: pa_nodejs_module_partial
    text: "听说过，但不太清楚它们的区别"
    confidence: MEDIUM
    trigger_keywords: ["模块", "包", "import", "require"]
    stage_index: null

  - id: pa_nodejs_module_no
    text: "没用过，都是直接写在一个文件里"
    confidence: HIGH
    trigger_keywords: ["模块", "包"]
    stage_index: null

  # 异步编程相关
  - id: pa_nodejs_async_yes
    text: "用过 Promise/async/await 处理异步"
    confidence: HIGH
    trigger_keywords: ["异步", "Promise", "async", "await", "回调"]
    stage_index: null

  - id: pa_nodejs_async_partial
    text: "只用过回调函数，不太了解 Promise"
    confidence: MEDIUM
    trigger_keywords: ["异步", "Promise", "async", "回调"]
    stage_index: null
```

**字段说明**：
- `trigger_keywords`（可选）：当 AI 生成的问题包含任一关键词时，该预制答案会被加入候选列表
- 如果 `trigger_keywords` 为空或不存在，该预制答案只在初始背景调研时显示（传统逻辑）

### AI 动态生成预制答案

#### Prompt 模板（新增结构化输出要求）

```java
"""
作为 AI 导师，根据学习者的回答"{userAnswer}"，提出一个深入的引导性问题。

请以 JSON 格式返回：
{
  "question": "你的问题内容",
  "suggestedAnswers": [
    { "text": "答案选项1", "confidence": "HIGH" },
    { "text": "答案选项2", "confidence": "MEDIUM" },
    { "text": "答案选项3", "confidence": "LOW" }
  ]
}

要求：
1. question：清晰、引导性的问题，帮助学习者深入思考
2. suggestedAnswers：3-5 个可能的答案选项，涵盖不同熟悉程度
   - HIGH：明确掌握、经常使用
   - MEDIUM：部分了解、偶尔使用
   - LOW：不确定、不了解、没用过
3. 答案选项要具体、可操作，避免模糊表述
4. 答案选项要覆盖"是/部分是/否"或"熟悉/了解/不清楚"等维度

示例：
{
  "question": "很好！那么在写交互页面时，你有没有用过模块化开发，比如用 import 或 require 引入第三方库？",
  "suggestedAnswers": [
    { "text": "用过，经常用 import 引入 React、Lodash 等", "confidence": "HIGH" },
    { "text": "听说过，但都是直接用 <script> 标签引入", "confidence": "MEDIUM" },
    { "text": "没接触过，代码都写在一个文件里", "confidence": "LOW" }
  ]
}
"""
```

#### 后端响应结构（新增字段）

```java
// AdvanceResponse.java
public class AdvanceResponse {
    private String message;              // AI 的问题
    private InteractionConfig interactionConfig;
    private String presetAnswerSource;   // 新增：来源标记
    // ... 其他字段
}

// InteractionConfig.java
public record InteractionConfig(
    String mode,
    List<PresetAnswer> presetAnswers,
    String source  // 新增："YAML" 或 "AI_GENERATED"
) {}
```

#### 前端显示区分（可选）

```tsx
// ChatPanel.tsx 中的预制答案面板
<div className="flex flex-col gap-2 px-4 py-3 border-b t-border t-panel">
  <p className="text-xs t-faint font-medium">
    💡 快速选择
    {presetAnswerSource === 'AI_GENERATED' && (
      <span className="ml-2 text-[10px] opacity-60">（AI 智能生成）</span>
    )}
  </p>
  <div className="flex flex-wrap gap-2">
    {/* 预制答案按钮 */}
  </div>
</div>
```

### 优先级规则

| 优先级 | 来源 | 条件 | 示例场景 |
|--------|------|------|----------|
| **1** | YAML 全局预制答案 | `stage_index=null` 且无 `trigger_keywords` | 初始背景调研问题 |
| **2** | YAML 关键词触发预制答案 | AI 问题包含 `trigger_keywords` | AI 追问"你用过模块化吗？" |
| **3** | YAML 阶段级预制答案 | `stage_index` 匹配当前 stage | Stage 1 的 PRACTICE 节点 |
| **4** | AI 动态生成预制答案 | 以上都无匹配时降级 | AI 提出新颖的、无法预见的问题 |

### 匹配逻辑（伪代码）

```java
public InteractionConfig loadInteractionConfig(
    String skillId, 
    Integer stageIndex, 
    String lastAiMessage  // AI 的最后一条消息
) {
    List<PresetAnswer> allAnswers = loadAllPresetAnswers(skillId);
    
    // 1. 过滤 stage_index 匹配的预制答案
    List<PresetAnswer> stageFiltered = allAnswers.stream()
        .filter(a -> a.stageIndex == null || a.stageIndex.equals(stageIndex))
        .toList();
    
    // 2. 如果有 AI 消息，进行关键词匹配过滤
    if (lastAiMessage != null && !lastAiMessage.isEmpty()) {
        List<PresetAnswer> keywordMatched = stageFiltered.stream()
            .filter(a -> {
                // 无 trigger_keywords 的预制答案（全局背景调研）
                if (a.triggerKeywords == null || a.triggerKeywords.isEmpty()) {
                    return false;  // 只在初始问题时显示
                }
                // 有 trigger_keywords 的预制答案（关键词匹配）
                return a.triggerKeywords.stream()
                    .anyMatch(lastAiMessage::contains);
            })
            .toList();
        
        if (!keywordMatched.isEmpty()) {
            return new InteractionConfig(mode, keywordMatched, "YAML");
        }
    }
    
    // 3. 无 AI 消息时，返回全局预制答案
    List<PresetAnswer> globalAnswers = stageFiltered.stream()
        .filter(a -> a.triggerKeywords == null || a.triggerKeywords.isEmpty())
        .toList();
    
    if (!globalAnswers.isEmpty()) {
        return new InteractionConfig(mode, globalAnswers, "YAML");
    }
    
    // 4. 都无匹配时，返回空（后续由 AI 动态生成）
    return new InteractionConfig(mode, List.of(), "PENDING");
}
```

### 用户体验增强

#### 场景 1：初始背景调研

```
AI：你之前有没有用过 Node.js 做过什么小项目或脚本？

💡 快速选择
[用过浏览器端 JS，写过交互页面]  ← YAML 全局预制答案
[跑过 node xxx.js，知道 console.log]
[没接触过 JS / Node，完全从零开始]
```

#### 场景 2：关键词匹配触发

```
AI：很好！那么你有没有用过模块化开发，比如 import 或 require？

💡 快速选择
[用过 import/require，引入过第三方库]  ← YAML 关键词触发
[听说过，但不太清楚它们的区别]
[没用过，都是直接写在一个文件里]
```

#### 场景 3：AI 动态生成

```
AI：非常好！那你在使用 import 时，有没有遇到过循环依赖的问题？

💡 快速选择（AI 智能生成）  ← 标记为 AI 生成
[遇到过，通过重构模块解决了]
[听说过，但没实际遇到]
[不知道循环依赖是什么]
```

### 降级策略

| 情况 | 处理方式 |
|------|---------|
| AI 未返回 `suggestedAnswers` | 不显示预制答案面板，允许自由输入 |
| AI 返回的答案选项 <2 个 | 不显示预制答案面板，质量不足 |
| AI 返回的答案选项 >5 个 | 只取前 5 个，避免选项过多 |
| AI 返回格式错误（非 JSON） | 解析失败时降级为纯文本问题，不显示预制答案 |

### 性能优化

1. **缓存 YAML 预制答案**：在 SkillRubricLoader 中缓存已加载的 Skill YAML，避免重复解析
2. **异步解析 AI 响应**：使用 CompletableFuture 异步解析 JSON，不阻塞主线程
3. **前端去重**：如果 YAML 和 AI 生成的答案文本重复，前端自动去重

---

## 移动端适配强制要求

### 响应式断点

| 断点 | 宽度 | 设备 | 主要适配策略 |
|------|------|------|-------------|
| `xs` | <640px | 手机竖屏 | 单列布局、底部导航、全屏输入 |
| `sm` | 640~768px | 手机横屏、小平板 | 侧边栏折叠、双列布局 |
| `md` | 768~1024px | 平板 | 三列布局、侧边栏展开 |
| `lg` | ≥1024px | 桌面 | 完整三列、固定侧边栏 |

### 布局适配规则

#### 手机端（<768px）

**必须**：
- ✅ 主内容区占满屏幕（`flex-1`）
- ✅ 顶部导航固定（`sticky top-0`）
- ✅ 底部输入区固定（`sticky bottom-0`）
- ✅ 左侧边栏改为抽屉（滑出/收起）
- ✅ 右侧产出区改为全屏弹窗（点击"查看产出"按钮打开）
- ✅ 预制答案面板固定在软键盘上方
- ✅ 消息列表自动滚动到底部（新消息到达时）
- ✅ 支持下拉刷新历史消息

**禁止**：
- ❌ 三列布局（会导致内容区过窄）
- ❌ 固定宽度（必须用百分比或 `flex`）
- ❌ 横向滚动（除非是图片轮播等特殊场景）
- ❌ 过小的点击区域（<44px）
- ❌ 模态弹窗遮挡输入框

#### 平板端（768~1024px）

**必须**：
- ✅ 左侧边栏可选展开/折叠
- ✅ 主内容区和产出区并排显示（60/40 分割）
- ✅ 预制答案面板浮动在输入框上方
- ✅ 支持横屏模式（landscape）

#### 桌面端（≥1024px）

**必须**：
- ✅ 完整三列布局（左 20%、中 50%、右 30%）
- ✅ 左侧边栏固定展开
- ✅ 预制答案面板嵌入在输入框上方
- ✅ 支持快捷键（Ctrl+Enter 发送、Ctrl+K 聚焦输入框）

### 软键盘适配

#### iOS

```typescript
// 监听键盘事件（Capacitor 插件）
import { Keyboard } from '@capacitor/keyboard'

useEffect(() => {
  const showListener = Keyboard.addListener('keyboardWillShow', info => {
    setKeyboardHeight(info.keyboardHeight)
  })
  const hideListener = Keyboard.addListener('keyboardWillHide', () => {
    setKeyboardHeight(0)
  })
  return () => {
    showListener.remove()
    hideListener.remove()
  }
}, [])
```

**适配要点**：
- ✅ 输入框上方内容区域高度 = `100vh - topbarHeight - keyboardHeight - inputHeight`
- ✅ 预制答案面板 `bottom: {keyboardHeight}px`
- ✅ 输入框聚焦时自动滚动到可见区域
- ✅ 键盘弹出时隐藏底部导航栏（为输入区腾出空间）

#### Android

```typescript
// AndroidManifest.xml
<activity android:windowSoftInputMode="adjustResize">
```

**适配要点**：
- ✅ 使用 `adjustResize` 模式自动调整布局
- ✅ 避免使用 `position: fixed` 在输入框区域（会被键盘遮挡）
- ✅ 使用 `IntersectionObserver` 检测输入框是否被遮挡

### 触感反馈规范

| 操作 | 触感类型 | 条件 |
|------|---------|------|
| 点击预制答案 | `light` | 所有移动端 |
| 提交作品 | `medium` | 仅支持的设备 |
| Rubric 通过 | `success` | 仅支持的设备 |
| Rubric 失败 | `warning` | 仅支持的设备 |
| Stage 完成 | `heavy` | 仅支持的设备 |

**降级策略**：
```typescript
import { Haptics } from '@capacitor/haptics'

async function triggerHaptic(style: 'light' | 'medium' | 'heavy') {
  try {
    if (Capacitor.isNativePlatform()) {
      await Haptics.impact({ style })
    }
  } catch (e) {
    // 静默降级，不影响功能
    console.log('[Haptics] 不支持触感反馈或已禁用')
  }
}
```

### 声音权限处理

参见 [SOUND_EFFECTS_GUIDE.md](./SOUND_EFFECTS_GUIDE.md)，核心要点：

- ✅ 首次播放前必须用户手势触发
- ✅ 提供全局静音开关（遵守系统静音状态）
- ✅ 音频文件 ≤50KB（避免流量消耗）
- ✅ 降级策略：无声音仍可正常使用

---

## 主题化反馈演出规范

### 主题体系

| 主题 ID | 视觉风格 | 文案风格 | 音效风格 | 动效风格 |
|---------|---------|---------|---------|---------|
| `cute` | 柔和圆角、渐变色、插画风格 | 轻松活泼、emoji 丰富 | 清脆悦耳、钢琴/木琴 | 弹跳、缩放、旋转 |
| `formal` | 直角边框、深色主题、扁平风格 | 克制专业、少 emoji | 低频提示音 | 淡入淡出、平滑过渡 |
| `enterprise` | 蓝灰色调、细边框、严肃字体 | 正式用语、无 emoji | 系统默认音 | 无动效或极简动效 |
| `playful` | 明亮色彩、不规则形状、卡通风格 | 夸张幽默、emoji 多 | 游戏音效、8-bit 风格 | 抖动、弹跳、爆炸效果 |

### 反馈事件与演出映射

| 事件 | `cute` | `formal` | `enterprise` | `playful` |
|------|--------|----------|--------------|-----------|
| **Rubric 通过** | 🎉 粉色五彩纸屑 + "太棒啦！" + 欢快音 | ✅ 绿色勾号淡入 + "通过评审" + 轻音 | ✓ 灰色勾号 + "通过" + 无音 | 🎊 爆炸动画 + "Perfect!" + 游戏音 |
| **Stage 完成** | 🌟 星星飞舞 + "你真厉害！" + 胜利音 | 📋 进度条填满 + "阶段完成" + 提示音 | □ 勾选框 + "已完成" + 无音 | 🏆 奖杯弹出 + "Level Up!" + 升级音 |
| **Rubric 失败** | 💬 温柔提示气泡 + "再试一次吧~" + 柔和音 | ⚠️ 黄色警告 + "需要改进" + 警告音 | ✗ 灰色叉号 + "未通过" + 无音 | 💥 摇晃动画 + "Oops!" + 碰撞音 |

### 实现要求

**禁止**：
- ❌ 只换颜色不换文案/动效/音效
- ❌ 硬编码主题配置在前端组件中
- ❌ 所有主题使用相同的反馈文案

**必须**：
- ✅ 主题配置存储在 `frontend/src/lib/themes.ts` 中
- ✅ 每个主题有独立的 `feedbackMessages` 对象
- ✅ 每个主题有独立的 `soundEffects` 映射
- ✅ 每个主题有独立的 `animationPresets` 配置
- ✅ 支持用户在设置中切换主题（未来 Phase）
- ✅ 默认主题根据用户背景智能推荐（儿童 → cute，企业 → enterprise）

### 示例配置

```typescript
// frontend/src/lib/themes.ts
export const themes = {
  cute: {
    name: '可爱风',
    colors: { primary: '#FF6B9D', accent: '#FFD93D' },
    feedbackMessages: {
      rubricPass: ['太棒啦！🎉', '你真厉害！✨', '完美答案！💖'],
      stageComplete: ['你真棒！继续加油！🌟', '又进步了！🎊'],
      rubricFail: ['再试一次吧~💬', '加油，你可以的！🌈']
    },
    soundEffects: {
      rubricPass: '/sounds/cute-success.mp3',
      stageComplete: '/sounds/cute-victory.mp3',
      rubricFail: '/sounds/cute-retry.mp3'
    },
    animations: {
      rubricPass: 'confetti-pink',
      stageComplete: 'stars-fly',
      rubricFail: 'gentle-shake'
    }
  },
  formal: {
    name: '正式风',
    colors: { primary: '#2563EB', accent: '#10B981' },
    feedbackMessages: {
      rubricPass: ['通过评审', '评审通过，可继续推进'],
      stageComplete: ['阶段完成', '已完成当前阶段'],
      rubricFail: ['需要改进', '未通过评审，请修改后重新提交']
    },
    soundEffects: {
      rubricPass: '/sounds/formal-success.mp3',
      stageComplete: '/sounds/formal-complete.mp3',
      rubricFail: '/sounds/formal-warning.mp3'
    },
    animations: {
      rubricPass: 'fade-in-check',
      stageComplete: 'progress-fill',
      rubricFail: 'fade-in-warning'
    }
  }
}
```

---

## 无障碍与包容性设计

### 视觉无障碍

| 要求 | 标准 | 检查方式 |
|------|------|----------|
| **对比度** | 文字与背景对比度 ≥4.5:1（WCAG AA） | Chrome DevTools Lighthouse |
| **字体大小** | 正文 ≥16px，标题 ≥20px | 浏览器缩放至 200% 仍可读 |
| **颜色依赖** | 不仅靠颜色区分信息（加图标/文字） | 开启色盲模式测试 |
| **焦点指示** | 键盘聚焦时有明显边框 | Tab 键导航检查 |

### 运动无障碍

| 要求 | 实现 |
|------|------|
| **尊重系统设置** | 检测 `prefers-reduced-motion` 媒体查询 |
| **禁用动画选项** | 用户设置中提供"减少动画"开关 |
| **降级策略** | 动画禁用时改为淡入淡出 |

```css
@media (prefers-reduced-motion: reduce) {
  * {
    animation-duration: 0.01ms !important;
    transition-duration: 0.01ms !important;
  }
}
```

### 认知无障碍

| 要求 | 实现 |
|------|------|
| **清晰的错误提示** | 错误消息说明具体问题和解决方法 |
| **渐进式信息呈现** | 避免一次性展示大量信息 |
| **可预测的交互** | 相同操作在不同页面行为一致 |
| **可撤销的操作** | 重要操作前提示确认 |

---

## 验收检查清单

### Level 2/3 改动强制检查项

在声明"完成"前，必须逐项确认：

#### 文档检查

- [ ] 本文档（`UI_UX_DESIGN_STANDARD.md`）已补充新增功能的设计规范
- [ ] 移动端适配策略已明确（响应式断点、布局规则、触感反馈）
- [ ] 主题化反馈已补充（文案、音效、动效）
- [ ] 无障碍要求已补充（对比度、键盘导航、屏幕阅读器）

#### 代码检查

- [ ] 交互模式由 Skill YAML 驱动（不硬编码）
- [ ] 预制答案面板在正确的节点显示（参考"显示逻辑"表格）
- [ ] 移动端布局在 <768px 断点正确切换
- [ ] 软键盘弹出时输入框不被遮挡
- [ ] 触感反馈有降级策略（不影响核心功能）
- [ ] 声音播放有权限处理（首次需用户手势触发）

#### 测试检查

- [ ] 在 iPhone SE（375px）上测试通过
- [ ] 在 iPad（768px）上测试通过
- [ ] 在 1080p 桌面上测试通过
- [ ] 开启"减少动画"设置后仍可正常使用
- [ ] 使用屏幕阅读器可正常导航
- [ ] 使用键盘可完成所有操作

#### 验收脚本

- [ ] `verify.bat` / `verify.sh` 全部通过
- [ ] `SmokeTest` 确认 Spring 上下文启动正常
- [ ] `NodeFsmTest` 确认节点状态机无回归

#### 文档同步

- [ ] `README.md` 更新了当前进度
- [ ] `docs/vibe-issues/YYYY-MM-DD.md` 记录了本次改动
- [ ] `.github/copilot-instructions.md` 更新了移动端强制要求（如适用）

---

## 附录：常见问题

### Q1: 如何判断改动是否需要更新本文档？

**A**: 以下情况必须更新本文档：
- 新增交互模式或修改交互模式逻辑
- 新增 UI 组件或修改组件显示逻辑
- 新增主题或修改主题反馈策略
- 新增移动端特性或修改移动端布局
- 新增无障碍要求或修改无障碍策略

### Q2: 预制答案在哪些节点显示？

**A**: 参考"预制答案面板规范 → 显示逻辑"表格：
- **必须显示**：INTRO（背景调研）、PRACTICE（练习）、REVIEW（确认理解）、RETRO（总结反思）
- **禁止显示**：CONCEPT（只需阅读）、TASK（需要提交作品）
- **前提条件**：`interaction_mode` 为 `PRESET_ONLY` 或 `HYBRID`，且有匹配的 preset_answers

### Q3: 如何在移动端测试软键盘适配？

**A**:
1. 使用真机调试（iOS/Android）
2. 在 Chrome DevTools 中模拟移动设备（Cmd+Shift+M）
3. 点击输入框，检查键盘弹出时：
   - 输入框是否可见（不被键盘遮挡）
   - 预制答案面板是否固定在键盘上方
   - 消息区域是否正确滚动到底部

### Q4: 如何确保主题切换不破坏功能？

**A**:
1. 主题配置与业务逻辑解耦（颜色/文案/音效在 `themes.ts` 中配置）
2. 每个主题必须提供完整的 `feedbackMessages`、`soundEffects`、`animations`
3. 切换主题后运行 `verify.bat` 确认无回归
4. 在 `SmokeTest` 中补充主题加载测试

### Q5: 如何处理不支持触感反馈的设备？

**A**: 使用静默降级策略：
```typescript
try {
  if (Capacitor.isNativePlatform()) {
    await Haptics.impact({ style: 'light' })
  }
} catch (e) {
  // 静默降级，不影响功能
}
```
不支持的设备不显示错误，不影响核心功能。

---

## 变更历史

| 日期 | 版本 | 变更说明 |
|------|------|----------|
| 2026-05-19 | v1.0 | 初始版本，定义交互模式体系、预制答案规范、移动端适配要求 |
