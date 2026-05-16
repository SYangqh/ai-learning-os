package com.learningos.modules.observability.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "llm_error_log")
@Getter @Setter @NoArgsConstructor
public class LlmErrorLog {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "session_id", columnDefinition = "uuid")
    private UUID sessionId;

    @Column(name = "provider_key", length = 50)
    private String providerKey;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "error_type", length = 100)
    private String errorType;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Type(JsonBinaryType.class)
    @Column(name = "request_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> requestSnapshot;

    @Column(name = "trace_id", length = 36)
    private String traceId;

    @CreationTimestamp
    @Column(name = "created_at", columnDefinition = "timestamptz", updatable = false)
    private OffsetDateTime createdAt;
}
