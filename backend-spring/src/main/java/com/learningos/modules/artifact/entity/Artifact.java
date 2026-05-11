package com.learningos.modules.artifact.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "artifacts")
@Getter @Setter @NoArgsConstructor
public class Artifact {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(name = "session_id", nullable = false, columnDefinition = "uuid")
    private UUID sessionId;

    @Column(name = "stage_id", nullable = false, columnDefinition = "uuid")
    private UUID stageId;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "node_key", nullable = false, length = 50)
    private String nodeKey;

    /** CODE / NOTE */
    @Column(nullable = false, length = 20)
    private String type;

    @Column(columnDefinition = "text", nullable = false)
    private String content;

    /** submitted / passed / needs_revision */
    @Column(nullable = false, length = 20)
    private String status = "submitted";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;
}
