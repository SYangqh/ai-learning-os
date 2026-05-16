package com.learningos.modules.llm.service;

import com.learningos.common.exception.AppException;
import com.learningos.infrastructure.crypto.CryptoService;
import com.learningos.infrastructure.crypto.EncryptedPayload;
import com.learningos.modules.llm.entity.UserLlmCredential;
import com.learningos.modules.llm.repository.UserLlmCredentialRepository;
import com.learningos.modules.observability.service.ObservabilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LlmCredentialService {

    private final UserLlmCredentialRepository credentialRepository;
    private final CryptoService cryptoService;
    private final ObservabilityService observabilityService;

    /** 新增或更新 API Key（upsert） */
    @Transactional
    public UserLlmCredential upsert(UUID userId, String providerKey, String plainApiKey) {
        // 用 JPQL UPDATE 直接撤销旧凭据，确保在 INSERT 前已落库
        int revoked = credentialRepository.revokeByUserAndProvider(userId, providerKey, OffsetDateTime.now());
        if (revoked > 0) {
            log.debug("Revoked {} old credential(s) for user={} provider={}", revoked, userId, providerKey);
        }

        // 加密新 Key
        EncryptedPayload payload = cryptoService.encrypt(plainApiKey);

        UserLlmCredential cred = new UserLlmCredential();
        cred.setUserId(userId);
        cred.setProviderKey(providerKey);
        cred.setEncCiphertext(payload.ciphertext());
        cred.setEncIv(payload.iv());
        cred.setEncTag(payload.tag());
        cred.setKeyId(payload.keyId());
        cred.setUpdatedAt(OffsetDateTime.now());
        credentialRepository.save(cred);

        log.info("Upserted LLM credential for user={} provider={}", userId, providerKey);
        observabilityService.audit(userId, "CREDENTIAL_SAVE", "CREDENTIAL", providerKey,
                Map.of("provider", providerKey, "action", revoked > 0 ? "update" : "create"));
        return cred;
    }

    /** 解密并返回明文 API Key */
    @Transactional
    public String decryptApiKey(UUID userId, String providerKey) {
        UserLlmCredential cred = credentialRepository
                .findByUserIdAndProviderKeyAndRevokedAtIsNull(userId, providerKey)
                .orElseThrow(() -> AppException.notFound("未找到 " + providerKey + " 的 API Key，请先配置"));

        String plainKey = cryptoService.decrypt(new EncryptedPayload(
            cred.getEncCiphertext(), cred.getEncIv(), cred.getKeyId()
        ));

        // 更新 last_used_at
        cred.setLastUsedAt(OffsetDateTime.now());
        credentialRepository.save(cred);

        return plainKey;
    }

    /** 列出已配置的凭据（不解密，只返回元信息） */
    @Transactional(readOnly = true)
    public List<CredentialSummary> list(UUID userId) {
        return credentialRepository.findByUserIdAndRevokedAtIsNull(userId)
                .stream()
                .map(c -> new CredentialSummary(c.getId(), c.getProviderKey(),
                        maskKey(cryptoService.decrypt(new EncryptedPayload(
                            c.getEncCiphertext(), c.getEncIv(), c.getKeyId()))),
                        c.getLastUsedAt()))
                .toList();
    }

    /** 软删除凭据 */
    @Transactional
    public void revoke(UUID credentialId, UUID userId) {
        UserLlmCredential cred = credentialRepository.findById(credentialId)
                .orElseThrow(() -> AppException.notFound("凭据不存在"));
        if (!cred.getUserId().equals(userId)) throw AppException.forbidden("无权操作此凭据");
        cred.setRevokedAt(OffsetDateTime.now());
        credentialRepository.save(cred);
    }

    private String maskKey(String plainKey) {
        if (plainKey == null || plainKey.length() < 8) return "****";
        return plainKey.substring(0, 7) + "****" + plainKey.substring(plainKey.length() - 4);
    }

    public record CredentialSummary(UUID id, String providerKey, String masked,
                                    java.time.OffsetDateTime lastUsedAt) {}
}
