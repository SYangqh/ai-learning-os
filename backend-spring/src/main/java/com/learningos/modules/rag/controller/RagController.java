package com.learningos.modules.rag.controller;

import com.learningos.common.Result;
import com.learningos.modules.rag.service.RagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
@Tag(name = "RAG", description = "知识库管理（向量化 Ingest / 检索）")
@SecurityRequirement(name = "bearerAuth")
public class RagController {

    private final RagService ragService;

    /**
     * POST /api/rag/ingest — 将文本块向量化并存入知识库。
     *
     * <p>每个用户只能用自己的 embedding Key 写入；skill_id 为 null 时写入全局知识库。</p>
     */
    @PostMapping("/ingest")
    @Operation(summary = "向量化并存入知识块")
    public Result<Map<String, Object>> ingest(@Valid @RequestBody IngestRequest req,
                                              @AuthenticationPrincipal UUID userId) {
        ragService.ingest(userId, req.skillId(), req.title(), req.content(), req.sourceRef());
        return Result.ok(Map.of("status", "ok"));
    }

    /**
     * POST /api/rag/retrieve — 检索最相关的知识块（调试 / 前端展示用）。
     */
    @PostMapping("/retrieve")
    @Operation(summary = "语义检索知识块（调试接口）")
    public Result<List<String>> retrieve(@Valid @RequestBody RetrieveRequest req,
                                         @AuthenticationPrincipal UUID userId) {
        List<String> chunks = ragService.retrieve(userId, req.query(), req.skillId(), req.topK() > 0 ? req.topK() : 3);
        return Result.ok(chunks);
    }

    // ─── Request Records ──────────────────────────────────────────────────────

    record IngestRequest(
            String skillId,
            @NotBlank String title,
            @NotBlank String content,
            String sourceRef
    ) {}

    record RetrieveRequest(
            @NotBlank String query,
            String skillId,
            int topK
    ) {}
}
