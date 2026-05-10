package com.learningos.modules.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor
public class User {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, length = 20)
    private String kind = "guest";          // guest / user

    @Column(nullable = false, length = 20)
    private String status = "active";       // active / disabled

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @Column(name = "merged_into_user_id", columnDefinition = "uuid")
    private UUID mergedIntoUserId;
}
