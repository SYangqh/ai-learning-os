package com.learningos.modules.observability.repository;

import com.learningos.modules.observability.entity.LlmErrorLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LlmErrorLogRepository extends JpaRepository<LlmErrorLog, UUID> {
}
