package com.learningos.modules.llm.repository;

import com.learningos.modules.llm.entity.LlmModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LlmModelRepository extends JpaRepository<LlmModel, UUID> {
    List<LlmModel> findByTaskAndEnabledTrue(String task);
    List<LlmModel> findByProviderKeyAndTaskAndEnabledTrue(String providerKey, String task);
}
