package com.learningos.modules.path.controller;

import com.learningos.common.Result;
import com.learningos.modules.path.entity.Stage;
import com.learningos.modules.path.service.PathService;
import com.learningos.modules.path.service.PathService.PathWithStages;
import com.learningos.modules.path.service.PathService.StageSessionResult;
import com.learningos.modules.session.entity.SessionMessage;
import com.learningos.modules.session.service.SkillRubricLoader;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Path", description = "学习路径与阶段管理")
@SecurityRequirement(name = "bearerAuth")
public class PathController {

    private final PathService pathService;
    private final SkillRubricLoader skillRubricLoader;

    // ─── POST /api/path/generate ──────────────────────────────────────────────

    @PostMapping("/path/generate")
    @Operation(summary = "根据用户画像用 AI 生成学习路径")
    public Result<Map<String, Object>> generatePath(@AuthenticationPrincipal UUID userId) {
        PathWithStages result = pathService.generatePath(userId);
        return Result.ok(toPathMap(result));
    }

    // ─── GET /api/path ────────────────────────────────────────────────────────

    @GetMapping("/path")
    @Operation(summary = "获取当前进行中的学习路径")
    public Result<Map<String, Object>> getCurrentPath(@AuthenticationPrincipal UUID userId) {
        return pathService.getCurrentPath(userId)
                .map(r -> Result.ok(toPathMap(r)))
                .orElseGet(() -> Result.ok(null));
    }

    // ─── POST /api/stage/{stageId}/start ─────────────────────────────────────

    @PostMapping("/stage/{stageId}/start")
    @Operation(summary = "开始或恢复一个学习阶段（返回会话历史 + AI 开场白）")
    public Result<Map<String, Object>> startStage(@PathVariable UUID stageId,
                                                  @AuthenticationPrincipal UUID userId) {
        StageSessionResult result = pathService.startStage(stageId, userId);
        return Result.ok(toSessionMap(result));
    }

    // ─── 序列化辅助 ───────────────────────────────────────────────────────────

    private Map<String, Object> toPathMap(PathWithStages pws) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("path_id", pws.path().getId());
        map.put("title", pws.path().getTitle());
        map.put("description", pws.path().getDescription());
        map.put("status", pws.path().getStatus());
        map.put("stages", pws.stages().stream().map(this::toStageMap).toList());
        return map;
    }

    private Map<String, Object> toStageMap(Stage s) {
        return Map.of(
            "id", s.getId(),
            "index", s.getStageIndex(),
            "title", s.getTitle(),
            "goal", s.getGoal() != null ? s.getGoal() : "",
            "status", s.getStatus()
        );
    }

    private Map<String, Object> toSessionMap(StageSessionResult r) {
        List<Map<String, Object>> messages = r.messages().stream()
                .map(m -> Map.<String, Object>of(
                    "id", m.getId(),
                    "role", m.getRole(),
                    "content", m.getContent(),
                    "created_at", m.getCreatedAt()
                ))
                .toList();

        var progress = r.session().getProgress();
        String currentNode    = progress != null ? (String)  progress.getOrDefault("current_node",    "intro") : "intro";
        String nodeStatus     = progress != null ? (String)  progress.getOrDefault("node_status",     "running") : "running";
        boolean awaitsArtifact = progress != null && Boolean.TRUE.equals(progress.get("awaits_artifact"));

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("session_id",       r.session().getId());
        map.put("stage_id",         r.stage().getId());
        map.put("stage_title",      r.stage().getTitle());
        map.put("is_new",           r.isNew());
        map.put("current_node",     currentNode);
        map.put("node_status",      nodeStatus);
        map.put("awaits_artifact",  awaitsArtifact);
        map.put("artifact_type",    skillRubricLoader.loadArtifactType(
                r.stage().getSkillId(), r.stage().getStageIndex()));
        map.put("messages",         messages);

        // 返回 interaction_config，让前端在 intro 节点就能显示预制答案
        SkillRubricLoader.InteractionConfig interactionConfig =
                skillRubricLoader.loadInteractionConfig(r.stage().getSkillId(), r.stage().getStageIndex(), null);
        map.put("interaction_config", Map.of(
                "mode",           interactionConfig.mode(),
                "preset_answers", interactionConfig.presetAnswers().stream().map(a -> Map.of(
                        "id",         a.id(),
                        "text",       a.text(),
                        "confidence", a.confidence()
                )).toList()
        ));

        return map;
    }
}
