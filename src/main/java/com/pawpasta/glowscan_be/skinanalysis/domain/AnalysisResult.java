package com.pawpasta.glowscan_be.skinanalysis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "analysis_results", schema = "app_skin_analysis")
public class AnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "analysis_results_session_id_fkey"))
    private AnalysisSession session;

    @Column(name = "quality_accepted", nullable = false)
    private boolean qualityAccepted;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quality_issues", nullable = false, columnDefinition = "jsonb")
    private List<String> qualityIssues = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "limitations", nullable = false, columnDefinition = "jsonb")
    private List<String> limitations = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> providerPayload = new LinkedHashMap<>();

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "timestamptz default CURRENT_TIMESTAMP")
    private OffsetDateTime createdAt;
}
