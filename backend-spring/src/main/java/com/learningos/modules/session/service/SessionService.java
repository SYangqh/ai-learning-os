package com.learningos.modules.session.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learningos.common.exception.AppException;
import com.learningos.modules.artifact.entity.Artifact;
import com.learningos.modules.artifact.repository.ArtifactRepository;
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
import java.util.stream.Collectors;

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

    /**
     * 这些节点由 AI 通过 [ADVANCE] 标记控制推进。
     * 若 AI 回复中未包含 [ADVANCE]，节点停留在当前，允许多轮对话直到学员真正理解。
     * intro 仍然自动推进（仅一轮引导），task/review 有各自的独立门控。
     */
    private static final Set<String> AI_ADVANCE_CONTROLLED_NODES = Set.of("concept", "practice", "retro");

    /** progress jsonb 里存储用的 key 名 */
    private static final String KEY_NODE          = "current_node";
    private static final String KEY_NODE_STATUS   = "node_status";   // pending/running/passed/failed
    private static final String KEY_AWAITS_ARTIFACT = "awaits_artifact";
    private static final String KEY_REVIEW_PASSED = "review_passed";
    private static final String KEY_ARTIFACT_SUBMITTED = "artifact_submitted";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final LearningSessionRepository sessionRepository;
    private final SessionMessageRepository messageRepository;
    private final StageRepository stageRepository;
    private final UserProfileRepository profileRepository;
    private final DynamicChatService chatService;
    private final RagService ragService;
    private final MasteryService masteryService;
    private final ArtifactRepository artifactRepository;
    private final SkillRubricLoader skillRubricLoader;
    private final MemoryService memoryService;

    // ─── 推进式对话（按节点顺序推进学习阶段）────────────────────────────────────────

    /**
     * 私有上下文 Record，用于在事务外把 prepareAdvance 的结果传给 persistReply。
     * earlyReturn 非 null 时表示门控短路（TASK 节点缺少 artifact），跳过 LLM 调用。
     */
    private record AdvanceContext(
            String currentNode,
            String stageSkillId,
            String artifactType,
            List<Map<String, String>> llmMessages,
            AdvanceResult earlyReturn) {}

    private record FreeChatContext(List<Map<String, String>> llmMessages) {}

    // 注意：不加 @Transactional，AI HTTP 调用不能包在事务里
    // 事务拆分：prepareAdvance()（验证+写用户消息）→ LLM → persistReply()（写回复+推进节点）
    public AdvanceResult advance(UUID sessionId, UUID userId, String userInput,
                                 String code, String inlineApiKey) {
        AdvanceContext ctx = prepareAdvance(sessionId, userId, userInput, code);
        if (ctx.earlyReturn() != null) return ctx.earlyReturn();

        String reply = chatService.chat(userId, inlineApiKey, ctx.llmMessages());
        return persistReply(sessionId, userId, reply, ctx.currentNode(), ctx.stageSkillId(), ctx.artifactType());
    }

    @Transactional
    protected AdvanceContext prepareAdvance(UUID sessionId, UUID userId,
                                            String userInput, String code) {
        LearningSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> AppException.notFound("会话不存在"));

        if (!session.getUserId().equals(userId)) throw AppException.forbidden("无权操作此会话");
        if (session.getFinishedAt() != null) throw AppException.badRequest("该学习阶段已完成");

        Stage stage = stageRepository.findById(session.getStageId())
                .orElseThrow(() -> AppException.notFound("阶段不存在"));

        String currentNode = currentNode(session);
        String artifactType = skillRubricLoader.loadArtifactType(stage.getSkillId(), stage.getStageIndex());

        // ── TASK 节点门控：NONE 类型直接跳过，其他类型必须先提交 artifact ─────────────
        if (ARTIFACT_REQUIRED_NODES.contains(currentNode) && !"NONE".equals(artifactType)) {
            boolean alreadySubmitted = Boolean.TRUE.equals(getProgress(session, KEY_ARTIFACT_SUBMITTED))
                    || artifactRepository.existsBySessionIdAndUserId(sessionId, userId);

            if (code != null && !code.isBlank() && !alreadySubmitted && "CODE".equals(artifactType)) {
                // 兼容旧前端：通过 advance 直接传代码时，自动持久化为 Artifact 记录
                Artifact artifact = new Artifact();
                artifact.setSessionId(sessionId);
                artifact.setStageId(session.getStageId());
                artifact.setUserId(userId);
                artifact.setNodeKey(currentNode);
                artifact.setType("CODE");
                artifact.setContent(code);
                artifactRepository.save(artifact);
                alreadySubmitted = true;
            }

            if (!alreadySubmitted) {
                return new AdvanceContext(currentNode, stage.getSkillId(), artifactType, null,
                        new AdvanceResult("请先提交你的作品，才能进入评审阶段。",
                                currentNode, "running", true, false, null, artifactType));
            }
            // 标记已提交（本事务写入）
            setProgress(session, KEY_ARTIFACT_SUBMITTED, true);
            sessionRepository.save(session);
        }

        // 保存用户消息（本事务写入）
        saveMessage(session.getId(), "user", userInput);
        if (code != null && !code.isBlank()) {
            saveMessage(session.getId(), "user", "```\n" + code + "\n```");
        }

        // 构建 LLM 上下文（读取历史、RAG、掌握度）
        List<Map<String, String>> messages = buildMessages(session, stage, userId, currentNode, false);
        return new AdvanceContext(currentNode, stage.getSkillId(), artifactType, messages, null);
    }

    @Transactional
    protected AdvanceResult persistReply(UUID sessionId, UUID userId, String reply,
                                         String currentNode, String stageSkillId, String artifactType) {
        LearningSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> AppException.notFound("会话不存在"));
        Stage stage = stageRepository.findById(session.getStageId())
                .orElseThrow(() -> AppException.notFound("阶段不存在"));

        // ── 解析 Rubric JSON（REVIEW 节点专用），提取用户可见的回复内容 ────────────
        RubricResult rubricResult = parseRubricJson(reply);
        String userReply = extractUserReply(reply);

        // ── 检测并剥离 [ADVANCE] 标记（仅用于内部逻辑，不展示给用户） ──────────────
        boolean aiRequestedAdvance = userReply.contains("[ADVANCE]");
        if (aiRequestedAdvance) {
            userReply = userReply.replace("[ADVANCE]", "").replaceAll("\\n{3,}", "\n\n").trim();
        }

        saveMessage(sessionId, "assistant", userReply);

        // ── REVIEW 节点：优先用 Rubric JSON 判断，fallback 关键词匹配 ─────────────
        if ("review".equals(currentNode)) {
            boolean reviewPassed = rubricResult != null
                    ? rubricResult.passed()
                    : (userReply.contains("[PASS]") || userReply.contains("[通过]"));
            setProgress(session, KEY_REVIEW_PASSED, reviewPassed);
            if (!reviewPassed) {
                setProgress(session, KEY_NODE_STATUS, "failed");
                sessionRepository.save(session);
                if (stageSkillId != null) {
                    masteryService.recordStageResult(userId, stageSkillId, false);
                }
                // 标记产出需要修改
                artifactRepository.updateStatusBySession(sessionId, userId, "needs_revision");
                // ── Phase 5：REVIEW 未通过 → 异步写 REVIEW_FAIL 记忆 ─────────────
                memoryService.remember(userId,
                        buildReviewFailSummary(rubricResult, userReply),
                        "REVIEW_FAIL", session.getStageId(), stageSkillId);
                log.debug("Session {} review not passed, staying at review", sessionId);
                return new AdvanceResult(userReply, "review", "failed", true, false, rubricResult, artifactType);
            }
            // 评审通过，标记产出为 passed
            artifactRepository.updateStatusBySession(sessionId, userId, "passed");
            // ── Phase 5：评审通过 → 异步把 Artifact 内容写入长期记忆 ──────────────
            artifactRepository.findTopBySessionIdAndUserIdOrderByCreatedAtDesc(sessionId, userId)
                    .ifPresent(artifact -> memoryService.remember(userId,
                            buildArtifactSummary(artifact, stage),
                            "ARTIFACT", session.getStageId(), stageSkillId));
        }

        // ── AI 控制推进节点（concept/practice/retro）：未含 [ADVANCE] 则停留 ────────
        if (AI_ADVANCE_CONTROLLED_NODES.contains(currentNode) && !aiRequestedAdvance) {
            sessionRepository.save(session);
            return new AdvanceResult(userReply, currentNode, "running",
                    ARTIFACT_REQUIRED_NODES.contains(currentNode) && !"NONE".equals(artifactType), false, null, artifactType);
        }

        // 推进节点
        String nextNode = nextNode(currentNode);
        boolean stageComplete = "complete".equals(nextNode);

        if (stageComplete && stageSkillId != null) {
            masteryService.recordStageResult(userId, stageSkillId, true);
        }

        updateNodeProgress(session, stageComplete ? "complete" : nextNode,
                stageComplete ? "passed" : "running",
                false, stageComplete);

        // ── stage 完成：标记当前 stage completed + 解锁下一 stage ────────────────
        if (stageComplete) {
            unlockNextStage(session.getStageId());
            // ── Phase 5：RETRO 完成 → 异步写长期记忆 ────────────────────────────
            writeRetroMemory(userId, sessionId, session.getStageId(), stageSkillId, userReply);
        }

        log.debug("Session {} advanced: {} -> {}", sessionId, currentNode, nextNode);
        String nextArtifactType = skillRubricLoader.loadArtifactType(stage.getSkillId(), stage.getStageIndex());
        return new AdvanceResult(userReply, stageComplete ? "complete" : nextNode,
                "running", ARTIFACT_REQUIRED_NODES.contains(nextNode) && !"NONE".equals(nextArtifactType),
                stageComplete, rubricResult, nextArtifactType);
    }

    // ─── 自由问答（不影响进度的补课对话）──────────────────────────────────────────

    // 注意：不加 @Transactional，AI HTTP 调用不能包在事务里
    public String freeChat(UUID sessionId, UUID userId, String userInput, String inlineApiKey) {
        FreeChatContext ctx = prepareFreeChat(sessionId, userId, userInput);
        String reply = chatService.chat(userId, inlineApiKey, ctx.llmMessages());
        saveChatReply(sessionId, reply);
        return reply;
    }

    @Transactional
    protected FreeChatContext prepareFreeChat(UUID sessionId, UUID userId, String userInput) {
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
        return new FreeChatContext(messages);
    }

    @Transactional
    protected void saveChatReply(UUID sessionId, String reply) {
        saveMessage(sessionId, "assistant", reply);
    }

    // ─── 内部：构建 LLM 消息列表 ──────────────────────────────────────────────────

    private List<Map<String, String>> buildMessages(LearningSession session, Stage stage,
                                                     UUID userId, String currentNode,
                                                     boolean isFreeChat) {
        UserProfile profile = profileRepository.findByUserId(userId).orElse(null);

        List<Map<String, String>> messages = new ArrayList<>();

        // RAG 上下文 + 难度提示 + 长期记忆
        String ragContext = ragService.retrieve(userId, currentNode + " " + stage.getTitle(),
                stage.getSkillId(), 3).stream()
                .collect(java.util.stream.Collectors.joining("\n---\n"));
        String memoryContext = memoryService.recall(userId,
                currentNode + " " + stage.getTitle(), stage.getSkillId(), 2);
        String difficultyHint = masteryService.getDifficultyHint(userId, stage.getSkillId());

        // System prompt（含 RAG + 长期记忆 + 难度提示）
        messages.add(Map.of("role", "system",
                "content", buildSystemPrompt(stage, profile, currentNode, isFreeChat,
                        ragContext, memoryContext, difficultyHint)));

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
                                      String ragContext, String memoryContext,
                                      String difficultyHint) {
        String background = profile != null ? profile.getBackground() : "技术从业者";
        String learningStyle = profile != null && profile.getLearningStyle() != null
                ? profile.getLearningStyle() : "project";
        String analogyBasis = profile != null && profile.getAnalogyBasis() != null
                ? profile.getAnalogyBasis() : background;

        // ── Phase 4：从 Skill YAML 加载背景感知类比 ──────────────────────────────
        String skillId = stage.getSkillId();
        Map<String, String> analogies = skillRubricLoader.loadAnalogies(skillId, analogyBasis);
        String analogySection = buildAnalogySection(analogies);

        if (isFreeChat) {
            return """
                你是一位耐心的 AI 编程导师，正在帮助一位学员自由提问补课。
                学员背景：%s（偏好学习方式：%s）
                当前所在阶段：【%s】目标：%s
                请用通俗易懂的语言回答学员的问题，必要时结合 "%s" 的类比来解释。
                回答要简洁，不超过 400 字，不要推进学习进度。
                """.formatted(background, learningStyle, stage.getTitle(),
                        stage.getGoal() != null ? stage.getGoal() : "", analogyBasis)
                + analogySection
                + buildMemoryBridgeSection(memoryContext);
        }

        // ── Phase 4：TASK 节点优先使用 YAML task_description ────────────────────
        String taskNodeInstruction;
        SkillRubricLoader.StageData stageData = skillRubricLoader.loadStageData(skillId, stage.getStageIndex());
        if (stageData.hasTaskDescription()) {
            taskNodeInstruction = """
                布置以下核心实践任务，明确说明验收标准并鼓励学员动手：

                %s

                当学员提交代码后，进行点评并判断是否达标。
                如果达标，回复中必须包含 [PASS] 标记。
                如果不达标，指出需要修改的具体问题，不要给出完整答案。
                """.formatted(stageData.taskDescription().strip());
        } else {
            taskNodeInstruction = """
                布置核心实践任务，明确说明验收标准。
                当学员提交代码后，进行点评并判断是否达标。
                如果达标，回复中必须包含 [PASS] 标记。
                如果不达标，指出需要修改的具体问题，不要给出完整答案。
                """;
        }

        String nodeInstruction = switch (currentNode) {
            case "intro"    -> "热情介绍本阶段目标和重要性，提出一个引导性问题了解学员现有认知。学员回答引导问题（哪怕说不知道）后，在回复末尾另起一行加上 [ADVANCE]，进入概念讲解节点。";
            case "concept"  -> """
                根据学员回答，清晰解释核心概念，配合代码示例（用 Markdown）。
                当你完整讲解了核心概念、且判断学员具备了基本理解（能复述、能举例、或提出有意义的问题），\
                在回复末尾另起一行加上 [ADVANCE]，进入练习节点。
                若学员说"不知道"或仍有明显疑问，继续讲解，换一种更直观的方式解释，不要加 [ADVANCE]。
                """;
            case "practice" -> """
                给出 1~2 道小练习题，要求学员口头或用代码回答，然后给出反馈。
                当且仅当学员回答基本正确或展示出对概念的理解，在回复末尾另起一行加上 [ADVANCE]，进入任务节点。
                若学员说"不知道"或回答不正确，继续引导，换一种方式提问或给更多提示，不要加 [ADVANCE]，不要直接给出完整答案。
                """;
            case "task"     -> taskNodeInstruction;
            case "review"   -> buildReviewInstruction(stage.getSkillId(), stage.getStageIndex());
            case "retro"    -> "做阶段总结：归纳本阶段学到了什么、与已有知识的连接、下一阶段预告。总结完成后，在回复末尾另起一行加上 [ADVANCE]，完成本阶段学习。";
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
            + analogySection
            + (difficultyHint.isBlank() ? "" : "\n" + difficultyHint)
            + ragContext
            + buildMemoryBridgeSection(memoryContext);
    }

    /** 将 analogy map 格式化为 Prompt 注入片段 */
    private String buildAnalogySection(Map<String, String> analogies) {
        if (analogies.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(
                "\n【背景感知类比参考】请优先使用以下类比来解释相关概念，让学员更容易理解：\n");
        analogies.forEach((concept, analogy) -> sb.append("- ").append(analogy).append("\n"));
        return sb.toString();
    }

    /**
     * 将历史记忆转化为"已知→未知"类比桥接指令。
     * 当学员有历史学习记忆时，显式要求 AI 用已知内容类比当前新概念。
     */
    private String buildMemoryBridgeSection(String memoryContext) {
        if (memoryContext == null || memoryContext.isBlank()) return "";
        return """

            【已学内容·类比桥接】
            以下是该学员在此前学习阶段积累的内容（包括掌握的概念、提交的代码等）。
            在解释当前节点的新概念时，请主动从下方内容中找出相关的"已知知识点"，
            用"你之前学过/写过 X，现在的 Y 和它类似，区别在于…"的方式帮助学员建立新旧知识连接。
            如果找不到相关联系，可以忽略此部分，不必强行类比。

            """ + memoryContext;
    }

    // ─── Phase 5：长期记忆写入 ─────────────────────────────────────────────────

    /**
     * RETRO 节点完成时，将 AI 总结回复异步写入长期记忆。
     * 取最近几条 assistant 消息拼合成摘要（避免只取最后一条片段）。
     */
    private void writeRetroMemory(UUID userId, UUID sessionId, UUID stageId,
                                   String skillId, String lastReply) {
        // 拼合 RETRO 节点最近 3 条 assistant 消息作为复盘摘要
        List<SessionMessage> history = messageRepository.findBySessionIdOrderByCreatedAt(sessionId);
        String summary = history.stream()
                .filter(m -> "assistant".equals(m.getRole()))
                .skip(Math.max(0, history.stream().filter(m -> "assistant".equals(m.getRole())).count() - 3))
                .map(SessionMessage::getContent)
                .collect(Collectors.joining("\n"));
        if (summary.isBlank()) summary = lastReply;

        memoryService.remember(userId, summary, "RETRO", stageId, skillId);
    }

    /**
     * 构建 REVIEW_FAIL 记忆内容：包含评审反馈 + hints（如有）。
     */
    private String buildReviewFailSummary(RubricResult rubric, String replyText) {
        StringBuilder sb = new StringBuilder("【评审未通过】");
        if (rubric != null) {
            if (rubric.feedback() != null) sb.append(rubric.feedback());
            if (rubric.hints() != null && !rubric.hints().isEmpty()) {
                sb.append("\n改进建议：");
                rubric.hints().forEach(h -> sb.append("\n- ").append(h));
            }
        } else {
            // 无结构化 rubric，直接取 AI 回复文本（截 300 字）
            String text = replyText != null ? replyText : "";
            sb.append(text.length() > 300 ? text.substring(0, 300) : text);
        }
        return sb.toString();
    }

    /**
     * 构建 ARTIFACT 记忆内容：记录阶段名称 + 产出类型 + 代码内容摘要。
     */
    private String buildArtifactSummary(Artifact artifact, Stage stage) {
        String content = artifact.getContent() != null ? artifact.getContent() : "";
        // 代码超过 800 字时截断，保留关键信息
        String snippet = content.length() > 800 ? content.substring(0, 800) + "\n...(截断)" : content;
        return String.format("【阶段产出·%s·%s节点】\n%s",
                stage.getTitle(), artifact.getNodeKey(), snippet);
    }

    // ─── Rubric 工具 ───────────────────────────────────────────────────────────

    /** 为 REVIEW 节点生成带 Rubric 标准 + 结构化输出指令的 prompt 片段 */
    private String buildReviewInstruction(String skillId, int stageIndex) {
        SkillRubricLoader.RubricCriteria rubric = skillRubricLoader.load(skillId, stageIndex);
        StringBuilder sb = new StringBuilder();
        sb.append("对学员提交的成果进行结构化 Rubric 评审。\n");
        if (!rubric.passCriteria().isEmpty()) {
            sb.append("\n通过标准：\n");
            rubric.passCriteria().forEach(c -> sb.append("- ").append(c).append("\n"));
        }
        if (!rubric.failHints().isEmpty()) {
            sb.append("\n常见问题提示：\n");
            rubric.failHints().forEach(h -> sb.append("- ").append(h).append("\n"));
        }
        sb.append("""

            请严格按以下格式输出（不要修改格式，不要省略第一行）：
            第一行：RUBRIC_JSON::{"passed":true或false,"score":0到100的整数,"feedback":"一句话总结","hints":["改进建议1"]}
            第二行：---
            之后：用中文给学员写详细的评审反馈（鼓励为主，清晰指出问题，不超过 500 字）。
            """);
        return sb.toString();
    }

    /** 从 LLM 原始回复中解析 RUBRIC_JSON:: 行，失败时返回 null */
    private RubricResult parseRubricJson(String rawReply) {
        if (rawReply == null) return null;
        return rawReply.lines()
                .filter(l -> l.startsWith("RUBRIC_JSON::"))
                .findFirst()
                .map(l -> l.substring("RUBRIC_JSON::".length()).trim())
                .map(json -> {
                    try {
                        return OBJECT_MAPPER.readValue(json, RubricResult.class);
                    } catch (Exception e) {
                        log.warn("Failed to parse RUBRIC_JSON: {}", e.getMessage());
                        return null;
                    }
                })
                .orElse(null);
    }

    /** 提取用户可见部分：--- 之后的内容；若无分隔线则去掉 RUBRIC_JSON:: 行 */
    private String extractUserReply(String rawReply) {
        if (rawReply == null) return "";
        // 找 "---" 分隔行
        int idx = rawReply.indexOf("\n---\n");
        if (idx >= 0) return rawReply.substring(idx + 5).trim();
        idx = rawReply.indexOf("---\n");
        if (idx == 0) return rawReply.substring(4).trim();
        // 无分隔线：去掉 RUBRIC_JSON:: 行，保留其余内容
        return rawReply.lines()
                .filter(l -> !l.startsWith("RUBRIC_JSON::"))
                .collect(Collectors.joining("\n"))
                .trim();
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
     * @param awaitsArtifact  是否要求提交 artifact
     * @param stageComplete   本阶段是否全部完成
     * @param artifactType    当前节点期望的产出类型（CODE/NOTE/DIAGRAM/ESSAY/PROOF/NONE）
     */
    public record AdvanceResult(String content, String currentNode, String nodeStatus,
                                boolean awaitsArtifact, boolean stageComplete,
                                RubricResult rubricResult, String artifactType) {}

    // ─── Stage 解锁 ────────────────────────────────────────────────────────────

    /**
     * 当前 stage 完成时：
     * 1. 将当前 stage 状态更新为 completed
     * 2. 找到同一 path 中 stageIndex+1 的 stage，更新为 active
     */
    @Transactional
    protected void unlockNextStage(UUID currentStageId) {
        stageRepository.findById(currentStageId).ifPresent(current -> {
            current.setStatus("completed");
            stageRepository.save(current);

            stageRepository.findByPathIdOrderByStageIndex(current.getPathId())
                    .stream()
                    .filter(s -> s.getStageIndex() == current.getStageIndex() + 1)
                    .findFirst()
                    .ifPresent(next -> {
                        next.setStatus("active");
                        stageRepository.save(next);
                        log.info("Stage {} unlocked (path={}, index={})",
                                next.getId(), next.getPathId(), next.getStageIndex());
                    });
        });
    }

    // ─── 重新生成最后一条 AI 回复 ─────────────────────────────────────────────

    /** 上下文 record，供 regenerateLast 使用 */
    record RegenerateContext(List<Map<String, String>> llmMessages) {}

    /**
     * 删除最后一条 assistant 消息并重新生成。
     * 节点状态不变，只替换消息内容。
     */
    // 注意：不加 @Transactional，AI HTTP 调用不能包在事务里
    public String regenerateLast(UUID sessionId, UUID userId, String inlineApiKey) {
        RegenerateContext ctx = prepareRegenerate(sessionId, userId);
        String reply = chatService.chat(userId, inlineApiKey, ctx.llmMessages());
        saveChatReply(sessionId, reply);
        return reply;
    }

    @Transactional
    protected RegenerateContext prepareRegenerate(UUID sessionId, UUID userId) {
        LearningSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> AppException.notFound("会话不存在"));
        if (!session.getUserId().equals(userId)) throw AppException.forbidden("无权操作此会话");

        Stage stage = stageRepository.findById(session.getStageId())
                .orElseThrow(() -> AppException.notFound("阶段不存在"));

        // 删除最后一条 assistant 消息（如有）
        messageRepository.findTopBySessionIdAndRoleOrderByCreatedAtDesc(sessionId, "assistant")
                .ifPresent(msg -> messageRepository.deleteById(msg.getId()));

        // 用删除后的历史重新构建消息列表
        List<Map<String, String>> messages = buildMessages(session, stage, userId,
                currentNode(session), false);
        return new RegenerateContext(messages);
    }
}

