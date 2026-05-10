package com.learningos.modules.llm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "llm_providers")
@Getter @Setter @NoArgsConstructor
public class LlmProvider {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, unique = true, length = 50)
    private String key;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false, length = 30)
    private String type;    // ANTHROPIC / OPENAI_COMPAT

    @Column(name = "base_url", length = 300)
    private String baseUrl;

    @Column(name = "supports_stream", nullable = false)
    private boolean supportsStream = true;

    @Column(name = "supports_embeddings", nullable = false)
    private boolean supportsEmbeddings = true;

    @Column(nullable = false)
    private boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;
}
