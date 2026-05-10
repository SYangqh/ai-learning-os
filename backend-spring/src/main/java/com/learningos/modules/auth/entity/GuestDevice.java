package com.learningos.modules.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "guest_devices")
@Getter @Setter @NoArgsConstructor
public class GuestDevice {

    @Id
    @Column(name = "device_id", columnDefinition = "uuid")
    private UUID deviceId;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @CreationTimestamp
    @Column(name = "first_seen_at", updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime firstSeenAt;

    @Column(name = "last_seen_at", columnDefinition = "timestamptz")
    private OffsetDateTime lastSeenAt;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @Column(name = "ip_hash", length = 64)
    private String ipHash;
}
