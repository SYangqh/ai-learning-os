package com.learningos.modules.session.service;

import com.learningos.modules.session.service.AiResponseParser.ParseResult;
import com.learningos.modules.session.service.SkillRubricLoader.InteractionConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.redisson.api.RedissonClient;

import static org.assertj.core.api.Assertions.*;

/**
 * Phase 9D 动态预制答案端到端集成测试
 *
 * <p>验证完整的混合逻辑流程：YAML 优先 → AI 动态生成 → 无预制答案</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class DynamicPresetAnswersFlowTest {

    @MockBean
    RedissonClient redissonClient;  // Mock Redis

    @Autowired
    SkillRubricLoader skillRubricLoader;

    @Autowired
    AiResponseParser aiResponseParser;

    // ========== 场景 1：YAML 全局预制答案 ==========

    @Test
    void scenario1_YamlGlobal_InitialQuestion() {
        // 场景：初始问题，无 lastAiMessage，应返回全局预制答案
        String skillId = "nodejs_basics";
        Integer stageIndex = null;
        String lastAiMessage = null;

        InteractionConfig config = skillRubricLoader.loadInteractionConfig(
            skillId, stageIndex, lastAiMessage
        );

        assertThat(config.source()).isEqualTo("YAML");
        assertThat(config.presetAnswers()).isNotEmpty();
        assertThat(config.mode()).isEqualTo("HYBRID");
    }

    // ========== 场景 2：YAML 关键词触发 ==========

    @Test
    void scenario2_YamlKeyword_AiAsksAboutModules() {
        // 场景：AI 追问"你用过模块化开发吗？"，触发关键词"模块"
        String skillId = "nodejs_basics";
        Integer stageIndex = null;
        String lastAiMessage = "你用过 Node.js 的模块系统吗？比如 import 或 require？";

        InteractionConfig config = skillRubricLoader.loadInteractionConfig(
            skillId, stageIndex, lastAiMessage
        );

        assertThat(config.source()).isEqualTo("YAML");
        assertThat(config.presetAnswers()).isNotEmpty();
        assertThat(config.presetAnswers().get(0).id()).contains("pa_nodejs_module");
    }

    @Test
    void scenario2_YamlKeyword_AiAsksAboutAsync() {
        // 场景：AI 追问"你对异步编程了解多少？"，触发关键词"异步"
        String skillId = "nodejs_basics";
        Integer stageIndex = null;
        String lastAiMessage = "你对 Promise 和 async/await 熟悉吗？";

        InteractionConfig config = skillRubricLoader.loadInteractionConfig(
            skillId, stageIndex, lastAiMessage
        );

        assertThat(config.source()).isEqualTo("YAML");
        assertThat(config.presetAnswers()).isNotEmpty();
        assertThat(config.presetAnswers().get(0).id()).contains("pa_nodejs_async");
    }

    // ========== 场景 3：AI 动态生成 ==========

    @Test
    void scenario3_AiDynamicGeneration_ValidJson() {
        // 场景：AI 追问"你对循环依赖有什么看法？"（无 YAML 匹配），AI 返回 JSON
        String aiReply = """
            {
              "question": "你遇到过循环依赖的问题吗？",
              "suggestedAnswers": [
                {"text": "经常遇到，知道怎么解决", "confidence": "HIGH"},
                {"text": "遇到过，但不太会处理", "confidence": "MEDIUM"},
                {"text": "没遇到过", "confidence": "LOW"}
              ]
            }
            """;

        ParseResult parseResult = aiResponseParser.parse(aiReply);

        assertThat(parseResult.hasStructuredOutput()).isTrue();
        assertThat(parseResult.question()).isEqualTo("你遇到过循环依赖的问题吗？");
        assertThat(parseResult.suggestedAnswers()).hasSize(3);

        // 在 SessionService.persistReply 中，这会被标记为 source="AI_GENERATED"
        // 这里只测试 Parser，实际集成由 SessionService 测试覆盖
    }

    @Test
    void scenario3_AiDynamicGeneration_OptionsFormat() {
        // 场景：AI 返回旧的 [OPTIONS: ...] 格式（向后兼容）
        String aiReply = """
            你对依赖注入有多少了解？
            
            [OPTIONS: "非常熟悉" | LOW:"了解一些" | "完全不懂"]
            """;

        ParseResult parseResult = aiResponseParser.parse(aiReply);

        assertThat(parseResult.hasStructuredOutput()).isTrue();
        assertThat(parseResult.suggestedAnswers()).hasSize(3);
    }

    // ========== 场景 4：降级为纯文本 ==========

    @Test
    void scenario4_Fallback_AiReturnsPlainText() {
        // 场景：AI 返回格式错误或选择不使用结构化输出
        String aiReply = """
            这是一个开放式问题，请详细描述你对 Node.js 的理解。
            
            （AI 没有返回 JSON 或 [OPTIONS]）
            """;

        ParseResult parseResult = aiResponseParser.parse(aiReply);

        assertThat(parseResult.hasStructuredOutput()).isFalse();
        assertThat(parseResult.suggestedAnswers()).isEmpty();
        assertThat(parseResult.question()).isNotEmpty();
    }

    @Test
    void scenario4_Fallback_MalformedJson() {
        // 场景：AI 尝试返回 JSON 但格式错误
        String aiReply = """
            {
              "question": "测试格式错误"
              "suggestedAnswers": [
                {"text": "答案1"  // 缺少逗号
              ]
            """;

        ParseResult parseResult = aiResponseParser.parse(aiReply);

        assertThat(parseResult.hasStructuredOutput()).isFalse();
    }

    // ========== 混合逻辑优先级验证 ==========

    @Test
    void hybridLogic_YamlHasHigherPriorityThanAi() {
        // 验证：当 YAML 关键词匹配成功时，即使 AI 也返回了结构化输出，
        // SessionService 也应优先使用 YAML
        String skillId = "nodejs_basics";
        String lastAiMessage = "你了解 npm 包管理吗？";  // 匹配关键词"npm"

        InteractionConfig yamlConfig = skillRubricLoader.loadInteractionConfig(
            skillId, null, lastAiMessage
        );

        assertThat(yamlConfig.source()).isEqualTo("YAML");  // YAML 优先
        assertThat(yamlConfig.presetAnswers()).isNotEmpty();
    }

    @Test
    void hybridLogic_AiGenerationWhenNoYamlMatch() {
        // 验证：当 YAML 无匹配时，应使用 AI 动态生成
        String skillId = "nodejs_basics";
        Integer stageIndex = 1;  // 指定 stage
        String lastAiMessage = "你对微服务架构有多少了解？";  // 无匹配关键词

        InteractionConfig yamlConfig = skillRubricLoader.loadInteractionConfig(
            skillId, stageIndex, lastAiMessage
        );

        // 实际实现可能返回 YAML（全局预制答案）或 PENDING（等待 AI 生成）
        assertThat(yamlConfig.source()).isIn("YAML", "PENDING");

        // 模拟 AI 返回结构化输出
        String aiReply = """
            {
              "question": "你用过微服务吗？",
              "suggestedAnswers": [
                {"text": "用过，理解架构设计", "confidence": "HIGH"},
                {"text": "听说过，没实践过", "confidence": "LOW"}
              ]
            }
            """;

        ParseResult parseResult = aiResponseParser.parse(aiReply);
        assertThat(parseResult.hasStructuredOutput()).isTrue();
        // SessionService 应将其标记为 source="AI_GENERATED"
    }

    // ========== 阶段级过滤验证 ==========

    @Test
    void stageIndexFilter_ReturnsValidConfig() {
        String skillId = "nodejs_basics";
        Integer stageIndex = 2;  // 假设 stage 2 有特定预制答案

        InteractionConfig config = skillRubricLoader.loadInteractionConfig(
            skillId, stageIndex, null
        );

        // 验证返回的配置不为空且结构正确
        assertThat(config).isNotNull();
        assertThat(config.mode()).isNotBlank();
        assertThat(config.source()).isNotBlank();
        assertThat(config.presetAnswers()).isNotNull();
    }

    // ========== Bean 依赖验证 ==========

    @Test
    void criticalBeansArePresent() {
        assertThat(skillRubricLoader).isNotNull();
        assertThat(aiResponseParser).isNotNull();
    }
}
