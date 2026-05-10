package com.learningos.modules.path.service;

import com.learningos.common.exception.AppException;
import com.learningos.modules.path.entity.LearningPath;
import com.learningos.modules.path.entity.Stage;
import com.learningos.modules.path.repository.LearningPathRepository;
import com.learningos.modules.path.repository.StageRepository;
import com.learningos.modules.session.entity.LearningSession;
import com.learningos.modules.session.entity.SessionMessage;
import com.learningos.modules.session.repository.LearningSessionRepository;
import com.learningos.modules.session.repository.SessionMessageRepository;
import com.learningos.modules.user.entity.UserProfile;
import com.learningos.modules.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PathService {

    private final LearningPathRepository pathRepository;
    private final StageRepository stageRepository;
    private final LearningSessionRepository sessionRepository;
    private final SessionMessageRepository messageRepository;
    private final UserProfileRepository profileRepository;
    private final ChatClient chatClient;

    // ─── 生成学习路径 ───────────────────────────────────────────────────────────

    @Transactional
    public PathWithStages generatePath(UUID userId) {
        UserProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> AppException.badRequest("请先完成个人画像填写"));

        // 调用 LLM 生成路径规划
        String prompt = buildPathPlannerPrompt(profile);
        String llmResponse = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        PathPlan plan = parsePlan(llmResponse, profile);

        // 持久化路径
        LearningPath path = new LearningPath();
        path.setUserId(userId);
        path.setTitle(plan.title());
        path.setDescription("为 " + profile.getBackground() + " 定制的 " + profile.getTarget() + " 学习路径");
        pathRepository.save(path);

        // 持久化阶段（第 0 阶段直接激活，其余锁定）
        List<Stage> stages = new ArrayList<>();
        for (int i = 0; i < plan.stages().size(); i++) {
            StagePlan sp = plan.stages().get(i);
            Stage stage = new Stage();
            stage.setPathId(path.getId());
            stage.setStageIndex(i);
            stage.setTitle(sp.title());
            stage.setGoal(sp.goal());
            stage.setSkillId(sp.skillId());
            stage.setStatus(i == 0 ? "active" : "locked");
            stages.add(stageRepository.save(stage));
        }

        log.info("Generated path {} for user {} with {} stages", path.getId(), userId, stages.size());
        return new PathWithStages(path, stages);
    }

    // ─── 查询当前路径 ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Optional<PathWithStages> getCurrentPath(UUID userId) {
        return pathRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .filter(p -> "ongoing".equals(p.getStatus()))
                .findFirst()
                .map(p -> new PathWithStages(p, stageRepository.findByPathIdOrderByStageIndex(p.getId())));
    }

    // ─── 开始/恢复阶段 ────────────────────────────────────────────────────────────

    @Transactional
    public StageSessionResult startStage(UUID stageId, UUID userId) {
        Stage stage = stageRepository.findById(stageId)
                .orElseThrow(() -> AppException.notFound("阶段不存在"));

        if ("locked".equals(stage.getStatus())) {
            throw AppException.forbidden("该阶段尚未解锁");
        }

        // 优先恢复未完成的会话
        Optional<LearningSession> existingSession =
                sessionRepository.findTopByStageIdAndUserIdAndFinishedAtIsNull(stageId, userId);

        if (existingSession.isPresent()) {
            LearningSession session = existingSession.get();
            List<SessionMessage> history = messageRepository.findBySessionIdOrderByCreatedAt(session.getId());
            return new StageSessionResult(stage, session, history, false);
        }

        // 新建会话，生成开场白
        String opening = generateStageOpening(stage);

        LearningSession session = new LearningSession();
        session.setStageId(stageId);
        session.setUserId(userId);
        session.setProgress(Map.of(
                "current_node",    "intro",
                "node_status",     "running",
                "awaits_artifact", false
        ));
        sessionRepository.save(session);

        SessionMessage openingMsg = new SessionMessage();
        openingMsg.setSessionId(session.getId());
        openingMsg.setRole("assistant");
        openingMsg.setContent(opening);
        messageRepository.save(openingMsg);

        log.info("Started new session {} for stage {} / user {}", session.getId(), stageId, userId);
        return new StageSessionResult(stage, session, List.of(openingMsg), true);
    }

    // ─── 内部：构建路径规划 Prompt ────────────────────────────────────────────────

    private String buildPathPlannerPrompt(UserProfile profile) {
        return """
            你是一位专业的学习路径规划师。根据学员的背景信息，为其设计一条结构化的学习路径。

            学员背景：
            - 职业/学历背景：%s
            - 已有技能：%s
            - 学习目标：%s
            - 每日学习时间：%d 分钟
            - 偏好学习方式：%s

            请按 JSON 格式返回（不要加 markdown 代码块）：
            {
              "title": "路径标题（20字以内）",
              "stages": [
                { "title": "阶段名", "goal": "阶段目标（50字以内）", "skill_id": "snake_case_id" }
              ]
            }

            要求：
            - 阶段数量 4~8 个，循序渐进
            - skill_id 使用英文小写下划线，如 python_basics、web_scraping
            - 每个阶段可在 %d 分钟/天的节奏下，2~4 周内完成
            """.formatted(
                profile.getBackground(),
                profile.getSkills() != null ? String.join(", ", profile.getSkills()) : "无",
                profile.getTarget(),
                profile.getDailyTime() != null ? profile.getDailyTime() : 60,
                profile.getLearningStyle() != null ? profile.getLearningStyle() : "project",
                profile.getDailyTime() != null ? profile.getDailyTime() : 60
        );
    }

    private String generateStageOpening(Stage stage) {
        String prompt = """
            你是一位耐心的编程导师，正在开始新的学习阶段。

            阶段名称：%s
            阶段目标：%s

            请用 2~3 段话（约 150 字）：
            1. 热情地介绍本阶段学习内容和重要性
            2. 说明本阶段结束时学员能做到什么
            3. 提出第一个引导性问题，了解学员现有的相关认知

            语气亲切自然，不要使用 Markdown 格式。
            """.formatted(stage.getTitle(), stage.getGoal() != null ? stage.getGoal() : "");

        return chatClient.prompt().user(prompt).call().content();
    }

    // ─── JSON 解析（宽松解析，LLM 输出可能有小偏差）──────────────────────────────

    @SuppressWarnings("unchecked")
    private PathPlan parsePlan(String llmResponse, UserProfile profile) {
        try {
            // 去掉可能的 markdown 代码块包裹
            String json = llmResponse.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("```(?:json)?\\s*", "").replaceAll("```\\s*$", "").trim();
            }

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> root = mapper.readValue(json, Map.class);

            String title = (String) root.getOrDefault("title", profile.getTarget() + " 学习路径");
            List<Map<String, String>> stagesRaw = (List<Map<String, String>>) root.get("stages");

            List<StagePlan> stages = new ArrayList<>();
            if (stagesRaw != null) {
                for (Map<String, String> s : stagesRaw) {
                    stages.add(new StagePlan(
                        s.getOrDefault("title", "未命名阶段"),
                        s.getOrDefault("goal", ""),
                        s.getOrDefault("skill_id", "")
                    ));
                }
            }
            return new PathPlan(title, stages);
        } catch (Exception e) {
            log.warn("Failed to parse LLM path plan, using fallback: {}", e.getMessage());
            // 降级：创建单阶段路径
            return new PathPlan(
                profile.getTarget() + " 学习路径",
                List.of(new StagePlan("基础入门", "完成基础概念学习", "basics"))
            );
        }
    }

    // ─── 内部 Record（本类内部传递数据，不暴露到 API 层）────────────────────────────

    public record PathWithStages(LearningPath path, List<Stage> stages) {}
    public record StageSessionResult(Stage stage, LearningSession session,
                                     List<SessionMessage> messages, boolean isNew) {}

    private record PathPlan(String title, List<StagePlan> stages) {}
    private record StagePlan(String title, String goal, String skillId) {}
}
