package com.learningos.modules.session.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 响应解析器：解析 AI 返回的结构化 JSON 响应（Phase 9D）
 *
 * <p>支持三种格式（优先级从高到低）：
 * <ol>
 *   <li>JSON 格式：{"question": "...", "suggestedAnswers": [...]}</li>
 *   <li>[OPTIONS: ...] 格式（向后兼容）</li>
 *   <li>纯文本（降级）</li>
 * </ol>
 * </p>
 */
@Slf4j
@Component
public class AiResponseParser {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 兼容旧格式：[OPTIONS: "A" | LOW:"B" | "C"]
    private static final Pattern OPTIONS_PATTERN =
            Pattern.compile("\\[OPTIONS:\\s*(.+?)\\]",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    /**
     * 解析 AI 返回的响应
     *
     * @param aiResponse AI 返回的完整响应
     * @return ParseResult（包含问题和建议答案）
     */
    public ParseResult parse(String aiResponse) {
        if (aiResponse == null || aiResponse.isBlank()) {
            log.warn("[AiResponseParser] AI 响应为空");
            return ParseResult.empty();
        }

        // 1. 尝试解析 JSON 格式（Phase 9D 新格式）
        ParseResult jsonResult = tryParseJson(aiResponse);
        if (jsonResult != null && jsonResult.hasStructuredOutput()) {
            log.debug("[AiResponseParser] 成功解析 JSON 格式: answersCount={}", 
                jsonResult.suggestedAnswers().size());
            return jsonResult;
        }

        // 2. 尝试解析 [OPTIONS: ...] 格式（向后兼容）
        ParseResult optionsResult = tryParseOptions(aiResponse);
        if (optionsResult != null && optionsResult.hasStructuredOutput()) {
            log.debug("[AiResponseParser] 成功解析 [OPTIONS] 格式: answersCount={}", 
                optionsResult.suggestedAnswers().size());
            return optionsResult;
        }

        // 3. 降级为纯文本
        log.debug("[AiResponseParser] 未找到结构化格式，降级为纯文本");
        return ParseResult.plainText(aiResponse);
    }

    /**
     * 尝试从响应中提取并解析 JSON 格式
     */
    private ParseResult tryParseJson(String response) {
        try {
            // 尝试直接解析整个响应为 JSON
            JsonNode root = objectMapper.readTree(response);
            return parseJsonNode(root, response);
        } catch (Exception directParseError) {
            // 直接解析失败，尝试提取 JSON 片段
            String jsonPart = extractJson(response);
            if (jsonPart == null) {
                return null;
            }

            try {
                JsonNode root = objectMapper.readTree(jsonPart);
                return parseJsonNode(root, jsonPart);
            } catch (Exception e) {
                log.debug("[AiResponseParser] JSON 解析失败: {}", e.getMessage());
                return null;
            }
        }
    }

    /**
     * 解析 JSON 节点为 ParseResult
     */
    private ParseResult parseJsonNode(JsonNode root, String originalJson) {
        // 解析 question
        String question = root.has("question")
                ? root.get("question").asText()
                : null;

        if (question == null || question.isBlank()) {
            log.debug("[AiResponseParser] JSON 缺少 question 字段");
            return null;
        }

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
            return null;  // 返回 null 让 tryParseJson 降级到 OPTIONS 或纯文本
        }

        if (suggestedAnswers.size() > 5) {
            log.info("[AiResponseParser] 答案选项过多（>5），只取前 5 个");
            suggestedAnswers = suggestedAnswers.subList(0, 5);
        }

        log.info("[AiResponseParser] 成功解析 JSON: question={}, answersCount={}",
                question.substring(0, Math.min(50, question.length())), suggestedAnswers.size());
        return new ParseResult(question, suggestedAnswers, true);
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
     * 尝试解析 [OPTIONS: ...] 格式（向后兼容）
     */
    private ParseResult tryParseOptions(String response) {
        Matcher m = OPTIONS_PATTERN.matcher(response);
        if (!m.find()) {
            return null;
        }

        String optionStr = m.group(1);
        String question = (response.substring(0, m.start()) + response.substring(m.end()))
                .replaceAll("\\n{3,}", "\n\n").trim();

        List<SuggestedAnswer> answers = new ArrayList<>();
        String[] parts = optionStr.split("\\|");
        for (String part : parts) {
            String raw = part.trim();
            if (raw.isEmpty()) continue;

            String confidence = "HIGH";
            String text = raw;
            if (raw.toUpperCase().startsWith("LOW:")) {
                confidence = "LOW";
                text = raw.substring(4).trim();
            }
            text = text.replaceAll("^\"|\"$", "").trim();
            if (!text.isEmpty()) {
                answers.add(new SuggestedAnswer(text, confidence));
            }
        }

        if (answers.size() < 2) {
            return ParseResult.plainText(question);
        }

        log.info("[AiResponseParser] 成功解析 [OPTIONS] 格式: answersCount={}", answers.size());
        return new ParseResult(question, answers, true);
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
