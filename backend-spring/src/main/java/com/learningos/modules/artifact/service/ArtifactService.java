package com.learningos.modules.artifact.service;

import com.learningos.common.exception.AppException;
import com.learningos.modules.artifact.entity.Artifact;
import com.learningos.modules.artifact.repository.ArtifactRepository;
import com.learningos.modules.observability.service.ObservabilityService;
import com.learningos.modules.session.entity.LearningSession;
import com.learningos.modules.session.repository.LearningSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArtifactService {

    private static final Set<String> VALID_TYPES = Set.of("CODE", "NOTE", "DIAGRAM", "ESSAY", "PROOF");

    private final ArtifactRepository artifactRepository;
    private final LearningSessionRepository sessionRepository;
    private final ObservabilityService observabilityService;

    /**
     * 提交学习产出（CODE / NOTE），同时在 session progress 中标记 artifact_submitted=true。
     * 同一个 session 可多次提交（重新提交），新记录追加，旧记录保留。
     */
    @Transactional
    public Artifact submit(UUID userId, UUID sessionId, String type, String content) {
        LearningSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> AppException.notFound("会话不存在"));
        if (!session.getUserId().equals(userId)) {
            throw AppException.forbidden("无权操作此会话");
        }
        if (session.getFinishedAt() != null) {
            throw AppException.badRequest("该学习阶段已完成，无法继续提交");
        }

        String validType = type.toUpperCase();
        if (!VALID_TYPES.contains(validType)) {
            throw AppException.badRequest("不支持的产出类型，允许：CODE / NOTE / DIAGRAM / ESSAY / PROOF");
        }

        String currentNode = getCurrentNode(session);

        Artifact artifact = new Artifact();
        artifact.setSessionId(sessionId);
        artifact.setStageId(session.getStageId());
        artifact.setUserId(userId);
        artifact.setNodeKey(currentNode);
        artifact.setType(validType);
        artifact.setContent(content);
        artifact = artifactRepository.save(artifact);

        // 标记 session progress：已提交 artifact
        Map<String, Object> progress = new HashMap<>(
                session.getProgress() != null ? session.getProgress() : Map.of());
        progress.put("artifact_submitted", true);
        session.setProgress(progress);
        sessionRepository.save(session);

        log.info("Artifact submitted: id={} type={} session={} node={}", artifact.getId(), validType, sessionId, currentNode);
        observabilityService.audit(userId, "ARTIFACT_SUBMIT", "ARTIFACT", artifact.getId().toString(),
                Map.of("type", validType, "session", sessionId.toString(), "node", currentNode));
        return artifact;
    }

    public List<Artifact> listBySession(UUID userId, UUID sessionId) {
        // 简单鉴权：不存在的 session 直接返回空列表
        return artifactRepository.findBySessionIdAndUserIdOrderByCreatedAtDesc(sessionId, userId);
    }

    private String getCurrentNode(LearningSession session) {
        Map<String, Object> progress = session.getProgress();
        if (progress == null) return "task";
        return (String) progress.getOrDefault("current_node", "task");
    }
}
