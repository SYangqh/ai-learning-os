package com.learningos.modules.session.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * AiResponseParser 单元测试（Phase 9D）
 *
 * <p>验证三层解析逻辑（JSON > OPTIONS > 纯文本）及降级策略。</p>
 */
class AiResponseParserTest {

    private final AiResponseParser parser = new AiResponseParser();

    // ========== Tier 1: JSON 格式解析 ==========

    @Test
    void parseValidJson_ReturnsStructuredResult() {
        String response = """
            {
              "question": "你用过 Node.js 的模块系统吗？",
              "suggestedAnswers": [
                {"text": "用过 import/require", "confidence": "HIGH"},
                {"text": "只听说过，没用过", "confidence": "MEDIUM"},
                {"text": "完全不了解", "confidence": "LOW"}
              ]
            }
            """;

        var result = parser.parse(response);

        assertThat(result.hasStructuredOutput()).isTrue();
        assertThat(result.question()).isEqualTo("你用过 Node.js 的模块系统吗？");
        assertThat(result.suggestedAnswers()).hasSize(3);
        assertThat(result.suggestedAnswers().get(0).text()).isEqualTo("用过 import/require");
        assertThat(result.suggestedAnswers().get(0).confidence()).isEqualTo("HIGH");
    }

    @Test
    void parseJsonWithExtraText_ExtractsJsonPart() {
        String response = """
            好的，我来问你一个问题：
            
            {
              "question": "你对异步编程有多少了解？",
              "suggestedAnswers": [
                {"text": "非常熟悉 async/await", "confidence": "HIGH"},
                {"text": "了解基本概念", "confidence": "MEDIUM"}
              ]
            }
            
            请选择最符合你的情况。
            """;

        var result = parser.parse(response);

        assertThat(result.hasStructuredOutput()).isTrue();
        assertThat(result.question()).isEqualTo("你对异步编程有多少了解？");
        assertThat(result.suggestedAnswers()).hasSize(2);
    }

    @Test
    void parseJsonWithLessThan2Answers_FallbackToPlainText() {
        String response = """
            {
              "question": "你的背景是什么？",
              "suggestedAnswers": [
                {"text": "前端开发", "confidence": "HIGH"}
              ]
            }
            """;

        var result = parser.parse(response);

        assertThat(result.hasStructuredOutput()).isFalse();  // 答案<2，降级
        assertThat(result.question()).isEqualTo(response);  // 降级时返回完整响应，非提取的 question
        assertThat(result.suggestedAnswers()).isEmpty();
    }

    @Test
    void parseJsonWithMoreThan5Answers_TakesFirst5() {
        String response = """
            {
              "question": "你最常用的编程语言？",
              "suggestedAnswers": [
                {"text": "JavaScript", "confidence": "HIGH"},
                {"text": "Python", "confidence": "HIGH"},
                {"text": "Java", "confidence": "MEDIUM"},
                {"text": "Go", "confidence": "MEDIUM"},
                {"text": "Rust", "confidence": "LOW"},
                {"text": "C++", "confidence": "LOW"},
                {"text": "PHP", "confidence": "LOW"}
              ]
            }
            """;

        var result = parser.parse(response);

        assertThat(result.hasStructuredOutput()).isTrue();
        assertThat(result.suggestedAnswers()).hasSize(5);  // 只取前 5 个
        assertThat(result.suggestedAnswers().get(4).text()).isEqualTo("Rust");
    }

    @Test
    void parseJsonMissingQuestion_ReturnsNull() {
        String response = """
            {
              "suggestedAnswers": [
                {"text": "选项1", "confidence": "HIGH"},
                {"text": "选项2", "confidence": "MEDIUM"}
              ]
            }
            """;

        var result = parser.parse(response);

        // JSON 解析失败时应降级到下一层（OPTIONS 或纯文本）
        // 这里会降级为纯文本
        assertThat(result.hasStructuredOutput()).isFalse();
    }

    @Test
    void parseJsonMissingConfidence_DefaultsToMedium() {
        String response = """
            {
              "question": "你了解 npm 吗？",
              "suggestedAnswers": [
                {"text": "非常熟悉"},
                {"text": "听说过", "confidence": "LOW"}
              ]
            }
            """;

        var result = parser.parse(response);

        assertThat(result.hasStructuredOutput()).isTrue();
        assertThat(result.suggestedAnswers()).hasSize(2);
        assertThat(result.suggestedAnswers().get(0).confidence()).isEqualTo("MEDIUM");  // 默认值
        assertThat(result.suggestedAnswers().get(1).confidence()).isEqualTo("LOW");
    }

    // ========== Tier 2: [OPTIONS: ...] 格式解析（向后兼容） ==========

    @Test
    void parseOptionsFormat_ReturnsStructuredResult() {
        String response = """
            你用过 ES6 的箭头函数吗？
            
            [OPTIONS: "经常用" | LOW:"偶尔用" | "没用过"]
            """;

        var result = parser.parse(response);

        assertThat(result.hasStructuredOutput()).isTrue();
        assertThat(result.question()).contains("你用过 ES6 的箭头函数吗？");
        assertThat(result.suggestedAnswers()).hasSize(3);
        assertThat(result.suggestedAnswers().get(0).text()).isEqualTo("经常用");
        assertThat(result.suggestedAnswers().get(0).confidence()).isEqualTo("HIGH");  // 默认
        assertThat(result.suggestedAnswers().get(1).text()).isEqualTo("偶尔用");
        assertThat(result.suggestedAnswers().get(1).confidence()).isEqualTo("LOW");
    }

    @Test
    void parseOptionsWithLessThan2Options_FallbackToPlainText() {
        String response = """
            你的名字是什么？
            
            [OPTIONS: "张三"]
            """;

        var result = parser.parse(response);

        assertThat(result.hasStructuredOutput()).isFalse();  // 选项<2，降级
        assertThat(result.question()).contains("你的名字是什么？");
    }

    // ========== Tier 3: 纯文本降级 ==========

    @Test
    void parseInvalidJson_FallbackToPlainText() {
        String response = """
            这是一段没有 JSON 格式的普通回复。
            AI 可能出现问题或者选择不返回结构化输出。
            """;

        var result = parser.parse(response);

        assertThat(result.hasStructuredOutput()).isFalse();
        assertThat(result.question()).isEqualTo(response);
        assertThat(result.suggestedAnswers()).isEmpty();
    }

    @Test
    void parseMalformedJson_FallbackToPlainText() {
        String response = """
            {
              "question": "你用过 Promise 吗",  // 缺少逗号
              "suggestedAnswers": [
                {"text": "用过" "confidence": "HIGH"}  // 缺少逗号
              ]
            """;

        var result = parser.parse(response);

        assertThat(result.hasStructuredOutput()).isFalse();
        assertThat(result.question()).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n\n"})
    void parseEmptyOrBlankResponse_ReturnsEmpty(String blank) {
        var result = parser.parse(blank);

        assertThat(result.hasStructuredOutput()).isFalse();
        assertThat(result.question()).isEmpty();
        assertThat(result.suggestedAnswers()).isEmpty();
    }

    @Test
    void parseNullResponse_ReturnsEmpty() {
        var result = parser.parse(null);

        assertThat(result.hasStructuredOutput()).isFalse();
        assertThat(result.question()).isEmpty();
    }

    // ========== 边界 Case ==========

    @Test
    void parseJsonWithEmptyAnswerText_SkipsThatAnswer() {
        String response = """
            {
              "question": "测试空答案",
              "suggestedAnswers": [
                {"text": "", "confidence": "HIGH"},
                {"text": "有效答案1", "confidence": "MEDIUM"},
                {"text": "   ", "confidence": "LOW"},
                {"text": "有效答案2", "confidence": "HIGH"}
              ]
            }
            """;

        var result = parser.parse(response);

        assertThat(result.hasStructuredOutput()).isTrue();
        assertThat(result.suggestedAnswers()).hasSize(2);  // 只保留非空答案
        assertThat(result.suggestedAnswers().get(0).text()).isEqualTo("有效答案1");
        assertThat(result.suggestedAnswers().get(1).text()).isEqualTo("有效答案2");
    }

    @Test
    void parseOptionsWithEmptyParts_SkipsThem() {
        String response = """
            问题
            [OPTIONS: "A" | | "B" |   | "C"]
            """;

        var result = parser.parse(response);

        assertThat(result.hasStructuredOutput()).isTrue();
        assertThat(result.suggestedAnswers()).hasSize(3);  // 跳过空 part
    }

    @Test
    void parseJsonWithCaseInsensitiveConfidence_ConvertsToUpperCase() {
        String response = """
            {
              "question": "测试大小写",
              "suggestedAnswers": [
                {"text": "答案1", "confidence": "high"},
                {"text": "答案2", "confidence": "Low"}
              ]
            }
            """;

        var result = parser.parse(response);

        assertThat(result.suggestedAnswers().get(0).confidence()).isEqualTo("HIGH");
        assertThat(result.suggestedAnswers().get(1).confidence()).isEqualTo("LOW");
    }

    // ========== 混合格式优先级 ==========

    @Test
    void jsonHasHigherPriorityThanOptions() {
        String response = """
            {
              "question": "这是 JSON 问题",
              "suggestedAnswers": [
                {"text": "JSON 答案1", "confidence": "HIGH"},
                {"text": "JSON 答案2", "confidence": "MEDIUM"}
              ]
            }
            
            [OPTIONS: "OPTIONS 答案1" | "OPTIONS 答案2"]
            """;

        var result = parser.parse(response);

        assertThat(result.hasStructuredOutput()).isTrue();
        assertThat(result.question()).isEqualTo("这是 JSON 问题");  // JSON 优先
        assertThat(result.suggestedAnswers().get(0).text()).isEqualTo("JSON 答案1");
    }
}
