package com.pawpasta.glowscan_be.skinanalysis.domain;

import com.pawpasta.glowscan_be.skinanalysis.domain.enums.ObservationType;
import com.pawpasta.glowscan_be.skinanalysis.domain.enums.ObservationVisibility;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "analysis_observations", schema = "app_skin_analysis")
public class AnalysisObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false,
            foreignKey = @ForeignKey(name = "analysis_observations_session_id_fkey"))
    private AnalysisSession session;

    @Enumerated(EnumType.STRING)
    @Column(name = "observation_type", nullable = false, length = 50)
    private ObservationType observationType;

    @Column(name = "severity", nullable = false)
    private Short severity;

    @Column(name = "confidence", nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private ObservationVisibility visibility;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "box_2d", nullable = false, columnDefinition = "jsonb")
    private List<Integer> box2d = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mask", columnDefinition = "jsonb")
    private List<List<Integer>> mask;

    @Column(name = "display_order", nullable = false)
    private Short displayOrder;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "timestamptz default CURRENT_TIMESTAMP")
    private OffsetDateTime createdAt;
}
