package com.learningos.modules.llm.controller;

import com.learningos.common.Result;
import com.learningos.modules.llm.entity.LlmModel;
import com.learningos.modules.llm.entity.LlmProvider;
import com.learningos.modules.llm.entity.UserLlmPreference;
import com.learningos.modules.llm.repository.LlmModelRepository;
import com.learningos.modules.llm.repository.LlmProviderRepository;
import com.learningos.modules.llm.repository.UserLlmPreferenceRepository;
import com.learningos.modules.llm.service.LlmCredentialService;
import com.learningos.modules.llm.service.LlmCredentialService.CredentialSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
@Tag(name = "LLM", description = "大模型 Provider / 凭据 / 偏好管理")
@SecurityRequirement(name = "bearerAuth")
public class LlmController {

    private final LlmProviderRepository providerRepository;
    private final LlmModelRepository modelRepository;
    private final LlmCredentialService credentialService;
    private final UserLlmPreferenceRepository preferenceRepository;

    // ─── Provider & Model 目录 ─────────────────────────────────────────────────

    @GetMapping("/providers")
    @Operation(summary = "获取可用的 LLM Provider 列表")
    public Result<List<Map<String, Object>>> providers() {
        List<Map<String, Object>> list = providerRepository.findByEnabledTrue().stream()
                .map(p -> Map.<String, Object>of(
                    "key", p.getKey(),
                    "display_name", p.getDisplayName(),
                    "type", p.getType(),
                    "supports_stream", p.isSupportsStream(),
                    "supports_embeddings", p.isSupportsEmbeddings()
                ))
                .toList();
        return Result.ok(list);
    }

    @GetMapping("/models")
    @Operation(summary = "获取模型目录")
    public Result<List<Map<String, Object>>> models(@RequestParam(defaultValue = "chat") String task) {
        List<Map<String, Object>> list = modelRepository.findByTaskAndEnabledTrue(task).stream()
                .map(m -> Map.<String, Object>of(
                    "provider_key", m.getProviderKey(),
                    "model_name", m.getModelName(),
                    "task", m.getTask(),
                    "context_window", m.getContextWindow() != null ? m.getContextWindow() : 0
                ))
                .toList();
        return Result.ok(list);
    }

    // ─── 凭据管理 ─────────────────────────────────────────────────────────────

    @PutMapping("/credentials")
    @Operation(summary = "新增或更新 API Key（upsert）")
    public Result<Map<String, Object>> upsertCredential(@Valid @RequestBody UpsertCredentialRequest req,
                                                        @AuthenticationPrincipal UUID userId) {
        var cred = credentialService.upsert(userId, req.providerKey(), req.apiKey());
        return Result.ok(Map.of("credential_id", cred.getId()));
    }

    @GetMapping("/credentials")
    @Operation(summary = "列出已配置的凭据（脱敏展示）")
    public Result<List<CredentialSummary>> listCredentials(@AuthenticationPrincipal UUID userId) {
        return Result.ok(credentialService.list(userId));
    }

    @DeleteMapping("/credentials/{id}")
    @Operation(summary = "撤销凭据（软删除）")
    public Result<Map<String, String>> revokeCredential(@PathVariable UUID id,
                                                        @AuthenticationPrincipal UUID userId) {
        credentialService.revoke(id, userId);
        return Result.ok(Map.of("message", "凭据已撤销"));
    }

    // ─── 偏好设置 ─────────────────────────────────────────────────────────────

    @PutMapping("/preferences")
    @Operation(summary = "设置默认 provider / model 偏好")
    public Result<Map<String, String>> setPreferences(@Valid @RequestBody PreferenceRequest req,
                                                      @AuthenticationPrincipal UUID userId) {
        UserLlmPreference pref = preferenceRepository.findById(userId)
                .orElse(new UserLlmPreference());
        pref.setUserId(userId);
        pref.setChatProviderKey(req.chatProviderKey());
        pref.setChatModelName(req.chatModelName());
        pref.setEmbeddingProviderKey(req.embeddingProviderKey());
        pref.setEmbeddingModelName(req.embeddingModelName());
        pref.setUpdatedAt(OffsetDateTime.now());
        preferenceRepository.save(pref);
        return Result.ok(Map.of("message", "偏好已保存"));
    }

    // ─── Request Records ──────────────────────────────────────────────────────

    record UpsertCredentialRequest(
            @NotBlank String providerKey,
            @NotBlank String apiKey
    ) {}

    record PreferenceRequest(
            String chatProviderKey,
            String chatModelName,
            String embeddingProviderKey,
            String embeddingModelName
    ) {}
}
