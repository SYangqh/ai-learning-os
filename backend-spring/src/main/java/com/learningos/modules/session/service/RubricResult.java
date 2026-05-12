package com.learningos.modules.session.service;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Rubric 结构化评审结果，由 SessionService 在 REVIEW 节点解析 LLM 输出后生成。
 */
public record RubricResult(
        @JsonProperty("passed")   boolean      passed,
        @JsonProperty("score")    int          score,
        @JsonProperty("feedback") String       feedback,
        @JsonProperty("hints")    List<String> hints
) {}
