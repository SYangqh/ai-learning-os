package com.learningos.modules.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "user_mastery")
@Getter @Setter @NoArgsConstructor
public class UserMastery {

    @EmbeddedId
    private UserMasteryId id;

    /**
     * 掌握度分数，0~100。
     * 50 为初始值（见 MasteryService），通过评审通过/失败动态调整。
     */
    @Column(name = "mastery_score", nullable = false)
    private int masteryScore = 50;

    @Column(name = "last_tested_at", columnDefinition = "timestamptz", nullable = false)
    private OffsetDateTime lastTestedAt = OffsetDateTime.now();
}
