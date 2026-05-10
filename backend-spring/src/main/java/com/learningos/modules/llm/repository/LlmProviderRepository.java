package com.learningos.modules.llm.repository;

import com.learningos.modules.llm.entity.LlmProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LlmProviderRepository extends JpaRepository<LlmProvider, UUID> {
    List<LlmProvider> findByEnabledTrue();
    Optional<LlmProvider> findByKey(String key);
}
