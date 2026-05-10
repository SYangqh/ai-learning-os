package com.learningos.modules.session.controller;

import com.learningos.common.Result;
import com.learningos.modules.session.service.SessionService;
import com.learningos.modules.session.service.SessionService.AdvanceResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Session", description = "学习会话推进与自由问答")
@SecurityRequirement(name = "bearerAuth")
public class SessionController {

    private final SessionService sessionService;

    /**
     * POST /api/session/advance — 推进式对话（按教学节点逐步推进）
     *
     * <p>兼容旧前端：支持 {@code api_key} 字段直接传入 API Key（BYOK 迁移期）。
     * 建议通过 {@code PUT /api/llm/credentials} 存储后不再明文传输。</p>
     */
    @PostMapping("/session/advance")
    @Operation(summary = "推进式对话（按节点顺序推进学习阶段）")
    public Result<Map<String, Object>> advance(@Valid @RequestBody AdvanceRequest req,
                                               @AuthenticationPrincipal UUID userId) {
        AdvanceResult result = sessionService.advance(
                req.sessionId(), userId, req.userInput(), req.code(), req.apiKey());

        return Result.ok(Map.of(
            "content",          result.content(),
            "current_node",     result.currentNode(),
            "node_status",      result.nodeStatus(),
            "awaits_artifact",  result.awaitsArtifact(),
            "stage_complete",   result.stageComplete(),
            "awaits_input",     !result.stageComplete()
        ));
    }

    /**
     * POST /api/chat — 自由问答（补课，不推进节点）
     */
    @PostMapping("/chat")
    @Operation(summary = "自由问答（不影响学习进度）")
    public Result<Map<String, Object>> chat(@Valid @RequestBody ChatRequest req,
                                            @AuthenticationPrincipal UUID userId) {
        String response = sessionService.freeChat(
                req.sessionId(), userId, req.message(), req.apiKey());

        return Result.ok(Map.of("response", response));
    }

    // ─── Request Records ─────────────────────────────────────────────────────

    record AdvanceRequest(
            @NotNull UUID sessionId,
            @NotBlank String userInput,
            String code,        // 可选：提交的代码
            String apiKey       // 可选：BYOK inline key（迁移期兼容）
    ) {}

    record ChatRequest(
            @NotNull UUID sessionId,
            @NotBlank String message,
            String apiKey       // 可选：BYOK inline key
    ) {}
}
