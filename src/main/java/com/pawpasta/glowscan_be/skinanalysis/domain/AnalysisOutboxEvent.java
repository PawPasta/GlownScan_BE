package com.pawpasta.glowscan_be.skinanalysis.domain;

import com.pawpasta.glowscan_be.skinanalysis.domain.enums.AnalysisOutboxEventType;
import com.pawpasta.glowscan_be.skinanalysis.domain.enums.AnalysisOutboxStatus;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "analysis_outbox", schema = "app_skin_analysis")
public class AnalysisOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false,
            foreignKey = @ForeignKey(name = "analysis_outbox_session_id_fkey"))
    private AnalysisSession session;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private AnalysisOutboxEventType eventType = AnalysisOutboxEventType.PROCESS_ANALYSIS;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AnalysisOutboxStatus status = AnalysisOutboxStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload = new LinkedHashMap<>();

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Generated(event = EventType.INSERT)
    @Column(name = "available_at", nullable = false, insertable = false,
            columnDefinition = "timestamptz default CURRENT_TIMESTAMP")
    private OffsetDateTime availableAt;

    @Column(name = "locked_at", columnDefinition = "timestamptz")
    private OffsetDateTime lockedAt;

    @Column(name = "processed_at", columnDefinition = "timestamptz")
    private OffsetDateTime processedAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "timestamptz default CURRENT_TIMESTAMP")
    private OffsetDateTime createdAt;
}
