package com.learningos.modules.path.repository;

import com.learningos.modules.path.entity.Stage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StageRepository extends JpaRepository<Stage, UUID> {
    List<Stage> findByPathIdOrderByStageIndex(UUID pathId);
}
