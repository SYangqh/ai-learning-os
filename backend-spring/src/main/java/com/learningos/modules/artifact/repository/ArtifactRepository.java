package com.learningos.modules.artifact.repository;

import com.learningos.modules.artifact.entity.Artifact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArtifactRepository extends JpaRepository<Artifact, UUID> {

    List<Artifact> findBySessionIdAndUserIdOrderByCreatedAtDesc(UUID sessionId, UUID userId);

    List<Artifact> findByStageIdAndUserIdOrderByCreatedAtDesc(UUID stageId, UUID userId);

    boolean existsBySessionIdAndUserId(UUID sessionId, UUID userId);

    Optional<Artifact> findTopBySessionIdAndUserIdOrderByCreatedAtDesc(UUID sessionId, UUID userId);

    @Modifying
    @Transactional
    @Query("UPDATE Artifact a SET a.status = :status WHERE a.sessionId = :sessionId AND a.userId = :userId")
    void updateStatusBySession(@Param("sessionId") UUID sessionId,
                               @Param("userId") UUID userId,
                               @Param("status") String status);
}
