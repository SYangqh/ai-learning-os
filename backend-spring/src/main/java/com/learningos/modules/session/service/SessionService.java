package com.learningos.modules.session.service;

import com.learningos.common.exception.AppException;
import com.learningos.modules.llm.service.DynamicChatService;
import com.learningos.modules.path.entity.Stage;
import com.learningos.modules.path.repository.StageRepository;
import com.learningos.modules.rag.service.RagService;
import com.learningos.modules.session.entity.LearningSession;
import com.learningos.modules.session.entity.SessionMessage;
import com.learningos.modules.session.repository.LearningSessionRepository;
import com.learningos.modules.session.repository.SessionMessageRepository;
import com.learningos.modules.user.entity.UserProfile;
import com.learningos.modules.user.repository.UserProfileRepository;
import com.learningos.modules.user.service.MasteryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    /** 保持上下文的最近消息条数 */
    private static final int MAX_CONTEXT_MESSAGES = 20;

    /**
     * 节点推进顺序（固定，不可跳过）。
     * TASK 节点要求必须提交 artifact，REVIEW 未通过时停留在 REVIEW，不自动推进。
     */
    private static final List<String> NODE_SEQUENCE =
            List.of("intro", "concept", "practice", "task", "review", "retro");

    /** 这些节点需要用户先提交 artifact 才能 advance */
    private static final Set<String> ARTIFACT_REQUIRED_NODES = Set.of("task");

    /** progress jsonb 里存储用的 key 名 */
    private static final String KEY_NODE          = "current_node";
    private static final String KEY_NODE_STATUS   = "node_status";   // pending/running/passed/failed
    private static final String KEY_AWAITS_ARTIFACT = "awaits_artifact";
    private static final String KEY_REVIEW_PASSED = "review_passed";
    private static final String KEY_ARTIFACT_SUBMITTED = "artifact_submitted";

    private final LearningSessionRepository sessionRepository;
    private final SessionMessageRepository messageRepository;
    private final StageRepository stageRepository;
    private final UserProfileRepository profileRepository;
    private final DynamicChatService chatService;
    private final RagService ragService;
    private final MasteryService masteryService;

    // ─── 推进式对话（按节点顺序推进学习阶段）────────────────────────────────────────

    @Transactional
    public AdvanceResult advance(UUID sessionId, UUID userId, String userInput,
                                 String code, String inlineApiKey) {
        LearningSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> AppException.notFound("会话不存在"));

        if (!session.getUserId().equals(userId)) throw AppException.forbidden("无权操作此会话");
        if (session.getFinishedAt() != null) throw AppException.badRequest("该学习阶段已完成");

        Stage stage = stageRepository.findById(session.getStageId())
                .orElseThrow(() -> AppException.notFound("阶段不存在"));

        String currentNode = currentNode(session);

        // ── TASK 节点门控：必须先提交 artifact（code 不为空即视为提交）──────────────
        if (ARTIFACT_REQUIRED_NODES.contains(currentNode)) {
            boolean hasArtifact = (code != null && !code.isBlank())
                    || Boolean.TRUE.equals(getProgress(session, KEY_ARTIFACT_SUBMITTED));
            if (!hasArtifact) {
                return new AdvanceResult(
                        "请先提交你的代码或作品，才能进入评审阶段。",
                        currentNode, "running", true, false);
            }
            // 标记已提交
            setProgress(session, KEY_ARTIFACT_SUBMITTED, true);
        }

        // 保存用户消息
        saveMessage(session.getId(), "user", userInput);
        if (code != null && !code.isBlank()) {
            saveMessage(session.getId(), "user", "```\n" + code + "\n```");
        }

        // 加载历史 + 构建系统 Prompt
        List<Map<String, String>> messages = buildMessages(session, stage, userId, currentNode, false);

        // 调用 LLM
        String reply = chatService.chat(userId, inlineApiKey, messages);
        saveMessage(session.getId(), "assistant", reply);

        // ── REVIEW 节点：LLM 回复包含 [PASS] 才能推进 ─────────────────────────────
        if ("review".equals(currentNode)) {
            boolean reviewPassed = reply.contains("[PASS]") || reply.contains("[通过]");
            setProgress(session, KEY_REVIEW_PASSED, reviewPassed);
            if (!reviewPassed) {
                // 不推进，停留在 review，让用户修改后重新提交
                setProgress(session, KEY_NODE_STATUS, "failed");
                sessionRepository.save(session);
                // 评审未通过 → 掌握度小幅下降
                if (stage.getSkillId() != null) {
                    masteryService.recordStageResult(userId, stage.getSkillId(), false);
                }
                log.debug("Session {} review not passed, staying at review", sessionId);
                return new AdvanceResult(reply, "review", "failed", true, false);
            }
        }

        // 推进节点
        String nextNode = nextNode(currentNode);
        boolean stageComplete = "complete".equals(nextNode);

        // 阶段完成 → 掌握度加分
        if (stageComplete && stage.getSkillId() != null) {
            masteryService.recordStageResult(userId, stage.getSkillId(), true);
        }

        updateNodeProgress(session, stageComplete ? "complete" : nextNode,
                stageComplete ? "passed" : "running",
                false, stageComplete);

        log.debug("Session {} advanced: {} → {}", sessionId, currentNode, nextNode);
        return new AdvanceResult(reply, stageComplete ? "complete" : nextNode,
                "running", ARTIFACT_REQUIRED_NODES.contains(nextNode), stageComplete);
    }

    // ─── 自由问答（不影响进度的补课对话）──────────────────────────────────────────

    @Transactional
    public String freeChat(UUID sessionId, UUID userId, String userInput, String inlineApiKey) {
        LearningSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> AppException.notFound("会话不存在"));

        if (!session.getUserId().equals(userId)) throw AppException.forbidden("无权操作此会话");

        Stage stage = stageRepository.findById(session.getStageId())
                .orElseThrow(() -> AppException.notFound("阶段不存在"));

        // 自由问答：记录但不推进节点
        saveMessage(session.getId(), "user", "[补课] " + userInput);

        List<Map<String, String>> messages = buildMessages(session, stage, userId,
                currentNode(session), true);
        // 将用户输入追加（去掉前缀标记）
        messages.add(Map.of("role", "user", "content", userInput));

        String reply = chatService.chat(userId, inlineApiKey, messages);
        saveMessage(session.getId(), "assistant", reply);

        return reply;
    }

    // ─── 内部：构建 LLM 消息列表 ──────────────────────────────────────────────────

    private List<Map<String, String>> buildMessages(LearningSession session, Stage stage,
                                                     UUID userId, String currentNode,
                                                     boolean isFreeChat) {
        UserProfile profile = profileRepository.findByUserId(userId).orElse(null);

        List<Map<String, String>> messages = new ArrayList<>();

        // RAG 上下文 + 难度提示
        String ragContext = ragService.retrieve(userId, currentNode + " " + stage.getTitle(),
                stage.getSkillId(), 3).stream()
                .collect(java.util.stream.Collectors.joining("\n---\n"));
        String difficultyHint = masteryService.getDifficultyHint(userId, stage.getSkillId());

        // System prompt
        messages.add(Map.of("role", "system",
                "content", buildSystemPrompt(stage, profile, currentNode, isFreeChat, ragContext, difficultyHint)));

        // 历史消息（最近 N 条，过滤掉 [补课] 标记的 user 消息，避免混淆节点进度）
        List<SessionMessage> history = messageRepository
                .findBySessionIdOrderByCreatedAt(session.getId());

        // 取最近 MAX_CONTEXT_MESSAGES 条
        int start = Math.max(0, history.size() - MAX_CONTEXT_MESSAGES);
        for (SessionMessage msg : history.subList(start, history.size())) {
            String content = msg.getContent();
            // 跳过 [补课] 标记
            if (content.startsWith("[补课] ")) continue;
            messages.add(Map.of("role", msg.getRole(), "content", content));
        }

        return messages;
    }

    private String buildSystemPrompt(Stage stage, UserProfile profile,
                                      String currentNode, boolean isFreeChat,
                                      String ragContext, String difficultyHint) {
        String background = profile != null ? profile.getBackground() : "技术从业者";
        String learningStyle = profile != null && profile.getLearningStyle() != null
                ? profile.getLearningStyle() : "project";
        String analogyBasis = profile != null && profile.getAnalogyBasis() != null
                ? profile.getAnalogyBasis() : background;

        if (isFreeChat) {
            return """
                你是一位耐心的 AI 编程导师，正在帮助一位学员自由提问补课。
                学员背景：%s（偏好学习方式：%s）
                当前所在阶段：【%s】目标：%s
                请用通俗易懂的语言回答学员的问题，必要时结合 "%s" 的类比来解释。
                回答要简洁，不超过 400 字，不要推进学习进度。
                """.formatted(background, learningStyle, stage.getTitle(),
                        stage.getGoal() != null ? stage.getGoal() : "", analogyBasis);
        }

        String nodeInstruction = switch (currentNode) {
            case "intro"    -> "热情介绍本阶段目标和重要性，提出一个引导性问题了解学员现有认知。";
            case "concept"  -> "根据学员回答，清晰解释核心概念，配合代码示例（用 Markdown）。";
            case "practice" -> "给出 1~2 道小练习题，要求学员口头或用代码回答，然后给出反馈。";
            case "task"     -> """
                布置核心实践任务，明确说明验收标准。
                当学员提交代码后，进行点评并判断是否达标。
                如果达标，回复中必须包含 [PASS] 标记。
                如果不达标，指出需要修改的具体问题，不要给出完整答案。
                """;
            case "review"   -> """
                对学员提交的成果进行综合评审。
                指出优点和改进点，给出具体建议。
                如果整体通过，回复末尾必须包含 [PASS] 标记。
                如果未通过，明确说明需要补充或修改什么。
                """;
            case "retro"    -> "做阶段总结：归纳本阶段学到了什么、与已有知识的连接、下一阶段预告。";
            default         -> "继续与学员交流，帮助推进学习。";
        };

        return ("""
            你是一位专业的 AI 编程导师，正在带领学员完成项目式学习。
            学员背景：%s（偏好学习方式：%s）
            当前阶段：【%s】目标：%s
            当前教学节点：[%s]

            节点任务：%s

            指导原则：
            - 语气亲切、鼓励，避免居高临下
            - 用 Markdown 格式化代码和要点
            - 不要直接给出完整答案，引导学员自己思考
            - 每次回复控制在 600 字以内
            """.formatted(background, learningStyle, stage.getTitle(),
                    stage.getGoal() != null ? stage.getGoal() : "",
                    currentNode, nodeInstruction))
            + (difficultyHint.isBlank() ? "" : "\n" + difficultyHint)
            + ragContext;
    }

    // ─── 内部工具 ──────────────────────────────────────────────────────────────

    private String currentNode(LearningSession session) {
        Map<String, Object> progress = session.getProgress();
        if (progress == null) return "intro";
        return (String) progress.getOrDefault(KEY_NODE, "intro");
    }

    private Object getProgress(LearningSession session, String key) {
        Map<String, Object> p = session.getProgress();
        return p != null ? p.get(key) : null;
    }

    private void setProgress(LearningSession session, String key, Object value) {
        Map<String, Object> progress = new HashMap<>(
                session.getProgress() != null ? session.getProgress() : Map.of());
        progress.put(key, value);
        session.setProgress(progress);
    }

    private String nextNode(String current) {
        int idx = NODE_SEQUENCE.indexOf(current);
        if (idx < 0 || idx >= NODE_SEQUENCE.size() - 1) return "complete";
        return NODE_SEQUENCE.get(idx + 1);
    }

    private void updateNodeProgress(LearningSession session, String nodeKey,
                                     String nodeStatus, boolean awaitsArtifact,
                                     boolean stageComplete) {
        Map<String, Object> progress = new HashMap<>(
                session.getProgress() != null ? session.getProgress() : Map.of());
        progress.put(KEY_NODE, nodeKey);
        progress.put(KEY_NODE_STATUS, nodeStatus);
        progress.put(KEY_AWAITS_ARTIFACT, awaitsArtifact);
        // reset artifact_submitted when entering a new artifact-required node
        if (ARTIFACT_REQUIRED_NODES.contains(nodeKey)) {
            progress.remove(KEY_ARTIFACT_SUBMITTED);
        }
        session.setProgress(progress);
        if (stageComplete) session.setFinishedAt(OffsetDateTime.now());
        sessionRepository.save(session);
    }

    private void saveMessage(UUID sessionId, String role, String content) {
        SessionMessage msg = new SessionMessage();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        messageRepository.save(msg);
    }

    // ─── Result Record ─────────────────────────────────────────────────────────

    /**
     * @param content         LLM 回复内容
     * @param currentNode     推进后的节点 key
     * @param nodeStatus      running / passed / failed
     * @param awaitsArtifact  是否要求提交 artifact（代码/作品）
     * @param stageComplete   本阶段是否全部完成
     */
    public record AdvanceResult(String content, String currentNode, String nodeStatus,
                                boolean awaitsArtifact, boolean stageComplete) {}
}
