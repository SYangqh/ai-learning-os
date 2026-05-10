package com.learningos.modules.path.repository;

import com.learningos.modules.path.entity.LearningPath;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LearningPathRepository extends JpaRepository<LearningPath, UUID> {
    List<LearningPath> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
