package com.learningos.modules.observability.repository;

import com.learningos.modules.observability.entity.TokenUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TokenUsageRepository extends JpaRepository<TokenUsage, UUID> {

    List<TokenUsage> findBySessionIdOrderByCreatedAtDesc(UUID sessionId);

    @Query("""
            SELECT COALESCE(SUM(t.totalTokens), 0)
            FROM TokenUsage t WHERE t.sessionId = :sessionId
            """)
    long sumTotalTokensBySession(@Param("sessionId") UUID sessionId);

    @Query("""
            SELECT COALESCE(SUM(t.totalTokens), 0)
            FROM TokenUsage t WHERE t.userId = :userId
            """)
    long sumTotalTokensByUser(@Param("userId") UUID userId);
}
