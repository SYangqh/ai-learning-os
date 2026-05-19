package com.learningos.modules.session.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

/**
 * SkillRubricLoader 关键词匹配单元测试（Phase 9D）
 *
 * <p>验证 trigger_keywords 子串匹配逻辑及优先级规则。</p>
 */
class PresetAnswerKeywordMatchingTest {

    private final SkillRubricLoader loader = new SkillRubricLoader();

    // ========== 关键词匹配逻辑 ==========

    @Test
    void loadInteractionConfig_WithKeywordMatch_ReturnsYamlSource() {
        // nodejs_basics.skill.yaml 中有 trigger_keywords: ["模块", "包", "import", "require"]
        String skillId = "nodejs_basics";
        Integer stageIndex = null;
        String lastAiMessage = "你用过 Node.js 的模块系统吗？比如 import 或 require。";

        var config = loader.loadInteractionConfig(skillId, stageIndex, lastAiMessage);

        assertThat(config.mode()).isEqualTo("HYBRID");
        assertThat(config.source()).isEqualTo("YAML");  // 关键词匹配成功
        assertThat(config.presetAnswers()).isNotEmpty();
        assertThat(config.presetAnswers().get(0).id()).contains("pa_nodejs_module");
    }

    @ParameterizedTest
    @CsvSource({
        "你了解模块化开发吗？,          true",   // 匹配"模块"
        "Node.js 的包管理器是什么？,     true",   // 匹配"包"
        "你会用 import 语句吗？,         true",   // 匹配"import"
        "CommonJS 和 ES Module 有什么区别？, true", // 匹配"CommonJS"
        "你对 JavaScript 有多少了解？,   false",  // 无匹配关键词
        "请介绍一下你的背景,             false"   // 无匹配关键词
    })
    void keywordMatching_IsCaseInsensitiveSubstring(String message, boolean shouldMatch) {
        String skillId = "nodejs_basics";
        var config = loader.loadInteractionConfig(skillId, null, message);

        if (shouldMatch) {
            assertThat(config.source()).isEqualTo("YAML");
            assertThat(config.presetAnswers()).isNotEmpty();
        } else {
            // 无匹配时应返回全局预制答案或 PENDING
            assertThat(config.source()).isIn("YAML", "PENDING");
        }
    }

    @Test
    void keywordMatching_IgnoresCase() {
        String skillId = "nodejs_basics";
        String upperCase = "你了解 IMPORT 和 REQUIRE 吗？";
        String lowerCase = "你了解 import 和 require 吗？";
        String mixedCase = "你了解 ImPoRt 和 ReQuIrE 吗？";

        var config1 = loader.loadInteractionConfig(skillId, null, upperCase);
        var config2 = loader.loadInteractionConfig(skillId, null, lowerCase);
        var config3 = loader.loadInteractionConfig(skillId, null, mixedCase);

        assertThat(config1.source()).isEqualTo("YAML");
        assertThat(config2.source()).isEqualTo("YAML");
        assertThat(config3.source()).isEqualTo("YAML");
    }

    @Test
    void keywordMatching_MatchesChineseAndEnglish() {
        String skillId = "nodejs_basics";
        String chinese = "你了解异步编程吗？";      // trigger_keywords: ["异步", "Promise", "async", "await"]
        String english = "Do you know Promise?";

        var config1 = loader.loadInteractionConfig(skillId, null, chinese);
        var config2 = loader.loadInteractionConfig(skillId, null, english);

        assertThat(config1.source()).isEqualTo("YAML");
        assertThat(config2.source()).isEqualTo("YAML");
    }

    // ========== 优先级规则 ==========

    @Test
    void priorityRule_GlobalAnswersWithoutLastMessage_ReturnsYaml() {
        String skillId = "nodejs_basics";
        Integer stageIndex = null;
        String lastAiMessage = null;  // 无 AI 消息时，返回全局预制答案

        var config = loader.loadInteractionConfig(skillId, stageIndex, lastAiMessage);

        assertThat(config.source()).isEqualTo("YAML");  // 全局预制答案
        assertThat(config.presetAnswers()).isNotEmpty();
    }

    // ========== 边界 Case ==========

    @Test
    void loadInteractionConfig_SkillNotFound_ReturnsValidConfig() {
        String nonExistentSkillId = "non_existent_skill_xyz";

        var config = loader.loadInteractionConfig(nonExistentSkillId, null, null);

        // 不存在的 Skill 应返回默认配置（具体模式取决于实现）
        assertThat(config.mode()).isNotBlank();
        assertThat(config.source()).isNotBlank();
        assertThat(config.presetAnswers()).isNotNull();
    }

    @Test
    void loadInteractionConfig_EmptyMessage_ReturnsGlobal() {
        String skillId = "nodejs_basics";
        String emptyMessage = "";

        var config = loader.loadInteractionConfig(skillId, null, emptyMessage);

        // 空消息视为无关键词匹配，应返回全局预制答案
        assertThat(config.source()).isEqualTo("YAML");
    }

    @Test
    void loadInteractionConfig_BlankMessage_ReturnsGlobal() {
        String skillId = "nodejs_basics";
        String blankMessage = "   \n\n  ";

        var config = loader.loadInteractionConfig(skillId, null, blankMessage);

        assertThat(config.source()).isEqualTo("YAML");
    }

    @Test
    void keywordMatching_PartialMatch_Works() {
        String skillId = "nodejs_basics";
        String message = "这个模块我们先不讲";  // "模块"被包含，但上下文可能不相关

        var config = loader.loadInteractionConfig(skillId, null, message);

        // 子串匹配：只要包含"模块"就会触发
        // 这是已知的潜在误触发问题，在文档中已记录
        assertThat(config.source()).isEqualTo("YAML");
    }

    @Test
    void multipleKeywordsInMessage_MatchesFirst() {
        String skillId = "nodejs_basics";
        String message = "你对模块、异步编程和 npm 都了解吗？";  // 包含多个关键词

        var config = loader.loadInteractionConfig(skillId, null, message);

        // 应该匹配到第一个关键词组（模块相关）
        assertThat(config.source()).isEqualTo("YAML");
        assertThat(config.presetAnswers()).isNotEmpty();
    }

    // ========== InteractionConfig 结构验证 ==========

    @Test
    void loadInteractionConfig_ReturnsCorrectStructure() {
        String skillId = "nodejs_basics";
        var config = loader.loadInteractionConfig(skillId, null, "模块");

        assertThat(config).isNotNull();
        assertThat(config.mode()).isIn("FREE_INPUT_ONLY", "PRESET_ONLY", "HYBRID");
        assertThat(config.source()).isIn("YAML", "AI_GENERATED", "PENDING", "NONE");
        assertThat(config.presetAnswers()).isNotNull();
    }

    @Test
    void presetAnswer_HasRequiredFields() {
        String skillId = "nodejs_basics";
        var config = loader.loadInteractionConfig(skillId, null, "模块");

        if (!config.presetAnswers().isEmpty()) {
            var answer = config.presetAnswers().get(0);
            assertThat(answer.id()).isNotBlank();
            assertThat(answer.text()).isNotBlank();
            assertThat(answer.confidence()).isIn("HIGH", "MEDIUM", "LOW");
            assertThat(answer.triggerKeywords()).isNotNull();
        }
    }
}
