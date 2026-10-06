package com.pawpasta.glowscan_be.skinanalysis.domain;

import com.pawpasta.glowscan_be.skinanalysis.domain.enums.AnalysisImageView;
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
@Table(name = "analysis_images", schema = "app_skin_analysis")
public class AnalysisImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false,
            foreignKey = @ForeignKey(name = "analysis_images_session_id_fkey"))
    private AnalysisSession session;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_view", nullable = false, length = 20)
    private AnalysisImageView imageView;

    @Column(name = "storage_public_id", nullable = false, columnDefinition = "text")
    private String storagePublicId;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "byte_size", nullable = false)
    private Long byteSize;

    @Column(name = "width", nullable = false)
    private Integer width;

    @Column(name = "height", nullable = false)
    private Integer height;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false,
            columnDefinition = "timestamptz default CURRENT_TIMESTAMP")
    private OffsetDateTime createdAt;

    @Column(name = "deleted_at", columnDefinition = "timestamptz")
    private OffsetDateTime deletedAt;
}
