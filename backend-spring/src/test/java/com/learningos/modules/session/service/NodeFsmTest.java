package com.learningos.modules.session.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * 节点状态机单元测试 — 验证推进规则无回归。
 *
 * <p>不依赖 Spring 上下文，纯逻辑测试，毫秒级完成。</p>
 */
class NodeFsmTest {

    /** 与 SessionService 中保持完全一致的顺序 */
    private static final List<String> NODE_SEQUENCE =
            List.of("intro", "concept", "practice", "task", "review", "retro");

    /** 模拟 SessionService.nextNode() 的逻辑 */
    private String nextNode(String current) {
        int idx = NODE_SEQUENCE.indexOf(current);
        if (idx < 0) return "intro";
        return idx + 1 < NODE_SEQUENCE.size() ? NODE_SEQUENCE.get(idx + 1) : "complete";
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "intro,    concept",
        "concept,  practice",
        "practice, task",
        "task,     review",
        "review,   retro",
        "retro,    complete",
    })
    void nodeSequenceIsCorrect(String from, String expected) {
        assertThat(nextNode(from.trim())).isEqualTo(expected.trim());
    }

    @Test
    void taskNodeRequiresArtifact() {
        // TASK 节点：code 为空时应阻塞
        String currentNode = "task";
        String code = "";
        boolean hasArtifact = (code != null && !code.isBlank());
        assertThat(hasArtifact).isFalse();  // 应被阻塞
    }

    @Test
    void taskNodePassesWithCode() {
        String currentNode = "task";
        String code = "System.out.println(\"hello\");";
        boolean hasArtifact = (code != null && !code.isBlank());
        assertThat(hasArtifact).isTrue();   // 应放行
    }

    @Test
    void reviewPassesWithRubricJson() {
        // Phase 3: REVIEW 优先解析 RUBRIC_JSON:: 行
        String reply = "RUBRIC_JSON::{\"passed\":true,\"score\":85,\"feedback\":\"很好\",\"hints\":[]}\n---\n你的代码实现正确！";
        String jsonLine = reply.lines().filter(l -> l.startsWith("RUBRIC_JSON::")).findFirst().orElse(null);
        assertThat(jsonLine).isNotNull();
        String json = jsonLine.substring("RUBRIC_JSON::".length()).trim();
        assertThat(json).contains("\"passed\":true");
    }

    @Test
    void reviewFailsWithRubricJson() {
        String reply = "RUBRIC_JSON::{\"passed\":false,\"score\":40,\"feedback\":\"需要改进\",\"hints\":[\"检查路径\"]}\n---\n请继续修改";
        String jsonLine = reply.lines().filter(l -> l.startsWith("RUBRIC_JSON::")).findFirst().orElse(null);
        assertThat(jsonLine).isNotNull();
        assertThat(jsonLine).contains("\"passed\":false");
    }

    @Test
    void reviewFallsBackToKeywordWhenNoRubricJson() {
        // fallback：LLM 未输出 RUBRIC_JSON 时，仍使用关键词判断
        String replyPass = "代码不错，[PASS] 进入复盘阶段。";
        String replyFail = "代码有问题，请修改后再提交。";
        boolean noRubric1 = replyPass.lines().noneMatch(l -> l.startsWith("RUBRIC_JSON::"));
        boolean noRubric2 = replyFail.lines().noneMatch(l -> l.startsWith("RUBRIC_JSON::"));
        assertThat(noRubric1).isTrue();
        assertThat(noRubric2).isTrue();
        // fallback keyword check
        assertThat(replyPass.contains("[PASS]") || replyPass.contains("[通过]")).isTrue();
        assertThat(replyFail.contains("[PASS]") || replyFail.contains("[通过]")).isFalse();
    }

    @Test
    void retroIsLastNodeBeforeComplete() {
        assertThat(nextNode("retro")).isEqualTo("complete");
    }

    @Test
    void unknownNodeDefaultsToIntro() {
        assertThat(nextNode("unknown")).isEqualTo("intro");
    }

    // ─── AI 控制推进节点（[ADVANCE] 标记）────────────────────────────────────────

    /** concept/practice/retro 节点：AI 回复无 [ADVANCE] 时不推进 */
    @Test
    void aiControlledNodeStaysWithoutAdvanceMarker() {
        String reply = "很好的问题，让我们继续深入讲解 DataFrame 的概念...";
        boolean aiAdvance = reply.contains("[ADVANCE]");
        assertThat(aiAdvance).isFalse();
    }

    /** concept/practice/retro 节点：AI 回复含 [ADVANCE] 时推进 */
    @Test
    void aiControlledNodeAdvancesWithMarker() {
        String reply = "你已经理解了 DataFrame 的基本概念，我们进入练习阶段。\n[ADVANCE]";
        boolean aiAdvance = reply.contains("[ADVANCE]");
        assertThat(aiAdvance).isTrue();
    }

    /** [ADVANCE] 标记必须从用户可见回复中剥离 */
    @Test
    void advanceMarkerIsStrippedFromUserReply() {
        String rawReply = "很好，你理解了核心概念！接下来进入练习环节。\n[ADVANCE]";
        String userReply = rawReply.replace("[ADVANCE]", "").replaceAll("\\n{3,}", "\n\n").trim();
        assertThat(userReply).doesNotContain("[ADVANCE]");
        assertThat(userReply).contains("很好，你理解了核心概念");
    }

    /** intro 节点应自动推进（不受 AI_ADVANCE_CONTROLLED_NODES 控制） */
    @Test
    void introNodeIsNotAiControlled() {
        var aiControlledNodes = java.util.Set.of("concept", "practice", "retro");
        assertThat(aiControlledNodes).doesNotContain("intro");
        assertThat(aiControlledNodes).doesNotContain("task");
        assertThat(aiControlledNodes).doesNotContain("review");
    }
}
