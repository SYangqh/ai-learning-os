package com.learningos.modules.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "auth_identities")
@Getter @Setter @NoArgsConstructor
public class AuthIdentity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String type;        // email / oauth

    @Column(length = 50)
    private String provider;    // google/github/... (type=oauth 时使用)

    @Column(name = "provider_user_id")
    private String providerUserId;

    @Column(columnDefinition = "citext")
    private String email;

    @Column(name = "email_verified_at", columnDefinition = "timestamptz")
    private OffsetDateTime emailVerifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;
}
