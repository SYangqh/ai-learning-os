package com.learningos.modules.llm.repository;

import com.learningos.modules.llm.entity.UserLlmCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserLlmCredentialRepository extends JpaRepository<UserLlmCredential, UUID> {

    List<UserLlmCredential> findByUserIdAndRevokedAtIsNull(UUID userId);

    Optional<UserLlmCredential> findByUserIdAndProviderKeyAndRevokedAtIsNull(UUID userId, String providerKey);
}
