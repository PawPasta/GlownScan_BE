package com.pawpasta.glowscan_be.skinanalysis.domain;

import com.pawpasta.glowscan_be.auth.domain.User;
import com.pawpasta.glowscan_be.skinanalysis.domain.enums.AnalysisKind;
import com.pawpasta.glowscan_be.skinanalysis.domain.enums.AnalysisProvider;
import com.pawpasta.glowscan_be.skinanalysis.domain.enums.AnalysisStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "analysis_sessions", schema = "app_skin_analysis")
public class AnalysisSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "analysis_sessions_user_id_fkey"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AnalysisStatus status = AnalysisStatus.UPLOAD_PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_kind", nullable = false, length = 30)
    private AnalysisKind analysisKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 30)
    private AnalysisProvider provider = AnalysisProvider.GEMINI;

    @Column(name = "provider_task_id", length = 255)
    private String providerTaskId;

    @Column(name = "model_version", length = 150)
    private String modelVersion;

    @Column(name = "prompt_version", length = 100)
    private String promptVersion;

    @Column(name = "result_schema_version", length = 100)
    private String resultSchemaVersion;

    @Column(name = "rules_version", length = 100)
    private String rulesVersion;

    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "next_attempt_at", columnDefinition = "timestamptz")
    private OffsetDateTime nextAttemptAt;

    @Column(name = "started_at", columnDefinition = "timestamptz")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at", columnDefinition = "timestamptz")
    private OffsetDateTime completedAt;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "timestamptz default CURRENT_TIMESTAMP")
    private OffsetDateTime createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "timestamptz default CURRENT_TIMESTAMP")
    private OffsetDateTime updatedAt;
}
