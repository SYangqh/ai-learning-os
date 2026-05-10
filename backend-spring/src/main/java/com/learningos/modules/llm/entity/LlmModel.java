package com.learningos.modules.llm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "llm_models")
@Getter @Setter @NoArgsConstructor
public class LlmModel {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(name = "provider_key", nullable = false, length = 50)
    private String providerKey;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(nullable = false, length = 20)
    private String task;    // chat / embeddings

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "context_window")
    private Integer contextWindow;
}
