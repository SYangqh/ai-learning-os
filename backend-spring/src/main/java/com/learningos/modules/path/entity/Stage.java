package com.learningos.modules.path.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "stages")
@Getter @Setter @NoArgsConstructor
public class Stage {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(name = "path_id", nullable = false, columnDefinition = "uuid")
    private UUID pathId;

    @Column(name = "stage_index", nullable = false)
    private int stageIndex;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String goal;

    @Column(name = "skill_id", length = 100)
    private String skillId;     // 关联 Skill 资源文件的 id

    @Column(name = "graph_name", length = 100)
    private String graphName;   // 原 FastAPI 版字段，兼容保留

    @Column(nullable = false, length = 20)
    private String status = "locked";   // locked / active / completed

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;
}
