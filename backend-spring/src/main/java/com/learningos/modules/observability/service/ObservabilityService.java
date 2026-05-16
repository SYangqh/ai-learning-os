package com.learningos.modules.observability.service;

import com.learningos.infrastructure.trace.TraceIdFilter;
import com.learningos.modules.observability.entity.AuditLog;
import com.learningos.modules.observability.entity.LlmErrorLog;
import com.learningos.modules.observability.entity.TokenUsage;
import com.learningos.modules.observability.repository.AuditLogRepository;
import com.learningos.modules.observability.repository.LlmErrorLogRepository;
import com.learningos.modules.observability.repository.TokenUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * 可观测性服务：异步写入 token_usage、audit_log、llm_error_log。
 * 所有方法均为 @Async，不阻塞主调用链路。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ObservabilityService {

    private final TokenUsageRepository tokenUsageRepository;
    private final AuditLogRepository auditLogRepository;
    private final LlmErrorLogRepository llmErrorLogRepository;

    // ─── Token 消耗统计 ────────────────────────────────────────────────────────

    @Async
    public void recordTokenUsage(UUID userId, UUID sessionId,
                                  String providerKey, String model,
                                  int promptTokens, int completionTokens) {
        try {
            TokenUsage usage = new TokenUsage();
            usage.setUserId(userId);
            usage.setSessionId(sessionId);
            usage.setProviderKey(providerKey);
            usage.setModel(model);
            usage.setPromptTokens(promptTokens);
            usage.setCompletionTokens(completionTokens);
            usage.setTotalTokens(promptTokens + completionTokens);
            usage.setEstimatedCostCny(estimateCost(providerKey, model, promptTokens, completionTokens));
            usage.setTraceId(TraceIdFilter.current());
            tokenUsageRepository.save(usage);
        } catch (Exception e) {
            log.warn("Failed to record token usage: {}", e.getMessage());
        }
    }

    /** 估算 CNY 成本（基于公开价格，仅用于展示，非账单依据） */
    private BigDecimal estimateCost(String providerKey, String model,
                                    int promptTokens, int completionTokens) {
        // 单位：CNY / 1k tokens（prompt + completion）
        // 价格为估算，用于趋势展示
        double pricePerKInput;
        double pricePerKOutput;

        if ("anthropic".equals(providerKey)) {
            // Claude 3.5 Sonnet: $3/$15 per 1M → ≈ ¥0.022/¥0.11 per 1k
            pricePerKInput  = 0.022;
            pricePerKOutput = 0.11;
        } else if ("deepseek".equals(providerKey)) {
            // DeepSeek V3: ¥0.001 / ¥0.002 per 1k
            pricePerKInput  = 0.001;
            pricePerKOutput = 0.002;
        } else if ("alibaba".equals(providerKey)) {
            // Qwen-Plus: ¥0.004 / ¥0.012 per 1k
            pricePerKInput  = 0.004;
            pricePerKOutput = 0.012;
        } else {
            // 其他 / OpenAI GPT-4o 估算: $5/$15 per 1M → ≈ ¥0.037/¥0.11 per 1k
            pricePerKInput  = 0.037;
            pricePerKOutput = 0.11;
        }

        double cost = (promptTokens / 1000.0) * pricePerKInput
                    + (completionTokens / 1000.0) * pricePerKOutput;
        return BigDecimal.valueOf(cost).setScale(6, java.math.RoundingMode.HALF_UP);
    }

    // ─── 操作审计 ──────────────────────────────────────────────────────────────

    @Async
    public void audit(UUID userId, String action,
                      String resourceType, String resourceId,
                      Map<String, Object> detail) {
        try {
            AuditLog log = new AuditLog();
            log.setUserId(userId);
            log.setAction(action);
            log.setResourceType(resourceType);
            log.setResourceId(resourceId);
            log.setDetail(detail);
            log.setTraceId(TraceIdFilter.current());
            auditLogRepository.save(log);
        } catch (Exception e) {
            this.log.warn("Failed to write audit log: {}", e.getMessage());
        }
    }

    // ─── LLM 错误记录 ─────────────────────────────────────────────────────────

    @Async
    public void recordLlmError(UUID userId, UUID sessionId,
                                String providerKey, String model,
                                String errorType, String errorMessage,
                                Map<String, Object> requestSnapshot) {
        try {
            LlmErrorLog err = new LlmErrorLog();
            err.setUserId(userId);
            err.setSessionId(sessionId);
            err.setProviderKey(providerKey);
            err.setModel(model);
            err.setErrorType(errorType);
            err.setErrorMessage(errorMessage);
            err.setRequestSnapshot(requestSnapshot);
            err.setTraceId(TraceIdFilter.current());
            llmErrorLogRepository.save(err);
        } catch (Exception e) {
            log.warn("Failed to write LLM error log: {}", e.getMessage());
        }
    }

    // ─── 查询 ──────────────────────────────────────────────────────────────────

    public record SessionUsageSummary(long totalTokens, double estimatedCostCny, int callCount) {}

    public SessionUsageSummary getSessionSummary(UUID sessionId) {
        var list = tokenUsageRepository.findBySessionIdOrderByCreatedAtDesc(sessionId);
        long total = list.stream().mapToLong(u -> u.getTotalTokens()).sum();
        double cost = list.stream()
                .mapToDouble(u -> u.getEstimatedCostCny() != null ? u.getEstimatedCostCny().doubleValue() : 0.0)
                .sum();
        return new SessionUsageSummary(total, cost, list.size());
    }
}
