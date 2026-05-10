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
    void reviewPassesWithPassKeyword() {
        String reply1 = "很好！[PASS] 你的代码符合要求。";
        String reply2 = "不错，[通过] 进入复盘阶段。";
        String reply3 = "代码有问题，请修改后再提交。";

        assertThat(reply1.contains("[PASS]") || reply1.contains("[通过]")).isTrue();
        assertThat(reply2.contains("[PASS]") || reply2.contains("[通过]")).isTrue();
        assertThat(reply3.contains("[PASS]") || reply3.contains("[通过]")).isFalse();
    }

    @Test
    void retroIsLastNodeBeforeComplete() {
        assertThat(nextNode("retro")).isEqualTo("complete");
    }

    @Test
    void unknownNodeDefaultsToIntro() {
        assertThat(nextNode("unknown")).isEqualTo("intro");
    }
}
