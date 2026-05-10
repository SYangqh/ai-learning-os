package com.learningos.modules.llm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_llm_preferences")
@Getter @Setter @NoArgsConstructor
public class UserLlmPreference {

    @Id
    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "chat_provider_key", length = 50)
    private String chatProviderKey;

    @Column(name = "chat_model_name", length = 100)
    private String chatModelName;

    @Column(name = "embedding_provider_key", length = 50)
    private String embeddingProviderKey;

    @Column(name = "embedding_model_name", length = 100)
    private String embeddingModelName;

    @Column(name = "updated_at", columnDefinition = "timestamptz")
    private OffsetDateTime updatedAt;
}
