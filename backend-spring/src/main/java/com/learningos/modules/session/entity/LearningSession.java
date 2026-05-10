package com.learningos.modules.session.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "learning_sessions")
@Getter @Setter @NoArgsConstructor
public class LearningSession {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(name = "stage_id", nullable = false, columnDefinition = "uuid")
    private UUID stageId;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @CreationTimestamp
    @Column(name = "started_at", updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at", columnDefinition = "timestamptz")
    private OffsetDateTime finishedAt;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> progress;   // { current_node, ... }
}
