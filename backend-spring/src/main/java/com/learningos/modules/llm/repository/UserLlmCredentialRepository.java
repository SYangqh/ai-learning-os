package com.learningos.modules.llm.repository;

import com.learningos.modules.llm.entity.UserLlmCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserLlmCredentialRepository extends JpaRepository<UserLlmCredential, UUID> {

    List<UserLlmCredential> findByUserIdAndRevokedAtIsNull(UUID userId);

    Optional<UserLlmCredential> findByUserIdAndProviderKeyAndRevokedAtIsNull(UUID userId, String providerKey);

    /** 直接执行 UPDATE，避免 JPA flush 延迟导致与后续 INSERT 的唯一索引冲突 */
    @Modifying
    @Query("UPDATE UserLlmCredential c SET c.revokedAt = :now WHERE c.userId = :userId AND c.providerKey = :providerKey AND c.revokedAt IS NULL")
    int revokeByUserAndProvider(@Param("userId") UUID userId, @Param("providerKey") String providerKey, @Param("now") OffsetDateTime now);
}
