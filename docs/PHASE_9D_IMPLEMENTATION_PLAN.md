# Phase 9D：动态预制答案实现规划

> **改动级别**：Level 3（Skill 体系架构层改动）
> **估计工作量**：8-12 小时（后端 6h + 前端 2h + 测试 4h）
> **前置依赖**：Phase 9B 完成（基础预制答案机制已实现）

---

## 目录

1. [功能概述](#功能概述)
2. [架构设计](#架构设计)
3. [实现步骤](#实现步骤)
4. [验收标准](#验收标准)
5. [风险评估](#风险评估)

---

## 功能概述

### 问题背景

当前预制答案机制只能覆盖 Skill YAML 中人工预定义的问题场景，AI 生成的深入追问无法提供预制答案选项，导致用户体验不一致。

### 解决方案

采用混合方案（YAML 优先 + AI 动态生成降级）：

1. **YAML 优先**：优先匹配 Skill YAML 中定义的预制答案（关键词触发）
2. **AI 降级**：无匹配时，由 AI 在生成问题时同时生成 3-5 个建议答案选项
3. **来源透明**：前端标记预制答案来源（YAML / AI 生成）

### 核心价值

- ✅ **全覆盖**：任何问题都能有预制答案，提升用户体验流畅度
- ✅ **质量保证**：重要问题使用人工精心编写的 YAML 预制答案
- ✅ **零维护**：新颖问题由 AI 自动生成答案选项，无需人工维护

---

## 架构设计

### 数据流图

```
┌─────────────────────────────────────────────┐
│ 1. 用户回答问题                              │
└─────────────────┬───────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────┐
│ 2. ChatController.advance()                 │
│    - 保存用户消息                            │
│    - 准备调用 AI                             │
└─────────────────┬───────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────┐
│ 3. SkillRubricLoader.loadInteractionConfig()│
│    - 读取 Skill YAML                        │
│    - 匹配 trigger_keywords                  │
│    - 返回 YAML 预制答案列表                  │
└─────────────────┬───────────────────────────┘
                  │
        ┌─────────┴─────────┐
        │                   │
        ▼                   ▼
  有 YAML 匹配          无 YAML 匹配
        │                   │
        │                   ▼
        │     ┌─────────────────────────────┐
        │     │ 4. AI 生成结构化 JSON        │
        │     │    {                        │
        │     │      "question": "...",     │
        │     │      "suggestedAnswers": [] │
        │     │    }                        │
        │     └─────────────┬───────────────┘
        │                   │
        │                   ▼
        │     ┌─────────────────────────────┐
        │     │ 5. AiResponseParser         │
        │     │    - 解析 JSON               │
        │     │    - 验证答案质量            │
        │     │    - 降级处理                │
        │     └─────────────┬───────────────┘
        │                   │
        └─────────┬─────────┘
                  │
                  ▼
┌─────────────────────────────────────────────┐
│ 6. 返回 AdvanceResponse                     │
│    - message: AI 的问题                     │
│    - interactionConfig:                     │
│      - presetAnswers: []                    │
│      - source: "YAML" | "AI_GENERATED"      │
└─────────────────┬───────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────┐
│ 7. 前端显示预制答案面板                      │
│    - YAML: 无标记                           │
│    - AI 生成: "💡 快速选择（AI 智能生成）"   │
└─────────────────────────────────────────────┘
```

### 关键组件

#### 后端新增/修改

| 组件 | 类型 | 说明 |
|------|------|------|
| `PresetAnswer.triggerKeywords` | 字段 | 新增可选字段，关键词数组 |
| `InteractionConfig.source` | 字段 | 新增来源标记（YAML / AI_GENERATED） |
| `AiResponseParser` | 新类 | 解析 AI 结构化 JSON 响应 |
| `SkillRubricLoader.loadInteractionConfig()` | 修改 | 增加关键词匹配逻辑 |
| `ChatService.advance()` | 修改 | 集成 AI 结构化输出和降级逻辑 |

#### 前端新增/修改

| 组件 | 类型 | 说明 |
|------|------|------|
| `InteractionConfig.source` | 字段 | 接收后端的来源标记 |
| `PresetAnswersPanel` | 修改 | 根据 `source` 显示来源标记 |

---

## 实现步骤

### Step 1：扩展 Skill YAML 结构（估时：1h）

**目标**：在 `nodejs_basics.skill.yaml` 中补充关键词触发的预制答案

**文件**：`backend-spring/src/main/resources/skills/nodejs_basics.skill.yaml`

**改动**：

```yaml
preset_answers:
  # 全局背景调研（已有）
  - id: pa_nodejs_exp_browser
    text: "用过浏览器端 JS，写过交互页面"
    confidence: HIGH
    stage_index: null

  # 新增：关键词触发的预制答案
  - id: pa_nodejs_module_yes
    text: "用过 import/require，引入过第三方库"
    confidence: HIGH
    trigger_keywords: ["模块", "包", "import", "require", "CommonJS", "ES Module"]
    stage_index: null

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

  - id: pa_nodejs_async_no
    text: "不了解异步编程，都是同步写法"
    confidence: LOW
    trigger_keywords: ["异步"]
    stage_index: null

  # npm 相关
  - id: pa_nodejs_npm_yes
    text: "用过 npm install 安装包"
    confidence: HIGH
    trigger_keywords: ["npm", "包管理", "依赖"]
    stage_index: null

  - id: pa_nodejs_npm_no
    text: "没用过 npm，不知道怎么安装包"
    confidence: LOW
    trigger_keywords: ["npm"]
    stage_index: null
```

**验收**：
- [ ] Skill YAML 能被成功解析（不报错）
- [ ] `trigger_keywords` 字段类型正确（字符串数组）

---

### Step 2：修改 SkillRubricLoader（估时：2h）

**目标**：实现关键词匹配逻辑

**文件**：`backend-spring/src/main/java/com/learningos/modules/session/service/SkillRubricLoader.java`

**改动**：

1. 修改 `PresetAnswer` record，新增 `triggerKeywords` 字段：

```java
public record PresetAnswer(
    String id, 
    String text, 
    String confidence, 
    Integer stageIndex,
    List<String> triggerKeywords  // 新增
) {}
```

2. 修改 `InteractionConfig` record，新增 `source` 字段：

```java
public record InteractionConfig(
    String mode, 
    List<PresetAnswer> presetAnswers,
    String source  // 新增："YAML" | "AI_GENERATED" | "PENDING"
) {
    public static final InteractionConfig DEFAULT = 
        new InteractionConfig("HYBRID", List.of(), "PENDING");
}
```

3. 修改 `loadInteractionConfig` 方法，增加关键词匹配逻辑：

```java
public InteractionConfig loadInteractionConfig(
    String skillId, 
    Integer stageIndex, 
    String lastAiMessage  // 新增参数
) {
    if (skillId == null || skillId.isBlank()) {
        log.debug("[InteractionConfig] skillId 为空，返回默认配置");
        return InteractionConfig.DEFAULT;
    }
    
    String path = "skills/" + skillId + ".skill.yaml";
    Resource resource = resourceLoader.getResource("classpath:" + path);
    
    if (!resource.exists()) {
        log.warn("[InteractionConfig] Skill YAML 不存在: {}", path);
        return InteractionConfig.DEFAULT;
    }
    
    try (InputStream is = resource.getInputStream()) {
        Yaml yaml = new Yaml();
        Map<String, Object> root = yaml.load(is);
        
        String mode = (String) root.getOrDefault("interaction_mode", "HYBRID");
        log.debug("[InteractionConfig] skillId={}, stageIndex={}, mode={}", 
            skillId, stageIndex, mode);
        
        // 读取 preset_answers 列表
        List<Map<String, Object>> rawAnswers = 
            (List<Map<String, Object>>) root.get("preset_answers");
        
        if (rawAnswers == null || rawAnswers.isEmpty()) {
            return new InteractionConfig(mode, List.of(), "YAML");
        }
        
        log.debug("[InteractionConfig] 原始预制答案数量: {}", rawAnswers.size());
        
        // 1. 过滤 stage_index 匹配的预制答案
        List<PresetAnswer> stageFiltered = rawAnswers.stream()
            .map(a -> {
                String id = (String) a.get("id");
                String text = (String) a.get("text");
                String confidence = (String) a.get("confidence");
                Integer targetStage = (Integer) a.get("stage_index");
                List<String> triggerKeywords = (List<String>) a.get("trigger_keywords");
                
                return new PresetAnswer(id, text, confidence, targetStage, 
                    triggerKeywords != null ? triggerKeywords : List.of());
            })
            .filter(a -> a.stageIndex() == null || a.stageIndex().equals(stageIndex))
            .toList();
        
        // 2. 如果有 AI 消息，进行关键词匹配过滤
        if (lastAiMessage != null && !lastAiMessage.isBlank()) {
            String lowerMessage = lastAiMessage.toLowerCase();
            
            List<PresetAnswer> keywordMatched = stageFiltered.stream()
                .filter(a -> {
                    // 无 trigger_keywords 的预制答案（全局背景调研）
                    if (a.triggerKeywords().isEmpty()) {
                        return false;  // 只在初始问题时显示
                    }
                    // 有 trigger_keywords 的预制答案（关键词匹配）
                    return a.triggerKeywords().stream()
                        .anyMatch(keyword -> lowerMessage.contains(keyword.toLowerCase()));
                })
                .toList();
            
            if (!keywordMatched.isEmpty()) {
                log.info("[InteractionConfig] 关键词匹配成功: skillId={}, count={}", 
                    skillId, keywordMatched.size());
                return new InteractionConfig(mode, keywordMatched, "YAML");
            }
        }
        
        // 3. 无 AI 消息时，返回全局预制答案
        List<PresetAnswer> globalAnswers = stageFiltered.stream()
            .filter(a -> a.triggerKeywords().isEmpty())
            .toList();
        
        if (!globalAnswers.isEmpty()) {
            log.info("[InteractionConfig] 返回全局预制答案: skillId={}, count={}", 
                skillId, globalAnswers.size());
            return new InteractionConfig(mode, globalAnswers, "YAML");
        }
        
        // 4. 都无匹配时，返回空（后续由 AI 动态生成）
        log.info("[InteractionConfig] 无匹配预制答案，等待 AI 动态生成: skillId={}", skillId);
        return new InteractionConfig(mode, List.of(), "PENDING");
        
    } catch (Exception e) {
        log.error("[InteractionConfig] 加载失败: skillId={}", skillId, e);
        return InteractionConfig.DEFAULT;
    }
}
```

**验收**：
- [ ] 单元测试：关键词匹配逻辑在至少 5 个场景下测试通过
- [ ] 单元测试：`source` 字段正确标记（YAML / PENDING）

---

### Step 3：修改 AI Prompt（估时：1h）

**目标**：让 AI 返回结构化 JSON 输出

**文件**：`backend-spring/src/main/java/com/learningos/modules/session/service/ChatService.java`

**改动**：

在 `buildPrompt` 方法中，增加结构化输出要求：

```java
private String buildPrompt(String userAnswer, SkillContext skillContext) {
    StringBuilder prompt = new StringBuilder();
    
    // ... 现有 prompt 逻辑 ...
    
    // 新增：要求 AI 返回结构化 JSON
    prompt.append("\n\n请以 JSON 格式返回：\n");
    prompt.append("{\n");
    prompt.append("  \"question\": \"你的问题内容\",\n");
    prompt.append("  \"suggestedAnswers\": [\n");
    prompt.append("    { \"text\": \"答案选项1\", \"confidence\": \"HIGH\" },\n");
    prompt.append("    { \"text\": \"答案选项2\", \"confidence\": \"MEDIUM\" },\n");
    prompt.append("    { \"text\": \"答案选项3\", \"confidence\": \"LOW\" }\n");
    prompt.append("  ]\n");
    prompt.append("}\n\n");
    prompt.append("要求：\n");
    prompt.append("1. question：清晰、引导性的问题\n");
    prompt.append("2. suggestedAnswers：3-5个可能的答案选项\n");
    prompt.append("3. confidence：HIGH（明确掌握）/MEDIUM（部分了解）/LOW（不确定/不了解）\n");
    
    return prompt.toString();
}
```

**验收**：
- [ ] AI 返回的响应包含 JSON 结构（手工测试）
- [ ] JSON 包含 `question` 和 `suggestedAnswers` 字段

---

### Step 4：新增 AiResponseParser（估时：2h）

**目标**：解析 AI 的结构化 JSON 响应

**文件**：`backend-spring/src/main/java/com/learningos/modules/session/service/AiResponseParser.java`（新建）

**代码**：

```java
package com.learningos.modules.session.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class AiResponseParser {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 解析 AI 返回的结构化 JSON 响应
     * 
     * @param aiResponse AI 返回的完整响应
     * @return ParseResult（包含问题和建议答案）
     */
    public ParseResult parse(String aiResponse) {
        if (aiResponse == null || aiResponse.isBlank()) {
            log.warn("[AiResponseParser] AI 响应为空");
            return ParseResult.empty();
        }
        
        try {
            // 尝试从响应中提取 JSON（可能包含额外的文本）
            String jsonPart = extractJson(aiResponse);
            if (jsonPart == null) {
                log.debug("[AiResponseParser] 未找到 JSON 结构，降级为纯文本");
                return ParseResult.plainText(aiResponse);
            }
            
            JsonNode root = objectMapper.readTree(jsonPart);
            
            // 解析 question
            String question = root.has("question") 
                ? root.get("question").asText() 
                : aiResponse;  // 降级为原始响应
            
            // 解析 suggestedAnswers
            List<SuggestedAnswer> suggestedAnswers = new ArrayList<>();
            if (root.has("suggestedAnswers") && root.get("suggestedAnswers").isArray()) {
                for (JsonNode answerNode : root.get("suggestedAnswers")) {
                    String text = answerNode.has("text") 
                        ? answerNode.get("text").asText() 
                        : null;
                    String confidence = answerNode.has("confidence") 
                        ? answerNode.get("confidence").asText().toUpperCase() 
                        : "MEDIUM";
                    
                    if (text != null && !text.isBlank()) {
                        suggestedAnswers.add(new SuggestedAnswer(text, confidence));
                    }
                }
            }
            
            // 验证答案质量
            if (suggestedAnswers.size() < 2) {
                log.warn("[AiResponseParser] 答案选项数量不足（<2），不显示预制答案");
                return ParseResult.plainText(question);
            }
            
            if (suggestedAnswers.size() > 5) {
                log.info("[AiResponseParser] 答案选项过多（>5），只取前 5 个");
                suggestedAnswers = suggestedAnswers.subList(0, 5);
            }
            
            log.info("[AiResponseParser] 成功解析: question={}, answersCount={}", 
                question, suggestedAnswers.size());
            return new ParseResult(question, suggestedAnswers, true);
            
        } catch (Exception e) {
            log.warn("[AiResponseParser] JSON 解析失败，降级为纯文本: {}", e.getMessage());
            return ParseResult.plainText(aiResponse);
        }
    }
    
    /**
     * 从可能包含额外文本的响应中提取 JSON 部分
     */
    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return null;
    }
    
    /**
     * 解析结果
     */
    public record ParseResult(
        String question,
        List<SuggestedAnswer> suggestedAnswers,
        boolean hasStructuredOutput
    ) {
        public static ParseResult empty() {
            return new ParseResult("", List.of(), false);
        }
        
        public static ParseResult plainText(String question) {
            return new ParseResult(question, List.of(), false);
        }
    }
    
    /**
     * 建议答案
     */
    public record SuggestedAnswer(
        String text,
        String confidence
    ) {}
}
```

**验收**：
- [ ] 单元测试：JSON 格式正确时，成功解析
- [ ] 单元测试：JSON 格式错误时，降级为纯文本
- [ ] 单元测试：答案选项 <2 个时，不返回预制答案
- [ ] 单元测试：答案选项 >5 个时，只取前 5 个

---

### Step 5：修改 ChatService.advance()（估时：2h）

**目标**：集成 YAML 预制答案和 AI 动态生成逻辑

**文件**：`backend-spring/src/main/java/com/learningos/modules/session/service/ChatService.java`

**改动**：

```java
public AdvanceResponse advance(UUID sessionId, String userInput) {
    // 1. 验证 session 和权限
    LearningSession session = sessionRepository.findById(sessionId)
        .orElseThrow(() -> new AppException("学习会话不存在"));
    
    // ... 现有逻辑 ...
    
    // 2. 获取最后一条 AI 消息（用于关键词匹配）
    List<SessionMessage> messages = messageRepository
        .findBySessionIdOrderByCreatedAtAsc(sessionId);
    String lastAiMessage = messages.stream()
        .filter(m -> "assistant".equals(m.getRole()))
        .map(SessionMessage::getContent)
        .reduce((first, second) -> second)  // 取最后一条
        .orElse(null);
    
    // 3. 尝试从 Skill YAML 加载预制答案
    InteractionConfig yamlConfig = skillRubricLoader.loadInteractionConfig(
        session.getSkillId(), 
        currentStageIndex,
        lastAiMessage
    );
    
    // 4. 调用 AI 生成回复
    String aiResponse = llmService.chat(buildPrompt(userInput, skillContext));
    
    // 5. 解析 AI 响应（尝试提取结构化 JSON）
    AiResponseParser.ParseResult parseResult = aiResponseParser.parse(aiResponse);
    String question = parseResult.question();
    
    // 6. 保存 AI 消息
    SessionMessage aiMessage = new SessionMessage();
    aiMessage.setSessionId(sessionId);
    aiMessage.setRole("assistant");
    aiMessage.setContent(question);
    messageRepository.save(aiMessage);
    
    // 7. 决定使用哪个预制答案来源
    InteractionConfig finalConfig;
    if (!yamlConfig.presetAnswers().isEmpty()) {
        // 优先使用 YAML 预制答案
        finalConfig = yamlConfig;
        log.info("[Advance] 使用 YAML 预制答案: count={}", yamlConfig.presetAnswers().size());
    } else if (parseResult.hasStructuredOutput() && 
               !parseResult.suggestedAnswers().isEmpty()) {
        // 降级使用 AI 动态生成的答案
        List<SkillRubricLoader.PresetAnswer> aiAnswers = parseResult.suggestedAnswers()
            .stream()
            .map(a -> new SkillRubricLoader.PresetAnswer(
                UUID.randomUUID().toString(),  // 生成临时 ID
                a.text(),
                a.confidence(),
                null,  // 动态生成的答案不关联 stage
                List.of()  // 动态生成的答案无 trigger_keywords
            ))
            .toList();
        finalConfig = new SkillRubricLoader.InteractionConfig(
            yamlConfig.mode(), 
            aiAnswers, 
            "AI_GENERATED"
        );
        log.info("[Advance] 使用 AI 动态生成的预制答案: count={}", aiAnswers.size());
    } else {
        // 无预制答案，纯文本模式
        finalConfig = new SkillRubricLoader.InteractionConfig(
            yamlConfig.mode(), 
            List.of(), 
            "NONE"
        );
        log.info("[Advance] 无预制答案，纯文本模式");
    }
    
    // 8. 返回响应
    return AdvanceResponse.builder()
        .message(question)
        .currentNode(session.getCurrentNode())
        .stageComplete(false)
        .awaitsInput(true)
        .awaitsArtifact(false)
        .interactionConfig(finalConfig)
        .build();
}
```

**验收**：
- [ ] 集成测试：有 YAML 匹配时，使用 YAML 预制答案
- [ ] 集成测试：无 YAML 匹配且 AI 返回 JSON 时，使用 AI 动态生成
- [ ] 集成测试：AI 返回格式错误时，降级为纯文本（无预制答案）

---

### Step 6：修改前端显示（估时：1h）

**目标**：前端根据 `source` 标记显示预制答案来源

**文件**：`frontend/src/components/learn/PresetAnswersPanel.tsx`

**改动**：

```tsx
interface PresetAnswersPanelProps {
  answers: PresetAnswer[]
  onSelectAnswer: (answer: PresetAnswer) => void
  source?: 'YAML' | 'AI_GENERATED' | 'NONE'  // 新增
}

export default function PresetAnswersPanel({ 
  answers, 
  onSelectAnswer,
  source = 'YAML'  // 新增
}: PresetAnswersPanelProps) {
  if (answers.length === 0) return null

  return (
    <div className="flex flex-col gap-2 px-4 py-3 border-b t-border t-panel">
      <p className="text-xs t-faint font-medium">
        💡 快速选择
        {source === 'AI_GENERATED' && (
          <span className="ml-2 text-[10px] opacity-60">（AI 智能生成）</span>
        )}
      </p>
      <div className="flex flex-wrap gap-2">
        {answers.map(a => (
          <button
            key={a.id}
            onClick={() => onSelectAnswer(a)}
            className={`text-xs px-3 py-1.5 rounded-lg border transition-all ${
              a.confidence === 'HIGH'
                ? 't-border hover:t-accent-text hover:border-current t-panel shadow-sm'
                : 't-border-sub t-faint hover:t-text opacity-75'
            }`}
          >
            {a.text}
          </button>
        ))}
      </div>
    </div>
  )
}
```

**文件**：`frontend/src/app/learn/page.tsx`

**改动**：

```tsx
// 接收后端返回的 source 字段
type InteractionConfig = { 
  mode: InteractionMode; 
  presetAnswers: PresetAnswer[];
  source?: string;  // 新增
}

// ... 在 SSE 处理中 ...
if (d.interaction_config) {
  setInteractionConfig({
    mode: d.interaction_config.mode,
    presetAnswers: d.interaction_config.preset_answers,
    source: d.interaction_config.source  // 新增
  })
}

// ... 在渲染中 ...
<PresetAnswersPanel 
  answers={interactionConfig.presetAnswers} 
  onSelectAnswer={(ans) => setUserInput(ans.text)}
  source={interactionConfig.source}  // 新增
/>
```

**验收**：
- [ ] YAML 预制答案：无来源标记
- [ ] AI 动态生成：显示"（AI 智能生成）"标记
- [ ] 移动端和桌面端都正确显示

---

### Step 7：测试与文档（估时：2h）

**目标**：编写单元测试和集成测试，更新文档

**测试清单**：

- [ ] `PresetAnswerTriggerKeywordTest`：验证关键词字段结构
- [ ] `PresetAnswerKeywordMatchingTest`：验证关键词匹配逻辑
- [ ] `AiResponseParserTest`：验证 JSON 解析和降级
- [ ] `DynamicPresetAnswersFlowTest`：端到端集成测试

**文档清单**：

- [x] `docs/UI_UX_DESIGN_STANDARD.md`：补充动态预制答案规范
- [x] `docs/SKILL_TESTING_STANDARD.md`：补充测试标准
- [x] `.github/copilot-instructions.md`：补充 Skill 规则
- [ ] `docs/vibe-issues/2026-05-19.md`：记录设计决策

---

## 验收标准

### 功能验收

- [ ] **场景 1（YAML 全局）**：初始问题显示 YAML 全局预制答案
- [ ] **场景 2（YAML 关键词）**：AI 追问"模块"时显示关键词触发的预制答案
- [ ] **场景 3（AI 动态生成）**：AI 追问"循环依赖"时显示 AI 动态生成的预制答案
- [ ] **场景 4（降级）**：AI 返回格式错误时不显示预制答案，允许自由输入

### 性能验收

- [ ] YAML 关键词匹配 <10ms（JMH 微基准测试）
- [ ] AI JSON 解析 <50ms（单元测试 + 计时）
- [ ] 端到端流程（含 AI 调用） <3s（集成测试 + 模拟 AI 响应）

### 质量验收

- [ ] `verify.bat` 全部通过（编译 + NodeFsmTest + SmokeTest）
- [ ] 代码覆盖率 ≥80%（后端关键逻辑）
- [ ] 前端在 iPhone SE、iPad、1080p 桌面上测试通过

---

## 风险评估

### 风险 1：AI 不稳定返回 JSON

**风险等级**：高

**影响**：如果 AI 频繁返回非 JSON 格式，用户体验会频繁降级为无预制答案

**缓解措施**：
1. Prompt 中明确要求 JSON 格式，提供示例
2. 使用 GPT-4 或 Claude 3.5 Sonnet（结构化输出能力更强）
3. 实现健壮的降级策略（解析失败时不影响核心流程）

### 风险 2：关键词匹配误触发

**风险等级**：中

**影响**：AI 问题包含无关的关键词（如"这个模块我们先不讲"），可能误触发预制答案

**缓解措施**：
1. 关键词尽量精确（如"用过模块化"而不是"模块"）
2. 可以增加"否定关键词"机制（未来优化）
3. 人工测试时补充边界 case，迭代关键词库

### 风险 3：性能影响

**风险等级**：低

**影响**：关键词匹配和 JSON 解析可能增加响应时间

**缓解措施**：
1. YAML 预制答案缓存（SkillRubricLoader 已有缓存机制）
2. JSON 解析异步处理（CompletableFuture）
3. 性能基准测试（JMH）

---

## 附录：后续优化方向

1. **学习与优化**：记录用户选择了哪个预制答案，优化 AI 生成的答案质量
2. **用户偏好**：允许用户关闭 AI 动态生成，只使用 YAML 预制答案
3. **多语言支持**：关键词匹配支持中英文混合（如"模块" / "module"）
4. **语义匹配**：升级为向量语义匹配，而不是简单的关键词匹配

---

## 变更历史

| 日期 | 版本 | 变更说明 |
|------|------|----------|
| 2026-05-19 | v1.0 | 初始版本，定义 Phase 9D 实现规划（混合方案） |
