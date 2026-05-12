package com.learningos.modules.session.repository;

import com.learningos.modules.session.entity.SessionMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionMessageRepository extends JpaRepository<SessionMessage, UUID> {
    List<SessionMessage> findBySessionIdOrderByCreatedAt(UUID sessionId);
    Optional<SessionMessage> findTopBySessionIdAndRoleOrderByCreatedAtDesc(UUID sessionId, String role);
}
