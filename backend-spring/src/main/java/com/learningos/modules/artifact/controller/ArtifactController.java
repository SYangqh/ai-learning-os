package com.learningos.modules.artifact.controller;

import com.learningos.common.Result;
import com.learningos.modules.artifact.entity.Artifact;
import com.learningos.modules.artifact.service.ArtifactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Artifact", description = "学习产出提交与查询")
@SecurityRequirement(name = "bearerAuth")
public class ArtifactController {

    private final ArtifactService artifactService;

    /**
     * POST /api/artifact — 提交学习产出（CODE / NOTE）
     *
     * <p>独立于 /session/advance，专门用于保存代码或笔记到持久化存储。
     * 提交成功后，session progress 中的 artifact_submitted 会置为 true，
     * 解锁 TASK 节点的 advance 门控。</p>
     */
    @PostMapping("/artifact")
    @Operation(summary = "提交学习产出（CODE / NOTE）")
    public Result<Map<String, Object>> submit(@Valid @RequestBody SubmitRequest req,
                                              @AuthenticationPrincipal UUID userId) {
        Artifact artifact = artifactService.submit(userId, req.sessionId(), req.type(), req.content());
        return Result.ok(Map.of(
                "id",         artifact.getId(),
                "type",       artifact.getType(),
                "status",     artifact.getStatus(),
                "node_key",   artifact.getNodeKey(),
                "created_at", artifact.getCreatedAt()
        ));
    }

    /**
     * GET /api/session/{sessionId}/artifacts — 查询某个 session 的所有产出（降序）
     */
    @GetMapping("/session/{sessionId}/artifacts")
    @Operation(summary = "查询 session 下的所有 Artifact")
    public Result<List<Map<String, Object>>> list(@PathVariable UUID sessionId,
                                                  @AuthenticationPrincipal UUID userId) {
        List<Artifact> artifacts = artifactService.listBySession(userId, sessionId);
        List<Map<String, Object>> result = artifacts.stream()
                .map(a -> Map.<String, Object>of(
                        "id",         a.getId(),
                        "type",       a.getType(),
                        "content",    a.getContent(),
                        "status",     a.getStatus(),
                        "node_key",   a.getNodeKey(),
                        "created_at", a.getCreatedAt()
                ))
                .toList();
        return Result.ok(result);
    }

    record SubmitRequest(
            @NotNull UUID sessionId,
            @NotBlank String type,     // CODE or NOTE
            @NotBlank String content
    ) {}
}
