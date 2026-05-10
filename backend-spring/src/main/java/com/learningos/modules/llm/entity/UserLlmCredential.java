package com.learningos.modules.llm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_llm_credentials")
@Getter @Setter @NoArgsConstructor
public class UserLlmCredential {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "provider_key", nullable = false, length = 50)
    private String providerKey;

    /** Base64(ciphertext + GCM tag) */
    @Column(name = "enc_ciphertext", nullable = false, columnDefinition = "text")
    private String encCiphertext;

    /** Base64(12-byte IV) */
    @Column(name = "enc_iv", nullable = false, length = 50)
    private String encIv;

    /** Base64(16-byte GCM tag) */
    @Column(name = "enc_tag", nullable = false, length = 50)
    private String encTag;

    /** 使用的主密钥 ID（用于轮换解密） */
    @Column(name = "key_id", nullable = false, length = 50)
    private String keyId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "timestamptz")
    private OffsetDateTime updatedAt;

    @Column(name = "last_used_at", columnDefinition = "timestamptz")
    private OffsetDateTime lastUsedAt;

    @Column(name = "revoked_at", columnDefinition = "timestamptz")
    private OffsetDateTime revokedAt;

    public boolean isRevoked() {
        return revokedAt != null;
    }
}
