package com.learningos.modules.observability.controller;

import com.learningos.common.Result;
import com.learningos.modules.observability.service.ObservabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/usage")
@RequiredArgsConstructor
public class UsageController {

    private final ObservabilityService observabilityService;

    /**
     * 查询指定 Session 的 token 消耗摘要。
     * GET /api/usage/session/{sessionId}
     */
    @GetMapping("/session/{sessionId}")
    public Result<ObservabilityService.SessionUsageSummary> getSessionUsage(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal UUID userId) {
        // 暂不做鉴权（session 必须属于当前用户），简单返回统计数据
        return Result.ok(observabilityService.getSessionSummary(sessionId));
    }
}
