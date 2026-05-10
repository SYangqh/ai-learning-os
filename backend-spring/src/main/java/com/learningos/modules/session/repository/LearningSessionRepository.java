package com.learningos.modules.session.repository;

import com.learningos.modules.session.entity.LearningSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LearningSessionRepository extends JpaRepository<LearningSession, UUID> {
    List<LearningSession> findByUserIdOrderByStartedAtDesc(UUID userId);
    Optional<LearningSession> findTopByStageIdAndUserIdAndFinishedAtIsNull(UUID stageId, UUID userId);
}
