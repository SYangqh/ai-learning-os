package com.learningos.modules.user.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "user_profiles")
@Getter @Setter @NoArgsConstructor
public class UserProfile {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String background;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<String> skills;

    @Column(nullable = false, length = 200)
    private String target;

    @Column(name = "learning_style", length = 50)
    private String learningStyle = "project";

    @Column(name = "daily_time")
    private Integer dailyTime = 60;

    @Column(name = "analogy_basis", length = 500)
    private String analogyBasis;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;
}
