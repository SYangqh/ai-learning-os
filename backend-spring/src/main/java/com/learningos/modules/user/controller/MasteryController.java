package com.learningos.modules.user.controller;

import com.learningos.common.Result;
import com.learningos.modules.user.entity.UserMastery;
import com.learningos.modules.user.service.MasteryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Mastery", description = "用户技能掌握度查询")
@SecurityRequirement(name = "bearerAuth")
public class MasteryController {

    private final MasteryService masteryService;

    /**
     * GET /api/mastery — 获取当前用户所有概念的掌握度列表。
     */
    @GetMapping("/mastery")
    @Operation(summary = "获取当前用户掌握度概览")
    public Result<List<Map<String, Object>>> getMastery(@AuthenticationPrincipal UUID userId) {
        List<UserMastery> list = masteryService.getMasteryList(userId);
        List<Map<String, Object>> result = list.stream()
                .map(m -> Map.<String, Object>of(
                    "concept_key",    m.getId().getConceptKey(),
                    "mastery_score",  m.getMasteryScore(),
                    "last_tested_at", m.getLastTestedAt()
                ))
                .toList();
        return Result.ok(result);
    }
}
