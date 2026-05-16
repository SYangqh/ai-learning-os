package com.learningos.modules.session.repository;

import com.learningos.modules.session.entity.LearningSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LearningSessionRepository extends JpaRepository<LearningSession, UUID> {
    List<LearningSession> findByUserIdOrderByStartedAtDesc(UUID userId);
    Optional<LearningSession> findTopByStageIdAndUserIdAndFinishedAtIsNull(UUID stageId, UUID userId);
    /** 不管是否完成，取最近一次会话（用于查看已完成阶段历史） */
    Optional<LearningSession> findTopByStageIdAndUserIdOrderByStartedAtDesc(UUID stageId, UUID userId);

    List<LearningSession> findByStageIdOrderByStartedAtDesc(UUID stageId);
}
